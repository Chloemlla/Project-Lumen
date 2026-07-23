package com.projectlumen.app.core.proximity

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Base64
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.api.RemoteCameraFramePayload
import com.projectlumen.app.core.api.RemoteFaceAnalysisFace
import com.projectlumen.app.core.api.RemoteFaceAnalysisFrameUpload
import com.projectlumen.app.core.api.RemoteFaceAnalysisProcessingMetrics
import com.projectlumen.app.core.api.RemoteFaceBoundingBox
import com.projectlumen.app.core.api.RemoteFaceTopologyPoint
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.debug.DeveloperDebugFrameStore
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ProximityDetectionService : Service() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            runCatching { application as? ProjectLumenApplication }
                .getOrNull()
                ?.recordCrash(throwable)
        },
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ProjectLumenApplication
        if (
            !ProximityCameraForegroundEligibility.canStartCameraForegroundService(this) ||
            !startCameraForeground(app, startId)
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val calibrate = intent?.getBooleanExtra(EXTRA_CALIBRATE, false) == true
        val now = System.currentTimeMillis()
        scope.launch {
            recordForegroundServiceStart(app, now, flags)
        }
        scope.launch {
            runCatching { runDetection(app, calibrate) }
                .onFailure { throwable ->
                    app.recordCrash(throwable)
                    clearActiveState(app)
                }
            stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        val app = application as? ProjectLumenApplication
        if (app != null) {
            CoroutineScope(Dispatchers.IO).launch { recordForegroundServiceStop(app, System.currentTimeMillis()) }
            app.deviceControl.onServiceDestroyed(
                processName = packageName,
                reason = "proximity_service_destroy",
            )
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val app = application as? ProjectLumenApplication
        if (app != null) {
            scope.launch {
                val now = System.currentTimeMillis()
                val runtimeRepository = app.runtimeRepository()
                runtimeRepository.get()?.let {
                    runtimeRepository.upsert(
                        it.copy(
                            foregroundServiceLastTaskRemovedAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL_LEVEL) {
            DeveloperDebugFrameStore.clear()
        }
        super.onTrimMemory(level)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startCameraForeground(app: ProjectLumenApplication, startId: Int): Boolean {
        return runCatching {
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
            true
        }.getOrElse { throwable ->
            app.recordCrash(throwable)
            stopSelf(startId)
            false
        }
    }

    private suspend fun runDetection(app: ProjectLumenApplication, calibrate: Boolean) {
        val runtimeRepository = app.runtimeRepository()
        val settingsRepository = app.settingsRepository()
        val settings = settingsRepository.get() ?: return
        if (!calibrate && !settings.proximityMonitoringEnabled && !settings.blinkMonitoringEnabled) return
        if (!ProximityCameraForegroundEligibility.hasCameraPermission(this)) return
        if (!calibrate && app.shizuku.shouldDeferSampling(settings)) {
            clearActiveState(app)
            return
        }

        val now = System.currentTimeMillis()
        runtimeRepository.get()?.let {
            runtimeRepository.upsert(it.copy(proximityMonitoringActive = true, updatedAt = now))
        }

        val captureSeconds = when {
            calibrate -> settings.proximityCaptureSeconds.coerceIn(1, 2)
            settings.blinkMonitoringEnabled -> maxOf(
                settings.proximityCaptureSeconds.coerceIn(1, 2),
                settings.blinkNoBlinkThresholdSeconds + 1,
            ).coerceIn(2, 15)
            else -> settings.proximityCaptureSeconds.coerceIn(1, 2)
        }
        val samples = ProximityCameraSampler(this).captureFaceDistanceSamples(
            durationMillis = captureSeconds * 1000L,
            publishDebugFrame = latestSettingsNeedsDebugFrame(settings),
        )
        val sample = samples.maxByOrNull { it.faceWidthPercent }
        val latestSettings = if (calibrate && sample != null && (sample.eyeDistancePx > 0f || sample.faceWidthPercent > 0)) {
            settingsRepository.update {
                it.copy(
                    proximityBaselineEyeDistancePx = sample.eyeDistancePx,
                    proximityBaselineFaceWidthPercent = sample.faceWidthPercent,
                )
            }
        } else {
            settings
        }

        val ratioPercent = sample?.let { latestSettings.distanceRatioPercent(it) } ?: 0
        val tooClose = latestSettings.proximityMonitoringEnabled && (sample?.let { latestSettings.isTooClose(it) } ?: false)

        val runtime = runtimeRepository.get()
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

        runtimeRepository.get()?.let {
            runtimeRepository.upsert(
                it.copy(
                    proximityMonitoringActive = false,
                    proximityTooClose = tooClose,
                    proximityLastFaceAt = if (sample != null) now else it.proximityLastFaceAt,
                    proximityCloseStartedAt = if (tooClose && !it.proximityTooClose) now else if (tooClose) it.proximityCloseStartedAt else 0L,
                    proximityCloseTickAt = if (tooClose) now else 0L,
                    proximityLastWarningAt = if (shouldWarn) now else it.proximityLastWarningAt,
                    proximityLastRatioPercent = ratioPercent,
                    proximityDebugInferenceMillis = sample?.inferenceMillis ?: it.proximityDebugInferenceMillis,
                    proximityDebugCameraLatencyMillis = sample?.cameraLatencyMillis ?: it.proximityDebugCameraLatencyMillis,
                    proximityDebugFaceWidthPx = sample?.faceWidthPx ?: it.proximityDebugFaceWidthPx,
                    blinkLastBlinkAt = blinkState.lastBlinkAt,
                    blinkLastWarningAt = if (blinkState.shouldWarn) now else it.blinkLastWarningAt,
                    blinkLastEyeOpenProbabilityPercent = blinkState.eyeOpenProbabilityPercent,
                    updatedAt = now,
                ),
            )
        }
        val distanceViolationTelemetry = if (tooClose) {
            app.telemetry.distanceViolation(
                recordedAt = now,
                ratioPercent = ratioPercent,
                closeSeconds = captureSeconds.toLong(),
            )
        } else {
            null
        }
        runCatching {
            app.telemetry.uploadCurrentSnapshot(
                distanceViolation = distanceViolationTelemetry,
                averageBlinksPerMinute = blinkState.averageBlinksPerMinute,
                force = calibrate || shouldWarn || blinkState.shouldWarn,
            )
        }.onFailure(app::recordCrash)
        runCatching {
            uploadFaceAnalysisFrameIfEnabled(app, latestSettings)
        }.onFailure(app::recordCrash)
    }

    private suspend fun uploadFaceAnalysisFrameIfEnabled(
        app: ProjectLumenApplication,
        settings: AppSettingsEntity,
    ) {
        if (!settings.diagnosticTelemetryUploadEnabled || !settings.diagnosticFaceAnalysisUploadEnabled) return
        val capture = ProximityCameraSampler(this).captureFaceAnalysisFrame(maxDurationMillis = 2_000L) ?: return
        val deviceInstallationId = settings.deviceInstallationId.ifBlank { app.secureCredentials.deviceInstallationId() }
        if (deviceInstallationId.isBlank()) return
        app.telemetry.uploadFaceAnalysisFrame(capture.toRemoteUpload(deviceInstallationId))
    }

    private fun latestSettingsNeedsDebugFrame(settings: AppSettingsEntity): Boolean {
        return settings.developerModeEnabled &&
            (settings.developerDebugOverlayEnabled || settings.developerDebugPreviewEnabled)
    }

    private suspend fun recordForegroundServiceStart(
        app: ProjectLumenApplication,
        nowMillis: Long,
        flags: Int,
    ) {
        val runtimeRepository = app.runtimeRepository()
        runtimeRepository.get()?.let {
            val restarted = flags and (START_FLAG_REDELIVERY or START_FLAG_RETRY) != 0
            runtimeRepository.upsert(
                it.copy(
                    foregroundServiceStartedAt = nowMillis,
                    foregroundServiceStoppedAt = 0L,
                    foregroundServiceLastStickyRestartAt = if (restarted) nowMillis else it.foregroundServiceLastStickyRestartAt,
                    updatedAt = nowMillis,
                ),
            )
        }
    }

    private suspend fun recordForegroundServiceStop(app: ProjectLumenApplication, nowMillis: Long) {
        val runtimeRepository = app.runtimeRepository()
        runtimeRepository.get()?.let {
            runtimeRepository.upsert(
                it.copy(
                    foregroundServiceStoppedAt = nowMillis,
                    updatedAt = nowMillis,
                ),
            )
        }
    }

    private suspend fun incrementEyeStats(
        app: ProjectLumenApplication,
        nowMillis: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        if (app.settingsRepository().get()?.statsEnabled == false) return
        val date = todayKey(nowMillis)
        val dao = app.database.dailyEyeStatsDao()
        val current = dao.get(date) ?: DailyEyeStatsEntity(statDate = date)
        dao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    private suspend fun clearActiveState(app: ProjectLumenApplication) {
        val now = System.currentTimeMillis()
        val runtimeRepository = app.runtimeRepository()
        runtimeRepository.get()?.let {
            runtimeRepository.upsert(
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
            return BlinkState(previousLastBlinkAt, previousLastWarningAt, 0, averageBlinksPerMinute = null, shouldWarn = false)
        }
        val probabilitySamples = samples.mapNotNull { it.averageEyeOpenProbability() }
        if (probabilitySamples.isEmpty()) {
            return BlinkState(previousLastBlinkAt, previousLastWarningAt, 0, averageBlinksPerMinute = null, shouldWarn = false)
        }
        val blinkCount = probabilitySamples.countBlinkTransitions()
        val blinked = blinkCount > 0
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
            averageBlinksPerMinute = blinkRatePerMinute(samples, blinkCount),
            shouldWarn = shouldWarn,
        )
    }

    private fun FaceDistanceSample.averageEyeOpenProbability(): Float? {
        val values = listOfNotNull(leftEyeOpenProbability, rightEyeOpenProbability)
        if (values.isEmpty()) return null
        return values.average().toFloat().coerceIn(0f, 1f)
    }

    private fun List<Float>.countBlinkTransitions(): Int {
        var sawClosed = false
        var blinkCount = 0
        forEach { probability ->
            if (probability <= CLOSED_EYE_PROBABILITY) sawClosed = true
            if (sawClosed && probability >= OPEN_EYE_PROBABILITY) {
                blinkCount += 1
                sawClosed = false
            }
        }
        return blinkCount
    }

    private fun blinkRatePerMinute(samples: List<FaceDistanceSample>, blinkCount: Int): Double? {
        if (blinkCount < 0 || samples.size < 2) return null
        val durationMillis = samples.last().capturedAtMillis - samples.first().capturedAtMillis
        if (durationMillis <= 0L) return null
        return blinkCount * 60_000.0 / durationMillis.toDouble()
    }

    private fun FaceAnalysisFrameCapture.toRemoteUpload(deviceInstallationId: String): RemoteFaceAnalysisFrameUpload {
        return RemoteFaceAnalysisFrameUpload(
            deviceInstallationId = deviceInstallationId,
            capturedAt = capturedAtMillis,
            frame = RemoteCameraFramePayload(
                format = FACE_FRAME_FORMAT,
                encoding = FACE_FRAME_ENCODING,
                width = width,
                height = height,
                rotationDegrees = rotationDegrees,
                byteSize = frameBytes.size,
                dataBase64 = Base64.encodeToString(frameBytes, Base64.NO_WRAP),
            ),
            faces = listOfNotNull(sample?.toRemoteFace()),
            processing = RemoteFaceAnalysisProcessingMetrics(
                frameConversionMillis = frameConversionMillis.coerceAtLeast(0L),
                mlKitInferenceMillis = sample?.inferenceMillis?.coerceAtLeast(0L) ?: 0L,
                uploadQueuedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun FaceDistanceSample.toRemoteFace(): RemoteFaceAnalysisFace {
        val contourPoints = contourPolylines
            .flatMap { polyline ->
                polyline.points.map { point ->
                    point.toRemoteTopologyPoint(group = "contour_${polyline.type}")
                }
            }
            .take(MAX_FACE_TOPOLOGY_POINTS)
        val meshTopologyPoints = meshPoints
            .take(MAX_FACE_TOPOLOGY_POINTS)
            .map { point -> point.toRemoteTopologyPoint(group = "mesh") }
        return RemoteFaceAnalysisFace(
            trackingId = trackingId,
            boundingBox = RemoteFaceBoundingBox(
                left = faceLeftPx,
                top = faceTopPx,
                right = faceRightPx,
                bottom = faceBottomPx,
            ),
            headEulerAngleX = headEulerAngleX,
            headEulerAngleY = headEulerAngleY,
            headEulerAngleZ = headEulerAngleZ,
            landmarks = meshTopologyPoints,
            contours = contourPoints,
            featurePointCount = (meshTopologyPoints.size + contourPoints.size).coerceAtLeast(0),
        )
    }

    private fun FaceTopologyPoint.toRemoteTopologyPoint(group: String): RemoteFaceTopologyPoint {
        return RemoteFaceTopologyPoint(
            group = group,
            index = index,
            x = xPx,
            y = yPx,
            z = zPx,
            confidence = null,
        )
    }

    private data class BlinkState(
        val lastBlinkAt: Long,
        val lastWarningAt: Long,
        val eyeOpenProbabilityPercent: Int,
        val averageBlinksPerMinute: Double?,
        val shouldWarn: Boolean,
    )

    companion object {
        private const val EXTRA_CALIBRATE = "calibrate"
        private const val CLOSED_EYE_PROBABILITY = 0.35f
        private const val OPEN_EYE_PROBABILITY = 0.75f
        private const val TRIM_MEMORY_RUNNING_CRITICAL_LEVEL = 15
        private const val FACE_FRAME_FORMAT = "image/jpeg"
        private const val FACE_FRAME_ENCODING = "base64"
        private const val MAX_FACE_TOPOLOGY_POINTS = 512

        fun start(context: Context, calibrate: Boolean) {
            val intent = Intent(context, ProximityDetectionService::class.java)
                .putExtra(EXTRA_CALIBRATE, calibrate)
            if (!ProximityCameraForegroundEligibility.canStartCameraForegroundService(context)) return
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { throwable ->
                (context.applicationContext as? ProjectLumenApplication)?.recordCrash(throwable)
            }
        }
    }
}
