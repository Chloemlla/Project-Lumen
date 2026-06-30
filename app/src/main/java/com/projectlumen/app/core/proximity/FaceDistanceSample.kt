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
    val inferenceMillis: Long = 0L,
    val cameraLatencyMillis: Long = 0L,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val capturedAtMillis: Long = System.currentTimeMillis(),
)
