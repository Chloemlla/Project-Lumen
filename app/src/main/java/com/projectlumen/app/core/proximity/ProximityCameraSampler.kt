package com.projectlumen.app.core.proximity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ProximityCameraSampler(private val context: Context) {
    suspend fun captureFaceDistance(): FaceDistanceSample? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val capture = captureJpeg() ?: return null
        val bitmap = BitmapFactory.decodeByteArray(capture.bytes, 0, capture.bytes.size) ?: return null
        return FaceDistanceAnalyzer().analyze(bitmap, capture.rotationDegrees)
    }

    @SuppressLint("MissingPermission")
    private suspend fun captureJpeg(): CapturedJpeg? {
        val cameraManager = context.getSystemService(CameraManager::class.java)
        val cameraId = frontCameraId(cameraManager) ?: return null
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val size = chooseJpegSize(characteristics) ?: Size(640, 480)
        val rotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        val thread = HandlerThread("ProjectLumenProximityCamera").apply { start() }
        val handler = Handler(thread.looper)
        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)

        return try {
            suspendCoroutine { continuation ->
                val finished = AtomicBoolean(false)
                fun complete(result: Result<CapturedJpeg?>) {
                    if (!finished.compareAndSet(false, true)) return
                    reader.close()
                    thread.quitSafely()
                    result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }

                reader.setOnImageAvailableListener({ availableReader ->
                    val image = availableReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = image.planes.first().buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        complete(Result.success(CapturedJpeg(bytes, rotation)))
                    } finally {
                        image.close()
                    }
                }, handler)

                cameraManager.openCamera(
                    cameraId,
                    object : CameraDevice.StateCallback() {
                        override fun onOpened(camera: CameraDevice) {
                            camera.createCaptureSession(
                                listOf(reader.surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(session: CameraCaptureSession) {
                                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                                            .apply { addTarget(reader.surface) }
                                            .build()
                                        session.capture(
                                            request,
                                            object : CameraCaptureSession.CaptureCallback() {
                                                override fun onCaptureCompleted(
                                                    session: CameraCaptureSession,
                                                    request: android.hardware.camera2.CaptureRequest,
                                                    result: android.hardware.camera2.TotalCaptureResult,
                                                ) {
                                                    session.close()
                                                    camera.close()
                                                }
                                            },
                                            handler,
                                        )
                                    }

                                    override fun onConfigureFailed(session: CameraCaptureSession) {
                                        session.close()
                                        camera.close()
                                        complete(Result.success(null))
                                    }
                                },
                                handler,
                            )
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
            }
        } finally {
            reader.close()
            thread.quitSafely()
        }
    }

    private fun frontCameraId(cameraManager: CameraManager): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    private fun chooseJpegSize(characteristics: CameraCharacteristics): Size? {
        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(ImageFormat.JPEG)
            ?: return null
        return sizes
            .filter { it.width <= 1280 && it.height <= 1280 }
            .maxByOrNull { it.width * it.height }
            ?: sizes.minByOrNull { it.width * it.height }
    }

    private data class CapturedJpeg(
        val bytes: ByteArray,
        val rotationDegrees: Int,
    )
}
