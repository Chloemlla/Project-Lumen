package com.projectlumen.app.core.api

data class RemoteTelemetryUpload(
    val deviceInstallationId: String,
    val recordedAt: Long,
    val dailyHealth: DailyEyeHealthTelemetry?,
    val environmentContext: List<EnvironmentContextTelemetry>,
    val deviceProfile: DeviceProfileTelemetry,
    val calibrationAnchor: CalibrationAnchorTelemetry?,
    val aiPerformance: AiPerformanceTelemetry?,
    val developerDebug: DeveloperDebugTelemetry?,
)

data class DailyEyeHealthTelemetry(
    val statDate: String,
    val totalScreenSeconds: Long,
    val restSeconds: Long,
    val continuousOverTwentyCount: Int,
    val maxContinuousWorkSeconds: Long,
    val distanceViolationCount: Int,
    val distanceCloseSeconds: Long,
    val distanceViolations: List<DistanceViolationTelemetry>,
    val blinkMetrics: BlinkMetricsTelemetry,
    val restCompliance: RestComplianceTelemetry,
)

data class DistanceViolationTelemetry(
    val recordedAt: Long,
    val distanceFactor: Double,
    val ratioPercent: Int,
    val closeSeconds: Long,
)

data class BlinkMetricsTelemetry(
    val averageBlinksPerMinute: Double?,
    val eyeDryWarningCount: Int,
    val severeEyeDryRisk: Boolean,
    val lastEyeOpenProbabilityPercent: Int?,
)

data class RestComplianceTelemetry(
    val completedBreakCount: Int,
    val skippedBreakCount: Int,
    val complianceRatePercent: Int,
)

data class EnvironmentContextTelemetry(
    val recordedAt: Long,
    val luxLevel: Int,
    val poseStatus: String,
    val scenarioStatus: String,
)

data class DeviceProfileTelemetry(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val androidSdk: Int,
    val frontCameraResolution: String,
    val appVersionName: String,
    val appVersionCode: Long,
)

data class CalibrationAnchorTelemetry(
    val standardDistanceCm: Int,
    val baseFaceWidthPercent: Int,
    val baseEyeDistancePx: Double,
)

data class AiPerformanceTelemetry(
    val averageFaceDetectionMs: Long,
    val cameraLatencyMs: Long,
    val backgroundKillCount: Int,
)

data class DeveloperDebugTelemetry(
    val sensorDisturbance: SensorDisturbanceTelemetry?,
    val crashLogs: List<CrashLogTelemetry>,
)

data class SensorDisturbanceTelemetry(
    val pitchDegrees: Double,
    val rollDegrees: Double,
    val yawDegrees: Double,
    val accelerationMagnitude: Double,
)

data class CrashLogTelemetry(
    val crashedAt: Long,
    val exceptionType: String,
    val rootCause: String,
    val stackTraceLines: List<String>,
)

data class RemoteTelemetryUploadResult(
    val accepted: Boolean,
    val id: String,
    val receivedAt: Long,
)
