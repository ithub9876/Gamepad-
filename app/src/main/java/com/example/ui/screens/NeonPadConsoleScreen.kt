package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hardware.BluetoothHidStatus
import com.example.hardware.GyroTelemetry
import com.example.hardware.WebBridgeState
import com.example.model.ActiveModal
import com.example.model.ClusterTransform
import com.example.model.ControlClusterId
import com.example.model.LayoutPreset
import com.example.model.PsButton
import com.example.ui.components.ButtonMapperModal
import com.example.ui.components.CenterConsoleDeck
import com.example.ui.components.ConnectionHubModal
import com.example.ui.components.EditableClusterContainer
import com.example.ui.components.HapticAndGyroModal
import com.example.ui.components.NeonAnalogThumbstick
import com.example.ui.components.PlayStationActionCluster
import com.example.ui.components.PlayStationDpad
import com.example.ui.components.ShoulderTriggerCluster
import com.example.ui.components.TurboAndMacroModal
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
import com.example.viewmodel.GamepadUiState
import com.example.viewmodel.GamepadViewModel
import kotlin.math.roundToInt

@Composable
fun NeonPadConsoleScreen(
    viewModel: GamepadViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val btStatus by viewModel.btStatus.collectAsStateWithLifecycle()
    val webState by viewModel.webBridgeState.collectAsStateWithLifecycle()
    val gyroTelemetry by viewModel.gyroTelemetry.collectAsStateWithLifecycle()

    // Runtime Permission Launcher for Android 12+ Bluetooth Connect & Advertise
    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.btHidManager.refreshState()
        viewModel.btHidManager.startHidProfile()
    }

    // System Discoverable Launcher so Windows laptop can find phone in Bluetooth Settings
    val discoverableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.btHidManager.refreshState()
    }

    val requestBtPerms = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            viewModel.btHidManager.startHidProfile()
        }
    }

    val makeDiscoverable = {
        try {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            discoverableLauncher.launch(intent)
        } catch (_: Exception) {
        }
    }

    BackHandler(enabled = uiState.activeModal != ActiveModal.NONE || uiState.isLayoutEditMode) {
        if (uiState.activeModal != ActiveModal.NONE) {
            viewModel.closeModal()
        } else if (uiState.isLayoutEditMode) {
            viewModel.toggleLayoutEditMode()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("neonpad_console_root")
    ) {
        // Subtle Futuristic Cyberpunk Background Grid & Radial Neon Aura
        ConsoleBackgroundGrid()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight

            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Top HUD Bar: Connection Status, Gyro, Turbo, Layout Editor, Mapper, Haptics
                TopConsoleHudBar(
                    uiState = uiState,
                    btStatus = btStatus,
                    webState = webState,
                    onOpenModal = viewModel::openModal,
                    onToggleGyro = viewModel::toggleGyroEnabled,
                    onToggleTurbo = viewModel::toggleTurboMode,
                    onToggleLayoutEditor = viewModel::toggleLayoutEditMode
                )

                // Optional Layout Editor Floating Control Strip when in Edit Mode
                if (uiState.isLayoutEditMode) {
                    LayoutEditorControlStrip(
                        uiState = uiState,
                        onSelectPreset = viewModel::applyLayoutPreset,
                        onChangeScale = { scale ->
                            viewModel.updateClusterScale(uiState.selectedClusterForEdit, scale)
                        },
                        onResetDefaults = viewModel::resetLayoutToDefaults,
                        onDone = viewModel::toggleLayoutEditMode
                    )
                }

                // Main Interactive Gamepad Deck (Adapts cleanly between Landscape and Portrait)
                if (isLandscape) {
                    LandscapeGamepadDeck(
                        uiState = uiState,
                        gyroTelemetry = gyroTelemetry,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    PortraitGamepadDeck(
                        uiState = uiState,
                        gyroTelemetry = gyroTelemetry,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Bottom Quick Macro & Connection Telemetry Strip
                BottomMacroAndTelemetryBar(
                    uiState = uiState,
                    btStatus = btStatus,
                    webState = webState,
                    onFireMacro = viewModel::executeMacroCombo,
                    onOpenConnectionHub = { viewModel.openModal(ActiveModal.CONNECTION_HUB) }
                )
            }
        }

        // Modals
        when (uiState.activeModal) {
            ActiveModal.CONNECTION_HUB -> {
                ConnectionHubModal(
                    btStatus = btStatus,
                    webState = webState,
                    onRequestBtPermissions = requestBtPerms,
                    onStartHidProfile = { viewModel.btHidManager.startHidProfile() },
                    onMakeDiscoverable = makeDiscoverable,
                    onConnectPairedHost = { addr -> viewModel.btHidManager.connectToPairedHost(addr) },
                    onDisconnectHost = { viewModel.btHidManager.disconnectCurrentHost() },
                    onSelectOutputMode = { mode -> viewModel.btHidManager.setOutputMode(mode) },
                    onRefreshNetwork = { viewModel.webBridgeServer.refreshLocalIp() },
                    onDismiss = viewModel::closeModal
                )
            }
            ActiveModal.BUTTON_MAPPER -> {
                ButtonMapperModal(
                    mappings = uiState.buttonMappings,
                    onUpdateMapping = viewModel::updateButtonMapping,
                    onResetMappings = viewModel::resetAllButtonMappings,
                    onDismiss = viewModel::closeModal
                )
            }
            ActiveModal.TURBO_MACRO_DECK -> {
                TurboAndMacroModal(
                    turboEnabled = uiState.turboEnabled,
                    turboRateHz = uiState.turboRateHz,
                    turboAssignedButtons = uiState.turboAssignedButtons,
                    macros = uiState.macros,
                    activeMacroId = uiState.activeMacroId,
                    onToggleTurbo = viewModel::toggleTurboMode,
                    onChangeTurboRate = viewModel::setTurboRateHz,
                    onToggleTurboButton = viewModel::toggleButtonTurboAssignment,
                    onExecuteMacro = viewModel::executeMacroCombo,
                    onAddMacro = viewModel::addCustomMacro,
                    onDeleteMacro = viewModel::deleteMacro,
                    onDismiss = viewModel::closeModal
                )
            }
            ActiveModal.HAPTIC_GYRO_SETTINGS -> {
                HapticAndGyroModal(
                    hapticIntensity = uiState.hapticIntensity,
                    customDurationMs = uiState.customHapticDurationMs,
                    customAmplitude = uiState.customHapticAmplitude,
                    vibrateOnRelease = uiState.vibrateOnRelease,
                    hasHardwareVibrator = viewModel.hapticManager.hasHardwareVibrator,
                    gyroEnabled = uiState.gyroEnabled,
                    gyroTargetRightStick = uiState.gyroTargetRightStick,
                    gyroSensitivity = uiState.gyroSensitivity,
                    gyroTelemetry = gyroTelemetry,
                    onSelectIntensity = viewModel::setHapticIntensity,
                    onUpdateCustomHaptic = viewModel::updateCustomHaptics,
                    onToggleVibrateRelease = viewModel::toggleVibrateOnRelease,
                    onTestRumble = { viewModel.hapticManager.triggerRumbleWaveform(180L) },
                    onToggleGyro = viewModel::toggleGyroEnabled,
                    onSetGyroTargetRight = viewModel::setGyroTargetRightStick,
                    onChangeGyroSensitivity = viewModel::updateGyroSensitivity,
                    onCalibrateGyro = viewModel::calibrateGyroCenter,
                    onDismiss = viewModel::closeModal
                )
            }
            ActiveModal.NONE -> {}
        }
    }
}

@Composable
private fun ConsoleBackgroundGrid() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Ambient Top & Bottom Neon Glows
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * 0.5f, 0f),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.5f, 0f)
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonAzure.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * 0.5f, h),
                radius = w * 0.55f
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.5f, h)
        )

        // Subtle Technical Grid Lines
        val step = 36.dp.toPx()
        var x = 0f
        while (x < w) {
            drawLine(
                color = DeckBorderIdle.copy(alpha = 0.18f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1f
            )
            x += step
        }
        var y = 0f
        while (y < h) {
            drawLine(
                color = DeckBorderIdle.copy(alpha = 0.18f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
            )
            y += step
        }
    }
}

