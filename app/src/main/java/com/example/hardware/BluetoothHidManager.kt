package com.example.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

data class PairedBtHost(
    val name: String,
    val address: String,
    val isConnected: Boolean
)

data class BluetoothHidStatus(
    val hardwareSupported: Boolean = false,
    val adapterEnabled: Boolean = false,
    val permissionsGranted: Boolean = false,
    val sdpRegistered: Boolean = false,
    val connectedHostName: String? = null,
    val connectedHostAddress: String? = null,
    val statusDetail: String = "Checking Bluetooth HID hardware...",
    val pairedHosts: List<PairedBtHost> = emptyList(),
    val reportsSentCount: Long = 0L,
    val lastReportLatencyUs: Long = 0L
)

@SuppressLint("MissingPermission")
class BluetoothHidManager(private val context: Context) {

    companion object {
        const val REPORT_ID: Int = 1

        /**
         * Authentic USB HID Gamepad Report Descriptor (Report ID 1, 7 bytes total):
         * - Byte 0 & 1: 16 Buttons bitmask (Button 1 .. Button 16)
         * - Byte 2: 8-way Hat Switch (D-Pad: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW, 8=Neutral)
         * - Byte 3: Left Stick X (-127 .. +127)
         * - Byte 4: Left Stick Y (-127 .. +127)
         * - Byte 5: Right Stick X / Z (-127 .. +127)
         * - Byte 6: Right Stick Y / Rz (-127 .. +127)
         */
        val GAMEPAD_HID_DESCRIPTOR: ByteArray = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop Ctrls)
            0x09.toByte(), 0x05.toByte(), // Usage (Game Pad)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID.toByte(), // Report ID (1)

            // 16 Buttons (2 bytes)
            0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (Button 1)
            0x29.toByte(), 0x10.toByte(), // Usage Maximum (Button 16)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1 bit)
            0x95.toByte(), 0x10.toByte(), // Report Count (16)
            0x81.toByte(), 0x02.toByte(), // Input (Data,Var,Abs)

