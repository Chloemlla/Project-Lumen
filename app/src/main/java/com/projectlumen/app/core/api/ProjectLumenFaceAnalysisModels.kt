package com.projectlumen.app.core.api

data class RemoteFaceAnalysisFrameUpload(
    val deviceInstallationId: String,
    val capturedAt: Long,
    val frame: RemoteCameraFramePayload,
    val faces: List<RemoteFaceAnalysisFace>,
    val processing: RemoteFaceAnalysisProcessingMetrics?,
)

data class RemoteCameraFramePayload(
    val format: String,
    val encoding: String,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val byteSize: Int,
    val dataBase64: String,
)

data class RemoteFaceAnalysisFace(
    val trackingId: Int?,
    val boundingBox: RemoteFaceBoundingBox,
    val headEulerAngleX: Float?,
    val headEulerAngleY: Float?,
    val headEulerAngleZ: Float?,
    val landmarks: List<RemoteFaceTopologyPoint>,
    val contours: List<RemoteFaceTopologyPoint>,
    val featurePointCount: Int,
)

data class RemoteFaceBoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class RemoteFaceTopologyPoint(
    val group: String,
    val index: Int,
    val x: Float,
    val y: Float,
    val z: Float?,
    val confidence: Float?,
)

data class RemoteFaceAnalysisProcessingMetrics(
    val frameConversionMillis: Long,
    val mlKitInferenceMillis: Long,
    val uploadQueuedAt: Long,
)

data class RemoteFaceAnalysisFrameUploadResult(
    val accepted: Boolean,
    val id: String,
    val receivedAt: Long,
)