@Composable
private fun TopConsoleHudBar(
    uiState: GamepadUiState,
    btStatus: BluetoothHidStatus,
    webState: WebBridgeState,
    onOpenModal: (ActiveModal) -> Unit,
    onToggleGyro: () -> Unit,
    onToggleTurbo: () -> Unit,
    onToggleLayoutEditor: () -> Unit
) {
    val isLiveConnected = btStatus.connectedHostAddress != null || webState.connectedClients > 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeepSpaceNavy.copy(alpha = 0.92f))
            .border(1.dp, DeckBorderIdle, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // Brand & Live Connection Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(CarbonSurface)
                .border(
                    1.dp,
                    if (isLiveConnected) ConnectedGreen else NeonCyan.copy(alpha = 0.6f),
                    RoundedCornerShape(10.dp)
                )
                .clickable { onOpenModal(ActiveModal.CONNECTION_HUB) }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("connection_status_pill")
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isLiveConnected) ConnectedGreen else NeonCyan)
            )
            Icon(
                imageVector = Icons.Default.BluetoothConnected,
                contentDescription = "Connection Status",
                tint = if (isLiveConnected) ConnectedGreen else NeonCyan,
                modifier = Modifier.size(16.dp)
            )
            Column {
                Text(
                    text = "NEONPAD",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimaryWhite
                )
                Text(
                    text = when {
                        btStatus.connectedHostName != null -> "BT HID: ${btStatus.connectedHostName}"
                        webState.connectedClients > 0 -> "WEB BRIDGE LIVE (${webState.connectedClients} PC)"
                        else -> "CONNECT PC (${webState.localIp}:${webState.port})"
                    },
                    fontSize = 9.sp,
                    color = if (isLiveConnected) ConnectedGreen else NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Quick Action Pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudChipButton(
                label = if (uiState.gyroEnabled) "GYRO ON" else "GYRO",
                icon = Icons.Default.Explore,
                isActive = uiState.gyroEnabled,
                activeColor = NeonCyan,
                onClick = onToggleGyro,
                testTag = "hud_gyro_button"
            )

            HudChipButton(
                label = if (uiState.turboEnabled) "TURBO ${uiState.turboRateHz}Hz" else "TURBO",
                icon = Icons.Default.Bolt,
                isActive = uiState.turboEnabled,
                activeColor = TurboAmber,
                onClick = onToggleTurbo,
                testTag = "hud_turbo_button"
            )

            HudChipButton(
                label = "MACROS",
                icon = Icons.Default.Bolt,
                isActive = uiState.activeModal == ActiveModal.TURBO_MACRO_DECK,
                activeColor = TurboAmber,
                onClick = { onOpenModal(ActiveModal.TURBO_MACRO_DECK) },
                testTag = "hud_macros_button"
            )

            HudChipButton(
                label = "MAPPER",
                icon = Icons.Default.Tune,
                isActive = uiState.activeModal == ActiveModal.BUTTON_MAPPER,
                activeColor = NeonCyan,
                onClick = { onOpenModal(ActiveModal.BUTTON_MAPPER) },
                testTag = "hud_mapper_button"
            )

            HudChipButton(
                label = if (uiState.isLayoutEditMode) "EDITING" else "LAYOUT",
                icon = Icons.Default.OpenWith,
                isActive = uiState.isLayoutEditMode,
                activeColor = TurboAmber,
                onClick = onToggleLayoutEditor,
                testTag = "hud_layout_button"
            )

            HudChipButton(
                label = "HAPTICS",
                icon = Icons.Default.Vibration,
                isActive = uiState.activeModal == ActiveModal.HAPTIC_GYRO_SETTINGS,
                activeColor = NeonCyan,
                onClick = { onOpenModal(ActiveModal.HAPTIC_GYRO_SETTINGS) },
                testTag = "hud_haptics_button"
            )
        }
    }
}

