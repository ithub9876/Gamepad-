package com.example.hardware

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.atan2
import kotlin.math.sqrt

data class GyroTelemetry(
    val sensorAvailable: Boolean = false,
    val sensorName: String = "Unavailable",
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val normalizedAimX: Float = 0f,
    val normalizedAimY: Float = 0f
)

class MotionSensorManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _telemetry = MutableStateFlow(
        GyroTelemetry(
            sensorAvailable = rotationSensor != null,
            sensorName = rotationSensor?.name ?: "No Motion Sensor Found"
        )
    )
    val telemetry: StateFlow<GyroTelemetry> = _telemetry.asStateFlow()

    private var isListening = false
    private var baselinePitch = 0f
    private var baselineRoll = 0f
    private var baselineYaw = 0f
    private var hasCalibratedBaseline = false
    private var sensitivityMultiplier = 1.0f

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun setSensitivity(sensitivity: Float) {
        sensitivityMultiplier = sensitivity.coerceIn(0.25f, 3.0f)
    }

    fun calibrateCenter() {
        val current = _telemetry.value
        baselinePitch = current.pitchDeg + baselinePitch
        baselineRoll = current.rollDeg + baselineRoll
        baselineYaw = current.yawDeg + baselineYaw
        _telemetry.value = current.copy(
            pitchDeg = 0f,
            rollDeg = 0f,
            yawDeg = 0f,
            normalizedAimX = 0f,
            normalizedAimY = 0f
        )
    }

    fun startListening() {
        if (isListening) return
        val sensor = rotationSensor ?: return
        hasCalibratedBaseline = false
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        _telemetry.value = _telemetry.value.copy(
            normalizedAimX = 0f,
            normalizedAimY = 0f
        )
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event ?: return
        var rawPitchDeg = 0f
        var rawRollDeg = 0f
        var rawYawDeg = 0f

        if (ev.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR ||
            ev.sensor.type == Sensor.TYPE_ROTATION_VECTOR
        ) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, ev.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            rawYawDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            rawPitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
            rawRollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
        } else if (ev.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = ev.values[0]
            val ay = ev.values[1]
            val az = ev.values[2]
            rawPitchDeg = Math.toDegrees(atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble()))).toFloat()
            rawRollDeg = Math.toDegrees(atan2(-ax.toDouble(), az.toDouble())).toFloat()
        }

        if (!hasCalibratedBaseline) {
            baselinePitch = rawPitchDeg
            baselineRoll = rawRollDeg
            baselineYaw = rawYawDeg
            hasCalibratedBaseline = true
        }

        val relPitch = rawPitchDeg - baselinePitch
        val relRoll = rawRollDeg - baselineRoll
        val relYaw = rawYawDeg - baselineYaw

        // Convert tilt degrees (-30..+30 deg full scale) into normalized [-1..1] stick values
        val maxTiltDeg = 28f / sensitivityMultiplier
        val aimX = applyDeadzone((relRoll / maxTiltDeg).coerceIn(-1f, 1f), 0.05f)
        val aimY = applyDeadzone((relPitch / maxTiltDeg).coerceIn(-1f, 1f), 0.05f)

        _telemetry.value = _telemetry.value.copy(
            pitchDeg = relPitch,
            rollDeg = relRoll,
            yawDeg = relYaw,
            normalizedAimX = aimX,
            normalizedAimY = aimY
        )
    }

    private fun applyDeadzone(value: Float, deadzone: Float): Float {
        if (kotlin.math.abs(value) < deadzone) return 0f
        return value
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