            // Hat Switch (D-Pad, 1 byte)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x39.toByte(), // Usage (Hat switch)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x07.toByte(), // Logical Maximum (7)
            0x35.toByte(), 0x00.toByte(), // Physical Minimum (0)
            0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315 deg)
            0x65.toByte(), 0x14.toByte(), // Unit (Eng Rot:Angular Pos)
            0x75.toByte(), 0x08.toByte(), // Report Size (8 bits)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x81.toByte(), 0x42.toByte(), // Input (Data,Var,Abs,Null)

            // 4 Analog Axes: X, Y, Z, Rz (4 signed bytes: -127 to +127)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // Usage (X - Left Stick X)
            0x09.toByte(), 0x31.toByte(), // Usage (Y - Left Stick Y)
            0x09.toByte(), 0x32.toByte(), // Usage (Z - Right Stick X)
            0x09.toByte(), 0x35.toByte(), // Usage (Rz - Right Stick Y)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (+127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8 bits)
            0x95.toByte(), 0x04.toByte(), // Report Count (4 axes)
            0x81.toByte(), 0x02.toByte(), // Input (Data,Var,Abs)

            0xC0.toByte()                 // End Collection
        )
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var reportsSent = 0L

    private val _status = MutableStateFlow(BluetoothHidStatus())
    val status: StateFlow<BluetoothHidStatus> = _status.asStateFlow()

    init {
        refreshState()
    }

    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun refreshState() {
        val adapter = bluetoothAdapter
        val perms = hasBluetoothPermissions()
        if (adapter == null) {
            _status.value = BluetoothHidStatus(
                hardwareSupported = false,
                adapterEnabled = false,
                permissionsGranted = perms,
                statusDetail = "No Bluetooth hardware adapter detected on this device."
            )
            return
        }

        val enabled = try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            false
        }

        val bonded = if (perms && enabled) {
            try {
                adapter.bondedDevices?.map { dev ->
                    PairedBtHost(
                        name = dev.name ?: "Windows PC (${dev.address.takeLast(5)})",
                        address = dev.address,
                        isConnected = connectedHost?.address == dev.address
                    )
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        _status.value = _status.value.copy(
            hardwareSupported = true,
            adapterEnabled = enabled,
            permissionsGranted = perms,
            pairedHosts = bonded,
            statusDetail = when {
                !perms -> "Grant Bluetooth permissions to enable direct HID Gamepad mode."
                !enabled -> "Bluetooth is turned off. Enable Bluetooth to pair with your Windows laptop."
                connectedHost != null -> "Connected as Native Bluetooth Gamepad to ${connectedHost?.name ?: connectedHost?.address}"
                _status.value.sdpRegistered -> "NeonPad HID Gamepad active & ready. Select your laptop below or pair from Windows Bluetooth settings."
                else -> "Ready to initialize Native Bluetooth HID Gamepad profile."
            }
        )
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            if (registered && pluggedDevice != null && connectedHost == null) {
                try {
                    hidDevice?.connect(pluggedDevice)
                } catch (_: Exception) {
                }
            }
            _status.value = _status.value.copy(
                sdpRegistered = registered,
                statusDetail = if (registered) {
                    "Bluetooth HID Gamepad SDP registered! Pair 'NeonPad Controller' in Windows Bluetooth Settings."
                } else {
                    "Bluetooth HID Gamepad profile unregistered."
                }
            )
            refreshState()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    _status.value = _status.value.copy(
                        connectedHostName = device?.name ?: "Windows Laptop",
                        connectedHostAddress = device?.address,
                        statusDetail = "Connected to ${device?.name ?: device?.address} via Native Bluetooth HID"
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost?.address == device?.address) {
                        connectedHost = null
                    }
                    _status.value = _status.value.copy(
                        connectedHostName = null,
                        connectedHostAddress = null,
                        statusDetail = "Disconnected from ${device?.name ?: "host"}."
                    )
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _status.value = _status.value.copy(
                        statusDetail = "Connecting to ${device?.name ?: device?.address}..."
                    )
                }
            }
            refreshState()
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                val hid = proxy as? BluetoothHidDevice ?: return
                hidDevice = hid
                registerGamepadSdp(hid)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedHost = null
                _status.value = _status.value.copy(
                    sdpRegistered = false,
                    connectedHostName = null,
                    connectedHostAddress = null,
                    statusDetail = "Bluetooth HID service disconnected."
                )
            }
        }
    }

    fun startHidProfile() {
        refreshState()
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions() || !adapter.isEnabled) return

        val existingHid = hidDevice
        if (existingHid != null) {
            registerGamepadSdp(existingHid)
            return
        }

        try {
            val supported = adapter.getProfileProxy(
                context,
                profileListener,
                BluetoothProfile.HID_DEVICE
            )
            if (!supported) {
                _status.value = _status.value.copy(
                    statusDetail = "This device's OEM Bluetooth stack restricts HID peripheral proxy. Use Wi-Fi Web Bridge for zero-install PC connection."
                )
            }
        } catch (e: Exception) {
            _status.value = _status.value.copy(
                statusDetail = "HID initialization error: ${e.localizedMessage ?: "Use Wi-Fi Web Bridge"}"
            )
        }
    }

    private fun registerGamepadSdp(hid: BluetoothHidDevice) {
        try {
            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                "NeonPad Wireless Controller",
                "NeonPad Digital Console Gamepad",
                "NeonPad",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                GAMEPAD_HID_DESCRIPTOR
            )
            val inQos = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800,
                9,
                0,
                11250,
                BluetoothHidDeviceAppQosSettings.MAX
            )
            val outQos = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800,
                9,
                0,
                11250,
                BluetoothHidDeviceAppQosSettings.MAX
            )
            hid.registerApp(sdpSettings, inQos, outQos, callbackExecutor, hidCallback)
        } catch (e: Exception) {
            _status.value = _status.value.copy(
                statusDetail = "Failed to register HID SDP: ${e.localizedMessage}"
            )
        }
    }

    fun connectToPairedHost(address: String) {
        val adapter = bluetoothAdapter ?: return
        val hid = hidDevice ?: return
        if (!hasBluetoothPermissions()) return
        try {
            val device = adapter.getRemoteDevice(address)
            hid.connect(device)
        } catch (e: Exception) {
            _status.value = _status.value.copy(
                statusDetail = "Connection error: ${e.localizedMessage}"
            )
        }
    }

    fun disconnectCurrentHost() {
        val host = connectedHost ?: return
        try {
            hidDevice?.disconnect(host)
        } catch (_: Exception) {
        }
    }

    /**
     * Sends a 7-byte standard USB/Bluetooth Gamepad HID report to the connected Windows laptop.
     */
    fun sendGamepadReport(
        buttonMask16: Int,
        hatSwitchValue: Int,
        leftX: Float,
        leftY: Float,
        rightX: Float,
        rightY: Float
    ): Boolean {
        val hid = hidDevice ?: return false
        val host = connectedHost ?: return false
        if (!_status.value.sdpRegistered) return false

        val startNano = System.nanoTime()
        val lx = (leftX.coerceIn(-1f, 1f) * 127f).toInt().toByte()
        val ly = (leftY.coerceIn(-1f, 1f) * 127f).toInt().toByte()
        val rx = (rightX.coerceIn(-1f, 1f) * 127f).toInt().toByte()
        val ry = (rightY.coerceIn(-1f, 1f) * 127f).toInt().toByte()

        val report = byteArrayOf(
            (buttonMask16 and 0xFF).toByte(),
            ((buttonMask16 shr 8) and 0xFF).toByte(),
            (hatSwitchValue and 0xFF).toByte(),
            lx,
            ly,
            rx,
            ry
        )

        return try {
            val sent = hid.sendReport(host, REPORT_ID, report)
            val elapsedUs = (System.nanoTime() - startNano) / 1000L
            if (sent) {
                reportsSent++
                if (reportsSent % 10L == 0L || reportsSent < 5L) {
                    _status.value = _status.value.copy(
                        reportsSentCount = reportsSent,
                        lastReportLatencyUs = elapsedUs
                    )
                }
            }
            sent
        } catch (_: Exception) {
            false
        }
    }

    fun stopHidProfile() {
        try {
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
        } catch (_: Exception) {
        }
        hidDevice = null
        connectedHost = null
    }
}
