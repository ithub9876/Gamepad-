package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hardware.BluetoothHidStatus
import com.example.hardware.GyroTelemetry
import com.example.hardware.WebBridgeState
import com.example.model.ButtonMappingEntry
import com.example.model.HapticIntensity
import com.example.model.MacroCombo
import com.example.model.PsButton
import com.example.ui.theme.CarbonElevated
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.ConnectedGreen
import com.example.ui.theme.DeckBorderIdle
import com.example.ui.theme.DeepSpaceNavy
import com.example.ui.theme.NeonAzure
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate
import com.example.ui.theme.TurboAmber
import kotlin.math.roundToInt

/**
 * Modal 1: Direct Connection Hub (Native Bluetooth HID Gamepad + Zero-Install Wi-Fi Web Bridge)
 */
@Composable
fun ConnectionHubModal(
    btStatus: BluetoothHidStatus,
    webState: WebBridgeState,
    onRequestBtPermissions: () -> Unit,
    onStartHidProfile: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onConnectPairedHost: (String) -> Unit,
    onDisconnectHost: () -> Unit,
    onSelectOutputMode: (com.example.hardware.BtHidOutputMode) -> Unit,
    onRefreshNetwork: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DeepSpaceNavy)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("connection_hub_modal")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothConnected,
                        contentDescription = "Connection Hub",
                        tint = NeonCyan
                    )
                    Text(
                        text = "ZERO-INSTALL PC CONNECTION HUB",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonCyan
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_connection_hub_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryWhite)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section A: Primary Native Bluetooth HID Gamepad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .border(
                        1.dp,
                        if (btStatus.connectedHostAddress != null) ConnectedGreen else DeckBorderIdle,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = NeonCyan)
                        Text(
                            text = "1. NATIVE BLUETOOTH HID GAMEPAD",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryWhite
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                when {
                                    btStatus.connectedHostAddress != null -> ConnectedGreen.copy(alpha = 0.2f)
                                    btStatus.sdpRegistered -> NeonCyan.copy(alpha = 0.18f)
                                    else -> TurboAmber.copy(alpha = 0.18f)
                                }
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when {
                                btStatus.connectedHostAddress != null -> "HID CONNECTED"
                                btStatus.sdpRegistered -> "HID READY"
                                else -> "SETUP NEEDED"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                btStatus.connectedHostAddress != null -> ConnectedGreen
                                btStatus.sdpRegistered -> NeonCyan
                                else -> TurboAmber
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = btStatus.statusDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (btStatus.connectedHostAddress != null) ConnectedGreen else TurboAmber
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mandatory Windows Bluetooth Pairing Explanation Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .border(1.dp, TurboAmber.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "⚠️ WHY WINDOWS / GAMEPAD TESTER DOESN'T SEE YOUR PHONE YET (3-STEP FIX):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TurboAmber
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "If you paired your phone to DESKTOP-0AJQGJD BEFORE opening NeonPad, Windows saved your phone as 'Audio/Phone Only' and blocks Gamepad connections.\n" +
                            "1. Tap 'Activate HID Gamepad' then 'Make Discoverable' below.\n" +
                            "2. On your Windows laptop: Settings -> Bluetooth & devices -> Remove/Unpair your phone.\n" +
                            "3. Click 'Add device -> Bluetooth' on Windows while this screen is open. Windows will now install the 'Wireless Controller' Gamepad driver!",
                        fontSize = 11.sp,
                        color = TextPrimaryWhite
                    )
                }

                if (btStatus.diagnosticLogs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBlack)
                            .padding(8.dp)
                    ) {
                        btStatus.diagnosticLogs.takeLast(4).forEach { logLine ->
                            Text(
                                text = logLine,
                                fontSize = 9.sp,
                                color = NeonCyan
                            )
                        }
                    }
                }

                if (btStatus.reportsSentCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "HID Reports Sent: ${btStatus.reportsSentCount} | Last Dispatch: ${btStatus.lastReportLatencyUs} µs",
                        style = MaterialTheme.typography.labelSmall,
                        color = ConnectedGreen
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Output Mode Selector (Universal Hybrid vs Pure Gamepad vs WASD+Mouse)
                Text(
                    text = "Bluetooth HID Output Mode:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    com.example.hardware.BtHidOutputMode.entries.forEach { mode ->
                        val selected = btStatus.outputMode == mode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonCyan else CarbonElevated)
                                .border(
                                    1.dp,
                                    if (selected) NeonCyan else DeckBorderIdle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onSelectOutputMode(mode)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    com.example.hardware.BtHidOutputMode.HYBRID_ALL -> "HYBRID (ALL)"
                                    com.example.hardware.BtHidOutputMode.GAMEPAD_ONLY -> "GAMEPAD"
                                    com.example.hardware.BtHidOutputMode.KEYBOARD_MOUSE_ONLY -> "WASD + MOUSE"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) ObsidianBlack else TextPrimaryWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!btStatus.permissionsGranted) {
                        Button(
                            onClick = onRequestBtPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.testTag("grant_bt_permissions_button")
                        ) {
                            Text("Grant Bluetooth Permissions", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onStartHidProfile,
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            modifier = Modifier.testTag("activate_hid_button")
                        ) {
                            Text(
                                text = if (btStatus.sdpRegistered) "Re-Sync HID Profile" else "Activate HID Gamepad",
                                color = ObsidianBlack,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = onMakeDiscoverable,
                            modifier = Modifier.testTag("bt_discoverable_button")
                        ) {
                            Text("Make Discoverable", color = NeonCyan)
                        }
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                } catch (_: Exception) {
                                }
                            }
                        ) {
                            Text("BT Settings", color = TextPrimaryWhite)
                        }
                    }
                }

                if (btStatus.pairedHosts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Paired Bluetooth Devices (Tap Connect PC — Auto-activates HID):",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    btStatus.pairedHosts.forEach { host ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CarbonElevated)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = host.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimaryWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = host.address,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySlate
                                )
                            }
                            if (host.isConnected) {
                                OutlinedButton(onClick = onDisconnectHost) {
                                    Text("Disconnect", color = TurboAmber)
                                }
                            } else {
                                Button(
                                    onClick = { onConnectPairedHost(host.address) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonAzure)
                                ) {
                                    Text("Connect PC", color = TextPrimaryWhite)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Section B: Zero-Install Wi-Fi Web & Windows OS Game Bridge
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .border(
                        1.dp,
                        if (webState.osInjectorClients > 0 || webState.connectedClients > 0) ConnectedGreen else NeonCyan.copy(alpha = 0.5f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = NeonCyan)
                        Text(
                            text = "2. WI-FI WINDOWS OS GAME BRIDGE",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryWhite
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                webState.osInjectorClients > 0 -> "OS GAME BRIDGE LIVE"
                                webState.connectedClients > 0 -> "${webState.connectedClients} BROWSER LIVE"
                                else -> "SERVER ONLINE"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (webState.connectedClients > 0) ConnectedGreen else NeonCyan
                        )
                        IconButton(
                            onClick = onRefreshNetwork,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh IP",
                                tint = NeonCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "To control external PC Games, Steam & Windows Desktop over Wi-Fi: open this URL on your laptop and click the yellow '1-Click Launcher (NeonPad-PC-Bridge.bat)' or paste the Win+R command shown on the page:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondarySlate
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .border(1.dp, NeonCyan, RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = webState.bridgeUrl,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("NeonPad Bridge URL", webState.bridgeUrl))
                            Toast.makeText(context, "Bridge URL copied: ${webState.bridgeUrl}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("copy_bridge_url_button")
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy URL",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy URL", color = NeonCyan)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "OS Game Injector Clients: ${webState.osInjectorClients} | Browser Clients: ${webState.connectedClients} | Frames Sent: ${webState.framesBroadcasted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (webState.osInjectorClients > 0) ConnectedGreen else TurboAmber
                )
            }
        }
    }
}

