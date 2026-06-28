package com.projectlumen.app.core.services

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import java.io.File

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

    private fun buildCsv(
        eyeStats: List<DailyEyeStatsEntity>,
        pomodoroStats: List<DailyPomodoroStatsEntity>,
    ): String {
        val pomodoroByDate = pomodoroStats.associateBy { it.statDate }
        val dates = (eyeStats.map { it.statDate } + pomodoroStats.map { it.statDate }).distinct().sortedDescending()
        return buildString {
            appendLine("date,working_seconds,rest_seconds,skip_count,completed_break_count,completed_tomato_count,focus_sessions,total_focus_seconds,total_pomodoro_break_seconds")
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
                        pomodoro?.completedTomatoCount ?: 0,
                        pomodoro?.completedFocusSessions ?: 0,
                        pomodoro?.totalFocusSeconds ?: 0,
                        pomodoro?.totalBreakSeconds ?: 0,
                    ).joinToString(","),
                )
            }
        }
    }
}
