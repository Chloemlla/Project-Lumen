package com.projectlumen.app.core.proximity

data class FaceDistanceSample(
    val eyeDistancePx: Float,
    val faceWidthPercent: Int,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val capturedAtMillis: Long = System.currentTimeMillis(),
)
