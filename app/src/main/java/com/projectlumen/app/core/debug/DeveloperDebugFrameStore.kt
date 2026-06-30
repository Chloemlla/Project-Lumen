package com.projectlumen.app.core.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.scale
import com.projectlumen.app.core.proximity.FaceDistanceSample
import com.projectlumen.app.core.proximity.FaceTopologyPoint
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
        val output = bitmap.scale(width, height)
            .copy(Bitmap.Config.ARGB_8888, true)
        if (sample != null) drawTopology(output, sample, scale)
        return output
    }

    private fun drawTopology(bitmap: Bitmap, sample: FaceDistanceSample, scale: Float) {
        val canvas = Canvas(bitmap)
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(210, 255, 82, 82)
            strokeWidth = 3f
        }
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(230, 105, 240, 174)
            strokeWidth = 2.5f
        }
        val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(120, 64, 196, 255)
            strokeWidth = 1f
        }
        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(220, 255, 214, 102)
        }

        if (sample.faceWidthPx > 0) {
            canvas.drawRect(
                Rect(
                    (sample.faceLeftPx * scale).roundToInt(),
                    (sample.faceTopPx * scale).roundToInt(),
                    (sample.faceRightPx * scale).roundToInt(),
                    (sample.faceBottomPx * scale).roundToInt(),
                ),
                framePaint,
            )
        }
        val pointsByIndex = sample.meshPoints.associateBy { it.index }
        sample.meshTriangles.forEach { triangle ->
            val first = pointsByIndex[triangle.firstPointIndex]
            val second = pointsByIndex[triangle.secondPointIndex]
            val third = pointsByIndex[triangle.thirdPointIndex]
            if (first != null && second != null && third != null) {
                canvas.drawScaledLine(first, second, scale, meshPaint)
                canvas.drawScaledLine(second, third, scale, meshPaint)
                canvas.drawScaledLine(third, first, scale, meshPaint)
            }
        }
        sample.contourPolylines.forEach { polyline ->
            polyline.points.zipWithNext { first, second ->
                canvas.drawScaledLine(first, second, scale, contourPaint)
            }
        }
        sample.meshPoints.forEach { point ->
            canvas.drawCircle(point.xPx * scale, point.yPx * scale, 1.5f, pointPaint)
        }
    }

    private fun Canvas.drawScaledLine(
        first: FaceTopologyPoint,
        second: FaceTopologyPoint,
        scale: Float,
        paint: Paint,
    ) {
        drawLine(
            first.xPx * scale,
            first.yPx * scale,
            second.xPx * scale,
            second.yPx * scale,
            paint,
        )
    }
}
