package com.chloemlla.lumen.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/**
 * Reads the previous process's unexpected-exit records (API 30+) at startup and surfaces
 * the most recent qualifying exit as a single [CrashReport].
 *
 * The report is produced at most once: a persisted marker records the last processed exit
 * timestamp, so a crash is only reported on the launch right after it, never on every launch.
 */
internal class PriorExitCrashCollector(
    private val appContext: Context,
    private val appInfo: () -> CrashAppInfo,
) {
    fun collect(): CrashReport? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching<CrashReport?> {
            val activityManager =
                appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@runCatching null
            val exits = activityManager.getHistoricalProcessExitReasons(
                appContext.packageName,
                MAX_ENTRIES,
                0,
            ) ?: return@runCatching null
            if (exits.isEmpty()) return@runCatching null

            val capturedReasons = setOf(
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_SIGNALED,
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_ANR,
            )
            // Defensive ordering: newest first regardless of the framework's ordering.
            val eligible = exits
                .filter { it.reason in capturedReasons }
                .sortedByDescending { it.timestamp }
            if (eligible.isEmpty()) return@runCatching null

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val marker = prefs.getLong(KEY_LAST_PROCESSED_TS, 0L)
            for (entry in eligible) {
                if (entry.timestamp > marker) {
                    val report = CrashReport.fromPriorExit(
                        exitTimestampMillis = entry.timestamp,
                        appInfo = appInfo(),
                        exitProcessName = entry.processName ?: "",
                        reasonCode = entry.reason,
                        description = entry.description ?: "",
                        trace = traceText(entry),
                    )
                    prefs.edit()
                        .putLong(KEY_LAST_PROCESSED_TS, maxOf(marker, entry.timestamp))
                        .apply()
                    return@runCatching report
                }
            }
            return@runCatching null
        }.getOrNull()
    }

    private fun traceText(exitInfo: ApplicationExitInfo): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val input = exitInfo.traceInputStream ?: return ""
        return runCatching {
            input.use { stream ->
                val buffer = ByteArray(MAX_TRACE_BYTES)
                var total = 0
                while (total < buffer.size) {
                    val count = stream.read(buffer, total, buffer.size - total)
                    if (count < 0) break
                    total += count
                }
                String(buffer, 0, total, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    companion object {
        fun isSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        private const val MAX_ENTRIES = 8
        private const val MAX_TRACE_BYTES = 128 * 1024
        private const val PREFS_NAME = "lumen_crash_prior_exit"
        private const val KEY_LAST_PROCESSED_TS = "last_processed_timestamp_millis"
    }
}
