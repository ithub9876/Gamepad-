package com.example.hardware

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.model.PsButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

enum class BtHidOutputMode(val title: String, val subtitle: String) {
    GAMEPAD_ONLY(
        "Standard Bluetooth Gamepad (joy.cpl & Gamepad Tester)",
        "Pure 52-byte USB/BT Gamepad descriptor for Windows & hardwaretester.com/gamepad"
    ),
    HYBRID_ALL(
        "Hybrid Gamepad + Keyboard",
        "Composite Gamepad + WASD Keyboard + Mouse Aim"
    ),
    KEYBOARD_MOUSE_ONLY(
        "WASD Keyboard + Mouse Only",
        "Pure Bluetooth Keyboard + Mouse for keyboard-only PC games"
    )
}

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
    val isRegistering: Boolean = false,
    val outputMode: BtHidOutputMode = BtHidOutputMode.GAMEPAD_ONLY,
    val connectedHostName: String? = null,
    val connectedHostAddress: String? = null,
    val statusDetail: String = "Initializing Bluetooth HID...",
    val diagnosticLogs: List<String> = emptyList(),
    val pairedHosts: List<PairedBtHost> = emptyList(),
    val reportsSentCount: Long = 0L,
    val lastReportLatencyUs: Long = 0L
)

@SuppressLint("MissingPermission")
class BluetoothHidManager(private val context: Context) {

    companion object {
        const val REPORT_ID_GAMEPAD: Int = 1
        const val REPORT_ID_KEYBOARD: Int = 2
        const val REPORT_ID_MOUSE: Int = 3

        /**
         * Pure, Single-Collection 52-Byte Standard USB/Bluetooth Gamepad Descriptor.
         * Tested against Windows 10/11 `bthhid.sys` + `hidgame.sys` and Chrome `hardwaretester.com/gamepad`.
         *
         * Report ID 1 (7 bytes total payload):
         * - Bytes 0..1: 16 Buttons (B0..B15, matching W3C Standard Gamepad)
         * - Byte 2    : 8-way Hat Switch (0..7 = N..NW, 8 = Neutral)
         * - Byte 3    : X Axis (Left Stick X: -127..+127)
         * - Byte 4    : Y Axis (Left Stick Y: -127..+127)
         * - Byte 5    : Z Axis (Right Stick X: -127..+127)
         * - Byte 6    : Rz Axis (Right Stick Y: -127..+127)
         */
        val GAMEPAD_HID_DESCRIPTOR: ByteArray = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop Ctrls)
            0x09.toByte(), 0x05.toByte(), // Usage (Game Pad)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_GAMEPAD.toByte(), // Report ID (1)

            // 16 Buttons (2 bytes)
            0x05.toByte(), 0x09.toByte(), // Usage Page (Button)
            0x19.toByte(), 0x01.toByte(), // Usage Minimum (Button 1)
            0x29.toByte(), 0x10.toByte(), // Usage Maximum (Button 16)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), // Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), // Report Size (1 bit)
            0x95.toByte(), 0x10.toByte(), // Report Count (16)
            0x81.toByte(), 0x02.toByte(), // Input (Data,Var,Abs)

            // Hat Switch (1 byte)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x39.toByte(), // Usage (Hat switch)
            0x15.toByte(), 0x00.toByte(), // Logical Minimum (0)
            0x25.toByte(), 0x07.toByte(), // Logical Maximum (7)
            0x35.toByte(), 0x00.toByte(), // Physical Minimum (0)
            0x46.toByte(), 0x3B.toByte(), 0x01.toByte(), // Physical Maximum (315)
            0x65.toByte(), 0x14.toByte(), // Unit (Eng Rot:Angular Pos)
            0x75.toByte(), 0x08.toByte(), // Report Size (8 bits)
            0x95.toByte(), 0x01.toByte(), // Report Count (1)
            0x81.toByte(), 0x42.toByte(), // Input (Data,Var,Abs,Null)

            // 4 Analog Stick Axes: X, Y, Z, Rz (4 signed bytes: -127..+127)
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x30.toByte(), // Usage (X)
            0x09.toByte(), 0x31.toByte(), // Usage (Y)
            0x09.toByte(), 0x32.toByte(), // Usage (Z)
            0x09.toByte(), 0x35.toByte(), // Usage (Rz)
            0x15.toByte(), 0x81.toByte(), // Logical Minimum (-127)
            0x25.toByte(), 0x7F.toByte(), // Logical Maximum (+127)
            0x75.toByte(), 0x08.toByte(), // Report Size (8 bits)
            0x95.toByte(), 0x04.toByte(), // Report Count (4)
            0x81.toByte(), 0x02.toByte(), // Input (Data,Var,Abs)

