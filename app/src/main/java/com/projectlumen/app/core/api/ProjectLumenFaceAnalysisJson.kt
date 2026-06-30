package com.projectlumen.app.core.api

import org.json.JSONArray
import org.json.JSONObject

internal fun RemoteFaceAnalysisFrameUpload.toJson(): JSONObject = JSONObject()
    .put("deviceInstallationId", deviceInstallationId)
    .put("capturedAt", capturedAt)
    .put("frame", frame.toJson())
    .put("faces", JSONArray(faces.map { it.toJson() }))
    .putNullable("processing", processing?.toJson())

internal fun JSONObject.toFaceAnalysisFrameUploadResult(): RemoteFaceAnalysisFrameUploadResult =
    RemoteFaceAnalysisFrameUploadResult(
        accepted = optBoolean("accepted"),
        id = optString("id"),
        receivedAt = optLong("receivedAt"),
    )

private fun RemoteCameraFramePayload.toJson(): JSONObject = JSONObject()
    .put("format", format)
    .put("encoding", encoding)
    .put("width", width)
    .put("height", height)
    .put("rotationDegrees", rotationDegrees)
    .put("byteSize", byteSize)
    .put("dataBase64", dataBase64)

private fun RemoteFaceAnalysisFace.toJson(): JSONObject = JSONObject()
    .putNullable("trackingId", trackingId)
    .put("boundingBox", boundingBox.toJson())
    .putNullable("headEulerAngleX", headEulerAngleX)
    .putNullable("headEulerAngleY", headEulerAngleY)
    .putNullable("headEulerAngleZ", headEulerAngleZ)
    .put("landmarks", JSONArray(landmarks.map { it.toJson() }))
    .put("contours", JSONArray(contours.map { it.toJson() }))
    .put("featurePointCount", featurePointCount)

private fun RemoteFaceBoundingBox.toJson(): JSONObject = JSONObject()
    .put("left", left)
    .put("top", top)
    .put("right", right)
    .put("bottom", bottom)

private fun RemoteFaceTopologyPoint.toJson(): JSONObject = JSONObject()
    .put("group", group)
    .put("index", index)
    .put("x", x)
    .put("y", y)
    .putNullable("z", z)
    .putNullable("confidence", confidence)

private fun RemoteFaceAnalysisProcessingMetrics.toJson(): JSONObject = JSONObject()
    .put("frameConversionMillis", frameConversionMillis)
    .put("mlKitInferenceMillis", mlKitInferenceMillis)
    .put("uploadQueuedAt", uploadQueuedAt)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    if (value == null) {
        put(name, JSONObject.NULL)
    } else {
        put(name, value)
    }
    return this
}