@Composable
private fun HudChipButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) activeColor else CarbonElevated)
            .border(
                1.dp,
                if (isActive) activeColor else DeckBorderIdle,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) ObsidianBlack else activeColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) ObsidianBlack else TextPrimaryWhite
        )
    }
}

@Composable
private fun LayoutEditorControlStrip(
    uiState: GamepadUiState,
    onSelectPreset: (LayoutPreset) -> Unit,
    onChangeScale: (Float) -> Unit,
    onResetDefaults: () -> Unit,
    onDone: () -> Unit
) {
    val selectedCluster = uiState.selectedClusterForEdit
    val currentTransform = uiState.clusterTransforms[selectedCluster] ?: ClusterTransform()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CarbonSurface.copy(alpha = 0.96f))
            .border(1.5.dp, TurboAmber, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("layout_editor_strip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "DRAG ANY MODULE TO MOVE • SELECTED: ${selectedCluster.title} (${(currentTransform.scale * 100).roundToInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = TurboAmber,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onResetDefaults,
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("reset_layout_button")
                ) {
                    Text("Reset", fontSize = 10.sp, color = TextPrimaryWhite)
                }
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = TurboAmber),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("save_layout_button")
                ) {
                    Text("Save Layout", fontSize = 10.sp, color = ObsidianBlack, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Size:",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
            Slider(
                value = currentTransform.scale,
                onValueChange = onChangeScale,
                valueRange = 0.65f..1.40f,
                colors = SliderDefaults.colors(
                    thumbColor = TurboAmber,
                    activeTrackColor = TurboAmber
                ),
                modifier = Modifier.weight(1f)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                LayoutPreset.entries.forEach { preset ->
                    val active = uiState.activePreset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) NeonCyan else CarbonElevated)
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = preset.displayName,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) ObsidianBlack else TextPrimaryWhite
                        )
                    }
                }
            }
        }
    }
}

