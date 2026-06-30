package com.projectlumen.app.core.proximity

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.hypot
import kotlin.math.roundToInt

class FaceDistanceAnalyzer {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build(),
    )

    suspend fun analyze(bitmap: Bitmap, rotationDegrees: Int): FaceDistanceSample? {
        val startedAt = System.currentTimeMillis()
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        val faces = detector.process(image).await()
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val orientedWidth = if (rotationDegrees % 180 == 0) bitmap.width else bitmap.height
        val box = face.boundingBox
        return FaceDistanceSample(
            eyeDistancePx = face.eyeDistancePx(),
            faceWidthPercent = face.faceWidthPercent(orientedWidth),
            faceWidthPx = box.safeWidth(),
            frameWidthPx = bitmap.width,
            frameHeightPx = bitmap.height,
            faceLeftPx = box.left.coerceAtLeast(0),
            faceTopPx = box.top.coerceAtLeast(0),
            faceRightPx = box.right.coerceAtMost(bitmap.width),
            faceBottomPx = box.bottom.coerceAtMost(bitmap.height),
            inferenceMillis = System.currentTimeMillis() - startedAt,
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability,
        )
    }

    private fun Face.eyeDistancePx(): Float {
        val left = getLandmark(FaceLandmark.LEFT_EYE)?.position
        val right = getLandmark(FaceLandmark.RIGHT_EYE)?.position
        if (left == null || right == null) return 0f
        return hypot((left.x - right.x).toDouble(), (left.y - right.y).toDouble()).toFloat()
    }

    private fun Face.faceWidthPercent(imageWidth: Int): Int {
        if (imageWidth <= 0) return 0
        return ((boundingBox.safeWidth().toFloat() / imageWidth.toFloat()) * 100f)
            .roundToInt()
            .coerceIn(0, 100)
    }

    private fun Rect.safeWidth(): Int = width().coerceAtLeast(0)

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return suspendCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
        }
    }
}
