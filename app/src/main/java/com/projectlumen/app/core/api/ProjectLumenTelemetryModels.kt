package com.projectlumen.app.core.api

data class RemoteTelemetryUpload(
    val deviceInstallationId: String,
    val sourceApp: String,
    val recordedAt: Long,
    val dailyHealth: DailyEyeHealthTelemetry?,
    val environmentContext: List<EnvironmentContextTelemetry>,
    val deviceProfile: DeviceProfileTelemetry,
    val calibrationAnchor: CalibrationAnchorTelemetry?,
    val aiPerformance: AiPerformanceTelemetry?,
    val developerDebug: DeveloperDebugTelemetry?,
    val deviceDiagnostics: DeviceDiagnosticsTelemetry?,
    val pomodoroProductivity: PomodoroProductivityTelemetry?,
    val userConfiguration: UserConfigurationTelemetry?,
)

data class DailyEyeHealthTelemetry(
    val statDate: String,
    val totalScreenSeconds: Long,
    val restSeconds: Long,
    val continuousOverTwentyCount: Int,
    val maxContinuousWorkSeconds: Long,
    val distanceViolationCount: Int,
    val distanceCloseSeconds: Long,
    val lowLightWarningCount: Int,
    val distanceViolations: List<DistanceViolationTelemetry>,
    val blinkMetrics: BlinkMetricsTelemetry,
    val restCompliance: RestComplianceTelemetry,
)

data class PomodoroProductivityTelemetry(
    val statDate: String,
    val completedTomatoCount: Int,
    val restartCount: Int,
    val completedFocusSessions: Int,
    val totalFocusSeconds: Long,
    val totalBreakSeconds: Long,
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

data class DeviceDiagnosticsTelemetry(
    val consentActiveAt: Long,
    val collectedAt: Long,
    val collectionSource: String,
    val shizukuReady: Boolean,
    val shizukuServerVersion: Int,
    val shizukuServerUid: Int,
    val userAppCount: Int,
    val userAppsTruncated: Boolean,
    val userApps: List<InstalledAppTelemetry>,
)

data class InstalledAppTelemetry(
    val packageName: String,
    val installerPackageName: String,
    val versionCode: Long?,
    val uid: Int?,
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
    val apiTraces: List<ApiTraceTelemetry>,
)

data class ApiTraceTelemetry(
    val startedAt: Long,
    val method: String,
    val path: String,
    val signed: Boolean,
    val integrityRequested: Boolean,
    val authorizationAttached: Boolean,
    val statusCode: Int?,
    val durationMillis: Long,
    val errorType: String,
    val errorMessage: String,
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

data class UserConfigurationTelemetry(
    val dailyGoal: DailyGoalTelemetry?,
    val audioFeedback: AudioFeedbackTelemetry?,
    val reminderPlans: List<ReminderPlanTelemetry>,
    val tipTemplates: List<TipTemplateTelemetry>,
)

data class AudioFeedbackTelemetry(
    val soundEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val preAlertSoundEnabled: Boolean,
    val restStartSoundEnabled: Boolean,
    val pomodoroWorkStartSoundEnabled: Boolean,
    val pomodoroWorkEndSoundEnabled: Boolean,
    val preAlertVolumePercent: Int,
    val restStartVolumePercent: Int,
    val restEndVolumePercent: Int,
    val pomodoroWorkStartVolumePercent: Int,
    val pomodoroWorkEndVolumePercent: Int,
)

data class DailyGoalTelemetry(
    val restBreakGoal: Int,
    val maxContinuousWorkMinutes: Int,
    val pomodoroGoal: Int,
    val weeklyActiveDaysGoal: Int,
    val updatedAt: Long,
)

data class ReminderPlanTelemetry(
    val id: Long,
    val enabled: Boolean,
    val warnIntervalMinutes: Int,
    val restDurationSeconds: Int,
    val quietHoursEnabled: Boolean,
    val quietMode: String,
    val sortOrder: Int,
    val updatedAt: Long,
)

data class TipTemplateTelemetry(
    val id: Long,
    val isBuiltin: Boolean,
    val backgroundType: String,
    val hasImage: Boolean,
    val showSkipButton: Boolean,
    val isPremium: Boolean,
    val hasRemoteId: Boolean,
    val countdownStyle: String,
    val sortOrder: Int,
    val updatedAt: Long,
)

data class RemoteTelemetryUploadResult(
    val accepted: Boolean,
    val id: String,
    val receivedAt: Long,
)
