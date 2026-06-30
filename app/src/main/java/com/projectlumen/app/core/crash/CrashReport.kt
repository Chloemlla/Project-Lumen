package com.projectlumen.app.core.crash

import android.os.Build
import com.projectlumen.app.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

data class CrashReport(
    val crashedAtMillis: Long,
    val crashedAtText: String,
    val exceptionType: String,
    val rootCause: String,
    val systemInfo: String,
    val stackTrace: String,
) {
    fun toClipboardText(): String = buildString {
        appendLine("Crash time: $crashedAtText")
        appendLine("Exception type: $exceptionType")
        appendLine("Root cause: $rootCause")
        appendLine("System info:")
        appendLine(systemInfo)
        appendLine("Stack trace:")
        appendLine(stackTrace)
    }

    companion object {
        fun fromThrowable(throwable: Throwable): CrashReport {
            val root = throwable.rootCause()
            val nowMillis = System.currentTimeMillis()
            return CrashReport(
                crashedAtMillis = nowMillis,
                crashedAtText = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).format(crashTimeFormatter),
                exceptionType = throwable::class.java.name,
                rootCause = sanitize(root.message?.takeIf { it.isNotBlank() } ?: root::class.java.name),
                systemInfo = buildSystemInfo(),
                stackTrace = sanitize(throwable.stackTraceText()),
            )
        }

        private fun buildSystemInfo(): String = listOf(
            "App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Build fingerprint: ${Build.FINGERPRINT}",
        ).joinToString("\n")

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
    }
}

fun crashReportFromJson(json: org.json.JSONObject): CrashReport {
    return CrashReport(
        crashedAtMillis = json.getLong("crashedAtMillis"),
        crashedAtText = json.getString("crashedAtText"),
        exceptionType = json.getString("exceptionType"),
        rootCause = json.getString("rootCause"),
        systemInfo = json.getString("systemInfo"),
        stackTrace = json.getString("stackTrace"),
    )
}
