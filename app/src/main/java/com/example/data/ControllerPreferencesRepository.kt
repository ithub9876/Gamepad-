package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.ButtonMappingEntry
import com.example.model.ClusterTransform
import com.example.model.ControlClusterId
import com.example.model.HapticIntensity
import com.example.model.LayoutPreset
import com.example.model.PsButton

class ControllerPreferencesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("neonpad_console_prefs", Context.MODE_PRIVATE)

    fun loadClusterTransforms(): Map<ControlClusterId, ClusterTransform> {
        val map = mutableMapOf<ControlClusterId, ClusterTransform>()
        for (cluster in ControlClusterId.entries) {
            val ox = prefs.getFloat("cluster_${cluster.name}_ox", 0f)
            val oy = prefs.getFloat("cluster_${cluster.name}_oy", 0f)
            val scale = prefs.getFloat("cluster_${cluster.name}_scale", 1.0f).coerceIn(0.65f, 1.45f)
            map[cluster] = ClusterTransform(offsetX = ox, offsetY = oy, scale = scale)
        }
        return map
    }

    fun saveClusterTransform(cluster: ControlClusterId, transform: ClusterTransform) {
        prefs.edit()
            .putFloat("cluster_${cluster.name}_ox", transform.offsetX)
            .putFloat("cluster_${cluster.name}_oy", transform.offsetY)
            .putFloat("cluster_${cluster.name}_scale", transform.scale.coerceIn(0.65f, 1.45f))
            .apply()
    }

    fun resetAllClusterTransforms(): Map<ControlClusterId, ClusterTransform> {
        val editor = prefs.edit()
        val map = mutableMapOf<ControlClusterId, ClusterTransform>()
        for (cluster in ControlClusterId.entries) {
            editor.remove("cluster_${cluster.name}_ox")
            editor.remove("cluster_${cluster.name}_oy")
            editor.remove("cluster_${cluster.name}_scale")
            map[cluster] = ClusterTransform(0f, 0f, 1.0f)
        }
        editor.apply()
        return map
    }

    fun applyPresetTransforms(preset: LayoutPreset): Map<ControlClusterId, ClusterTransform> {
        val map = when (preset) {
            LayoutPreset.PS5_DUALSENSE -> ControlClusterId.entries.associateWith {
                ClusterTransform(0f, 0f, 1.0f)
            }
            LayoutPreset.FPS_COMPETITIVE -> mapOf(
                ControlClusterId.LEFT_SHOULDERS to ClusterTransform(12f, 8f, 1.15f),
                ControlClusterId.RIGHT_SHOULDERS to ClusterTransform(-12f, 8f, 1.15f),
                ControlClusterId.DPAD to ClusterTransform(0f, -6f, 0.90f),
                ControlClusterId.ACTION_BUTTONS to ClusterTransform(0f, -6f, 0.95f),
                ControlClusterId.LEFT_STICK to ClusterTransform(14f, -10f, 1.22f),
                ControlClusterId.RIGHT_STICK to ClusterTransform(-14f, -10f, 1.25f),
                ControlClusterId.CENTER_DECK to ClusterTransform(0f, 0f, 0.95f)
            )
            LayoutPreset.RACING_SIM -> mapOf(
                ControlClusterId.LEFT_SHOULDERS to ClusterTransform(20f, 14f, 1.25f),
                ControlClusterId.RIGHT_SHOULDERS to ClusterTransform(-20f, 14f, 1.25f),
                ControlClusterId.DPAD to ClusterTransform(0f, 0f, 0.85f),
                ControlClusterId.ACTION_BUTTONS to ClusterTransform(-10f, 0f, 1.15f),
                ControlClusterId.LEFT_STICK to ClusterTransform(18f, -6f, 1.20f),
                ControlClusterId.RIGHT_STICK to ClusterTransform(-8f, 0f, 0.90f),
                ControlClusterId.CENTER_DECK to ClusterTransform(0f, 0f, 1.0f)
            )
            LayoutPreset.ARCADE_FIGHTER -> mapOf(
                ControlClusterId.LEFT_SHOULDERS to ClusterTransform(0f, 0f, 1.0f),
                ControlClusterId.RIGHT_SHOULDERS to ClusterTransform(0f, 0f, 1.05f),
                ControlClusterId.DPAD to ClusterTransform(16f, 8f, 1.28f),
                ControlClusterId.ACTION_BUTTONS to ClusterTransform(-18f, 8f, 1.30f),
                ControlClusterId.LEFT_STICK to ClusterTransform(0f, 10f, 0.80f),
                ControlClusterId.RIGHT_STICK to ClusterTransform(0f, 10f, 0.80f),
                ControlClusterId.CENTER_DECK to ClusterTransform(0f, -8f, 0.92f)
            )
        }
        map.forEach { (cluster, transform) -> saveClusterTransform(cluster, transform) }
        prefs.edit().putString("active_preset", preset.name).apply()
        return map
    }

    fun loadActivePreset(): LayoutPreset {
        val name = prefs.getString("active_preset", LayoutPreset.PS5_DUALSENSE.name)
        return LayoutPreset.entries.find { it.name == name } ?: LayoutPreset.PS5_DUALSENSE
    }

    fun loadButtonMappings(): Map<PsButton, ButtonMappingEntry> {
        val result = mutableMapOf<PsButton, ButtonMappingEntry>()
        for (btn in PsButton.entries) {
            val hidBit = prefs.getInt("map_hid_${btn.name}", btn.defaultHidBit)
            val pcKey = prefs.getString("map_key_${btn.name}", btn.defaultPcKey) ?: btn.defaultPcKey
            result[btn] = ButtonMappingEntry(btn, hidBit, pcKey)
        }
        return result
    }

    fun saveButtonMapping(button: PsButton, hidBit: Int, pcKey: String) {
        prefs.edit()
            .putInt("map_hid_${button.name}", hidBit.coerceIn(0, 17))
            .putString("map_key_${button.name}", pcKey)
            .apply()
    }

    fun resetButtonMappings(): Map<PsButton, ButtonMappingEntry> {
        val editor = prefs.edit()
        val result = mutableMapOf<PsButton, ButtonMappingEntry>()
        for (btn in PsButton.entries) {
            editor.remove("map_hid_${btn.name}")
            editor.remove("map_key_${btn.name}")
            result[btn] = ButtonMappingEntry(btn, btn.defaultHidBit, btn.defaultPcKey)
        }
        editor.apply()
        return result
    }

    fun loadHapticIntensity(): HapticIntensity {
        val name = prefs.getString("haptic_intensity", HapticIntensity.MEDIUM.name)
        return HapticIntensity.entries.find { it.name == name } ?: HapticIntensity.MEDIUM
    }

    fun saveHapticIntensity(intensity: HapticIntensity) {
        prefs.edit().putString("haptic_intensity", intensity.name).apply()
    }

    fun loadCustomHapticDuration(): Long = prefs.getLong("haptic_custom_dur", 24L)
    fun loadCustomHapticAmplitude(): Int = prefs.getInt("haptic_custom_amp", 190)

    fun saveCustomHapticParams(durationMs: Long, amplitude: Int) {
        prefs.edit()
            .putLong("haptic_custom_dur", durationMs.coerceIn(5L, 120L))
            .putInt("haptic_custom_amp", amplitude.coerceIn(1, 255))
            .apply()
    }

    fun loadGyroSensitivity(): Float = prefs.getFloat("gyro_sensitivity", 1.2f)

    fun saveGyroSensitivity(sensitivity: Float) {
        prefs.edit().putFloat("gyro_sensitivity", sensitivity.coerceIn(0.3f, 3.0f)).apply()
    }
}
