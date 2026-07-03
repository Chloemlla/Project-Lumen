package com.projectlumen.app.core.crash

import android.app.Application
import android.os.Build
import android.os.Process
import com.projectlumen.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

data class CrashReport(
    val reportId: String,
    val crashedAtMillis: Long,
    val crashedAtText: String,
    val exceptionType: String,
    val rootCause: String,
    val threadName: String,
    val processName: String,
    val systemInfo: String,
    val stackTrace: String,
    val recentEvents: List<String> = emptyList(),
) {
    fun toClipboardText(): String = buildString {
        appendLine("Report ID: $reportId")
        appendLine("Crash time: $crashedAtText")
        appendLine("Exception type: $exceptionType")
        appendLine("Root cause: $rootCause")
        appendLine("Thread: $threadName")
        appendLine("Process: $processName")
        appendLine("System info:")
        appendLine(systemInfo)
        if (recentEvents.isNotEmpty()) {
            appendLine("Recent app events:")
            recentEvents.forEach { appendLine(it) }
        }
        appendLine("Stack trace:")
        appendLine(stackTrace)
    }

    companion object {
        fun fromThrowable(throwable: Throwable): CrashReport {
            val root = throwable.rootCause()
            val nowMillis = System.currentTimeMillis()
            val stackTrace = sanitize(throwable.stackTraceText())
            val exceptionType = throwable::class.java.name
            val rootCause = sanitize(root.message?.takeIf { it.isNotBlank() } ?: root::class.java.name)
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace),
                crashedAtMillis = nowMillis,
                crashedAtText = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).format(crashTimeFormatter),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = Thread.currentThread().name,
                processName = processName(),
                systemInfo = buildSystemInfo(),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
            )
        }

        fun fromThrowableFallback(throwable: Throwable, reportFailure: Throwable): CrashReport {
            val nowMillis = System.currentTimeMillis()
            val stackTrace = throwable.stackTraceToString()
            val exceptionType = throwable::class.java.name
            val rootCause = throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.java.name
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace),
                crashedAtMillis = nowMillis,
                crashedAtText = nowMillis.toString(),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = Thread.currentThread().name,
                processName = processName(),
                systemInfo = "Crash report construction failed: ${reportFailure::class.java.name}",
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
            )
        }

        private fun buildSystemInfo(): String = listOf(
            "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Commit: ${BuildConfig.SHORT_HASH}",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "ABI: ${Build.SUPPORTED_ABIS.joinToString()}",
            "Memory: ${memorySnapshot()}",
            "Build fingerprint: ${Build.FINGERPRINT}",
        ).joinToString("\n")

        private fun processName(): String {
            val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                "pid:${Process.myPid()}"
            }
            return name.ifBlank { "unknown" }
        }

        private fun memorySnapshot(): String {
            val runtime = Runtime.getRuntime()
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEBIBYTE
            val maxMb = runtime.maxMemory() / BYTES_PER_MEBIBYTE
            return "${usedMb} MiB used / ${maxMb} MiB max"
        }

        private fun reportId(
            crashedAtMillis: Long,
            exceptionType: String,
            rootCause: String,
            stackTrace: String,
        ): String {
            val seed = "$crashedAtMillis|$exceptionType|$rootCause|${stackTrace.lineSequence().firstOrNull().orEmpty()}"
            return MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(it) }
                .take(12)
        }

        private fun Throwable.rootCause(): Throwable {
            var current = this
            while (current.cause != null && current.cause !== current) {
                current = current.cause!!
            }
            return current
        }

        private fun Throwable.stackTraceText(): String {
            val writer = StringWriter()
            printStackTrace(PrintWriter(writer))
            return writer.toString()
        }

        private fun sanitize(value: String): String {
            return value
                .replace(Regex("""[A-Za-z]:\\Users\\[^\\\s]+"""), "[user-home]")
                .replace(Regex("""/home/[^/\s]+"""), "[user-home]")
                .replace(Regex("""/Users/[^/\s]+"""), "[user-home]")
                .replace(Regex("""content://[^\s]+"""), "[content-uri]")
                .replace(Regex("""file://[^\s]+"""), "[file-uri]")
        }

        private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
    }
}

fun crashReportFromJson(json: org.json.JSONObject): CrashReport {
    return CrashReport(
        reportId = json.optString("reportId").ifBlank {
            "${json.getLong("crashedAtMillis")}".takeLast(12)
        },
        crashedAtMillis = json.getLong("crashedAtMillis"),
        crashedAtText = json.getString("crashedAtText"),
        exceptionType = json.getString("exceptionType"),
        rootCause = json.getString("rootCause"),
        threadName = json.optString("threadName", "unknown"),
        processName = json.optString("processName", "unknown"),
        systemInfo = json.getString("systemInfo"),
        stackTrace = json.getString("stackTrace"),
        recentEvents = buildList {
            val events = json.optJSONArray("recentEvents") ?: return@buildList
            for (index in 0 until events.length()) {
                events.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        },
    )
}
