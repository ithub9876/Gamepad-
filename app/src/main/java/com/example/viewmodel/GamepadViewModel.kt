package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ControllerPreferencesRepository
import com.example.hardware.BluetoothHidManager
import com.example.hardware.BluetoothHidStatus
import com.example.hardware.GyroTelemetry
import com.example.hardware.HapticFeedbackManager
import com.example.hardware.MotionSensorManager
import com.example.hardware.WebBridgeServer
import com.example.hardware.WebBridgeState
import com.example.model.ActiveModal
import com.example.model.ButtonMappingEntry
import com.example.model.ClusterTransform
import com.example.model.ControlClusterId
import com.example.model.HapticIntensity
import com.example.model.LayoutPreset
import com.example.model.MacroCombo
import com.example.model.PsButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class GamepadUiState(
    val pressedButtons: Set<PsButton> = emptySet(),
    val effectiveButtons: Set<PsButton> = emptySet(),
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val effectiveLeftX: Float = 0f,
    val effectiveLeftY: Float = 0f,
    val effectiveRightX: Float = 0f,
    val effectiveRightY: Float = 0f,
    val l2Trigger: Float = 0f,
    val r2Trigger: Float = 0f,
    val isLayoutEditMode: Boolean = false,
    val selectedClusterForEdit: ControlClusterId = ControlClusterId.ACTION_BUTTONS,
    val clusterTransforms: Map<ControlClusterId, ClusterTransform> = emptyMap(),
    val activePreset: LayoutPreset = LayoutPreset.PS5_DUALSENSE,
    val buttonMappings: Map<PsButton, ButtonMappingEntry> = emptyMap(),
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val customHapticDurationMs: Long = 24L,
    val customHapticAmplitude: Int = 190,
    val vibrateOnRelease: Boolean = true,
    val gyroEnabled: Boolean = false,
    val gyroTargetRightStick: Boolean = true,
    val gyroSensitivity: Float = 1.2f,
    val turboEnabled: Boolean = false,
    val turboRateHz: Int = 12,
    val turboAssignedButtons: Set<PsButton> = setOf(PsButton.CROSS, PsButton.R2_BTN),
    val macros: List<MacroCombo> = listOf(
        MacroCombo("combo_dash", "Rapid Dash Strike (L1 + ✕ + ▢)", listOf(PsButton.L1, PsButton.CROSS, PsButton.SQUARE), 65L),
        MacroCombo("combo_ult", "Finisher Surge (△ + ◯ + R2)", listOf(PsButton.TRIANGLE, PsButton.CIRCLE, PsButton.R2_BTN), 75L),
        MacroCombo("combo_reload", "Tactical Slide Reload (◯ + L3 + ▢)", listOf(PsButton.CIRCLE, PsButton.L3, PsButton.SQUARE), 80L)
    ),
    val activeMacroId: String? = null,
    val activeModal: ActiveModal = ActiveModal.NONE
)

class GamepadViewModel(application: Application) : AndroidViewModel(application) {

    private val prefsRepo = ControllerPreferencesRepository(application)
    val btHidManager = BluetoothHidManager(application)
    val hapticManager = HapticFeedbackManager(application)
    val motionSensorManager = MotionSensorManager(application)
    val webBridgeServer = WebBridgeServer(
        onClientRumbleRequest = {
            hapticManager.triggerRumbleWaveform(180L)
        }
    )

    private val _uiState = MutableStateFlow(
        GamepadUiState(
            clusterTransforms = prefsRepo.loadClusterTransforms(),
            activePreset = prefsRepo.loadActivePreset(),
            buttonMappings = prefsRepo.loadButtonMappings(),
            hapticIntensity = prefsRepo.loadHapticIntensity(),
            customHapticDurationMs = prefsRepo.loadCustomHapticDuration(),
            customHapticAmplitude = prefsRepo.loadCustomHapticAmplitude(),
            gyroSensitivity = prefsRepo.loadGyroSensitivity()
        )
    )
    val uiState: StateFlow<GamepadUiState> = _uiState.asStateFlow()

