package com.projectlumen.app.core.proximity

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProximityDetectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        ServiceCompat.startForeground(
            this,
            NotificationIds.PROXIMITY_FOREGROUND,
            app.notifications.buildProximityForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            } else {
                0
            },
        )
        val calibrate = intent?.getBooleanExtra(EXTRA_CALIBRATE, false) == true
        scope.launch {
            runCatching { runDetection(app, calibrate) }
                .onFailure { clearActiveState(app) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runDetection(app: ProjectLumenApplication, calibrate: Boolean) {
        val db = app.database
        val settings = SettingsRepository(db.appSettingsDao(), app.eyeCarePreferences).get() ?: return
        if (!calibrate && !settings.proximityMonitoringEnabled && !settings.blinkMonitoringEnabled) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return

        val now = System.currentTimeMillis()
        db.runtimeStateDao().get()?.let {
            db.runtimeStateDao().upsert(it.copy(proximityMonitoringActive = true, updatedAt = now))
        }

        val captureSeconds = when {
            calibrate -> settings.proximityCaptureSeconds.coerceIn(1, 2)
            settings.blinkMonitoringEnabled -> maxOf(
                settings.proximityCaptureSeconds.coerceIn(1, 2),
                settings.blinkNoBlinkThresholdSeconds + 1,
            ).coerceIn(2, 15)
            else -> settings.proximityCaptureSeconds.coerceIn(1, 2)
        }
        val samples = ProximityCameraSampler(this).captureFaceDistanceSamples(captureSeconds * 1000L)
        val sample = samples.maxByOrNull { it.faceWidthPercent }
        val latestSettings = if (calibrate && sample != null && (sample.eyeDistancePx > 0f || sample.faceWidthPercent > 0)) {
            val updated = settings.copy(
                proximityBaselineEyeDistancePx = sample.eyeDistancePx,
                proximityBaselineFaceWidthPercent = sample.faceWidthPercent,
                updatedAt = System.currentTimeMillis(),
            )
            db.appSettingsDao().upsert(updated)
            app.eyeCarePreferences.saveFromSettings(updated)
            updated
        } else {
            settings
        }

        val ratioPercent = sample?.let { latestSettings.distanceRatioPercent(it) } ?: 0
        val tooClose = latestSettings.proximityMonitoringEnabled && (sample?.let { latestSettings.isTooClose(it) } ?: false)

        val runtime = db.runtimeStateDao().get()
        val lastWarningAt = runtime?.proximityLastWarningAt ?: 0L
        val shouldWarn = latestSettings.proximityMonitoringEnabled &&
            tooClose &&
            now - lastWarningAt >= latestSettings.proximityAlertCooldownSeconds * 1000L
        val blinkState = evaluateBlinkState(
            settings = latestSettings,
            samples = samples,
            previousLastBlinkAt = runtime?.blinkLastBlinkAt ?: 0L,
            previousLastWarningAt = runtime?.blinkLastWarningAt ?: 0L,
            nowMillis = now,
        )
        if (tooClose) {
            incrementEyeStats(app, now) {
                it.copy(
                    proximityWarningCount = it.proximityWarningCount + if (shouldWarn) 1 else 0,
                    proximityCloseSeconds = it.proximityCloseSeconds + captureSeconds,
                )
            }
        }
        if (blinkState.shouldWarn) {
            incrementEyeStats(app, now) {
                it.copy(eyeDryWarningCount = it.eyeDryWarningCount + 1)
            }
        }
        if (shouldWarn) app.notifications.showProximityWarning(ratioPercent)
        if (blinkState.shouldWarn) app.notifications.showEyeDryWarning()
        if (latestSettings.globalOverlayEnabled && shouldWarn && ratioPercent >= latestSettings.overlayStrictDistancePercent) {
            EyeProtectionOverlayService.show(
                context = this,
                title = getString(com.projectlumen.app.R.string.overlay_distance_title),
                message = getString(com.projectlumen.app.R.string.overlay_distance_message),
                durationSeconds = latestSettings.overlayRestDurationSeconds,
            )
        }
        if (latestSettings.globalOverlayEnabled && blinkState.shouldWarn) {
            EyeProtectionOverlayService.show(
                context = this,
                title = getString(com.projectlumen.app.R.string.overlay_blink_title),
                message = getString(com.projectlumen.app.R.string.overlay_blink_message),
                durationSeconds = latestSettings.overlayRestDurationSeconds,
            )
        }

        db.runtimeStateDao().get()?.let {
            db.runtimeStateDao().upsert(
                it.copy(
                    proximityMonitoringActive = false,
                    proximityTooClose = tooClose,
                    proximityLastFaceAt = if (sample != null) now else it.proximityLastFaceAt,
                    proximityCloseStartedAt = if (tooClose && !it.proximityTooClose) now else if (tooClose) it.proximityCloseStartedAt else 0L,
                    proximityCloseTickAt = if (tooClose) now else 0L,
                    proximityLastWarningAt = if (shouldWarn) now else it.proximityLastWarningAt,
                    proximityLastRatioPercent = ratioPercent,
                    blinkLastBlinkAt = blinkState.lastBlinkAt,
                    blinkLastWarningAt = if (blinkState.shouldWarn) now else it.blinkLastWarningAt,
                    blinkLastEyeOpenProbabilityPercent = blinkState.eyeOpenProbabilityPercent,
                    updatedAt = now,
                ),
            )
        }
    }

    private suspend fun incrementEyeStats(
        app: ProjectLumenApplication,
        nowMillis: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        if (app.database.appSettingsDao().get()?.statsEnabled == false) return
        val date = todayKey(nowMillis)
        val dao = app.database.dailyEyeStatsDao()
        val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
        dao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    private suspend fun clearActiveState(app: ProjectLumenApplication) {
        val now = System.currentTimeMillis()
        app.database.runtimeStateDao().get()?.let {
            app.database.runtimeStateDao().upsert(
                it.copy(
                    proximityMonitoringActive = false,
                    proximityTooClose = false,
                    updatedAt = now,
                ),
            )
        }
    }

    private fun AppSettingsEntity.distanceRatioPercent(
        sample: FaceDistanceSample,
    ): Int {
        return when {
            proximityBaselineEyeDistancePx > 0f && sample.eyeDistancePx > 0f -> {
                ((sample.eyeDistancePx / proximityBaselineEyeDistancePx) * 100f).roundToInt()
            }
            proximityBaselineFaceWidthPercent > 0 && sample.faceWidthPercent > 0 -> {
                ((sample.faceWidthPercent.toFloat() / proximityBaselineFaceWidthPercent.toFloat()) * 100f).roundToInt()
            }
            else -> sample.faceWidthPercent
        }.coerceAtLeast(0)
    }

    private fun AppSettingsEntity.isTooClose(
        sample: FaceDistanceSample,
    ): Boolean {
        val multiplier = proximityDistanceMultiplierPercent / 100f
        return when {
            proximityBaselineEyeDistancePx > 0f && sample.eyeDistancePx > 0f -> {
                sample.eyeDistancePx > proximityBaselineEyeDistancePx * multiplier
            }
            proximityBaselineFaceWidthPercent > 0 && sample.faceWidthPercent > 0 -> {
                sample.faceWidthPercent > proximityBaselineFaceWidthPercent * multiplier
            }
            else -> sample.faceWidthPercent >= proximityFaceThresholdPercent
        }
    }

    private fun evaluateBlinkState(
        settings: AppSettingsEntity,
        samples: List<FaceDistanceSample>,
        previousLastBlinkAt: Long,
        previousLastWarningAt: Long,
        nowMillis: Long,
    ): BlinkState {
        if (!settings.blinkMonitoringEnabled || samples.isEmpty()) {
            return BlinkState(previousLastBlinkAt, previousLastWarningAt, 0, shouldWarn = false)
        }
        val probabilitySamples = samples.mapNotNull { it.averageEyeOpenProbability() }
        if (probabilitySamples.isEmpty()) {
            return BlinkState(previousLastBlinkAt, previousLastWarningAt, 0, shouldWarn = false)
        }
        var sawClosed = false
        var blinked = false
        probabilitySamples.forEach { probability ->
            if (probability <= CLOSED_EYE_PROBABILITY) sawClosed = true
            if (sawClosed && probability >= OPEN_EYE_PROBABILITY) blinked = true
        }
        val lastBlinkAt = when {
            blinked -> nowMillis
            previousLastBlinkAt > 0L -> previousLastBlinkAt
            probabilitySamples.last() < OPEN_EYE_PROBABILITY -> nowMillis
            else -> samples.first().capturedAtMillis
        }
        val eyesOpen = probabilitySamples.takeLast(2).all { it >= OPEN_EYE_PROBABILITY }
        val dryForMillis = nowMillis - lastBlinkAt
        val shouldWarn = eyesOpen &&
            dryForMillis >= settings.blinkNoBlinkThresholdSeconds * 1000L &&
            nowMillis - previousLastWarningAt >= settings.blinkAlertCooldownSeconds * 1000L
        return BlinkState(
            lastBlinkAt = lastBlinkAt,
            lastWarningAt = if (shouldWarn) nowMillis else previousLastWarningAt,
            eyeOpenProbabilityPercent = (probabilitySamples.last() * 100f).roundToInt().coerceIn(0, 100),
            shouldWarn = shouldWarn,
        )
    }

    private fun FaceDistanceSample.averageEyeOpenProbability(): Float? {
        val values = listOfNotNull(leftEyeOpenProbability, rightEyeOpenProbability)
        if (values.isEmpty()) return null
        return values.average().toFloat().coerceIn(0f, 1f)
    }

    private data class BlinkState(
        val lastBlinkAt: Long,
        val lastWarningAt: Long,
        val eyeOpenProbabilityPercent: Int,
        val shouldWarn: Boolean,
    )

    companion object {
        private const val EXTRA_CALIBRATE = "calibrate"
        private const val CLOSED_EYE_PROBABILITY = 0.35f
        private const val OPEN_EYE_PROBABILITY = 0.75f

        fun start(context: Context, calibrate: Boolean) {
            val intent = Intent(context, ProximityDetectionService::class.java)
                .putExtra(EXTRA_CALIBRATE, calibrate)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