/**
 * Modal 2: Custom Button Mapping Editor
 */
@Composable
fun ButtonMapperModal(
    mappings: Map<PsButton, ButtonMappingEntry>,
    onUpdateMapping: (PsButton, Int, String) -> Unit,
    onResetMappings: () -> Unit,
    onDismiss: () -> Unit
) {
    var editingButton by remember { mutableStateOf<PsButton?>(null) }
    var selectedHidBit by remember { mutableIntStateOf(0) }
    var customKeyLabel by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DeepSpaceNavy)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .testTag("button_mapper_modal")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "CUSTOM BUTTON & HID MAPPER",
                    style = MaterialTheme.typography.titleLarge,
                    color = NeonCyan
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onResetMappings,
                        modifier = Modifier.testTag("reset_mappings_button")
                    ) {
                        Text("Reset Defaults", color = TurboAmber)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryWhite)
                    }
                }
            }

            Text(
                text = "Tap any PlayStation button to customize its USB/Bluetooth HID Bit (1–16) and PC Action Label.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondarySlate
            )

            Spacer(modifier = Modifier.height(10.dp))

            val targetBtn = editingButton
            if (targetBtn != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarbonElevated)
                        .border(1.dp, TurboAmber, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Editing ${targetBtn.label} (${targetBtn.symbol})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TurboAmber
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HID Gamepad Button Index: #${selectedHidBit + 1} (Bit $selectedHidBit)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryWhite
                    )
                    Slider(
                        value = selectedHidBit.toFloat(),
                        onValueChange = { selectedHidBit = it.roundToInt().coerceIn(0, 15) },
                        valueRange = 0f..15f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan
                        )
                    )
                    OutlinedTextField(
                        value = customKeyLabel,
                        onValueChange = { customKeyLabel = it },
                        label = { Text("PC Action / Key Binding Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onUpdateMapping(
                                    targetBtn,
                                    selectedHidBit,
                                    customKeyLabel.ifBlank { targetBtn.defaultPcKey }
                                )
                                editingButton = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Save Mapping", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(onClick = { editingButton = null }) {
                            Text("Cancel", color = TextPrimaryWhite)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(PsButton.entries) { btn ->
                    val entry = mappings[btn] ?: ButtonMappingEntry(btn, btn.defaultHidBit, btn.defaultPcKey)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarbonSurface)
                            .border(1.dp, DeckBorderIdle, RoundedCornerShape(10.dp))
                            .clickable {
                                editingButton = btn
                                selectedHidBit = entry.mappedHidBit.coerceIn(0, 15)
                                customKeyLabel = entry.mappedPcKey
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(btn.accentColor.copy(alpha = 0.2f))
                                    .border(1.dp, btn.accentColor, CircleShape)
                            ) {
                                Text(
                                    text = btn.symbol,
                                    color = btn.accentColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Column {
                                Text(
                                    text = btn.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimaryWhite,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "PC Action: ${entry.mappedPcKey}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondarySlate
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianBlack)
                                .border(1.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (entry.mappedHidBit in 0..15) {
                                    "HID BTN #${entry.mappedHidBit + 1}"
                                } else {
                                    "HAT D-PAD"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal 3: Rapid-Fire Turbo Engine & Programmable Macro Combos Deck
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TurboAndMacroModal(
    turboEnabled: Boolean,
    turboRateHz: Int,
    turboAssignedButtons: Set<PsButton>,
    macros: List<MacroCombo>,
    activeMacroId: String?,
    onToggleTurbo: () -> Unit,
    onChangeTurboRate: (Int) -> Unit,
    onToggleTurboButton: (PsButton) -> Unit,
    onExecuteMacro: (MacroCombo) -> Unit,
    onAddMacro: (String, List<PsButton>) -> Unit,
    onDeleteMacro: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newMacroName by remember { mutableStateOf("") }
    val recordedSequence = remember { mutableStateListOf<PsButton>() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DeepSpaceNavy)
                .border(1.5.dp, TurboAmber.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("turbo_macro_modal")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚡ RAPID-FIRE TURBO & MACRO COMBOS",
                    style = MaterialTheme.typography.titleLarge,
                    color = TurboAmber
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryWhite)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Turbo Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Rapid-Fire Turbo Oscillator",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryWhite
                        )
                        Text(
                            text = "Pulses held buttons automatically at $turboRateHz Hz",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondarySlate
                        )
                    }
                    Switch(
                        checked = turboEnabled,
                        onCheckedChange = { onToggleTurbo() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = TurboAmber
                        ),
                        modifier = Modifier.testTag("turbo_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Turbo Pulse Frequency: $turboRateHz presses/sec",
                    style = MaterialTheme.typography.labelSmall,
                    color = TurboAmber
                )
                Slider(
                    value = turboRateHz.toFloat(),
                    onValueChange = { onChangeTurboRate(it.roundToInt()) },
                    valueRange = 5f..25f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = TurboAmber,
                        activeTrackColor = TurboAmber
                    )
                )

                Text(
                    text = "Select Buttons Armed with Turbo (⚡):",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                val turboCandidates = listOf(
                    PsButton.CROSS, PsButton.CIRCLE, PsButton.SQUARE, PsButton.TRIANGLE,
                    PsButton.L1, PsButton.R1, PsButton.L2_BTN, PsButton.R2_BTN
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    turboCandidates.forEach { btn ->
                        val armed = btn in turboAssignedButtons
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (armed) TurboAmber else CarbonElevated)
                                .border(1.dp, if (armed) TurboAmber else DeckBorderIdle, RoundedCornerShape(8.dp))
                                .clickable { onToggleTurboButton(btn) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${btn.symbol} ${btn.label}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (armed) ObsidianBlack else TextPrimaryWhite
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Macro Combos Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .padding(14.dp)
            ) {
                Text(
                    text = "One-Tap Macro Combos",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(8.dp))

                macros.forEach { macro ->
                    val isRunning = activeMacroId == macro.id
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarbonElevated)
                            .border(
                                1.dp,
                                if (isRunning) ConnectedGreen else DeckBorderIdle,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = macro.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimaryWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = macro.buttons.joinToString(" → ") { it.symbol },
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { onExecuteMacro(macro) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Fire Macro",
                                    tint = ObsidianBlack,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("FIRE", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { onDeleteMacro(macro.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Macro", tint = TextSecondarySlate)
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = DeckBorderIdle
                )

                Text(
                    text = "Build New Custom Macro Sequence:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TurboAmber
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = newMacroName,
                    onValueChange = { newMacroName = it },
                    label = { Text("Combo Name (e.g. Parry Counter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (recordedSequence.isEmpty()) {
                        "Tap buttons below to append to combo sequence:"
                    } else {
                        "Sequence: " + recordedSequence.joinToString(" → ") { it.symbol }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        PsButton.CROSS, PsButton.CIRCLE, PsButton.SQUARE, PsButton.TRIANGLE,
                        PsButton.L1, PsButton.R1, PsButton.L2_BTN, PsButton.R2_BTN, PsButton.L3, PsButton.R3
                    ).forEach { b ->
                        OutlinedButton(
                            onClick = {
                                if (recordedSequence.size < 8) recordedSequence.add(b)
                            }
                        ) {
                            Text("+ ${b.symbol}", color = TextPrimaryWhite)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            if (recordedSequence.isNotEmpty()) {
                                val title = newMacroName.ifBlank {
                                    "Custom Combo (${recordedSequence.joinToString("+") { it.symbol }})"
                                }
                                onAddMacro(title, recordedSequence.toList())
                                newMacroName = ""
                                recordedSequence.clear()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TurboAmber)
                    ) {
                        Text("Save Macro Combo", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { recordedSequence.clear() }) {
                        Text("Clear Steps", color = TextSecondarySlate)
                    }
                }
            }
        }
    }
}

/**
 * Modal 4: Adjustable Haptic Vibration Patterns & Real Gyroscope Motion Aiming Settings
 */
@Composable
fun HapticAndGyroModal(
    hapticIntensity: HapticIntensity,
    customDurationMs: Long,
    customAmplitude: Int,
    vibrateOnRelease: Boolean,
    hasHardwareVibrator: Boolean,
    gyroEnabled: Boolean,
    gyroTargetRightStick: Boolean,
    gyroSensitivity: Float,
    gyroTelemetry: GyroTelemetry,
    onSelectIntensity: (HapticIntensity) -> Unit,
    onUpdateCustomHaptic: (Long, Int) -> Unit,
    onToggleVibrateRelease: () -> Unit,
    onTestRumble: () -> Unit,
    onToggleGyro: () -> Unit,
    onSetGyroTargetRight: (Boolean) -> Unit,
    onChangeGyroSensitivity: (Float) -> Unit,
    onCalibrateGyro: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(DeepSpaceNavy)
                .border(1.5.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
                .testTag("haptic_gyro_modal")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Vibration, contentDescription = null, tint = NeonCyan)
                    Text(
                        text = "HAPTIC ENGINE & MOTION GYRO AIM",
                        style = MaterialTheme.typography.titleLarge,
                        color = NeonCyan
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimaryWhite)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Haptics Tuning Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Physical Haptic Feedback Engine",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryWhite
                    )
                    Text(
                        text = if (hasHardwareVibrator) "MOTOR READY" else "NO MOTOR",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasHardwareVibrator) ConnectedGreen else TurboAmber
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HapticIntensity.entries.forEach { mode ->
                        val selected = hapticIntensity == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) NeonCyan else CarbonElevated)
                                .border(1.dp, if (selected) NeonCyan else DeckBorderIdle, RoundedCornerShape(8.dp))
                                .clickable { onSelectIntensity(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) ObsidianBlack else TextPrimaryWhite
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Custom Pulse Duration: ${customDurationMs}ms | Intensity Amplitude: ${(customAmplitude * 100 / 255)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Slider(
                    value = customDurationMs.toFloat(),
                    onValueChange = { onUpdateCustomHaptic(it.roundToInt().toLong(), customAmplitude) },
                    valueRange = 5f..100f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )
                Slider(
                    value = customAmplitude.toFloat(),
                    onValueChange = { onUpdateCustomHaptic(customDurationMs, it.roundToInt()) },
                    valueRange = 20f..255f,
                    colors = SliderDefaults.colors(thumbColor = NeonAzure, activeTrackColor = NeonAzure)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Switch(
                            checked = vibrateOnRelease,
                            onCheckedChange = { onToggleVibrateRelease() },
                            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBlack, checkedTrackColor = NeonCyan)
                        )
                        Text(
                            text = "Micro-Tick on Button Release",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimaryWhite
                        )
                    }
                    Button(
                        onClick = onTestRumble,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.testTag("test_rumble_button")
                    ) {
                        Text("Test Rumble Motor", color = ObsidianBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Gyroscope Motion Aiming / Steering Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Motion Gyroscope Aiming & Steering",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryWhite
                        )
                        Text(
                            text = "Sensor: ${gyroTelemetry.sensorName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondarySlate
                        )
                    }
                    Switch(
                        checked = gyroEnabled,
                        onCheckedChange = { onToggleGyro() },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBlack, checkedTrackColor = NeonCyan),
                        modifier = Modifier.testTag("gyro_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { onSetGyroTargetRight(true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (gyroTargetRightStick) NeonCyan else CarbonElevated
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Right Stick (FPS Aim)",
                            color = if (gyroTargetRightStick) ObsidianBlack else TextPrimaryWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { onSetGyroTargetRight(false) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!gyroTargetRightStick) NeonCyan else CarbonElevated
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Left Stick (Racing Steer)",
                            color = if (!gyroTargetRightStick) ObsidianBlack else TextPrimaryWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Gyro Sensitivity Multiplier: ${"%.2f".format(gyroSensitivity)}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
                Slider(
                    value = gyroSensitivity,
                    onValueChange = onChangeGyroSensitivity,
                    valueRange = 0.3f..3.0f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Live Tilt: Pitch ${"%.1f".format(gyroTelemetry.pitchDeg)}° | Roll ${"%.1f".format(gyroTelemetry.rollDeg)}°",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ConnectedGreen
                    )
                    OutlinedButton(
                        onClick = onCalibrateGyro,
                        modifier = Modifier.testTag("calibrate_gyro_button")
                    ) {
                        Text("Calibrate Zero Center", color = NeonCyan)
                    }
                }
            }
        }
    }
}