    val btStatus: StateFlow<BluetoothHidStatus> = btHidManager.status
    val webBridgeState: StateFlow<WebBridgeState> = webBridgeServer.state
    val gyroTelemetry: StateFlow<GyroTelemetry> = motionSensorManager.telemetry

    private var turboPulsePhase = false
    private var macroJob: Job? = null

    init {
        motionSensorManager.setSensitivity(_uiState.value.gyroSensitivity)
        // Automatically start the local zero-install Web Bridge server
        webBridgeServer.startServer(8080)
        // Start Bluetooth HID profile if permissions are already granted
        if (btHidManager.hasBluetoothPermissions()) {
            btHidManager.startHidProfile()
        }
        startTurboOscillatorLoop()
        startHighFrequencyDispatchLoop()
    }

    private fun startTurboOscillatorLoop() {
        viewModelScope.launch {
            while (isActive) {
                val state = _uiState.value
                val hz = state.turboRateHz.coerceIn(2, 25)
                val halfPeriodMs = (500L / hz).coerceAtLeast(16L)
                delay(halfPeriodMs)
                if (state.turboEnabled && state.pressedButtons.any { it in state.turboAssignedButtons }) {
                    turboPulsePhase = !turboPulsePhase
                    if (turboPulsePhase) {
                        hapticManager.triggerButtonRelease(state.hapticIntensity)
                    }
                    recomputeEffectiveControlsAndSend()
                } else {
                    turboPulsePhase = true
                }
            }
        }
    }

    private fun startHighFrequencyDispatchLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(16L) // ~60Hz telemetry and HID report frame rate
                recomputeEffectiveControlsAndSend()
            }
        }
    }

    fun onButtonPress(button: PsButton) {
        val current = _uiState.value
        if (button !in current.pressedButtons) {
            hapticManager.triggerButtonPress(
                intensity = current.hapticIntensity,
                customDurationMs = current.customHapticDurationMs,
                customAmplitude = current.customHapticAmplitude
            )
            _uiState.update { state ->
                val updated = state.pressedButtons + button
                val l2 = if (button == PsButton.L2_BTN) 1f else state.l2Trigger
                val r2 = if (button == PsButton.R2_BTN) 1f else state.r2Trigger
                state.copy(pressedButtons = updated, l2Trigger = l2, r2Trigger = r2)
            }
            recomputeEffectiveControlsAndSend()
        }
    }

    fun onButtonRelease(button: PsButton) {
        val current = _uiState.value
        if (button in current.pressedButtons) {
            if (current.vibrateOnRelease) {
                hapticManager.triggerButtonRelease(current.hapticIntensity)
            }
            _uiState.update { state ->
                val updated = state.pressedButtons - button
                val l2 = if (button == PsButton.L2_BTN) 0f else state.l2Trigger
                val r2 = if (button == PsButton.R2_BTN) 0f else state.r2Trigger
                state.copy(pressedButtons = updated, l2Trigger = l2, r2Trigger = r2)
            }
            recomputeEffectiveControlsAndSend()
        }
    }

    fun onTriggerValueChange(isLeft: Boolean, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _uiState.update { state ->
            val btn = if (isLeft) PsButton.L2_BTN else PsButton.R2_BTN
            val updatedBtns = if (clamped >= 0.25f) {
                if (btn !in state.pressedButtons) {
                    hapticManager.triggerStickDetent()
                }
                state.pressedButtons + btn
            } else {
                state.pressedButtons - btn
            }
            if (isLeft) {
                state.copy(l2Trigger = clamped, pressedButtons = updatedBtns)
            } else {
                state.copy(r2Trigger = clamped, pressedButtons = updatedBtns)
            }
        }
        recomputeEffectiveControlsAndSend()
    }

    fun onLeftStickMove(x: Float, y: Float) {
        val prev = _uiState.value
        val clampedX = x.coerceIn(-1f, 1f)
        val clampedY = y.coerceIn(-1f, 1f)
        // Haptic edge detent when hitting outer rim
        val prevMag = kotlin.math.hypot(prev.leftStickX, prev.leftStickY)
        val newMag = kotlin.math.hypot(clampedX, clampedY)
        if (prevMag < 0.92f && newMag >= 0.95f) {
            hapticManager.triggerStickDetent()
        }
        _uiState.update { it.copy(leftStickX = clampedX, leftStickY = clampedY) }
        recomputeEffectiveControlsAndSend()
    }

    fun onRightStickMove(x: Float, y: Float) {
        val prev = _uiState.value
        val clampedX = x.coerceIn(-1f, 1f)
        val clampedY = y.coerceIn(-1f, 1f)
        val prevMag = kotlin.math.hypot(prev.rightStickX, prev.rightStickY)
        val newMag = kotlin.math.hypot(clampedX, clampedY)
        if (prevMag < 0.92f && newMag >= 0.95f) {
            hapticManager.triggerStickDetent()
        }
        _uiState.update { it.copy(rightStickX = clampedX, rightStickY = clampedY) }
        recomputeEffectiveControlsAndSend()
    }

    fun setDpadDirection(up: Boolean, down: Boolean, left: Boolean, right: Boolean) {
        val prev = _uiState.value.pressedButtons
        val next = prev.toMutableSet()
        if (up) next.add(PsButton.DPAD_UP) else next.remove(PsButton.DPAD_UP)
        if (down) next.add(PsButton.DPAD_DOWN) else next.remove(PsButton.DPAD_DOWN)
        if (left) next.add(PsButton.DPAD_LEFT) else next.remove(PsButton.DPAD_LEFT)
        if (right) next.add(PsButton.DPAD_RIGHT) else next.remove(PsButton.DPAD_RIGHT)

        if (next != prev) {
            if (next.size > prev.size) {
                hapticManager.triggerButtonPress(
                    _uiState.value.hapticIntensity,
                    _uiState.value.customHapticDurationMs,
                    _uiState.value.customHapticAmplitude
                )
            }
            _uiState.update { it.copy(pressedButtons = next) }
            recomputeEffectiveControlsAndSend()
        }
    }

    private fun recomputeEffectiveControlsAndSend() {
        val state = _uiState.value
        val gyro = gyroTelemetry.value

        // Apply Turbo masking
        val effectiveBtns = if (state.turboEnabled && !turboPulsePhase) {
            state.pressedButtons.filterNot { it in state.turboAssignedButtons }.toSet()
        } else {
            state.pressedButtons
        }

        // Combine touch thumbsticks + motion gyro aiming
        val effLx = if (state.gyroEnabled && !state.gyroTargetRightStick) {
            (state.leftStickX + gyro.normalizedAimX).coerceIn(-1f, 1f)
        } else {
            state.leftStickX
        }
        val effLy = if (state.gyroEnabled && !state.gyroTargetRightStick) {
            (state.leftStickY + gyro.normalizedAimY).coerceIn(-1f, 1f)
        } else {
            state.leftStickY
        }

        val effRx = if (state.gyroEnabled && state.gyroTargetRightStick) {
            (state.rightStickX + gyro.normalizedAimX).coerceIn(-1f, 1f)
        } else {
            state.rightStickX
        }
        val effRy = if (state.gyroEnabled && state.gyroTargetRightStick) {
            (state.rightStickY + gyro.normalizedAimY).coerceIn(-1f, 1f)
        } else {
            state.rightStickY
        }

        _uiState.update {
            it.copy(
                effectiveButtons = effectiveBtns,
                effectiveLeftX = effLx,
                effectiveLeftY = effLy,
                effectiveRightX = effRx,
                effectiveRightY = effRy
            )
        }

        // Compute 16-bit HID Button Mask using custom button mappings
        var buttonMask = 0
        for (btn in effectiveBtns) {
            val mappedBit = state.buttonMappings[btn]?.mappedHidBit ?: btn.defaultHidBit
            if (mappedBit in 0..15) {
                buttonMask = buttonMask or (1 shl mappedBit)
            }
        }

        val hatSwitch = computeHatSwitch(
            up = PsButton.DPAD_UP in effectiveBtns,
            down = PsButton.DPAD_DOWN in effectiveBtns,
            left = PsButton.DPAD_LEFT in effectiveBtns,
            right = PsButton.DPAD_RIGHT in effectiveBtns
        )

        // Dispatch to native Bluetooth HID Gamepad if connected
        btHidManager.sendGamepadReport(
            buttonMask16 = buttonMask,
            hatSwitchValue = hatSwitch,
            leftX = effLx,
            leftY = effLy,
            rightX = effRx,
            rightY = effRy
        )

        // Dispatch to zero-install Wi-Fi Web Bridge clients if connected
        if (webBridgeState.value.connectedClients > 0) {
            val json = JSONObject().apply {
                put("lx", effLx.toDouble())
                put("ly", effLy.toDouble())
                put("rx", effRx.toDouble())
                put("ry", effRy.toDouble())
                put("l2", state.l2Trigger.toDouble())
                put("r2", state.r2Trigger.toDouble())
                put("hat", hatSwitch)
                put("pitch", gyro.pitchDeg.toDouble())
                put("roll", gyro.rollDeg.toDouble())
                val arr = JSONArray()
                effectiveBtns.forEach { arr.put(it.name) }
                put("buttons", arr)
            }
            webBridgeServer.broadcastGamepadStateJson(json.toString())
        }
    }

    private fun computeHatSwitch(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Int {
        return when {
            up && right -> 1
            up && left -> 7
            down && right -> 3
            down && left -> 5
            up -> 0
            right -> 2
            down -> 4
            left -> 6
            else -> 8 // Neutral
        }
    }

    fun executeMacroCombo(macro: MacroCombo) {
        if (macroJob?.isActive == true) return
        macroJob = viewModelScope.launch {
            _uiState.update { it.copy(activeMacroId = macro.id) }
            hapticManager.triggerRumbleWaveform(120L)
            for (stepBtn in macro.buttons) {
                onButtonPress(stepBtn)
                delay(macro.stepDelayMs)
                onButtonRelease(stepBtn)
                delay(35L)
            }
            _uiState.update { it.copy(activeMacroId = null) }
        }
    }

    fun addCustomMacro(name: String, sequence: List<PsButton>) {
        if (name.isBlank() || sequence.isEmpty()) return
        val newMacro = MacroCombo(
            id = "macro_${System.currentTimeMillis()}",
            name = name.trim(),
            buttons = sequence,
            stepDelayMs = 70L
        )
        _uiState.update { it.copy(macros = it.macros + newMacro) }
    }

    fun deleteMacro(id: String) {
        _uiState.update { state -> state.copy(macros = state.macros.filterNot { it.id == id }) }
    }

    // Gyro Controls
    fun toggleGyroEnabled() {
        val next = !_uiState.value.gyroEnabled
        if (next) {
            motionSensorManager.startListening()
        } else {
            motionSensorManager.stopListening()
        }
        hapticManager.triggerStickDetent()
        _uiState.update { it.copy(gyroEnabled = next) }
    }

    fun setGyroTargetRightStick(targetRight: Boolean) {
        _uiState.update { it.copy(gyroTargetRightStick = targetRight) }
    }

    fun updateGyroSensitivity(sensitivity: Float) {
        motionSensorManager.setSensitivity(sensitivity)
        prefsRepo.saveGyroSensitivity(sensitivity)
        _uiState.update { it.copy(gyroSensitivity = sensitivity) }
    }

    fun calibrateGyroCenter() {
        motionSensorManager.calibrateCenter()
        hapticManager.triggerStickDetent()
    }

    // Turbo Controls
    fun toggleTurboMode() {
        val next = !_uiState.value.turboEnabled
        hapticManager.triggerStickDetent()
        _uiState.update { it.copy(turboEnabled = next) }
    }

    fun setTurboRateHz(hz: Int) {
        _uiState.update { it.copy(turboRateHz = hz.coerceIn(5, 25)) }
    }

    fun toggleButtonTurboAssignment(button: PsButton) {
        _uiState.update { state ->
            val next = if (button in state.turboAssignedButtons) {
                state.turboAssignedButtons - button
            } else {
                state.turboAssignedButtons + button
            }
            state.copy(turboAssignedButtons = next)
        }
    }

    // Layout Editor Controls
    fun toggleLayoutEditMode() {
        val next = !_uiState.value.isLayoutEditMode
        hapticManager.triggerStickDetent()
        _uiState.update { it.copy(isLayoutEditMode = next) }
    }

    fun selectClusterForEdit(cluster: ControlClusterId) {
        _uiState.update { it.copy(selectedClusterForEdit = cluster) }
    }

    fun moveClusterBy(cluster: ControlClusterId, dxDp: Float, dyDp: Float) {
        _uiState.update { state ->
            val current = state.clusterTransforms[cluster] ?: ClusterTransform()
            val updated = current.copy(
                offsetX = (current.offsetX + dxDp).coerceIn(-160f, 160f),
                offsetY = (current.offsetY + dyDp).coerceIn(-120f, 120f)
            )
            prefsRepo.saveClusterTransform(cluster, updated)
            state.copy(
                clusterTransforms = state.clusterTransforms + (cluster to updated),
                selectedClusterForEdit = cluster
            )
        }
    }

    fun updateClusterScale(cluster: ControlClusterId, scale: Float) {
        val clamped = scale.coerceIn(0.65f, 1.45f)
        _uiState.update { state ->
            val current = state.clusterTransforms[cluster] ?: ClusterTransform()
            val updated = current.copy(scale = clamped)
            prefsRepo.saveClusterTransform(cluster, updated)
            state.copy(clusterTransforms = state.clusterTransforms + (cluster to updated))
        }
    }

    fun applyLayoutPreset(preset: LayoutPreset) {
        val map = prefsRepo.applyPresetTransforms(preset)
        hapticManager.triggerStickDetent()
        _uiState.update { it.copy(clusterTransforms = map, activePreset = preset) }
    }

    fun resetLayoutToDefaults() {
        val map = prefsRepo.resetAllClusterTransforms()
        hapticManager.triggerStickDetent()
        _uiState.update {
            it.copy(
                clusterTransforms = map,
                activePreset = LayoutPreset.PS5_DUALSENSE
            )
        }
    }

    // Button Mapping Controls
    fun updateButtonMapping(button: PsButton, hidBit: Int, pcKey: String) {
        prefsRepo.saveButtonMapping(button, hidBit, pcKey)
        _uiState.update { state ->
            val updatedEntry = ButtonMappingEntry(button, hidBit, pcKey)
            state.copy(buttonMappings = state.buttonMappings + (button to updatedEntry))
        }
    }

    fun resetAllButtonMappings() {
        val map = prefsRepo.resetButtonMappings()
        hapticManager.triggerStickDetent()
        _uiState.update { it.copy(buttonMappings = map) }
    }

    // Haptics Controls
    fun setHapticIntensity(intensity: HapticIntensity) {
        prefsRepo.saveHapticIntensity(intensity)
        _uiState.update { it.copy(hapticIntensity = intensity) }
        hapticManager.triggerButtonPress(
            intensity = intensity,
            customDurationMs = _uiState.value.customHapticDurationMs,
            customAmplitude = _uiState.value.customHapticAmplitude
        )
    }

    fun updateCustomHaptics(durationMs: Long, amplitude: Int) {
        prefsRepo.saveCustomHapticParams(durationMs, amplitude)
        _uiState.update {
            it.copy(
                hapticIntensity = HapticIntensity.CUSTOM,
                customHapticDurationMs = durationMs,
                customHapticAmplitude = amplitude
            )
        }
    }

    fun toggleVibrateOnRelease() {
        _uiState.update { it.copy(vibrateOnRelease = !it.vibrateOnRelease) }
    }

    fun openModal(modal: ActiveModal) {
        if (modal == ActiveModal.CONNECTION_HUB) {
            btHidManager.refreshState()
            webBridgeServer.refreshLocalIp()
        }
        _uiState.update { it.copy(activeModal = modal) }
    }

    fun closeModal() {
        _uiState.update { it.copy(activeModal = ActiveModal.NONE) }
    }

    override fun onCleared() {
        super.onCleared()
        motionSensorManager.stopListening()
        btHidManager.stopHidProfile()
        webBridgeServer.stopServer()
    }
}
