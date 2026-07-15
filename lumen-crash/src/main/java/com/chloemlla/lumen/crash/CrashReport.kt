package com.chloemlla.lumen.crash

import android.app.Application
import android.os.Build
import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
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
    val authorName: String = CrashAuthorAttribution.AUTHOR_NAME,
    val authorUrl: String = CrashAuthorAttribution.AUTHOR_URL,
    val authorFingerprint: String = CrashAuthorAttribution.FINGERPRINT_HEX,
) {
    fun toClipboardText(): String {
        AuthorIntegrity.verifyOrThrow("export-clipboard")
        val author = AuthorIntegrity.verifiedAuthorBlock()
        return buildString {
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
            appendLine("Author: ${author.authorName}")
            appendLine("Author URL: ${author.authorUrl}")
            appendLine("Author fingerprint: ${author.authorFingerprint}")
            appendLine(author.footerLabel)
        }
    }

    companion object {
        fun fromThrowable(throwable: Throwable, appInfo: CrashAppInfo): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-throwable")
            val author = AuthorIntegrity.verifiedAuthorBlock()
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
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
            )
        }

        fun fromThrowableFallback(
            throwable: Throwable,
            reportFailure: Throwable,
            appInfo: CrashAppInfo,
        ): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-throwable-fallback")
            val author = AuthorIntegrity.verifiedAuthorBlock()
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
                systemInfo = "Crash report construction failed: ${reportFailure::class.java.name}\n" +
                    buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
            )
        }

        private fun buildSystemInfo(appInfo: CrashAppInfo): String = listOf(
            "App: ${appInfo.appDisplayName}",
            "App version: ${appInfo.versionName} (${appInfo.versionCode})",
            "Commit: ${appInfo.commitHash}",
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}",
            "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "ABI: ${Build.SUPPORTED_ABIS.joinToString()}",
            "Memory: ${memorySnapshot()}",
            "Build fingerprint: ${Build.FINGERPRINT}",
            "Crash SDK author: ${CrashAuthorAttribution.AUTHOR_NAME}",
            "Crash SDK author URL: ${CrashAuthorAttribution.AUTHOR_URL}",
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

fun crashReportFromJson(json: JSONObject): CrashReport {
    AuthorIntegrity.verifyOrThrow("from-json")
    val author = AuthorIntegrity.verifiedAuthorBlock()
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
        authorName = author.authorName,
        authorUrl = author.authorUrl,
        authorFingerprint = author.authorFingerprint,
    )
}

fun CrashReport.toJson(): JSONObject {
    AuthorIntegrity.verifyOrThrow("to-json")
    val author = AuthorIntegrity.verifiedAuthorBlock()
    return JSONObject().apply {
        put("reportId", reportId)
        put("crashedAtMillis", crashedAtMillis)
        put("crashedAtText", crashedAtText)
        put("exceptionType", exceptionType)
        put("rootCause", rootCause)
        put("threadName", threadName)
        put("processName", processName)
        put("systemInfo", systemInfo)
        put("stackTrace", stackTrace)
        put("recentEvents", JSONArray().apply {
            recentEvents.forEach { event -> put(event) }
        })
        put("authorName", author.authorName)
        put("authorUrl", author.authorUrl)
        put("authorFingerprint", author.authorFingerprint)
    }
}
