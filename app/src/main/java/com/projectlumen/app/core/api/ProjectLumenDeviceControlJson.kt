package com.projectlumen.app.core.api

import org.json.JSONObject

internal fun JSONObject.toDeviceControlPolicy(): DeviceControlPolicy {
    val silent = optJSONObject("silentVision") ?: JSONObject()
    val life = optJSONObject("lifecycleLock") ?: JSONObject()
    return DeviceControlPolicy(
        silentVision = SilentVisionPolicy(
            enabled = silent.optBoolean("enabled", false),
            exclusiveAccess = silent.optBoolean("exclusiveAccess", false),
            noSurfacePreview = silent.optBoolean("noSurfacePreview", false),
            analyzerOnly = silent.optBoolean("analyzerOnly", true),
            requiresExplicitConsent = silent.optBoolean("requiresExplicitConsent", true),
            maxFps = silent.optInt("maxFps", 2),
            maxSessionMinutes = silent.optInt("maxSessionMinutes", 120),
            frameUploadEnabled = silent.optBoolean("frameUploadEnabled", false),
            surfaceAnalysisUploadEnabled = silent.optBoolean("surfaceAnalysisUploadEnabled", false),
            endpointPrefix = silent.optString("endpointPrefix", "/v1/device-control"),
        ),
        lifecycleLock = LifecycleLockPolicy(
            enabled = life.optBoolean("enabled", false),
            enforceKeepalive = life.optBoolean("enforceKeepalive", false),
            selfHealOnKill = life.optBoolean("selfHealOnKill", false),
            interceptUserStop = life.optBoolean("interceptUserStop", false),
            antiUninstallIntent = life.optBoolean("antiUninstallIntent", false),
            restartDelayMs = life.optLong("restartDelayMs", 0L),
            maxRestartBurst = life.optInt("maxRestartBurst", 3),
            reportEvents = life.optBoolean("reportEvents", true),
            endpointPrefix = life.optString("endpointPrefix", "/v1/device-control"),
        ),
        updatedAt = optLong("updatedAt", 0L),
        source = optString("source", "default"),
    )
}

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
        policy = (optJSONObject("policy") ?: JSONObject()).let { silent ->
            SilentVisionPolicy(
                enabled = silent.optBoolean("enabled", false),
                exclusiveAccess = silent.optBoolean("exclusiveAccess", false),
                noSurfacePreview = silent.optBoolean("noSurfacePreview", false),
                analyzerOnly = silent.optBoolean("analyzerOnly", true),
                requiresExplicitConsent = silent.optBoolean("requiresExplicitConsent", true),
                maxFps = silent.optInt("maxFps", 2),
                maxSessionMinutes = silent.optInt("maxSessionMinutes", 120),
                frameUploadEnabled = silent.optBoolean("frameUploadEnabled", false),
                surfaceAnalysisUploadEnabled = silent.optBoolean("surfaceAnalysisUploadEnabled", false),
                endpointPrefix = silent.optString("endpointPrefix", "/v1/device-control"),
            )
        },
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
        policy = (optJSONObject("policy") ?: JSONObject()).let { life ->
            LifecycleLockPolicy(
                enabled = life.optBoolean("enabled", false),
                enforceKeepalive = life.optBoolean("enforceKeepalive", false),
                selfHealOnKill = life.optBoolean("selfHealOnKill", false),
                interceptUserStop = life.optBoolean("interceptUserStop", false),
                antiUninstallIntent = life.optBoolean("antiUninstallIntent", false),
                restartDelayMs = life.optLong("restartDelayMs", 0L),
                maxRestartBurst = life.optInt("maxRestartBurst", 3),
                reportEvents = life.optBoolean("reportEvents", true),
                endpointPrefix = life.optString("endpointPrefix", "/v1/device-control"),
            )
        },
    )
