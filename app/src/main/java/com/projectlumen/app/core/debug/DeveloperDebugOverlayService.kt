package com.projectlumen.app.core.debug

import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.services.ForegroundServiceController
import com.projectlumen.app.core.services.ForegroundServiceStartEligibility
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DeveloperDebugOverlayService : Service(), SensorEventListener {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            runCatching { application as? ProjectLumenApplication }
                .getOrNull()
                ?.recordHandledFailure(throwable)
        },
    )
    private val handler = Handler(Looper.getMainLooper())
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private val sensorSnapshot = AtomicReference(SensorSnapshot())
    private lateinit var sensorManager: SensorManager
    private var overlayView: LinearLayout? = null
    private var previewImage: ImageView? = null
    private var renderedThumbnail: Bitmap? = null
    private var sensorsRegistered = false
    private var overlayTicking = false
    private var lastRuntimeWriteAt = 0L
    private var lastWrittenSensorSnapshot: SensorSnapshot? = null
    private var lastMemoryHealthSampleAt = 0L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        val promoted = ForegroundServiceController.promote(
            service = this,
            notificationId = NotificationIds.DEVELOPER_DEBUG_FOREGROUND,
            notificationProvider = { app.notifications.buildDeveloperDebugForegroundNotification() },
            foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!promoted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        recordServiceStart(app, flags)
        if (intent?.action == ACTION_SIMULATE_LOW_MEMORY) {
            simulateLowMemory(app)
        }
        sampleMemoryHealth(force = true)
        registerSensors()
        if (!overlayTicking) tickOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        recordServiceStop()
        overlayTicking = false
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        sensorManager.unregisterListener(this)
        DeveloperDebugFrameStore.clear()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? ProjectLumenApplication
        if (app != null) {
            scope.launch {
                val now = System.currentTimeMillis()
                app.runtimeRepository().update {
                    it.copy(
                        foregroundServiceLastTaskRemovedAt = now,
                        updatedAt = now,
                    )
                }
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        val now = System.currentTimeMillis()
        scope.launch { MemoryHealthMonitor.recordTrim(this@DeveloperDebugOverlayService, level, now) }
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            DeveloperDebugFrameStore.clear()
        }
        super.onTrimMemory(level)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> event.values.firstOrNull()?.let { lux ->
                sensorSnapshot.set(sensorSnapshot.get().copy(lux = lux))
            }
            Sensor.TYPE_ACCELEROMETER -> handleAccelerometer(event.values)
            Sensor.TYPE_ROTATION_VECTOR -> handleRotationVector(event.values)
        }
        val now = System.currentTimeMillis()
        if (now - lastRuntimeWriteAt < RUNTIME_WRITE_INTERVAL_MILLIS) return
        val snapshot = sensorSnapshot.get()
        if (!hasMeaningfulSensorChange(snapshot, lastWrittenSensorSnapshot)) return
        lastRuntimeWriteAt = now
        lastWrittenSensorSnapshot = snapshot
        writeSensorRuntime(snapshot, now)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSensors() {
        if (sensorsRegistered) return
        listOf(
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_ROTATION_VECTOR,
        ).mapNotNull(sensorManager::getDefaultSensor).forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorsRegistered = true
    }

    private fun tickOverlay() {
        overlayTicking = true
        sampleMemoryHealth()
        scope.launch {
            val settings = (application as ProjectLumenApplication).settingsRepository().get()
            handler.post {
                if (
                    settings?.developerModeEnabled == true &&
                    settings.developerDebugOverlayEnabled &&
                    Settings.canDrawOverlays(this@DeveloperDebugOverlayService)
                ) {
                    if (ensureOverlay()) renderOverlay()
                } else {
                    removeOverlay()
                    overlayTicking = false
                    handler.removeCallbacksAndMessages(null)
                    stopSelf()
                }
            }
        }
        if (overlayTicking) {
            handler.postDelayed(::tickOverlay, OVERLAY_TICK_INTERVAL_MILLIS)
        }
    }

    private fun ensureOverlay(): Boolean {
        if (overlayView != null) return true
        val windowManager = getSystemService(WindowManager::class.java) ?: return false
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.argb(214, 6, 10, 14))
        }
        val preview = ImageView(this).apply {
            adjustViewBounds = false
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.argb(255, 8, 12, 18))
            layoutParams = LinearLayout.LayoutParams(dp(240), dp(180))
        }.also(container::addView)

        val params = WindowManager.LayoutParams(
            dp(252),
            dp(192),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(72)
        }
        val added = runCatching { windowManager.addView(container, params) }
            .onFailure { (application as? ProjectLumenApplication)?.recordHandledFailure(it) }
            .isSuccess
        if (!added) return false
        overlayView = container
        previewImage = preview
        return true
    }

    private fun renderOverlay() {
        val frame = DeveloperDebugFrameStore.latest()
        previewImage?.visibility = View.VISIBLE
        val thumbnail = frame?.thumbnail
        if (thumbnail === renderedThumbnail) return
        renderedThumbnail = thumbnail
        if (thumbnail != null) {
            previewImage?.setImageBitmap(thumbnail)
        } else {
            previewImage?.setImageDrawable(null)
        }
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
        previewImage = null
        renderedThumbnail = null
    }

    private fun handleAccelerometer(values: FloatArray) {
        val x = if (values.isNotEmpty()) values[0] else 0f
        val y = if (values.size > 1) values[1] else 0f
        val z = if (values.size > 2) values[2] else 0f
        sensorSnapshot.set(
            sensorSnapshot.get().copy(
                accelerationMagnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat(),
                pitch = Math.toDegrees(kotlin.math.atan2((-x).toDouble(), sqrt((y * y + z * z).toDouble()))).toFloat(),
                roll = Math.toDegrees(kotlin.math.atan2(y.toDouble(), z.toDouble())).toFloat(),
            ),
        )
    }

    private fun handleRotationVector(values: FloatArray) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        sensorSnapshot.set(
            sensorSnapshot.get().copy(
                yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat(),
                pitch = Math.toDegrees(orientationAngles[1].toDouble()).toFloat(),
                roll = Math.toDegrees(orientationAngles[2].toDouble()).toFloat(),
            ),
        )
    }

    private fun hasMeaningfulSensorChange(current: SensorSnapshot, previous: SensorSnapshot?): Boolean {
        if (previous == null) return true
        return abs(current.lux - previous.lux) >= LUX_CHANGE_THRESHOLD ||
            abs(current.pitch - previous.pitch) >= ANGLE_CHANGE_THRESHOLD_DEGREES ||
            abs(current.roll - previous.roll) >= ANGLE_CHANGE_THRESHOLD_DEGREES ||
            abs(current.yaw - previous.yaw) >= ANGLE_CHANGE_THRESHOLD_DEGREES ||
            abs(current.accelerationMagnitude - previous.accelerationMagnitude) >= ACCELERATION_CHANGE_THRESHOLD
    }

    private fun writeSensorRuntime(snapshot: SensorSnapshot, nowMillis: Long) {
        val app = application as ProjectLumenApplication
        scope.launch {
            app.runtimeRepository().update {
                it.copy(
                    ambientLastLux = snapshot.lux,
                    sensorPitchDegrees = snapshot.pitch,
                    sensorRollDegrees = snapshot.roll,
                    sensorYawDegrees = snapshot.yaw,
                    sensorLastAccelerationMagnitude = snapshot.accelerationMagnitude,
                    updatedAt = nowMillis,
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun simulateLowMemory(app: ProjectLumenApplication) {
        DeveloperDebugFrameStore.clear()
        onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        scope.launch(NonCancellable) {
            val now = System.currentTimeMillis()
            app.runtimeRepository().update {
                it.copy(
                    developerLastLowMemorySimulatedAt = now,
                    updatedAt = now,
                )
            }
        }
    }

    private fun recordServiceStart(app: ProjectLumenApplication, flags: Int) {
        scope.launch {
            val now = System.currentTimeMillis()
            val restarted = flags and (START_FLAG_REDELIVERY or START_FLAG_RETRY) != 0
            app.runtimeRepository().update {
                it.copy(
                    foregroundServiceStartedAt = now,
                    foregroundServiceStoppedAt = 0L,
                    foregroundServiceLastStickyRestartAt = if (restarted) now else it.foregroundServiceLastStickyRestartAt,
                    updatedAt = now,
                )
            }
        }
    }

    private fun recordServiceStop() {
        val app = application as? ProjectLumenApplication ?: return
        // NonCancellable：紧随其后的 scope.cancel() 不能让停止时间戳丢掉。
        scope.launch(NonCancellable) {
            val now = System.currentTimeMillis()
            app.runtimeRepository().update {
                it.copy(
                    foregroundServiceStoppedAt = now,
                    updatedAt = now,
                )
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun sampleMemoryHealth(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastMemoryHealthSampleAt < MEMORY_HEALTH_SAMPLE_INTERVAL_MILLIS) return
        lastMemoryHealthSampleAt = now
        scope.launch { MemoryHealthMonitor.sample(this@DeveloperDebugOverlayService, now) }
    }

    private data class SensorSnapshot(
        val lux: Float = 0f,
        val pitch: Float = 0f,
        val roll: Float = 0f,
        val yaw: Float = 0f,
        val accelerationMagnitude: Float = 0f,
    )

    companion object {
        private const val ACTION_SIMULATE_LOW_MEMORY = "com.projectlumen.app.DEVELOPER_SIMULATE_LOW_MEMORY"
        private const val MEMORY_HEALTH_SAMPLE_INTERVAL_MILLIS = 5_000L
        private const val OVERLAY_TICK_INTERVAL_MILLIS = 2_000L
        private const val RUNTIME_WRITE_INTERVAL_MILLIS = 2_000L
        private const val LUX_CHANGE_THRESHOLD = 1f
        private const val ANGLE_CHANGE_THRESHOLD_DEGREES = 1f
        private const val ACCELERATION_CHANGE_THRESHOLD = 0.2f

        fun start(context: Context) {
            ForegroundServiceController.start(
                context = context,
                intent = Intent(context, DeveloperDebugOverlayService::class.java),
                eligibilityCheck = ForegroundServiceStartEligibility::canStartFromForegroundProcess,
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, DeveloperDebugOverlayService::class.java))
        }

        fun simulateLowMemory(context: Context) {
            ForegroundServiceController.start(
                context = context,
                intent = Intent(context, DeveloperDebugOverlayService::class.java)
                    .setAction(ACTION_SIMULATE_LOW_MEMORY),
                eligibilityCheck = ForegroundServiceStartEligibility::canStartFromForegroundProcess,
            )
        }
    }
}
