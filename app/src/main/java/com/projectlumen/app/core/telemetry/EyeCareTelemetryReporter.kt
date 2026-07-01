package com.projectlumen.app.core.telemetry

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.core.api.AiPerformanceTelemetry
import com.projectlumen.app.core.api.BlinkMetricsTelemetry
import com.projectlumen.app.core.api.CalibrationAnchorTelemetry
import com.projectlumen.app.core.api.CrashLogTelemetry
import com.projectlumen.app.core.api.DailyEyeHealthTelemetry
import com.projectlumen.app.core.api.DeviceProfileTelemetry
import com.projectlumen.app.core.api.DeveloperDebugTelemetry
import com.projectlumen.app.core.api.DistanceViolationTelemetry
import com.projectlumen.app.core.api.EnvironmentContextTelemetry
import com.projectlumen.app.core.api.ProjectLumenApiClient
import com.projectlumen.app.core.api.ProjectLumenApiConfig
import com.projectlumen.app.core.api.RemoteTelemetryUpload
import com.projectlumen.app.core.api.RemoteTelemetryUploadResult
import com.projectlumen.app.core.api.RestComplianceTelemetry
import com.projectlumen.app.core.api.SensorDisturbanceTelemetry
import com.projectlumen.app.core.crash.CrashReportStore
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.time.todayKey
import com.projectlumen.app.openapi.LumenOpenContracts
import com.projectlumen.app.openapi.sanitizeLumenOpenSourceApp
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.roundToInt

