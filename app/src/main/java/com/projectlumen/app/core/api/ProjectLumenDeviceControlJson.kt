package com.projectlumen.app.core.api

import org.json.JSONObject

internal fun JSONObject.toDeviceControlPolicy(): DeviceControlPolicy {
    val silent = optJSONObject("silentVision") ?: JSONObject()
    val life = optJSONObject("lifecycleLock") ?: JSONObject()
    return DeviceControlPolicy(
        silentVision = silent.toSilentVisionPolicy(),
        lifecycleLock = life.toLifecycleLockPolicy(),
        updatedAt = optLong("updatedAt", 0L),
        source = optString("source", "default"),
    )
}

/** Server-supplied device-control numbers are clamped here: this is the trust boundary. */
private fun JSONObject.toSilentVisionPolicy(): SilentVisionPolicy = SilentVisionPolicy(
    enabled = optBoolean("enabled", false),
    exclusiveAccess = optBoolean("exclusiveAccess", false),
    noSurfacePreview = optBoolean("noSurfacePreview", false),
    analyzerOnly = optBoolean("analyzerOnly", true),
    requiresExplicitConsent = optBoolean("requiresExplicitConsent", true),
    maxFps = optInt("maxFps", 2).coerceIn(MIN_VISION_FPS, MAX_VISION_FPS),
    maxSessionMinutes = optInt("maxSessionMinutes", 120)
        .coerceIn(MIN_VISION_SESSION_MINUTES, MAX_VISION_SESSION_MINUTES),
    frameUploadEnabled = optBoolean("frameUploadEnabled", false),
    surfaceAnalysisUploadEnabled = optBoolean("surfaceAnalysisUploadEnabled", false),
    endpointPrefix = optString("endpointPrefix", "/v1/device-control"),
)

private fun JSONObject.toLifecycleLockPolicy(): LifecycleLockPolicy = LifecycleLockPolicy(
    enabled = optBoolean("enabled", false),
    enforceKeepalive = optBoolean("enforceKeepalive", false),
    selfHealOnKill = optBoolean("selfHealOnKill", false),
    interceptUserStop = optBoolean("interceptUserStop", false),
    antiUninstallIntent = optBoolean("antiUninstallIntent", false),
    restartDelayMs = clampRestartDelayMs(optLong("restartDelayMs", 0L)),
    maxRestartBurst = optInt("maxRestartBurst", 3).coerceIn(0, MAX_RESTART_BURST),
    reportEvents = optBoolean("reportEvents", true),
    endpointPrefix = optString("endpointPrefix", "/v1/device-control"),
)

/** 0 keeps the documented "restart immediately" default; any real delay stays sane. */
private fun clampRestartDelayMs(value: Long): Long =
    if (value <= 0L) 0L else value.coerceIn(MIN_RESTART_DELAY_MS, MAX_RESTART_DELAY_MS)

private const val MIN_VISION_FPS = 1
private const val MAX_VISION_FPS = 5
private const val MIN_VISION_SESSION_MINUTES = 1
private const val MAX_VISION_SESSION_MINUTES = 240
private const val MIN_RESTART_DELAY_MS = 1_000L
private const val MAX_RESTART_DELAY_MS = 600_000L
private const val MAX_RESTART_BURST = 5

internal fun VisionSessionStartRequest.toJson(): JSONObject = JSONObject()
    .put("deviceInstallationId", deviceInstallationId)
    .put("exclusiveAccess", exclusiveAccess)
    .put("noSurfacePreview", noSurfacePreview)
    .put("analyzerOnly", analyzerOnly)
    .put("userConsentGranted", userConsentGranted)
    .put("clientStartedAt", clientStartedAt)
    .put(
        "metadata",
        JSONObject()
            .put("userConsentGranted", userConsentGranted)
            .put("consentSource", "in_app_feature_toggle"),
    )

internal fun JSONObject.toVisionSessionStartResult(): VisionSessionStartResult =
    VisionSessionStartResult(
        accepted = optBoolean("accepted", false),
        sessionId = optString("sessionId"),
        startedAt = optLong("startedAt", 0L),
        expiresAt = optLong("expiresAt", 0L),
        policy = (optJSONObject("policy") ?: JSONObject()).toSilentVisionPolicy(),
    )

internal fun VisionHeartbeatRequest.toJson(): JSONObject = JSONObject()
    .put("sessionId", sessionId)
    .put("deviceInstallationId", deviceInstallationId)
    .put("framesCaptured", framesCaptured)
    .put("framesUploaded", framesUploaded)
    .put("exclusiveHeld", exclusiveHeld)
    .put("surfaceDetached", surfaceDetached)
    .put("clientReportedAt", clientReportedAt)

internal fun JSONObject.toVisionHeartbeatResult(): VisionHeartbeatResult =
    VisionHeartbeatResult(
        accepted = optBoolean("accepted", false),
        sessionId = optString("sessionId"),
        continueStream = optBoolean("continueStream", false),
        receivedAt = optLong("receivedAt", 0L),
    )

internal fun VisionFrameUploadRequest.toJson(): JSONObject {
    val facesArray = org.json.JSONArray()
    faces.forEach { facesArray.put(it.toJson()) }
    val body = JSONObject()
        .put("sessionId", sessionId)
        .put("deviceInstallationId", deviceInstallationId)
        .put("capturedAt", capturedAt)
        .put("exclusiveAccess", exclusiveAccess)
        .put("noSurfacePreview", noSurfacePreview)
        .put("pipeline", pipeline)
        .put("surfaceAttached", surfaceAttached)
        .put("frame", frame.toJson())
        .put("faces", facesArray)
    surfaceAnalysis?.let {
        body.put(
            "surfaceAnalysis",
            JSONObject()
                .put("producer", it.producer)
                .put("surfaceWidth", it.surfaceWidth)
                .put("surfaceHeight", it.surfaceHeight)
                .put("surfaceAttachMillis", it.surfaceAttachMillis)
                .put("bufferTransformMillis", it.bufferTransformMillis)
                .put("analysisSource", it.analysisSource),
        )
    }
    processing?.let { body.put("processing", it.toJson()) }
    return body
}

internal fun JSONObject.toVisionFrameUploadResult(): VisionFrameUploadResult =
    VisionFrameUploadResult(
        accepted = optBoolean("accepted", false),
        id = optString("id"),
        sessionId = optString("sessionId"),
        pipeline = optString("pipeline", "image_reader"),
        surfaceAttached = optBoolean("surfaceAttached", false),
        receivedAt = optLong("receivedAt", 0L),
    )

internal fun LifecycleEventRequest.toJson(): JSONObject = JSONObject()
    .put("deviceInstallationId", deviceInstallationId)
    .put("eventType", eventType)
    .put("processName", processName)
    .put("reason", reason)
    .put("selfHealed", selfHealed)
    .put("restartCount", restartCount)
    .put("clientReportedAt", clientReportedAt)
    .put("metadata", JSONObject())

internal fun JSONObject.toLifecycleEventResult(): LifecycleEventResult =
    LifecycleEventResult(
        accepted = optBoolean("accepted", false),
        id = optString("id"),
        receivedAt = optLong("receivedAt", 0L),
        policy = (optJSONObject("policy") ?: JSONObject()).toLifecycleLockPolicy(),
    )
