package com.chloemlla.lumen.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Reads the previous processes' unexpected-exit records (API 30+) at startup and returns every
 * qualifying death that has not been reported yet, newest first.
 *
 * Reporting **more than one** record matters for WebView-shell hosts (Tauri, Capacitor, plain
 * `WebView`): the app and the WebView renderer are separate processes, and a session usually dies
 * from a renderer crash followed by a main-process death (or the other way around). Returning a
 * single newest record hid the second death completely — and when the newest record *was* the
 * renderer (`:sandboxed_process0`), the SDK used to persist it into the pending-report slot and
 * block the next launch with a crash screen for a crash the app never had.
 *
 * Each record is reported at most once: a persisted marker holds the newest processed timestamp,
 * so a death is only reported on the launch right after it.
 *
 * [MAX_ENTRIES] asks for more records than the platform ring buffer holds. CRooot's isolated
 * probe processes (`TeeGrantDomainGranteeService`, `SelinuxContextValidityCarrierService`,
 * `VirtualizationIsolatedProbeService`) are reaped as `REASON_OTHER` / `ISOLATED NOT NEEDED`
 * several times per scan, and those records are newer than any real crash, so a narrow window
 * hides the crash this collector exists to find. The reason filter below still excludes them.
 */
internal class PriorExitCrashCollector(
    private val appContext: Context,
    private val appInfo: () -> CrashAppInfo,
) {
    fun collect(): List<CrashReport> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return runCatching {
            val activityManager =
                appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return@runCatching emptyList()
            // The signature is getHistoricalProcessExitReasons(packageName, pid, maxNum): pid must
            // be 0 to mean "every process that belonged to this package", and maxNum carries the
            // window size. Passing the window as pid silently filtered to a single process id and
            // made this collector return null on every launch.
            val exits = activityManager.getHistoricalProcessExitReasons(
                appContext.packageName,
                /* pid = */ 0,
                /* maxNum = */ MAX_ENTRIES,
            ) ?: return@runCatching emptyList()
            if (exits.isEmpty()) return@runCatching emptyList()

            val capturedReasons = setOf(
                ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_SIGNALED,
                ApplicationExitInfo.REASON_CRASH,
                ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_ANOMALY,
            )
            // Defensive ordering: newest first regardless of the framework's ordering.
            val eligible = exits
                .filter { it.reason in capturedReasons }
                .sortedByDescending { it.timestamp }
            if (eligible.isEmpty()) return@runCatching emptyList()

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val marker = prefs.getLong(KEY_LAST_PROCESSED_TS, 0L)
            val fresh = eligible.filter { it.timestamp > marker }
            if (fresh.isEmpty()) return@runCatching emptyList()

            // Advance the marker before building reports: a report that fails to build must not be
            // retried forever, and the next launch would otherwise re-report the same deaths.
            prefs.edit()
                .putLong(KEY_LAST_PROCESSED_TS, maxOf(marker, fresh.first().timestamp))
                .apply()

            val info = appInfo()
            fresh.take(MAX_REPORTS).map { entry ->
                CrashReport.fromPriorExit(
                    exitTimestampMillis = entry.timestamp,
                    appInfo = info,
                    exitProcessName = entry.processName ?: "",
                    reasonCode = entry.reason,
                    description = describe(entry),
                    trace = traceText(entry),
                    exitStatus = entry.status,
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Android 11+ only; callers already guard on [isSupported]. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun describe(entry: ApplicationExitInfo): String {
        val parts = mutableListOf<String>()
        entry.description?.takeIf { it.isNotBlank() }?.let(parts::add)
        // Importance tells a foreground death apart from a background service reap, which is the
        // difference between "the user saw a crash" and "nobody noticed".
        val importance = entry.importance
        if (importance != 0) parts.add("importance=$importance")
        val rssKb = entry.rss
        if (rssKb > 0L) parts.add("rss=${rssKb}KiB")
        return parts.joinToString(" / ")
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun traceText(exitInfo: ApplicationExitInfo): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        return runCatching {
            val input = exitInfo.traceInputStream ?: return@runCatching ""
            input.use { stream ->
                val buffer = ByteArray(MAX_TRACE_BYTES + 1)
                var total = 0
                while (total < buffer.size) {
                    val count = stream.read(buffer, total, buffer.size - total)
                    if (count < 0) break
                    if (count == 0) {
                        val byte = stream.read()
                        if (byte < 0) break
                        buffer[total++] = byte.toByte()
                    } else {
                        total += count
                    }
                }
                val size = minOf(total, MAX_TRACE_BYTES)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    exitInfo.reason == ApplicationExitInfo.REASON_CRASH_NATIVE
                ) {
                    NativeTombstoneTrace.decode(buffer.copyOf(size), truncated = total > size)
                } else {
                    String(buffer, 0, size, Charsets.UTF_8)
                }
            }
        }.getOrDefault("")
    }

    companion object {
        fun isSupported(): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        private const val MAX_ENTRIES = 32
        private const val MAX_REPORTS = 5
        private const val MAX_TRACE_BYTES = 128 * 1024
        private const val PREFS_NAME = "lumen_crash_prior_exit"
        private const val KEY_LAST_PROCESSED_TS = "last_processed_timestamp_millis"
    }
}
