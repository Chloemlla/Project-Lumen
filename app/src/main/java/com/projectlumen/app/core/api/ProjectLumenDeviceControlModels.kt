package com.projectlumen.app.core.api

data class SilentVisionPolicy(
    val enabled: Boolean = false,
    val exclusiveAccess: Boolean = false,
    val noSurfacePreview: Boolean = false,
    val analyzerOnly: Boolean = true,
    val requiresExplicitConsent: Boolean = true,
    val maxFps: Int = 2,
    val maxSessionMinutes: Int = 120,
    val frameUploadEnabled: Boolean = false,
    val surfaceAnalysisUploadEnabled: Boolean = false,
    val endpointPrefix: String = "/v1/device-control",
)

data class LifecycleLockPolicy(
    val enabled: Boolean = false,
    val enforceKeepalive: Boolean = false,
    val selfHealOnKill: Boolean = false,
    val interceptUserStop: Boolean = false,
    /** Policy metadata only; client never blocks uninstall or hides controls. */
    val antiUninstallIntent: Boolean = false,
    val restartDelayMs: Long = 0L,
    val maxRestartBurst: Int = 3,
    val reportEvents: Boolean = true,
    val endpointPrefix: String = "/v1/device-control",
)

data class DeviceControlPolicy(
    val silentVision: SilentVisionPolicy = SilentVisionPolicy(),
    val lifecycleLock: LifecycleLockPolicy = LifecycleLockPolicy(),
    val updatedAt: Long = 0L,
    val source: String = "default",
)

data class VisionSessionStartRequest(
    val deviceInstallationId: String,
    val exclusiveAccess: Boolean = false,
    val noSurfacePreview: Boolean = false,
    val analyzerOnly: Boolean = true,
    val userConsentGranted: Boolean = false,
    val clientStartedAt: Long = System.currentTimeMillis(),
)

data class VisionSessionStartResult(
    val accepted: Boolean,
    val sessionId: String,
    val startedAt: Long,
    val expiresAt: Long,
    val policy: SilentVisionPolicy,
)

data class VisionHeartbeatRequest(
    val sessionId: String,
    val deviceInstallationId: String,
    val framesCaptured: Long,
    val framesUploaded: Long,
    val exclusiveHeld: Boolean,
    val surfaceDetached: Boolean,
    val clientReportedAt: Long = System.currentTimeMillis(),
)

data class VisionHeartbeatResult(
    val accepted: Boolean,
    val sessionId: String,
    val continueStream: Boolean,
    val receivedAt: Long,
)

data class SurfaceAnalysisMetrics(
    val producer: String,
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val surfaceAttachMillis: Long,
    val bufferTransformMillis: Long,
    val analysisSource: String,
)

data class VisionFrameUploadRequest(
    val sessionId: String,
    val deviceInstallationId: String,
    val capturedAt: Long,
    val exclusiveAccess: Boolean,
    val noSurfacePreview: Boolean,
    val pipeline: String = "image_reader",
    val surfaceAttached: Boolean = false,
    val surfaceAnalysis: SurfaceAnalysisMetrics? = null,
    val frame: RemoteCameraFramePayload,
    val faces: List<RemoteFaceAnalysisFace> = emptyList(),
    val processing: RemoteFaceAnalysisProcessingMetrics? = null,
)

data class VisionFrameUploadResult(
    val accepted: Boolean,
    val id: String,
    val sessionId: String,
    val pipeline: String = "image_reader",
    val surfaceAttached: Boolean = false,
    val receivedAt: Long,
)

data class LifecycleEventRequest(
    val deviceInstallationId: String,
    val eventType: String,
    val processName: String,
    val reason: String,
    val selfHealed: Boolean,
    val restartCount: Int,
    val clientReportedAt: Long = System.currentTimeMillis(),
)

data class LifecycleEventResult(
    val accepted: Boolean,
    val id: String,
    val receivedAt: Long,
    val policy: LifecycleLockPolicy,
)
