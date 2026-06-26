package com.projectlumen.project_lumen

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import kotlin.system.exitProcess

class ProjectLumenApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidCrashGuard.install(this)
    }
}

object AndroidCrashGuard {
    private const val CRASH_PREFS = "project_lumen_crash"
    private const val LAST_CRASH_KEY = "last_crash"
    private const val CRASH_FILE_NAME = "android_crash.log"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val report = buildCrashReport(thread, throwable)
            writeCrashReport(appContext, report)
            scheduleCrashReportActivity(appContext, report)

            if (previousHandler == null) {
                exitProcess(10)
            } else {
                previousHandler.uncaughtException(thread, throwable)
            }
        }
    }

    fun getLastCrashReport(context: Context): String {
        return context
            .getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
            .getString(LAST_CRASH_KEY, null)
            ?: readCrashLog(context)
            ?: "No Android crash report was recorded."
    }

    private fun buildCrashReport(thread: Thread, throwable: Throwable): String {
        val stackTrace = StringWriter().also { writer ->
            throwable.printStackTrace(PrintWriter(writer))
        }.toString()

        return buildString {
            appendLine("Project-Lumen Android crash report")
            appendLine("timestamp=${Instant.now()}")
            appendLine("thread=${thread.name}")
            appendLine("androidSdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("error=${throwable::class.java.name}: ${throwable.message}")
            appendLine()
            appendLine(stackTrace)
        }
    }

    private fun writeCrashReport(context: Context, report: String) {
        runCatching {
            context
                .getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(LAST_CRASH_KEY, report)
                .apply()

            val logDirectory = File(context.filesDir, "logs")
            if (!logDirectory.exists()) {
                logDirectory.mkdirs()
            }
            File(logDirectory, CRASH_FILE_NAME).appendText("$report\n\n")
        }
    }

    private fun readCrashLog(context: Context): String? {
        return runCatching {
            File(File(context.filesDir, "logs"), CRASH_FILE_NAME)
                .takeIf { it.exists() }
                ?.readText()
        }.getOrNull()
    }

    private fun scheduleCrashReportActivity(context: Context, report: String) {
        runCatching {
            val intent = Intent(context, CrashLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(CrashLogActivity.EXTRA_CRASH_REPORT, report)
            val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            val pendingIntent = PendingIntent.getActivity(context, 1001, intent, flags)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 250,
                pendingIntent,
            )
        }
    }
}