class EyeCareTelemetryReporter(
    private val context: Context,
    private val database: AppDatabase,
    private val apiClient: ProjectLumenApiClient,
    private val accessTokenProvider: suspend () -> String? = {
        ProjectLumenApiConfig.telemetryAccessToken.takeIf { it.isNotBlank() }
    },
) {
    private val lastUploadAt = AtomicLong(0L)

    suspend fun uploadCurrentSnapshot(
        distanceViolation: DistanceViolationTelemetry? = null,
        averageBlinksPerMinute: Double? = null,
        force: Boolean = false,
        sourceApp: String = LumenOpenContracts.SOURCE_APP_PROJECT_LUMEN,
    ): RemoteTelemetryUploadResult? {
        return runCatching {
            uploadCurrentSnapshotUnchecked(
                distanceViolation = distanceViolation,
                averageBlinksPerMinute = averageBlinksPerMinute,
                force = force,
                sourceApp = sourceApp,
            )
        }.getOrNull()
    }

    private suspend fun uploadCurrentSnapshotUnchecked(
        distanceViolation: DistanceViolationTelemetry? = null,
        averageBlinksPerMinute: Double? = null,
        force: Boolean = false,
        sourceApp: String = LumenOpenContracts.SOURCE_APP_PROJECT_LUMEN,
    ): RemoteTelemetryUploadResult? {
        val accessToken = accessTokenProvider()?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val nowMillis = System.currentTimeMillis()
        if (!force && nowMillis - lastUploadAt.get() < MIN_UPLOAD_INTERVAL_MILLIS) return null
        val settings = database.appSettingsDao().get() ?: return null
        if (!settings.statsEnabled) return null
        val runtime = database.runtimeStateDao().get() ?: RuntimeStateEntity()
        val stats = database.dailyEyeStatsDao().get(todayKey(nowMillis)) ?: DailyEyeStatsEntity(statDate = todayKey(nowMillis))
        val upload = RemoteTelemetryUpload(
            deviceInstallationId = settings.deviceInstallationId,
            sourceApp = sanitizeLumenOpenSourceApp(sourceApp),
            recordedAt = nowMillis,
            dailyHealth = stats.toDailyHealthTelemetry(runtime, distanceViolation, averageBlinksPerMinute),
            environmentContext = listOf(runtime.toEnvironmentContext(settings, nowMillis)),
            deviceProfile = buildDeviceProfile(),
            calibrationAnchor = settings.toCalibrationAnchor(),
            aiPerformance = runtime.toAiPerformance(),
            developerDebug = runtime.toDeveloperDebug(settings),
        )
        return apiClient.uploadTelemetry(accessToken, upload)
            .also { lastUploadAt.set(nowMillis) }
    }

    fun distanceViolation(
        recordedAt: Long,
        ratioPercent: Int,
        closeSeconds: Long,
    ): DistanceViolationTelemetry {
        return DistanceViolationTelemetry(
            recordedAt = recordedAt,
            distanceFactor = ratioPercent.coerceAtLeast(0) / 100.0,
            ratioPercent = ratioPercent.coerceAtLeast(0),
            closeSeconds = closeSeconds.coerceAtLeast(0L),
        )
    }

    private fun DailyEyeStatsEntity.toDailyHealthTelemetry(
        runtime: RuntimeStateEntity,
        distanceViolation: DistanceViolationTelemetry?,
        averageBlinksPerMinute: Double?,
    ): DailyEyeHealthTelemetry {
        val breakDecisions = completedBreakCount + skipCount
        val complianceRate = if (breakDecisions > 0) {
            ((completedBreakCount.toFloat() / breakDecisions.toFloat()) * 100f).roundToInt()
        } else {
            100
        }.coerceIn(0, 100)
        val latestDistanceViolation = distanceViolation ?: runtime.takeIf {
            it.proximityTooClose && it.proximityLastRatioPercent > 0
        }?.let {
            DistanceViolationTelemetry(
                recordedAt = it.proximityCloseTickAt.takeIf { tick -> tick > 0L } ?: System.currentTimeMillis(),
                distanceFactor = it.proximityLastRatioPercent / 100.0,
                ratioPercent = it.proximityLastRatioPercent,
                closeSeconds = 0L,
            )
        }
        val continuousOverTwentyViolations = if (maxContinuousWorkSeconds >= TWENTY_MINUTES_SECONDS) {
            maxOf(preAlertCount, 1)
        } else {
            preAlertCount
        }

        return DailyEyeHealthTelemetry(
            statDate = statDate,
            totalScreenSeconds = workingSeconds,
            restSeconds = restSeconds,
            continuousOverTwentyCount = continuousOverTwentyViolations,
            maxContinuousWorkSeconds = maxContinuousWorkSeconds,
            distanceViolationCount = proximityWarningCount,
            distanceCloseSeconds = proximityCloseSeconds,
            distanceViolations = listOfNotNull(latestDistanceViolation),
            blinkMetrics = BlinkMetricsTelemetry(
                averageBlinksPerMinute = averageBlinksPerMinute,
                eyeDryWarningCount = eyeDryWarningCount,
                severeEyeDryRisk = eyeDryWarningCount > 0,
                lastEyeOpenProbabilityPercent = runtime.blinkLastEyeOpenProbabilityPercent.takeIf { it > 0 },
            ),
            restCompliance = RestComplianceTelemetry(
                completedBreakCount = completedBreakCount,
                skippedBreakCount = skipCount,
                complianceRatePercent = complianceRate,
            ),
        )
    }

    private fun RuntimeStateEntity.toEnvironmentContext(
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): EnvironmentContextTelemetry {
        val luxLevel = when {
            ambientLastLux > 10_000f -> 2
            ambientTooDark || ambientLastLux in 0f..settings.ambientLightLowLuxThreshold.toFloat() -> 0
            else -> 1
        }
        return EnvironmentContextTelemetry(
            recordedAt = nowMillis,
            luxLevel = luxLevel,
            poseStatus = poseStatus(),
            scenarioStatus = scenarioStatus(luxLevel, nowMillis),
        )
    }

    private fun RuntimeStateEntity.poseStatus(): String {
        return when {
            abs(sensorRollDegrees) >= LYING_ANGLE_THRESHOLD || abs(sensorPitchDegrees) >= LYING_ANGLE_THRESHOLD -> "lying"
            sensorLastAccelerationMagnitude > BUMPY_ACCELERATION_THRESHOLD -> "bumpy"
            else -> "stable"
        }
    }

    private fun scenarioStatus(luxLevel: Int, nowMillis: Long): String {
        val hour = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).hour
        return when {
            luxLevel == 0 && (hour >= 23 || hour < 6) -> "late_night_low_light"
            luxLevel == 0 -> "low_light"
            luxLevel == 2 -> "strong_outdoor_light"
            else -> "normal"
        }
    }

    private fun buildDeviceProfile(): DeviceProfileTelemetry {
        return DeviceProfileTelemetry(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            androidSdk = Build.VERSION.SDK_INT,
            frontCameraResolution = frontCameraResolution(),
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        )
    }

    private fun frontCameraResolution(): String {
        return runCatching {
            val manager = context.getSystemService(CameraManager::class.java) ?: return "unknown"
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: return "unknown"
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.YUV_420_888)
                ?.maxByOrNull { it.width * it.height }
                ?: return "unknown"
            "${size.width}x${size.height}"
        }.getOrDefault("unknown")
    }

    private fun AppSettingsEntity.toCalibrationAnchor(): CalibrationAnchorTelemetry? {
        if (proximityBaselineFaceWidthPercent <= 0 && proximityBaselineEyeDistancePx <= 0f) return null
        return CalibrationAnchorTelemetry(
            standardDistanceCm = STANDARD_CALIBRATION_DISTANCE_CM,
            baseFaceWidthPercent = proximityBaselineFaceWidthPercent,
            baseEyeDistancePx = proximityBaselineEyeDistancePx.toDouble(),
        )
    }

    private fun RuntimeStateEntity.toAiPerformance(): AiPerformanceTelemetry {
        return AiPerformanceTelemetry(
            averageFaceDetectionMs = proximityDebugInferenceMillis,
            cameraLatencyMs = proximityDebugCameraLatencyMillis,
            backgroundKillCount = if (foregroundServiceLastTaskRemovedAt > 0L) 1 else 0,
        )
    }

    private fun RuntimeStateEntity.toDeveloperDebug(settings: AppSettingsEntity): DeveloperDebugTelemetry? {
        if (!settings.developerModeEnabled) return null
        return DeveloperDebugTelemetry(
            sensorDisturbance = SensorDisturbanceTelemetry(
                pitchDegrees = sensorPitchDegrees.toDouble(),
                rollDegrees = sensorRollDegrees.toDouble(),
                yawDegrees = sensorYawDegrees.toDouble(),
                accelerationMagnitude = sensorLastAccelerationMagnitude.toDouble(),
            ),
            crashLogs = CrashReportStore(context).load()?.let { report ->
                listOf(
                    CrashLogTelemetry(
                        crashedAt = report.crashedAtMillis,
                        exceptionType = report.exceptionType.take(MAX_CRASH_FIELD_LENGTH),
                        rootCause = report.rootCause.take(MAX_CRASH_FIELD_LENGTH),
                        stackTraceLines = report.stackTrace
                            .lines()
                            .filter { it.isNotBlank() }
                            .take(MAX_CRASH_STACK_LINES)
                            .map { it.take(MAX_CRASH_LINE_LENGTH) },
                    ),
                )
            }.orEmpty(),
        )
    }

    private companion object {
        private const val MIN_UPLOAD_INTERVAL_MILLIS = 60_000L
        private const val TWENTY_MINUTES_SECONDS = 20 * 60L
        private const val STANDARD_CALIBRATION_DISTANCE_CM = 35
        private const val LYING_ANGLE_THRESHOLD = 55f
        private const val BUMPY_ACCELERATION_THRESHOLD = 13.8f
        private const val MAX_CRASH_STACK_LINES = 32
        private const val MAX_CRASH_LINE_LENGTH = 320
        private const val MAX_CRASH_FIELD_LENGTH = 160
    }
}
