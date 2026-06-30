package com.projectlumen.app.core.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.projectlumen.app.core.proximity.FaceDistanceSample
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

data class DeveloperDebugFrame(
    val sample: FaceDistanceSample?,
    val thumbnail: Bitmap?,
    val capturedAtMillis: Long,
    val fps: Float,
)

object DeveloperDebugFrameStore {
    private val latestFrame = AtomicReference<DeveloperDebugFrame?>(null)

    fun latest(): DeveloperDebugFrame? = latestFrame.get()

    fun clear() {
        latestFrame.set(null)
    }

    fun publish(bitmap: Bitmap?, sample: FaceDistanceSample?) {
        val previous = latestFrame.get()
        val now = System.currentTimeMillis()
        val fps = previous?.capturedAtMillis
            ?.takeIf { it > 0L && now > it }
            ?.let { 1000f / (now - it).toFloat() }
            ?: 0f
        val thumbnail = bitmap?.let { createDebugThumbnail(it, sample) }
        val next = DeveloperDebugFrame(
            sample = sample,
            thumbnail = thumbnail,
            capturedAtMillis = now,
            fps = fps,
        )
        latestFrame.set(next)
    }

    private fun createDebugThumbnail(bitmap: Bitmap, sample: FaceDistanceSample?): Bitmap {
        val maxWidth = 240
        val scale = (maxWidth.toFloat() / bitmap.width.toFloat()).coerceAtMost(1f)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val output = Bitmap.createScaledBitmap(bitmap, width, height, true)
            .copy(Bitmap.Config.ARGB_8888, true)
        if (sample != null && sample.faceWidthPx > 0) {
            val rect = Rect(
                (sample.faceLeftPx * scale).roundToInt(),
                (sample.faceTopPx * scale).roundToInt(),
                (sample.faceRightPx * scale).roundToInt(),
                (sample.faceBottomPx * scale).roundToInt(),
            )
            Canvas(output).drawRect(
                rect,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.RED
                    strokeWidth = 4f
                },
            )
        }
        return output
    }
}
