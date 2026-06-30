package com.projectlumen.app.core.api

import org.json.JSONArray
import org.json.JSONObject

internal fun RemoteTelemetryUpload.toJson(): JSONObject = JSONObject()
    .put("deviceInstallationId", deviceInstallationId)
    .put("recordedAt", recordedAt)
    .putNullable("dailyHealth", dailyHealth?.toJson())
    .put("environmentContext", JSONArray(environmentContext.map { it.toJson() }))
    .put("deviceProfile", deviceProfile.toJson())
    .putNullable("calibrationAnchor", calibrationAnchor?.toJson())
    .putNullable("aiPerformance", aiPerformance?.toJson())
    .putNullable("developerDebug", developerDebug?.toJson())

internal fun JSONObject.toTelemetryUploadResult(): RemoteTelemetryUploadResult = RemoteTelemetryUploadResult(
    accepted = optBoolean("accepted"),
    id = optString("id"),
    receivedAt = optLong("receivedAt"),
)

private fun DailyEyeHealthTelemetry.toJson(): JSONObject = JSONObject()
    .put("statDate", statDate)
    .put("totalScreenSeconds", totalScreenSeconds)
    .put("restSeconds", restSeconds)
    .put("continuousOverTwentyCount", continuousOverTwentyCount)
    .put("maxContinuousWorkSeconds", maxContinuousWorkSeconds)
    .put("distanceViolationCount", distanceViolationCount)
    .put("distanceCloseSeconds", distanceCloseSeconds)
    .put("distanceViolations", JSONArray(distanceViolations.map { it.toJson() }))
    .put("blinkMetrics", blinkMetrics.toJson())
    .put("restCompliance", restCompliance.toJson())

private fun DistanceViolationTelemetry.toJson(): JSONObject = JSONObject()
    .put("recordedAt", recordedAt)
    .put("distanceFactor", distanceFactor)
    .put("ratioPercent", ratioPercent)
    .put("closeSeconds", closeSeconds)

private fun BlinkMetricsTelemetry.toJson(): JSONObject = JSONObject()
    .putNullable("averageBlinksPerMinute", averageBlinksPerMinute)
    .put("eyeDryWarningCount", eyeDryWarningCount)
    .put("severeEyeDryRisk", severeEyeDryRisk)
    .putNullable("lastEyeOpenProbabilityPercent", lastEyeOpenProbabilityPercent)

private fun RestComplianceTelemetry.toJson(): JSONObject = JSONObject()
    .put("completedBreakCount", completedBreakCount)
    .put("skippedBreakCount", skippedBreakCount)
    .put("complianceRatePercent", complianceRatePercent)

private fun EnvironmentContextTelemetry.toJson(): JSONObject = JSONObject()
    .put("recordedAt", recordedAt)
    .put("luxLevel", luxLevel)
    .put("poseStatus", poseStatus)
    .put("scenarioStatus", scenarioStatus)

private fun DeviceProfileTelemetry.toJson(): JSONObject = JSONObject()
    .put("manufacturer", manufacturer)
    .put("model", model)
    .put("androidRelease", androidRelease)
    .put("androidSdk", androidSdk)
    .put("frontCameraResolution", frontCameraResolution)
    .put("appVersionName", appVersionName)
    .put("appVersionCode", appVersionCode)

private fun CalibrationAnchorTelemetry.toJson(): JSONObject = JSONObject()
    .put("standardDistanceCm", standardDistanceCm)
    .put("baseFaceWidthPercent", baseFaceWidthPercent)
    .put("baseEyeDistancePx", baseEyeDistancePx)

private fun AiPerformanceTelemetry.toJson(): JSONObject = JSONObject()
    .put("averageFaceDetectionMs", averageFaceDetectionMs)
    .put("cameraLatencyMs", cameraLatencyMs)
    .put("backgroundKillCount", backgroundKillCount)

private fun DeveloperDebugTelemetry.toJson(): JSONObject = JSONObject()
    .putNullable("sensorDisturbance", sensorDisturbance?.toJson())
    .put("crashLogs", JSONArray(crashLogs.map { it.toJson() }))

private fun SensorDisturbanceTelemetry.toJson(): JSONObject = JSONObject()
    .put("pitchDegrees", pitchDegrees)
    .put("rollDegrees", rollDegrees)
    .put("yawDegrees", yawDegrees)
    .put("accelerationMagnitude", accelerationMagnitude)

private fun CrashLogTelemetry.toJson(): JSONObject = JSONObject()
    .put("crashedAt", crashedAt)
    .put("exceptionType", exceptionType)
    .put("rootCause", rootCause)
    .put("stackTraceLines", JSONArray(stackTraceLines))

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
    return this
}
