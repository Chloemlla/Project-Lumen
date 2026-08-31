package com.projectlumen.app.core.proximity

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

class FaceDistanceAnalyzer(private val includeTopology: Boolean = false) : Closeable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .apply {
                if (includeTopology) setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            }
            .build(),
    )
    private val meshDetector = if (includeTopology) {
        FaceMeshDetection.getClient(
            FaceMeshDetectorOptions.Builder()
                .setUseCase(FaceMeshDetectorOptions.FACE_MESH)
                .build(),
        )
    } else {
        null
    }

    override fun close() {
        runCatching { detector.close() }
        runCatching { meshDetector?.close() }
    }

    suspend fun analyze(bitmap: Bitmap, rotationDegrees: Int): FaceDistanceSample? {
        val startedAt = System.currentTimeMillis()
        val image = InputImage.fromBitmap(bitmap, rotationDegrees)
        val faces = detector.process(image).await()
        val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null
        val orientedWidth = if (rotationDegrees % 180 == 0) bitmap.width else bitmap.height
        val box = face.boundingBox
        val topology = if (includeTopology) {
            face.extractTopology(image)
        } else {
            FaceTopology.EMPTY
        }
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
            trackingId = face.trackingId,
            headEulerAngleX = face.headEulerAngleX,
            headEulerAngleY = face.headEulerAngleY,
            headEulerAngleZ = face.headEulerAngleZ,
            inferenceMillis = System.currentTimeMillis() - startedAt,
            leftEyeOpenProbability = face.leftEyeOpenProbability,
            rightEyeOpenProbability = face.rightEyeOpenProbability,
            contourPolylines = topology.contourPolylines,
            meshPoints = topology.meshPoints,
            meshTriangles = topology.meshTriangles,
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

    private suspend fun Face.extractTopology(image: InputImage): FaceTopology {
        val contours = faceContourTypes.mapNotNull { type ->
            val points = getContour(type)?.points.orEmpty()
            if (points.isEmpty()) {
                null
            } else {
                FaceTopologyPolyline(
                    type = type,
                    points = points.mapIndexed { index, point ->
                        FaceTopologyPoint(index = index, xPx = point.x, yPx = point.y)
                    },
                )
            }
        }
        val mesh = try {
            meshDetector?.process(image)?.await()
                ?.maxByOrNull { it.boundingBox.safeArea() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            null
        }
        val meshPoints = mesh?.allPoints?.map { point ->
            val position = point.position
            FaceTopologyPoint(
                index = point.index,
                xPx = position.x,
                yPx = position.y,
                zPx = position.z,
            )
        }.orEmpty()
        val meshPointPresence = BooleanArray((meshPoints.maxOfOrNull { it.index } ?: -1) + 1)
        meshPoints.forEach { point ->
            if (point.index >= 0) meshPointPresence[point.index] = true
        }
        fun isKnownMeshPoint(index: Int): Boolean =
            index >= 0 && index < meshPointPresence.size && meshPointPresence[index]
        val meshTriangles = mesh?.allTriangles?.mapNotNull { triangle ->
            val points = triangle.allPoints
            if (points.size != 3) {
                null
            } else {
                val firstIndex = points[0].index
                val secondIndex = points[1].index
                val thirdIndex = points[2].index
                if (
                    isKnownMeshPoint(firstIndex) &&
                    isKnownMeshPoint(secondIndex) &&
                    isKnownMeshPoint(thirdIndex)
                ) {
                    FaceMeshTriangle(
                        firstPointIndex = firstIndex,
                        secondPointIndex = secondIndex,
                        thirdPointIndex = thirdIndex,
                    )
                } else {
                    null
                }
            }
        }.orEmpty()
        return FaceTopology(
            contourPolylines = contours,
            meshPoints = meshPoints,
            meshTriangles = meshTriangles,
        )
    }

    private fun Rect.safeWidth(): Int = width().coerceAtLeast(0)

    private fun Rect.safeArea(): Int = safeWidth() * height().coerceAtLeast(0)

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                val failure = task.exception
                when {
                    // A canceled Task never calls success/failure listeners.
                    task.isCanceled -> continuation.cancel()
                    failure != null -> continuation.resumeWithException(failure)
                    else -> continuation.resume(task.result)
                }
            }
        }
    }

    private data class FaceTopology(
        val contourPolylines: List<FaceTopologyPolyline>,
        val meshPoints: List<FaceTopologyPoint>,
        val meshTriangles: List<FaceMeshTriangle>,
    ) {
        companion object {
            val EMPTY = FaceTopology(
                contourPolylines = emptyList(),
                meshPoints = emptyList(),
                meshTriangles = emptyList(),
            )
        }
    }

    private companion object {
        val faceContourTypes = listOf(
            FaceContour.FACE,
            FaceContour.LEFT_EYEBROW_TOP,
            FaceContour.LEFT_EYEBROW_BOTTOM,
            FaceContour.RIGHT_EYEBROW_TOP,
            FaceContour.RIGHT_EYEBROW_BOTTOM,
            FaceContour.LEFT_EYE,
            FaceContour.RIGHT_EYE,
            FaceContour.UPPER_LIP_TOP,
            FaceContour.UPPER_LIP_BOTTOM,
            FaceContour.LOWER_LIP_TOP,
            FaceContour.LOWER_LIP_BOTTOM,
            FaceContour.NOSE_BRIDGE,
            FaceContour.NOSE_BOTTOM,
            FaceContour.LEFT_CHEEK,
            FaceContour.RIGHT_CHEEK,
        )
    }
}