/**
 * Landscape Console Deck: Authentic DualSense Widescreen Layout.
 */
@Composable
private fun LandscapeGamepadDeck(
    uiState: GamepadUiState,
    gyroTelemetry: GyroTelemetry,
    viewModel: GamepadViewModel,
    modifier: Modifier = Modifier
) {
    val transforms = uiState.clusterTransforms

    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Top Row: Left Shoulder (L2/L1) + Center Deck (Share/Touchpad/Options/PS) + Right Shoulder (R1/R2)
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            EditableClusterContainer(
                clusterId = ControlClusterId.LEFT_SHOULDERS,
                transform = transforms[ControlClusterId.LEFT_SHOULDERS] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.LEFT_SHOULDERS,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                ShoulderTriggerCluster(
                    isLeft = true,
                    bumperButton = PsButton.L1,
                    triggerButton = PsButton.L2_BTN,
                    isBumperPressed = PsButton.L1 in uiState.effectiveButtons,
                    isTriggerPressed = PsButton.L2_BTN in uiState.effectiveButtons,
                    triggerAnalogValue = uiState.l2Trigger,
                    isBumperTurbo = uiState.turboEnabled && PsButton.L1 in uiState.turboAssignedButtons,
                    isTriggerTurbo = uiState.turboEnabled && PsButton.L2_BTN in uiState.turboAssignedButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    onAnalogTriggerChange = { viewModel.onTriggerValueChange(isLeft = true, value = it) }
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.CENTER_DECK,
                transform = transforms[ControlClusterId.CENTER_DECK] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.CENTER_DECK,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                CenterConsoleDeck(
                    pressedButtons = uiState.effectiveButtons,
                    leftX = uiState.effectiveLeftX,
                    leftY = uiState.effectiveLeftY,
                    rightX = uiState.effectiveRightX,
                    rightY = uiState.effectiveRightY,
                    gyroEnabled = uiState.gyroEnabled,
                    pitchDeg = gyroTelemetry.pitchDeg,
                    rollDeg = gyroTelemetry.rollDeg,
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.RIGHT_SHOULDERS,
                transform = transforms[ControlClusterId.RIGHT_SHOULDERS] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.RIGHT_SHOULDERS,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                ShoulderTriggerCluster(
                    isLeft = false,
                    bumperButton = PsButton.R1,
                    triggerButton = PsButton.R2_BTN,
                    isBumperPressed = PsButton.R1 in uiState.effectiveButtons,
                    isTriggerPressed = PsButton.R2_BTN in uiState.effectiveButtons,
                    triggerAnalogValue = uiState.r2Trigger,
                    isBumperTurbo = uiState.turboEnabled && PsButton.R1 in uiState.turboAssignedButtons,
                    isTriggerTurbo = uiState.turboEnabled && PsButton.R2_BTN in uiState.turboAssignedButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    onAnalogTriggerChange = { viewModel.onTriggerValueChange(isLeft = false, value = it) }
                )
            }
        }

        // Main Ergonomic Controls Row: D-Pad + Left Stick + Right Stick + PlayStation Action Cluster
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            EditableClusterContainer(
                clusterId = ControlClusterId.DPAD,
                transform = transforms[ControlClusterId.DPAD] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.DPAD,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                PlayStationDpad(
                    pressedButtons = uiState.effectiveButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onDpadChange = viewModel::setDpadDirection,
                    dpadSize = 136.dp
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.LEFT_STICK,
                transform = transforms[ControlClusterId.LEFT_STICK] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.LEFT_STICK,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                NeonAnalogThumbstick(
                    label = "LEFT",
                    stickClickButton = PsButton.L3,
                    stickX = uiState.effectiveLeftX,
                    stickY = uiState.effectiveLeftY,
                    isStickClickPressed = PsButton.L3 in uiState.effectiveButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onStickMove = viewModel::onLeftStickMove,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    diameter = 122.dp
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.RIGHT_STICK,
                transform = transforms[ControlClusterId.RIGHT_STICK] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.RIGHT_STICK,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                NeonAnalogThumbstick(
                    label = "RIGHT",
                    stickClickButton = PsButton.R3,
                    stickX = uiState.effectiveRightX,
                    stickY = uiState.effectiveRightY,
                    isStickClickPressed = PsButton.R3 in uiState.effectiveButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onStickMove = viewModel::onRightStickMove,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    diameter = 122.dp
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.ACTION_BUTTONS,
                transform = transforms[ControlClusterId.ACTION_BUTTONS] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.ACTION_BUTTONS,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                PlayStationActionCluster(
                    pressedButtons = uiState.effectiveButtons,
                    turboButtons = if (uiState.turboEnabled) uiState.turboAssignedButtons else emptySet(),
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    clusterSize = 144.dp,
                    buttonSize = 48.dp
                )
            }
        }
    }
}

/**
 * Portrait Console Deck: Ergonomic Vertical Handheld Console Layout so users can play
 * comfortably in either portrait or landscape orientation.
 */
@Composable
private fun PortraitGamepadDeck(
    uiState: GamepadUiState,
    gyroTelemetry: GyroTelemetry,
    viewModel: GamepadViewModel,
    modifier: Modifier = Modifier
) {
    val transforms = uiState.clusterTransforms

    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Row 1: Left & Right Shoulder Triggers (L2/L1 and R1/R2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            EditableClusterContainer(
                clusterId = ControlClusterId.LEFT_SHOULDERS,
                transform = transforms[ControlClusterId.LEFT_SHOULDERS] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.LEFT_SHOULDERS,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                ShoulderTriggerCluster(
                    isLeft = true,
                    bumperButton = PsButton.L1,
                    triggerButton = PsButton.L2_BTN,
                    isBumperPressed = PsButton.L1 in uiState.effectiveButtons,
                    isTriggerPressed = PsButton.L2_BTN in uiState.effectiveButtons,
                    triggerAnalogValue = uiState.l2Trigger,
                    isBumperTurbo = uiState.turboEnabled && PsButton.L1 in uiState.turboAssignedButtons,
                    isTriggerTurbo = uiState.turboEnabled && PsButton.L2_BTN in uiState.turboAssignedButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    onAnalogTriggerChange = { viewModel.onTriggerValueChange(isLeft = true, value = it) }
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.RIGHT_SHOULDERS,
                transform = transforms[ControlClusterId.RIGHT_SHOULDERS] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.RIGHT_SHOULDERS,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                ShoulderTriggerCluster(
                    isLeft = false,
                    bumperButton = PsButton.R1,
                    triggerButton = PsButton.R2_BTN,
                    isBumperPressed = PsButton.R1 in uiState.effectiveButtons,
                    isTriggerPressed = PsButton.R2_BTN in uiState.effectiveButtons,
                    triggerAnalogValue = uiState.r2Trigger,
                    isBumperTurbo = uiState.turboEnabled && PsButton.R1 in uiState.turboAssignedButtons,
                    isTriggerTurbo = uiState.turboEnabled && PsButton.R2_BTN in uiState.turboAssignedButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    onAnalogTriggerChange = { viewModel.onTriggerValueChange(isLeft = false, value = it) }
                )
            }
        }

        // Row 2: Center Console Touchpad, Share, Options, PS Home
        EditableClusterContainer(
            clusterId = ControlClusterId.CENTER_DECK,
            transform = transforms[ControlClusterId.CENTER_DECK] ?: ClusterTransform(),
            isEditMode = uiState.isLayoutEditMode,
            isSelected = uiState.selectedClusterForEdit == ControlClusterId.CENTER_DECK,
            onSelect = viewModel::selectClusterForEdit,
            onDragDeltaDp = viewModel::moveClusterBy
        ) {
            CenterConsoleDeck(
                pressedButtons = uiState.effectiveButtons,
                leftX = uiState.effectiveLeftX,
                leftY = uiState.effectiveLeftY,
                rightX = uiState.effectiveRightX,
                rightY = uiState.effectiveRightY,
                gyroEnabled = uiState.gyroEnabled,
                pitchDeg = gyroTelemetry.pitchDeg,
                rollDeg = gyroTelemetry.rollDeg,
                isEditMode = uiState.isLayoutEditMode,
                onButtonPress = viewModel::onButtonPress,
                onButtonRelease = viewModel::onButtonRelease
            )
        }

        // Row 3: D-Pad (Left) + PlayStation Action Cluster (Right)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            EditableClusterContainer(
                clusterId = ControlClusterId.DPAD,
                transform = transforms[ControlClusterId.DPAD] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.DPAD,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                PlayStationDpad(
                    pressedButtons = uiState.effectiveButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onDpadChange = viewModel::setDpadDirection,
                    dpadSize = 144.dp
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.ACTION_BUTTONS,
                transform = transforms[ControlClusterId.ACTION_BUTTONS] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.ACTION_BUTTONS,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                PlayStationActionCluster(
                    pressedButtons = uiState.effectiveButtons,
                    turboButtons = if (uiState.turboEnabled) uiState.turboAssignedButtons else emptySet(),
                    isEditMode = uiState.isLayoutEditMode,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    clusterSize = 152.dp,
                    buttonSize = 50.dp
                )
            }
        }

        // Row 4: Dual Analog Thumbsticks (Left Stick L3 + Right Stick R3)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            EditableClusterContainer(
                clusterId = ControlClusterId.LEFT_STICK,
                transform = transforms[ControlClusterId.LEFT_STICK] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.LEFT_STICK,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                NeonAnalogThumbstick(
                    label = "LEFT",
                    stickClickButton = PsButton.L3,
                    stickX = uiState.effectiveLeftX,
                    stickY = uiState.effectiveLeftY,
                    isStickClickPressed = PsButton.L3 in uiState.effectiveButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onStickMove = viewModel::onLeftStickMove,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    diameter = 126.dp
                )
            }

            EditableClusterContainer(
                clusterId = ControlClusterId.RIGHT_STICK,
                transform = transforms[ControlClusterId.RIGHT_STICK] ?: ClusterTransform(),
                isEditMode = uiState.isLayoutEditMode,
                isSelected = uiState.selectedClusterForEdit == ControlClusterId.RIGHT_STICK,
                onSelect = viewModel::selectClusterForEdit,
                onDragDeltaDp = viewModel::moveClusterBy
            ) {
                NeonAnalogThumbstick(
                    label = "RIGHT",
                    stickClickButton = PsButton.R3,
                    stickX = uiState.effectiveRightX,
                    stickY = uiState.effectiveRightY,
                    isStickClickPressed = PsButton.R3 in uiState.effectiveButtons,
                    isEditMode = uiState.isLayoutEditMode,
                    onStickMove = viewModel::onRightStickMove,
                    onButtonPress = viewModel::onButtonPress,
                    onButtonRelease = viewModel::onButtonRelease,
                    diameter = 126.dp
                )
            }
        }
    }
}

