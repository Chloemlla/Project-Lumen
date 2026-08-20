package com.projectlumen.app.core.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
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
    private val thumbnailPaint by lazy { Paint(Paint.FILTER_BITMAP_FLAG) }
    private val framePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(210, 255, 82, 82)
            strokeWidth = 3f
        }
    }
    private val contourPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(230, 105, 240, 174)
            strokeWidth = 2.5f
        }
    }
    private val meshPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb(120, 64, 196, 255)
            strokeWidth = 1f
        }
    }
    private val pointPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(220, 255, 214, 102)
        }
    }

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
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), thumbnailPaint)
        if (sample != null) drawTopology(canvas, sample, scale)
        return output
    }

    private fun drawTopology(canvas: Canvas, sample: FaceDistanceSample, scale: Float) {
        if (sample.faceWidthPx > 0) {
            canvas.drawRect(
                (sample.faceLeftPx * scale).roundToInt().toFloat(),
                (sample.faceTopPx * scale).roundToInt().toFloat(),
                (sample.faceRightPx * scale).roundToInt().toFloat(),
                (sample.faceBottomPx * scale).roundToInt().toFloat(),
                framePaint,
            )
        }
        val meshPoints = sample.meshPoints
        val pointsByIndex = arrayOfNulls<FaceTopologyPoint>((meshPoints.maxOfOrNull { it.index } ?: -1) + 1)
        meshPoints.forEach { point ->
            if (point.index >= 0) pointsByIndex[point.index] = point
        }
        sample.meshTriangles.forEach { triangle ->
            val first = pointsByIndex.getOrNull(triangle.firstPointIndex)
            val second = pointsByIndex.getOrNull(triangle.secondPointIndex)
            val third = pointsByIndex.getOrNull(triangle.thirdPointIndex)
            if (first != null && second != null && third != null) {
                canvas.drawScaledLine(first, second, scale, meshPaint)
                canvas.drawScaledLine(second, third, scale, meshPaint)
                canvas.drawScaledLine(third, first, scale, meshPaint)
            }
        }
        sample.contourPolylines.forEach { polyline ->
            val points = polyline.points
            for (index in 1 until points.size) {
                canvas.drawScaledLine(points[index - 1], points[index], scale, contourPaint)
            }
        }
        meshPoints.forEach { point ->
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