            0xC0.toByte()                 // End Collection
        )

        /**
         * Pure 6-KRO Keyboard Descriptor (for KEYBOARD_MOUSE_ONLY mode)
         */
        val KEYBOARD_HID_DESCRIPTOR: ByteArray = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), REPORT_ID_KEYBOARD.toByte(), // Report ID (2)
            0x05.toByte(), 0x07.toByte(), // Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x02.toByte(),
            0x95.toByte(), 0x01.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x81.toByte(), 0x01.toByte(),
            0x95.toByte(), 0x06.toByte(),
            0x75.toByte(), 0x08.toByte(),
            0x15.toByte(), 0x00.toByte(),
            0x25.toByte(), 0x65.toByte(),
            0x05.toByte(), 0x07.toByte(),
            0x19.toByte(), 0x00.toByte(),
            0x29.toByte(), 0x65.toByte(),
            0x81.toByte(), 0x00.toByte(),
            0xC0.toByte()
        )

        val COMPOSITE_HID_DESCRIPTOR: ByteArray = GAMEPAD_HID_DESCRIPTOR + KEYBOARD_HID_DESCRIPTOR
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var pendingConnectAddress: String? = null
    private var reportsSent = 0L

    private val _status = MutableStateFlow(BluetoothHidStatus())
    val status: StateFlow<BluetoothHidStatus> = _status.asStateFlow()

    init {
        appendLog("BluetoothHidManager initialized.")
        refreshState(preserveStatusText = false)
    }

    private fun appendLog(msg: String) {
        val stamp = timeFormat.format(Date())
        val updated = (_status.value.diagnosticLogs + "[$stamp] $msg").takeLast(8)
        _status.value = _status.value.copy(diagnosticLogs = updated)
    }

    fun setOutputMode(mode: BtHidOutputMode) {
        if (_status.value.outputMode == mode) return
        _status.value = _status.value.copy(outputMode = mode)
        appendLog("Switched HID Descriptor mode to ${mode.name}. Re-registering SDP...")
        if (_status.value.sdpRegistered || hidDevice != null) {
            startHidProfile()
        }
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

    fun refreshState(preserveStatusText: Boolean = true) {
        val adapter = bluetoothAdapter
        val perms = hasBluetoothPermissions()
        if (adapter == null) {
            _status.value = _status.value.copy(
                hardwareSupported = false,
                adapterEnabled = false,
                permissionsGranted = perms,
                statusDetail = "No Bluetooth hardware adapter detected."
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

        val current = _status.value
        val nextStatusDetail = if (preserveStatusText && current.statusDetail.isNotBlank() &&
            current.statusDetail != "Initializing Bluetooth HID..."
        ) {
            current.statusDetail
        } else {
            when {
                !perms -> "Step 1: Tap 'Grant Bluetooth Permissions' below."
                !enabled -> "Bluetooth is off. Turn on Bluetooth to pair with Windows."
                connectedHost != null -> "CONNECTED to ${connectedHost?.name ?: connectedHost?.address}! Press any controller button now."
                current.sdpRegistered -> "HID Gamepad Active! Now pair from Windows (see 3-Step Pairing Guide below)."
                else -> "Tap 'Activate HID Gamepad' below to start."
            }
        }

        _status.value = current.copy(
            hardwareSupported = true,
            adapterEnabled = enabled,
            permissionsGranted = perms,
            pairedHosts = bonded,
            statusDetail = nextStatusDetail
        )
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(pluggedDevice, registered)
            appendLog("onAppStatusChanged: registered=$registered, plugged=${pluggedDevice?.name ?: "none"}")
            _status.value = _status.value.copy(
                sdpRegistered = registered,
                isRegistering = false,
                statusDetail = if (registered) {
                    "HID Gamepad Profile Registered! Ready for Windows connection."
                } else {
                    "HID Gamepad Profile Unregistered."
                }
            )

            if (registered) {
                val targetAddr = pendingConnectAddress
                pendingConnectAddress = null
                val adapter = bluetoothAdapter
                if (targetAddr != null && adapter != null) {
                    mainHandler.postDelayed({
                        try {
                            val dev = adapter.getRemoteDevice(targetAddr)
                            appendLog("Auto-connecting to $targetAddr (${dev.name})...")
                            hidDevice?.connect(dev)
                        } catch (e: Exception) {
                            appendLog("Auto-connect error: ${e.message}")
                        }
                    }, 300L)
                }
            }
            refreshState(preserveStatusText = true)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            val devName = device?.name ?: device?.address ?: "PC"
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    appendLog("SUCCESS: HID Connected to $devName!")
                    _status.value = _status.value.copy(
                        connectedHostName = devName,
                        connectedHostAddress = device?.address,
                        statusDetail = "LIVE CONNECTED to $devName! Open Gamepad Tester & press buttons!"
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost?.address == device?.address) {
                        connectedHost = null
                    }
                    appendLog("DISCONNECTED from $devName (Windows rejected L2CAP HID).")
                    _status.value = _status.value.copy(
                        connectedHostName = null,
                        connectedHostAddress = null,
                        statusDetail = "Windows rejected HID connection to $devName because it was paired BEFORE NeonPad was active! Follow the Yellow 3-Step Fix below: Remove phone in Windows Bluetooth, then Re-Pair while NeonPad is open."
                    )
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    appendLog("Connecting L2CAP HID channel to $devName...")
                    _status.value = _status.value.copy(
                        statusDetail = "Connecting HID Gamepad channel to $devName..."
                    )
                }
            }
            refreshState(preserveStatusText = true)
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                val hid = proxy as? BluetoothHidDevice ?: return
                hidDevice = hid
                appendLog("HID_DEVICE proxy acquired. Registering SDP record...")
                registerGamepadSdp(hid)
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                connectedHost = null
                appendLog("HID_DEVICE service proxy disconnected.")
                _status.value = _status.value.copy(
                    sdpRegistered = false,
                    isRegistering = false,
                    connectedHostName = null,
                    connectedHostAddress = null
                )
            }
        }
    }

    fun startHidProfile() {
        refreshState(preserveStatusText = true)
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions()) {
            _status.value = _status.value.copy(
                statusDetail = "Bluetooth permissions needed. Tap 'Grant Bluetooth Permissions'."
            )
            return
        }
        if (!adapter.isEnabled) {
            _status.value = _status.value.copy(
                statusDetail = "Bluetooth is turned OFF. Please enable Bluetooth."
            )
            return
        }

        _status.value = _status.value.copy(
            isRegistering = true,
            statusDetail = "Registering Bluetooth HID Gamepad SDP service..."
        )

        val existingHid = hidDevice
        if (existingHid != null) {
            try {
                existingHid.unregisterApp()
            } catch (_: Exception) {
            }
            mainHandler.postDelayed({
                registerGamepadSdp(existingHid)
            }, 250L)
            return
        }

        try {
            appendLog("Requesting BluetoothProfile.HID_DEVICE proxy...")
            val supported = adapter.getProfileProxy(
                context,
                profileListener,
                BluetoothProfile.HID_DEVICE
            )
            if (!supported) {
                appendLog("ERROR: getProfileProxy(HID_DEVICE) returned false.")
                _status.value = _status.value.copy(
                    isRegistering = false,
                    statusDetail = "Your phone's OS disables Bluetooth HID peripheral mode. Use the Wi-Fi Bridge below!"
                )
            }
        } catch (e: Exception) {
            appendLog("Exception in getProfileProxy: ${e.message}")
            _status.value = _status.value.copy(
                isRegistering = false,
                statusDetail = "HID init error: ${e.localizedMessage}"
            )
        }
    }

    private fun registerGamepadSdp(hid: BluetoothHidDevice) {
        try {
            val mode = _status.value.outputMode
            val descriptor = when (mode) {
                BtHidOutputMode.GAMEPAD_ONLY -> GAMEPAD_HID_DESCRIPTOR
                BtHidOutputMode.HYBRID_ALL -> COMPOSITE_HID_DESCRIPTOR
                BtHidOutputMode.KEYBOARD_MOUSE_ONLY -> KEYBOARD_HID_DESCRIPTOR
            }
            // Subclass: 0x08 (Gamepad) for Gamepad mode so Windows installs HidGame driver!
            val subclass: Byte = when (mode) {
                BtHidOutputMode.GAMEPAD_ONLY -> 0x08.toByte()
                BtHidOutputMode.HYBRID_ALL -> BluetoothHidDevice.SUBCLASS1_COMBO
                BtHidOutputMode.KEYBOARD_MOUSE_ONLY -> BluetoothHidDevice.SUBCLASS1_KEYBOARD
            }

            val sdpSettings = BluetoothHidDeviceAppSdpSettings(
                "Wireless Controller",
                "NeonPad Bluetooth Gamepad",
                "NeonPad",
                subclass,
                descriptor
            )
            val ok = hid.registerApp(sdpSettings, null, null, callbackExecutor, hidCallback)
            appendLog("registerApp(subclass=$subclass, bytes=${descriptor.size}) -> $ok")
            if (!ok) {
                _status.value = _status.value.copy(
                    isRegistering = false,
                    statusDetail = "Bluetooth HID stack busy. Turn Bluetooth OFF and ON, then tap 'Activate HID Gamepad'."
                )
            }
        } catch (e: Exception) {
            appendLog("registerApp exception: ${e.message}")
            _status.value = _status.value.copy(
                isRegistering = false,
                statusDetail = "SDP Registration Error: ${e.localizedMessage}"
            )
        }
    }

    fun connectToPairedHost(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!hasBluetoothPermissions()) return

        if (hidDevice == null || !_status.value.sdpRegistered) {
            appendLog("HID not registered yet; queueing connection to $address...")
            pendingConnectAddress = address
            startHidProfile()
            return
        }

        try {
            val device = adapter.getRemoteDevice(address)
            appendLog("Calling hidDevice.connect(${device.name ?: address})...")
            _status.value = _status.value.copy(
                statusDetail = "Connecting to ${device.name ?: address}..."
            )
            val initiated = hidDevice?.connect(device) ?: false
            if (!initiated) {
                appendLog("hidDevice.connect() returned false.")
                _status.value = _status.value.copy(
                    statusDetail = "Connection refused. Remove phone from Windows Bluetooth settings and re-pair while NeonPad is open!"
                )
            }
        } catch (e: Exception) {
            appendLog("connect exception: ${e.message}")
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
     * Sends reports continuously at 60Hz when connected so Windows and `hardwaretester.com/gamepad`
     * immediately detect the controller and update all axes & buttons in real time.
     */
    fun sendCompositeInput(
        effectiveButtons: Set<PsButton>,
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
        val mode = _status.value.outputMode
        var anySent = false

        if (mode == BtHidOutputMode.GAMEPAD_ONLY || mode == BtHidOutputMode.HYBRID_ALL) {
            val lx = (leftX.coerceIn(-1f, 1f) * 127f).toInt().toByte()
            val ly = (leftY.coerceIn(-1f, 1f) * 127f).toInt().toByte()
            val rx = (rightX.coerceIn(-1f, 1f) * 127f).toInt().toByte()
            val ry = (rightY.coerceIn(-1f, 1f) * 127f).toInt().toByte()

            val gpReport = byteArrayOf(
                (buttonMask16 and 0xFF).toByte(),
                ((buttonMask16 shr 8) and 0xFF).toByte(),
                (hatSwitchValue and 0xFF).toByte(),
                lx,
                ly,
                rx,
                ry
            )
            try {
                anySent = hid.sendReport(host, REPORT_ID_GAMEPAD, gpReport) || anySent
            } catch (_: Exception) {
            }
        }

        if (mode == BtHidOutputMode.KEYBOARD_MOUSE_ONLY || mode == BtHidOutputMode.HYBRID_ALL) {
            var modifiers = 0
            if (PsButton.L3 in effectiveButtons) modifiers = modifiers or 0x02
            if (PsButton.CIRCLE in effectiveButtons) modifiers = modifiers or 0x01

            val keys = mutableListOf<Byte>()
            fun addKey(code: Int) {
                if (keys.size < 6 && code.toByte() !in keys) keys.add(code.toByte())
            }
            if (leftY < -0.35f) addKey(0x1A) // W
            if (leftY > 0.35f) addKey(0x16)  // S
            if (leftX < -0.35f) addKey(0x04) // A
            if (leftX > 0.35f) addKey(0x07)  // D
            if (PsButton.DPAD_UP in effectiveButtons) addKey(0x52)
            if (PsButton.DPAD_DOWN in effectiveButtons) addKey(0x51)
            if (PsButton.DPAD_LEFT in effectiveButtons) addKey(0x50)
            if (PsButton.DPAD_RIGHT in effectiveButtons) addKey(0x4F)
            if (PsButton.CROSS in effectiveButtons) addKey(0x2C)
            if (PsButton.SQUARE in effectiveButtons) addKey(0x15)
            if (PsButton.TRIANGLE in effectiveButtons) addKey(0x09)
            if (PsButton.L1 in effectiveButtons) addKey(0x14)
            if (PsButton.R1 in effectiveButtons) addKey(0x08)

            val kbReport = ByteArray(8)
            kbReport[0] = modifiers.toByte()
            for (i in 0 until 6) {
                kbReport[i + 2] = if (i < keys.size) keys[i] else 0
            }
            try {
                anySent = hid.sendReport(host, REPORT_ID_KEYBOARD, kbReport) || anySent
            } catch (_: Exception) {
            }
        }

        if (anySent) {
            val elapsedUs = (System.nanoTime() - startNano) / 1000L
            reportsSent++
            if (reportsSent % 10L == 0L || reportsSent < 5L) {
                _status.value = _status.value.copy(
                    reportsSentCount = reportsSent,
                    lastReportLatencyUs = elapsedUs
                )
            }
        }
        return anySent
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
