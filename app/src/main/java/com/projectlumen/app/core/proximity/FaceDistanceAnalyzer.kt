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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.hypot
import kotlin.math.roundToInt

class FaceDistanceAnalyzer(private val includeTopology: Boolean = false) {
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
        val mesh = runCatching {
            meshDetector?.process(image)?.await()
                ?.maxByOrNull { it.boundingBox.safeArea() }
        }.getOrNull()
        val meshPoints = mesh?.allPoints?.map { point ->
            val position = point.position
            FaceTopologyPoint(
                index = point.index,
                xPx = position.x,
                yPx = position.y,
                zPx = position.z,
            )
        }.orEmpty()
        val meshPointIndexes = meshPoints.mapTo(mutableSetOf()) { it.index }
        val meshTriangles = mesh?.allTriangles?.mapNotNull { triangle ->
            val indexes = triangle.allPoints.map { it.index }
            if (indexes.size == 3 && indexes.all(meshPointIndexes::contains)) {
                FaceMeshTriangle(
                    firstPointIndex = indexes[0],
                    secondPointIndex = indexes[1],
                    thirdPointIndex = indexes[2],
                )
            } else {
                null
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
        return suspendCoroutine { continuation ->
            addOnSuccessListener { continuation.resume(it) }
            addOnFailureListener { continuation.resumeWithException(it) }
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
