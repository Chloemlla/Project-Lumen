package com.projectlumen.app.core.proximity

data class FaceDistanceSample(
    val eyeDistancePx: Float,
    val faceWidthPercent: Int,
    val faceWidthPx: Int = 0,
    val frameWidthPx: Int = 0,
    val frameHeightPx: Int = 0,
    val faceLeftPx: Int = 0,
    val faceTopPx: Int = 0,
    val faceRightPx: Int = 0,
    val faceBottomPx: Int = 0,
    val trackingId: Int? = null,
    val headEulerAngleX: Float? = null,
    val headEulerAngleY: Float? = null,
    val headEulerAngleZ: Float? = null,
    val inferenceMillis: Long = 0L,
    val cameraLatencyMillis: Long = 0L,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val contourPolylines: List<FaceTopologyPolyline> = emptyList(),
    val meshPoints: List<FaceTopologyPoint> = emptyList(),
    val meshTriangles: List<FaceMeshTriangle> = emptyList(),
    val capturedAtMillis: Long = System.currentTimeMillis(),
)

data class FaceTopologyPoint(
    val index: Int,
    val xPx: Float,
    val yPx: Float,
    val zPx: Float? = null,
)

data class FaceTopologyPolyline(
    val type: Int,
    val points: List<FaceTopologyPoint>,
)

data class FaceMeshTriangle(
    val firstPointIndex: Int,
    val secondPointIndex: Int,
    val thirdPointIndex: Int,
)

data class FaceAnalysisFrameCapture(
    val capturedAtMillis: Long,
    val frameBytes: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val frameConversionMillis: Long,
    val sample: FaceDistanceSample?,
)