@Composable
private fun BottomMacroAndTelemetryBar(
    uiState: GamepadUiState,
    btStatus: BluetoothHidStatus,
    webState: WebBridgeState,
    onFireMacro: (com.example.model.MacroCombo) -> Unit,
    onOpenConnectionHub: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeepSpaceNavy.copy(alpha = 0.9f))
            .border(1.dp, DeckBorderIdle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        // Quick Macro Combo Triggers
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "COMBOS:",
                style = MaterialTheme.typography.labelSmall,
                color = TurboAmber,
                fontWeight = FontWeight.Bold
            )
            uiState.macros.take(3).forEach { macro ->
                val isRunning = uiState.activeMacroId == macro.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isRunning) TurboAmber else CarbonElevated)
                        .border(
                            1.dp,
                            if (isRunning) TurboAmber else NeonAzure.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onFireMacro(macro) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("quick_macro_${macro.id}")
                ) {
                    Text(
                        text = macro.buttons.joinToString("+") { it.symbol },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) ObsidianBlack else NeonCyan
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Live Active Input Readout & Web Bridge Address
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable(onClick = onOpenConnectionHub)
        ) {
            val activeSymbols = uiState.effectiveButtons.joinToString(" ") { it.symbol }
            Text(
                text = if (activeSymbols.isNotEmpty()) {
                    "INPUT: $activeSymbols"
                } else {
                    "BRIDGE: ${webState.bridgeUrl}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (activeSymbols.isNotEmpty()) ConnectedGreen else TextSecondarySlate
            )
            if (btStatus.reportsSentCount > 0 || webState.framesBroadcasted > 0) {
                Text(
                    text = "TX:${btStatus.reportsSentCount + webState.framesBroadcasted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonCyan
                )
            }
        }
    }
}
