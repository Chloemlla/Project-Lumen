package com.projectlumen.app.core.proximity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat
import com.projectlumen.app.core.debug.DeveloperDebugFrameStore
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class ProximityCameraSampler(private val context: Context) {
    suspend fun captureFaceDistanceSamples(
        durationMillis: Long,
        sampleIntervalMillis: Long = 900L,
        publishDebugFrame: Boolean = false,
    ): List<FaceDistanceSample> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        val deadline = System.currentTimeMillis() + durationMillis.coerceIn(750L, 15_000L)
        val samples = mutableListOf<FaceDistanceSample>()
        do {
            val captureBudgetMillis = (deadline - System.currentTimeMillis()).coerceAtMost(1_500L)
            if (captureBudgetMillis < 750L) break
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
        val timeoutMillis = maxDurationMillis.coerceIn(750L, 2_500L)
        val captureStartedAt = System.currentTimeMillis()
        val capture = withTimeoutOrNull(timeoutMillis) { capturePreviewFrame() } ?: return null
        val cameraLatencyMillis = System.currentTimeMillis() - captureStartedAt
        val bitmap = BitmapFactory.decodeByteArray(capture.bytes, 0, capture.bytes.size) ?: return null
        return try {
            val sample = FaceDistanceAnalyzer()
                .analyze(bitmap, capture.rotationDegrees)
                ?.copy(cameraLatencyMillis = cameraLatencyMillis)
            if (publishDebugFrame) {
                DeveloperDebugFrameStore.publish(bitmap, sample)
            }
            sample
        } finally {
            bitmap.recycle()
        }
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

        return try {
            suspendCancellableCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                var cameraDevice: CameraDevice? = null
                var captureSession: CameraCaptureSession? = null

                fun release() {
                    runCatching { captureSession?.close() }
                    captureSession = null
                    runCatching { cameraDevice?.close() }
                    cameraDevice = null
                    runCatching { reader.close() }
                    runCatching { thread.quitSafely() }
                }

                fun complete(result: Result<CapturedFrame?>) {
                    if (!finished.compareAndSet(false, true)) return
                    release()
                    if (!continuation.isActive) return
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) release()
                }

                reader.setOnImageAvailableListener({ availableReader ->
                    val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val result = runCatching<CapturedFrame?> {
                        CapturedFrame(image.toJpegBytes(), rotation)
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
                                cameraDevice = camera
                                runCatching {
                                    camera.createCaptureSession(
                                        listOf(reader.surface),
                                        object : CameraCaptureSession.StateCallback() {
                                            override fun onConfigured(session: CameraCaptureSession) {
                                                captureSession = session
                                                submitPreviewRequest(camera, session, handler, reader) { result ->
                                                    complete(result)
                                                }
                                            }

                                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                                session.close()
                                                camera.close()
                                                complete(Result.success(null))
                                            }
                                        },
                                        handler,
                                    )
                                }.onFailure {
                                    complete(Result.success(null))
                                }
                            }

                            override fun onDisconnected(camera: CameraDevice) {
                                camera.close()
                                complete(Result.success(null))
                            }

                            override fun onError(camera: CameraDevice, error: Int) {
                                camera.close()
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
            reader.close()
            thread.quitSafely()
        }
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
        return ByteArrayOutputStream().use { output ->
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
        for (row in 0 until height) {
            val rowOffset = row * yPlane.rowStride
            val outputOffset = row * width
            for (column in 0 until width) {
                nv21[outputOffset + column] = yBuffer.get(rowOffset + column * yPlane.pixelStride)
            }
        }

        val uPlane = planes[1]
        val vPlane = planes[2]
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        for (row in 0 until chromaHeight) {
            val outputOffset = ySize + row * width
            val uRowOffset = row * uPlane.rowStride
            val vRowOffset = row * vPlane.rowStride
            for (column in 0 until chromaWidth) {
                nv21[outputOffset + column * 2] = vBuffer.get(vRowOffset + column * vPlane.pixelStride)
                nv21[outputOffset + column * 2 + 1] = uBuffer.get(uRowOffset + column * uPlane.pixelStride)
            }
        }
        return nv21
    }

    private data class CapturedFrame(
        val bytes: ByteArray,
        val rotationDegrees: Int,
    )
}
