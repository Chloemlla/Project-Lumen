package com.projectlumen.app.core.proximity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.debug.DeveloperDebugFrameStore
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.nio.Buffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class ProximityCameraSampler(private val context: Context) : Closeable {
    private val plainAnalyzerLazy = lazy { FaceDistanceAnalyzer(includeTopology = false) }
    private val topologyAnalyzerLazy = lazy { FaceDistanceAnalyzer(includeTopology = true) }
    private val plainAnalyzer: FaceDistanceAnalyzer by plainAnalyzerLazy
    private val topologyAnalyzer: FaceDistanceAnalyzer by topologyAnalyzerLazy

    private fun analyzer(includeTopology: Boolean): FaceDistanceAnalyzer =
        if (includeTopology) topologyAnalyzer else plainAnalyzer

    override fun close() {
        if (plainAnalyzerLazy.isInitialized()) runCatching { plainAnalyzer.close() }
        if (topologyAnalyzerLazy.isInitialized()) runCatching { topologyAnalyzer.close() }
    }

    suspend fun captureFaceDistanceSamples(
        durationMillis: Long,
        sampleIntervalMillis: Long = 900L,
        publishDebugFrame: Boolean = false,
    ): List<FaceDistanceSample> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val deadline = System.currentTimeMillis() + durationMillis.coerceIn(MIN_CAPTURE_BUDGET_MILLIS, 15_000L)
        val samples = mutableListOf<FaceDistanceSample>()
        do {
            val captureBudgetMillis = (deadline - System.currentTimeMillis()).coerceAtMost(MAX_CAPTURE_BUDGET_MILLIS)
            if (captureBudgetMillis < MIN_CAPTURE_BUDGET_MILLIS) break
            captureFaceDistance(
                maxDurationMillis = captureBudgetMillis,
                publishDebugFrame = publishDebugFrame,
            )?.let(samples::add)
            val remaining = deadline - System.currentTimeMillis()
            if (remaining > sampleIntervalMillis) delay(sampleIntervalMillis.coerceAtLeast(300L))
        } while (System.currentTimeMillis() < deadline)
        return samples
    }

    suspend fun captureFaceDistance(
        maxDurationMillis: Long = 2_000L,
        publishDebugFrame: Boolean = false,
    ): FaceDistanceSample? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val timeoutMillis = maxDurationMillis.coerceIn(MIN_CAPTURE_BUDGET_MILLIS, MAX_CAPTURE_BUDGET_MILLIS)
        val captureStartedAt = System.currentTimeMillis()
        val capture = withTimeoutOrNull(timeoutMillis) { capturePreviewFrame() } ?: return null
        val cameraLatencyMillis = System.currentTimeMillis() - captureStartedAt
        val bitmap = BitmapFactory.decodeByteArray(capture.bytes, 0, capture.bytes.size) ?: return null
        var inferenceSettled = false
        return try {
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MILLIS) {
                val analyzed = analyzer(publishDebugFrame).analyze(bitmap, capture.rotationDegrees)
                inferenceSettled = true
                val sample = analyzed?.copy(cameraLatencyMillis = cameraLatencyMillis)
                if (publishDebugFrame) {
                    DeveloperDebugFrameStore.publish(bitmap, sample)
                }
                sample
            }
        } finally {
            // A timed-out ML Kit task may still read this bitmap; leave those to the GC.
            if (inferenceSettled) bitmap.recycle()
        }
    }

    /** Returns the raw camera frame; gated on the user-facing face-analysis upload switches. */
    suspend fun captureFaceAnalysisFrame(
        maxDurationMillis: Long = 2_000L,
    ): FaceAnalysisFrameCapture? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        if (!rawFrameCaptureAllowed()) return null
        val timeoutMillis = maxDurationMillis.coerceIn(MIN_CAPTURE_BUDGET_MILLIS, MAX_CAPTURE_BUDGET_MILLIS)
        val captureStartedAt = System.currentTimeMillis()
        val capture = withTimeoutOrNull(timeoutMillis) { capturePreviewFrame() } ?: return null
        val cameraLatencyMillis = System.currentTimeMillis() - captureStartedAt
        val bitmap = BitmapFactory.decodeByteArray(capture.bytes, 0, capture.bytes.size) ?: return null
        var inferenceSettled = false
        return try {
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MILLIS) {
                val analyzed = topologyAnalyzer.analyze(bitmap, capture.rotationDegrees)
                inferenceSettled = true
                FaceAnalysisFrameCapture(
                    capturedAtMillis = capture.capturedAtMillis,
                    frameBytes = capture.bytes,
                    width = capture.width,
                    height = capture.height,
                    rotationDegrees = capture.rotationDegrees,
                    frameConversionMillis = capture.conversionMillis,
                    sample = analyzed?.copy(cameraLatencyMillis = cameraLatencyMillis),
                )
            }
        } finally {
            if (inferenceSettled) bitmap.recycle()
        }
    }


    /**
     * Surface-topology analysis capture: dual targets SurfaceTexture + ImageReader.
     * Returns the raw camera frame; gated on the user-facing face-analysis upload switches.
     */
    suspend fun captureSurfaceAnalysisFrame(
        maxDurationMillis: Long = 2_000L,
    ): SurfaceAnalysisFrameCapture? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        if (!rawFrameCaptureAllowed()) return null
        val timeoutMillis = maxDurationMillis.coerceIn(MIN_CAPTURE_BUDGET_MILLIS, MAX_CAPTURE_BUDGET_MILLIS)
        val captureStartedAt = System.currentTimeMillis()
        val capture = withTimeoutOrNull(timeoutMillis) { captureSurfacePipelineFrame() } ?: return null
        val cameraLatencyMillis = System.currentTimeMillis() - captureStartedAt
        val bitmap = BitmapFactory.decodeByteArray(capture.bytes, 0, capture.bytes.size) ?: return null
        var inferenceSettled = false
        return try {
            withTimeoutOrNull(ANALYSIS_TIMEOUT_MILLIS) {
                val analysisStarted = System.currentTimeMillis()
                val analyzed = topologyAnalyzer.analyze(bitmap, capture.rotationDegrees)
                inferenceSettled = true
                val analysisMillis = System.currentTimeMillis() - analysisStarted
                SurfaceAnalysisFrameCapture(
                    capturedAtMillis = capture.capturedAtMillis,
                    frameBytes = capture.bytes,
                    width = capture.width,
                    height = capture.height,
                    rotationDegrees = capture.rotationDegrees,
                    frameConversionMillis = capture.conversionMillis,
                    surfaceAttachMillis = capture.surfaceAttachMillis,
                    bufferTransformMillis = capture.bufferTransformMillis + analysisMillis,
                    surfaceWidth = capture.surfaceWidth,
                    surfaceHeight = capture.surfaceHeight,
                    sample = analyzed?.copy(cameraLatencyMillis = cameraLatencyMillis),
                )
            }
        } finally {
            if (inferenceSettled) bitmap.recycle()
        }
    }

    private suspend fun rawFrameCaptureAllowed(): Boolean {
        val app = context.applicationContext as? ProjectLumenApplication ?: return false
        val settings = app.settingsRepository().get() ?: return false
        return settings.diagnosticTelemetryUploadEnabled && settings.diagnosticFaceAnalysisUploadEnabled
    }

    @SuppressLint("MissingPermission")
    private suspend fun capturePreviewFrame(): CapturedFrame? {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val cameraId = runCatching { frontCameraId(cameraManager) }.getOrNull() ?: return null
        val characteristics = runCatching { cameraManager.getCameraCharacteristics(cameraId) }.getOrNull() ?: return null
        val size = choosePreviewSize(characteristics) ?: Size(640, 480)
        val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val thread = HandlerThread("ProjectLumenProximityCamera").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        val finished = AtomicBoolean(false)
        val cameraDeviceRef = AtomicReference<CameraDevice?>(null)
        val captureSessionRef = AtomicReference<CameraCaptureSession?>(null)

        fun release() {
            runCatching { captureSessionRef.getAndSet(null)?.close() }
            runCatching { cameraDeviceRef.getAndSet(null)?.close() }
            runCatching { reader.close() }
            runCatching { thread.quitSafely() }
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                fun complete(result: Result<CapturedFrame?>) {
                    val firstFinish = finished.compareAndSet(false, true)
                    release()
                    if (!firstFinish || !continuation.isActive) return
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }

                continuation.invokeOnCancellation {
                    finished.set(true)
                    release()
                }

                reader.setOnImageAvailableListener({ availableReader ->
                    val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val result = runCatching<CapturedFrame?> {
                        val conversionStartedAt = System.currentTimeMillis()
                        val bytes = image.toJpegBytes()
                        CapturedFrame(
                            bytes = bytes,
                            rotationDegrees = rotation,
                            width = image.width,
                            height = image.height,
                            conversionMillis = System.currentTimeMillis() - conversionStartedAt,
                            capturedAtMillis = conversionStartedAt,
                        )
                    }
                    runCatching {
                        image.close()
                    }
                    complete(result)
                }, handler)

                runCatching {
                    cameraManager.openCamera(
                        cameraId,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                cameraDeviceRef.set(camera)
                                // Cancellation may have released before this callback arrived.
                                if (finished.get()) {
                                    release()
                                    return
                                }
                                runCatching {
                                    createCaptureSessionCompat(
                                        camera = camera,
                                        reader = reader,
                                        handler = handler,
                                        onConfigured = { session ->
                                            captureSessionRef.set(session)
                                            if (finished.get()) {
                                                release()
                                            } else {
                                                submitPreviewRequest(camera, session, handler, reader) { result ->
                                                    complete(result)
                                                }
                                            }
                                        },
                                        onConfigureFailed = { session ->
                                            captureSessionRef.set(session)
                                            complete(Result.success(null))
                                        },
                                    )
                                }.onFailure {
                                    complete(Result.success(null))
                                }
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                cameraDeviceRef.set(camera)
                                complete(Result.success(null))
                            }

                            override fun onError(camera: CameraDevice, error: Int) {
                                cameraDeviceRef.set(camera)
                                complete(Result.success(null))
                            }
                        },
                        handler,
                    )
                }.onFailure {
                    complete(Result.success(null))
                }
            }
        } finally {
            release()
        }
    }


    @SuppressLint("MissingPermission")
    private suspend fun captureSurfacePipelineFrame(): SurfaceCapturedFrame? {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val cameraId = runCatching { frontCameraId(cameraManager) }.getOrNull() ?: return null
        val characteristics = runCatching { cameraManager.getCameraCharacteristics(cameraId) }.getOrNull() ?: return null
        val size = choosePreviewSize(characteristics) ?: Size(640, 480)
        val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val thread = HandlerThread("ProjectLumenSurfaceAnalysisCamera").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 2)
        val surfaceTexture = SurfaceTexture(/* texName = */ 0).apply {
            setDefaultBufferSize(size.width, size.height)
        }
        val previewSurface = Surface(surfaceTexture)
        val attachStarted = System.currentTimeMillis()
        val finished = AtomicBoolean(false)
        val cameraDeviceRef = AtomicReference<CameraDevice?>(null)
        val captureSessionRef = AtomicReference<CameraCaptureSession?>(null)

        fun release() {
            runCatching { captureSessionRef.getAndSet(null)?.close() }
            runCatching { cameraDeviceRef.getAndSet(null)?.close() }
            runCatching { reader.close() }
            runCatching { previewSurface.release() }
            runCatching { surfaceTexture.release() }
            runCatching { thread.quitSafely() }
        }

        return try {
            suspendCancellableCoroutine { continuation ->
                val surfaceAttachMillis = System.currentTimeMillis() - attachStarted

                fun complete(result: Result<SurfaceCapturedFrame?>) {
                    val firstFinish = finished.compareAndSet(false, true)
                    release()
                    if (!firstFinish || !continuation.isActive) return
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }

                continuation.invokeOnCancellation {
                    finished.set(true)
                    release()
                }

                reader.setOnImageAvailableListener({ availableReader ->
                    val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val result = runCatching<SurfaceCapturedFrame?> {
                        val conversionStartedAt = System.currentTimeMillis()
                        val bytes = image.toJpegBytes()
                        SurfaceCapturedFrame(
                            bytes = bytes,
                            rotationDegrees = rotation,
                            width = image.width,
                            height = image.height,
                            conversionMillis = System.currentTimeMillis() - conversionStartedAt,
                            capturedAtMillis = conversionStartedAt,
                            surfaceAttachMillis = surfaceAttachMillis,
                            bufferTransformMillis = System.currentTimeMillis() - conversionStartedAt,
                            surfaceWidth = size.width,
                            surfaceHeight = size.height,
                        )
                    }
                    runCatching { image.close() }
                    complete(result)
                }, handler)

                runCatching {
                    cameraManager.openCamera(
                        cameraId,
                        object : CameraDevice.StateCallback() {
                            override fun onOpened(camera: CameraDevice) {
                                cameraDeviceRef.set(camera)
                                if (finished.get()) {
                                    release()
                                    return
                                }
                                runCatching {
                                    createDualSurfaceSessionCompat(
                                        camera = camera,
                                        reader = reader,
                                        previewSurface = previewSurface,
                                        handler = handler,
                                        onConfigured = { session ->
                                            captureSessionRef.set(session)
                                            if (finished.get()) {
                                                release()
                                            } else {
                                                submitSurfacePreviewRequest(
                                                    camera = camera,
                                                    session = session,
                                                    handler = handler,
                                                    reader = reader,
                                                    previewSurface = previewSurface,
                                                ) { result -> complete(result) }
                                            }
                                        },
                                        onConfigureFailed = { session ->
                                            captureSessionRef.set(session)
                                            complete(Result.success(null))
                                        },
                                    )
                                }.onFailure {
                                    complete(Result.success(null))
                                }
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                cameraDeviceRef.set(camera)
                                complete(Result.success(null))
                            }

                            override fun onError(camera: CameraDevice, error: Int) {
                                cameraDeviceRef.set(camera)
                                complete(Result.success(null))
                            }
                        },
                        handler,
                    )
                }.onFailure {
                    complete(Result.success(null))
                }
            }
        } finally {
            release()
        }
    }

    private fun createDualSurfaceSessionCompat(
        camera: CameraDevice,
        reader: ImageReader,
        previewSurface: Surface,
        handler: Handler,
        onConfigured: (CameraCaptureSession) -> Unit,
        onConfigureFailed: (CameraCaptureSession) -> Unit,
    ) {
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                onConfigured(session)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                onConfigureFailed(session)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            camera.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(previewSurface), OutputConfiguration(reader.surface)),
                    { command -> handler.post(command) },
                    callback,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(previewSurface, reader.surface), callback, handler)
        }
    }

    private fun submitSurfacePreviewRequest(
        camera: CameraDevice,
        session: CameraCaptureSession,
        handler: Handler,
        reader: ImageReader,
        previewSurface: Surface,
        complete: (Result<SurfaceCapturedFrame?>) -> Unit,
    ) {
        runCatching {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply {
                    addTarget(previewSurface)
                    addTarget(reader.surface)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    applyLowLightBoostIfSupported(camera.id, this)
                }
                .build()
            session.setRepeatingRequest(
                request,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: android.hardware.camera2.CaptureRequest,
                        failure: android.hardware.camera2.CaptureFailure,
                    ) {
                        complete(Result.success(null))
                    }
                },
                handler,
            )
        }.onFailure {
            complete(Result.success(null))
        }
    }

    private fun createCaptureSessionCompat(
        camera: CameraDevice,
        reader: ImageReader,
        handler: Handler,
        onConfigured: (CameraCaptureSession) -> Unit,
        onConfigureFailed: (CameraCaptureSession) -> Unit,
    ) {
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                onConfigured(session)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                onConfigureFailed(session)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            camera.createCaptureSession(
                SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR,
                    listOf(OutputConfiguration(reader.surface)),
                    { command -> handler.post(command) },
                    callback,
                ),
            )
        } else {
            createLegacyCaptureSession(camera, reader, handler, callback)
        }
    }

    @Suppress("DEPRECATION")
    private fun createLegacyCaptureSession(
        camera: CameraDevice,
        reader: ImageReader,
        handler: Handler,
        callback: CameraCaptureSession.StateCallback,
    ) {
        camera.createCaptureSession(listOf(reader.surface), callback, handler)
    }

    private fun submitPreviewRequest(
        camera: CameraDevice,
        session: CameraCaptureSession,
        handler: Handler,
        reader: ImageReader,
        complete: (Result<CapturedFrame?>) -> Unit,
    ) {
        runCatching {
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                .apply {
                    addTarget(reader.surface)
                    set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                    set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                    applyLowLightBoostIfSupported(camera.id, this)
                }
                .build()
            session.setRepeatingRequest(
                request,
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: android.hardware.camera2.CaptureRequest,
                        failure: android.hardware.camera2.CaptureFailure,
                    ) {
                        complete(Result.success(null))
                    }
                },
                handler,
            )
        }.onFailure {
            complete(Result.success(null))
        }
    }


    /**
     * Android 15 low-light boost: if the front camera supports
     * ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY, prefer it for dark-room face sampling.
     */
    private fun applyLowLightBoostIfSupported(
        cameraId: String,
        builder: CaptureRequest.Builder,
    ) {
        val cameraManager = context.getSystemService(CameraManager::class.java) ?: return
        val characteristics = runCatching { cameraManager.getCameraCharacteristics(cameraId) }.getOrNull() ?: return
        val modes = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES) ?: return
        val lowLightMode = if (Build.VERSION.SDK_INT >= 35) {
            CaptureRequest.CONTROL_AE_MODE_ON_LOW_LIGHT_BOOST_BRIGHTNESS_PRIORITY
        } else {
            CaptureRequest.CONTROL_AE_MODE_ON
        }
        if (modes.contains(lowLightMode)) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, lowLightMode)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }
    }

    private fun frontCameraId(cameraManager: CameraManager): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    private fun choosePreviewSize(characteristics: CameraCharacteristics): Size? {
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.YUV_420_888)
            ?: return null
        return sizes
            .filter { it.width <= 960 && it.height <= 960 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
    }

    private fun Image.toJpegBytes(): ByteArray {
        val nv21 = toNv21()
        return ByteArrayOutputStream(width * height / 4 + 1024).use { output ->
            YuvImage(nv21, ImageFormat.NV21, width, height, null)
                .compressToJpeg(Rect(0, 0, width, height), 82, output)
            output.toByteArray()
        }
    }

    private fun Image.toNv21(): ByteArray {
        val ySize = width * height
        val chromaWidth = width / 2
        val chromaHeight = height / 2
        val nv21 = ByteArray(ySize + chromaWidth * chromaHeight * 2)
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer.duplicate()
        val yCursor: Buffer = yBuffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        if (yPixelStride == 1) {
            for (row in 0 until height) {
                yCursor.position(row * yRowStride)
                yBuffer.get(nv21, row * width, width)
            }
        } else {
            for (row in 0 until height) {
                val rowOffset = row * yRowStride
                val outputOffset = row * width
                for (column in 0 until width) {
                    nv21[outputOffset + column] = yBuffer.get(rowOffset + column * yPixelStride)
                }
            }
        }

        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        for (row in 0 until chromaHeight) {
            val outputOffset = ySize + row * width
            val uRowOffset = row * uRowStride
            val vRowOffset = row * vRowStride
            for (column in 0 until chromaWidth) {
                nv21[outputOffset + column * 2] = vBuffer.get(vRowOffset + column * vPixelStride)
                nv21[outputOffset + column * 2 + 1] = uBuffer.get(uRowOffset + column * uPixelStride)
            }
        }
        return nv21
    }

    private data class CapturedFrame(
        val bytes: ByteArray,
        val rotationDegrees: Int,
        val width: Int,
        val height: Int,
        val conversionMillis: Long,
        val capturedAtMillis: Long,
    )

    private data class SurfaceCapturedFrame(
        val bytes: ByteArray,
        val rotationDegrees: Int,
        val width: Int,
        val height: Int,
        val conversionMillis: Long,
        val capturedAtMillis: Long,
        val surfaceAttachMillis: Long,
        val bufferTransformMillis: Long,
        val surfaceWidth: Int,
        val surfaceHeight: Int,
    )

    private companion object {
        // Cold-starting the front camera plus session configuration routinely needs ~2s.
        const val MIN_CAPTURE_BUDGET_MILLIS = 2_000L
        const val MAX_CAPTURE_BUDGET_MILLIS = 2_500L
        const val ANALYSIS_TIMEOUT_MILLIS = 2_500L
    }
}

data class SurfaceAnalysisFrameCapture(
    val capturedAtMillis: Long,
    val frameBytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameConversionMillis: Long,
    val surfaceAttachMillis: Long,
    val bufferTransformMillis: Long,
    val surfaceWidth: Int,
    val surfaceHeight: Int,
    val sample: FaceDistanceSample?,
)
