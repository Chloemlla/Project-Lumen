package com.projectlumen.app.core.services

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max

class ExportService(private val context: Context) {
    fun shareCsv(eyeStats: List<DailyEyeStatsEntity>, pomodoroStats: List<DailyPomodoroStatsEntity>) {
        val file = File(context.cacheDir, "project_lumen_stats.csv")
        file.writeText(buildCsv(eyeStats, pomodoroStats), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun shareMonthlyPdf(eyeStats: List<DailyEyeStatsEntity>, pomodoroStats: List<DailyPomodoroStatsEntity>) {
        val file = File(context.cacheDir, "project_lumen_monthly_report.pdf")
        FileOutputStream(file).use { output ->
            val document = buildMonthlyPdf(eyeStats, pomodoroStats)
            try {
                document.writeTo(output)
            } finally {
                document.close()
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.monthly_report_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun shareStatsImage(eyeStats: List<DailyEyeStatsEntity>, pomodoroStats: List<DailyPomodoroStatsEntity>) {
        val file = File(context.cacheDir, "project_lumen_stats.png")
        FileOutputStream(file).use { output ->
            buildStatsBitmap(eyeStats, pomodoroStats).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.export_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun buildCsv(
        eyeStats: List<DailyEyeStatsEntity>,
        pomodoroStats: List<DailyPomodoroStatsEntity>,
    ): String {
        val pomodoroByDate = pomodoroStats.associateBy { it.statDate }
        val dates = (eyeStats.map { it.statDate } + pomodoroStats.map { it.statDate }).distinct().sortedDescending()
        return buildString {
            appendLine("date,working_seconds,rest_seconds,skip_count,completed_break_count,max_continuous_work_seconds,proximity_warning_count,proximity_close_seconds,eye_dry_warning_count,low_light_warning_count,completed_tomato_count,focus_sessions,total_focus_seconds,total_pomodoro_break_seconds")
            dates.forEach { date ->
                val eye = eyeStats.firstOrNull { it.statDate == date }
                val pomodoro = pomodoroByDate[date]
                appendLine(
                    listOf(
                        date,
                        eye?.workingSeconds ?: 0,
                        eye?.restSeconds ?: 0,
                        eye?.skipCount ?: 0,
                        eye?.completedBreakCount ?: 0,
                        eye?.maxContinuousWorkSeconds ?: 0,
                        eye?.proximityWarningCount ?: 0,
                        eye?.proximityCloseSeconds ?: 0,
                        eye?.eyeDryWarningCount ?: 0,
                        eye?.lowLightWarningCount ?: 0,
                        pomodoro?.completedTomatoCount ?: 0,
                        pomodoro?.completedFocusSessions ?: 0,
                        pomodoro?.totalFocusSeconds ?: 0,
                        pomodoro?.totalBreakSeconds ?: 0,
                    ).joinToString(","),
                )
            }
        }
    }

    private fun buildStatsBitmap(
        eyeStats: List<DailyEyeStatsEntity>,
        pomodoroStats: List<DailyPomodoroStatsEntity>,
    ): Bitmap {
        val width = 1200
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 32, 40)
            textSize = 46f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(73, 83, 96)
            textSize = 28f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 65, 81)
            textSize = 24f
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(229, 234, 240) }
        val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36, 107, 115) }
        val restPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 80, 62) }
        val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(87, 94, 168) }
        canvas.drawColor(Color.rgb(248, 250, 252))
        canvas.drawText(context.getString(R.string.export_subject), 56f, 76f, titlePaint)
        val totalWorkMinutes = eyeStats.sumOf { it.workingSeconds } / 60L
        val totalRestMinutes = eyeStats.sumOf { it.restSeconds } / 60L
        val totalTomatoes = pomodoroStats.sumOf { it.completedTomatoCount }
        canvas.drawText(
            "${context.getString(R.string.working_time)} ${totalWorkMinutes}m  ${context.getString(R.string.rest_time)} ${totalRestMinutes}m  ${context.getString(R.string.completed_tomatoes)} $totalTomatoes",
            56f,
            124f,
            bodyPaint,
        )
        val recent = eyeStats.take(7).reversed()
        val maxSeconds = max(1L, recent.maxOfOrNull { max(it.workingSeconds, it.restSeconds) } ?: 1L)
        var y = 190f
        recent.forEach { stat ->
            canvas.drawText(stat.statDate.takeLast(5), 56f, y + 25f, labelPaint)
            drawBar(canvas, 190f, y, 900f, stat.workingSeconds.toFloat() / maxSeconds, trackPaint, workPaint)
            drawBar(canvas, 190f, y + 34f, 900f, stat.restSeconds.toFloat() / maxSeconds, trackPaint, restPaint)
            y += 92f
        }
        val pomodoroRecent = pomodoroStats.take(7).reversed()
        if (pomodoroRecent.isNotEmpty()) {
            y += 24f
            canvas.drawText(context.getString(R.string.focus_sessions), 56f, y, bodyPaint)
            y += 36f
            val maxFocus = max(1, pomodoroRecent.maxOf { it.completedFocusSessions })
            pomodoroRecent.forEach { stat ->
                canvas.drawText(stat.statDate.takeLast(5), 56f, y + 25f, labelPaint)
                drawBar(canvas, 190f, y, 900f, stat.completedFocusSessions.toFloat() / maxFocus.toFloat(), trackPaint, focusPaint)
                y += 48f
            }
        }
        return bitmap
    }

    private fun buildMonthlyPdf(
        eyeStats: List<DailyEyeStatsEntity>,
        pomodoroStats: List<DailyPomodoroStatsEntity>,
    ): PdfDocument {
        val monthKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val monthlyEyeStats = eyeStats.filter { it.statDate.startsWith(monthKey) }.sortedBy { it.statDate }
        val monthlyPomodoroStats = pomodoroStats.filter { it.statDate.startsWith(monthKey) }.sortedBy { it.statDate }
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 32, 40)
            textSize = 26f
            isFakeBoldText = true
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(36, 107, 115)
            textSize = 15f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 65, 81)
            textSize = 12f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(91, 104, 124)
            textSize = 10f
        }
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(229, 234, 240) }
        val workPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(36, 107, 115) }
        val restPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(185, 80, 62) }

        canvas.drawColor(Color.WHITE)
        canvas.drawText(context.getString(R.string.monthly_report_title), 40f, 56f, titlePaint)
        canvas.drawText(monthKey, 40f, 78f, smallPaint)

        val totalWorkMinutes = monthlyEyeStats.sumOf { it.workingSeconds } / 60L
        val totalRestMinutes = monthlyEyeStats.sumOf { it.restSeconds } / 60L
        val completedBreaks = monthlyEyeStats.sumOf { it.completedBreakCount }
        val skips = monthlyEyeStats.sumOf { it.skipCount }
        val totalBreakDecisions = (completedBreaks + skips).coerceAtLeast(1)
        val restCompletionRate = (completedBreaks * 100) / totalBreakDecisions
        val tomatoes = monthlyPomodoroStats.sumOf { it.completedTomatoCount }
        val focusSessions = monthlyPomodoroStats.sumOf { it.completedFocusSessions }

        var y = 118f
        canvas.drawText(context.getString(R.string.advanced_statistics), 40f, y, sectionPaint)
        y += 26f
        drawPdfMetric(canvas, bodyPaint, 40f, y, context.getString(R.string.working_time), "${totalWorkMinutes}m")
        drawPdfMetric(canvas, bodyPaint, 300f, y, context.getString(R.string.rest_time), "${totalRestMinutes}m")
        y += 24f
        drawPdfMetric(canvas, bodyPaint, 40f, y, context.getString(R.string.rest_completion_rate), "$restCompletionRate%")
        drawPdfMetric(canvas, bodyPaint, 300f, y, context.getString(R.string.skip_rate), "${(skips * 100) / totalBreakDecisions}%")
        y += 24f
        drawPdfMetric(canvas, bodyPaint, 40f, y, context.getString(R.string.completed_tomatoes), tomatoes.toString())
        drawPdfMetric(canvas, bodyPaint, 300f, y, context.getString(R.string.focus_sessions), focusSessions.toString())

        y += 44f
        canvas.drawText(context.getString(R.string.monthly_report_daily_trend), 40f, y, sectionPaint)
        y += 22f
        val maxSeconds = max(1L, monthlyEyeStats.maxOfOrNull { max(it.workingSeconds, it.restSeconds) } ?: 1L)
        monthlyEyeStats.takeLast(22).forEach { stat ->
            canvas.drawText(stat.statDate.takeLast(5), 40f, y + 9f, smallPaint)
            drawBar(canvas, 98f, y, 400f, stat.workingSeconds.toFloat() / maxSeconds, trackPaint, workPaint)
            drawBar(canvas, 98f, y + 16f, 400f, stat.restSeconds.toFloat() / maxSeconds, trackPaint, restPaint)
            y += 38f
        }
        if (monthlyEyeStats.isEmpty()) {
            canvas.drawText(context.getString(R.string.statistics_no_export_data), 40f, y, bodyPaint)
        }

        document.finishPage(page)
        return document
    }

    private fun drawPdfMetric(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        label: String,
        value: String,
    ) {
        canvas.drawText("$label: $value", x, y, paint)
    }

    private fun drawBar(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        progress: Float,
        trackPaint: Paint,
        fillPaint: Paint,
    ) {
        val radius = 12f
        canvas.drawRoundRect(RectF(x, y, x + width, y + 20f), radius, radius, trackPaint)
        canvas.drawRoundRect(RectF(x, y, x + width * progress.coerceIn(0.04f, 1f), y + 20f), radius, radius, fillPaint)
    }
}
