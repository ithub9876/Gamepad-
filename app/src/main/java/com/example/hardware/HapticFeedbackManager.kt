package com.example.hardware

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.HapticIntensity

class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    val hasHardwareVibrator: Boolean
        get() = vibrator?.hasVibrator() == true

    val hasAmplitudeControl: Boolean
        get() = vibrator?.hasAmplitudeControl() == true

    fun triggerButtonPress(
        intensity: HapticIntensity,
        customDurationMs: Long = 22L,
        customAmplitude: Int = 180
    ) {
        if (intensity == HapticIntensity.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val duration = if (intensity == HapticIntensity.CUSTOM) {
            customDurationMs.coerceIn(5L, 120L)
        } else {
            intensity.durationMs
        }

        val amp = if (intensity == HapticIntensity.CUSTOM) {
            customAmplitude.coerceIn(1, 255)
        } else {
            intensity.amplitude.coerceIn(1, 255)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                intensity == HapticIntensity.LIGHT &&
                !hasAmplitudeControl
            ) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                val actualAmp = if (vib.hasAmplitudeControl()) amp else VibrationEffect.DEFAULT_AMPLITUDE
                vib.vibrate(VibrationEffect.createOneShot(duration, actualAmp))
            }
        } catch (_: Exception) {
            // Ignore security or OEM hardware exceptions gracefully
        }
    }

    fun triggerButtonRelease(intensity: HapticIntensity) {
        if (intensity == HapticIntensity.OFF) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                vib.vibrate(VibrationEffect.createOneShot(6L, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) {
        }
    }

    fun triggerStickDetent() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                vib.vibrate(VibrationEffect.createOneShot(10L, 120))
            }
        } catch (_: Exception) {
        }
    }

    fun triggerRumbleWaveform(durationMs: Long = 160L) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        try {
            val timings = longArrayOf(0L, durationMs / 3, 25L, (durationMs * 2) / 3)
            val amplitudes = if (vib.hasAmplitudeControl()) {
                intArrayOf(0, 180, 0, 255)
            } else {
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (_: Exception) {
        }
    }
}
