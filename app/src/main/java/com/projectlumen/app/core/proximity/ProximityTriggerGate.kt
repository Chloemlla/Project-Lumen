package com.projectlumen.app.core.proximity

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal class ProximityTriggerGate(private val context: Context) {
    suspend fun canRun(settings: AppSettingsEntity): Boolean {
        if (!settings.developerModeEnabled) return true
        if (!settings.developerStillnessTriggerEnabled && !settings.developerShakeSuppressionEnabled) return true
        val manager = context.getSystemService(SensorManager::class.java) ?: return true
        val sample = withTimeoutOrNull(900L) { sampleMotion(manager) } ?: return true
        val stillEnough = !settings.developerStillnessTriggerEnabled ||
            sample.maxGyroscopeMagnitude <= STILL_GYRO_THRESHOLD
        val notShaking = !settings.developerShakeSuppressionEnabled ||
            sample.maxAccelerationDelta <= SHAKE_ACCELERATION_DELTA_THRESHOLD
        return stillEnough && notShaking
    }

    private suspend fun sampleMotion(manager: SensorManager): MotionSample {
        val accelerometer = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (accelerometer == null && gyroscope == null) {
            return MotionSample()
        }
        return suspendCancellableCoroutine { continuation ->
            val listener = object : SensorEventListener {
                var baselineAcceleration = 0f
                var maxAccelerationDelta = 0f
                var maxGyroscopeMagnitude = 0f

                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            val magnitude = vectorMagnitude(event.values)
                            if (baselineAcceleration == 0f) baselineAcceleration = magnitude
                            maxAccelerationDelta = maxOf(maxAccelerationDelta, kotlin.math.abs(magnitude - baselineAcceleration))
                        }
                        Sensor.TYPE_GYROSCOPE -> {
                            maxGyroscopeMagnitude = maxOf(maxGyroscopeMagnitude, vectorMagnitude(event.values))
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            // Sensor callbacks arrive at high rate; keep them off the main looper.
            val thread = HandlerThread("ProjectLumenProximityTriggerGate").apply { start() }
            val handler = Handler(thread.looper)
            fun finish() {
                manager.unregisterListener(listener)
                thread.quitSafely()
                if (continuation.isActive) {
                    continuation.resume(
                        MotionSample(
                            maxAccelerationDelta = listener.maxAccelerationDelta,
                            maxGyroscopeMagnitude = listener.maxGyroscopeMagnitude,
                        ),
                    )
                }
            }
            val finishRunnable = Runnable { finish() }
            if (accelerometer != null) {
                manager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
            }
            if (gyroscope != null) {
                manager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME, handler)
            }
            handler.postDelayed(finishRunnable, 650L)
            continuation.invokeOnCancellation {
                handler.removeCallbacks(finishRunnable)
                manager.unregisterListener(listener)
                thread.quitSafely()
            }
        }
    }

    private fun vectorMagnitude(values: FloatArray): Float {
        val x = if (values.isNotEmpty()) values[0] else 0f
        val y = if (values.size > 1) values[1] else 0f
        val z = if (values.size > 2) values[2] else 0f
        return kotlin.math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }

    private data class MotionSample(
        val maxAccelerationDelta: Float = 0f,
        val maxGyroscopeMagnitude: Float = 0f,
    )

    private companion object {
        private const val STILL_GYRO_THRESHOLD = 0.18f
        private const val SHAKE_ACCELERATION_DELTA_THRESHOLD = 4.0f
    }
}
