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
private val reportWindowsHomeRegex = Regex("""[A-Za-z]:\\Users\\[^\\\s]+""")
private val reportLinuxHomeRegex = Regex("""/home/[^/\s]+""")
private val reportMacHomeRegex = Regex("""/Users/[^/\s]+""")
private val reportContentUriRegex = Regex("""content://[^\s]+""")
private val reportFileUriRegex = Regex("""file://[^\s]+""")
private val reportBearerTokenRegex = Regex("""(?i)Bearer\s+[A-Za-z0-9._~+/=-]{8,}""")
private val reportSecretParamRegex =
    Regex("""(?i)\b(token|key|secret|password|passwd|auth|signature)=[^&\s"']+""")
private const val REPORT_HEX_DIGITS = "0123456789abcdef"

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
    val kind: CrashReportKind = CrashReportKind.CRASH,
    val durationMillis: Long = 0L,
    val exitReason: String? = null,
) {
    fun toClipboardText(): String {
        AuthorIntegrity.verifyOrThrow("export-clipboard")
        val author = AuthorIntegrity.verifiedAuthorBlock()
        return buildString(
            systemInfo.length + stackTrace.length + recentEvents.sumOf { it.length + 1 } + 512,
        ) {
            appendLine("Report ID: $reportId")
            appendLine("Report type: ${kind.wireValue}")
            appendLine("Crash time: $crashedAtText")
            if (durationMillis > 0L) {
                appendLine("Unresponsive duration: ${durationMillis} ms")
            }
            if (exitReason != null) {
                appendLine("Exit reason: $exitReason")
            }
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
        fun fromThrowable(
            throwable: Throwable,
            appInfo: CrashAppInfo,
            kind: CrashReportKind = CrashReportKind.CRASH,
        ): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-throwable")
            val author = AuthorIntegrity.verifiedAuthorBlock()
            val root = throwable.rootCause()
            val nowMillis = System.currentTimeMillis()
            val stackTrace = sanitize(throwable.stackTraceText())
            val exceptionType = throwable::class.java.name
            val rootCause = sanitize(root.message?.takeIf { it.isNotBlank() } ?: root::class.java.name)
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace, kind),
                crashedAtMillis = nowMillis,
                crashedAtText = formatTime(nowMillis),
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
                kind = kind,
            )
        }

        fun fromThrowableFallback(
            throwable: Throwable,
            reportFailure: Throwable,
            appInfo: CrashAppInfo,
            kind: CrashReportKind = CrashReportKind.CRASH,
        ): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-throwable-fallback")
            val author = AuthorIntegrity.verifiedAuthorBlock()
            val nowMillis = System.currentTimeMillis()
            val stackTrace = sanitize(throwable.stackTraceToString())
            val exceptionType = throwable::class.java.name
            val rootCause = sanitize(
                throwable.message?.takeIf { it.isNotBlank() } ?: throwable::class.java.name,
            )
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace, kind),
                crashedAtMillis = nowMillis,
                crashedAtText = formatTime(nowMillis),
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
                kind = kind,
            )
        }

        fun fromWatchdog(
            kind: CrashReportKind,
            durationMillis: Long,
            mainThread: Thread,
            threadDump: String,
            appInfo: CrashAppInfo,
        ): CrashReport {
            require(kind != CrashReportKind.CRASH && kind != CrashReportKind.NON_FATAL) {
                "Watchdog reports must describe an unresponsive main thread."
            }
            AuthorIntegrity.verifyOrThrow("from-watchdog")
            val author = AuthorIntegrity.verifiedAuthorBlock()
            val nowMillis = System.currentTimeMillis()
            val duration = durationMillis.coerceAtLeast(0L)
            val exceptionType = when (kind) {
                CrashReportKind.ANR -> "android.app.ApplicationNotResponding"
                CrashReportKind.STARTUP_HANG -> "com.chloemlla.lumen.crash.StartupHang"
                CrashReportKind.FREEZE -> "com.chloemlla.lumen.crash.MainThreadFreeze"
                CrashReportKind.CRASH,
                CrashReportKind.NON_FATAL,
                CrashReportKind.PRIOR_EXIT -> error("Unreachable crash watchdog kind")
            }
            val rootCause = when (kind) {
                CrashReportKind.ANR, CrashReportKind.FREEZE ->
                    "Main thread did not process a heartbeat for $duration ms"
                CrashReportKind.STARTUP_HANG ->
                    "Application did not report its first rendered frame within $duration ms"
                CrashReportKind.CRASH,
                CrashReportKind.NON_FATAL,
                CrashReportKind.PRIOR_EXIT -> error("Unreachable crash watchdog kind")
            }
            val stackTrace = sanitize(threadDump).ifBlank { "<thread dump unavailable>" }
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace, kind),
                crashedAtMillis = nowMillis,
                crashedAtText = formatTime(nowMillis),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = mainThread.name.ifBlank { "main" },
                processName = processName(),
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = kind,
                durationMillis = duration,
            )
        }

        fun fromPriorExit(
            exitTimestampMillis: Long,
            appInfo: CrashAppInfo,
            exitProcessName: String,
            reasonCode: Int,
            description: String,
            trace: String,
        ): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-prior-exit")
            val author = AuthorIntegrity.verifiedAuthorBlock()
            val reasonName = processExitReasonName(reasonCode)
            val exceptionType = reasonName
            val rootCause = description.ifBlank { reasonName }
            val stackTrace = sanitize(trace).ifBlank { "<exit trace unavailable>" }
            return CrashReport(
                reportId = reportId(exitTimestampMillis, exceptionType, rootCause, stackTrace, CrashReportKind.PRIOR_EXIT),
                crashedAtMillis = exitTimestampMillis,
                crashedAtText = formatTime(exitTimestampMillis),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = "previous-process",
                processName = exitProcessName.ifBlank { "unknown" },
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = CrashReportKind.PRIOR_EXIT,
                exitReason = listOfNotNull(reasonName, description.takeIf { it.isNotBlank() }).joinToString(" / "),
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
            kind: CrashReportKind = CrashReportKind.CRASH,
        ): String {
            val kindPrefix = if (kind == CrashReportKind.CRASH) "" else "${kind.wireValue}|"
            val seed = "$crashedAtMillis|$kindPrefix$exceptionType|$rootCause|" +
                stackTrace.lineSequence().firstOrNull().orEmpty()
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(seed.toByteArray(Charsets.UTF_8))
            val byteCount = minOf(REPORT_ID_HEX_LENGTH / 2, digest.size)
            return buildString(byteCount * 2) {
                for (index in 0 until byteCount) {
                    val value = digest[index].toInt() and 0xFF
                    append(REPORT_HEX_DIGITS[value ushr 4])
                    append(REPORT_HEX_DIGITS[value and 0x0F])
                }
            }
        }

        private fun formatTime(millis: Long): String {
            return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(crashTimeFormatter)
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
                .replace(reportWindowsHomeRegex, "[user-home]")
                .replace(reportLinuxHomeRegex, "[user-home]")
                .replace(reportMacHomeRegex, "[user-home]")
                .replace(reportContentUriRegex, "[content-uri]")
                .replace(reportFileUriRegex, "[file-uri]")
                .replace(reportBearerTokenRegex, "Bearer [redacted]")
                .replace(reportSecretParamRegex, "$1=[redacted]")
        }

        private const val BYTES_PER_MEBIBYTE = 1024L * 1024L
        private const val REPORT_ID_HEX_LENGTH = 12
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
        kind = CrashReportKind.fromWireValue(json.optString("kind").takeIf { it.isNotBlank() }),
        durationMillis = json.optLong("durationMillis", 0L).coerceAtLeast(0L),
        exitReason = json.optString("exitReason").takeIf { it.isNotBlank() },
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
        put("kind", kind.wireValue)
        put("durationMillis", durationMillis.coerceAtLeast(0L))
        exitReason?.let { put("exitReason", it) }
        put("authorName", author.authorName)
        put("authorUrl", author.authorUrl)
        put("authorFingerprint", author.authorFingerprint)
    }
}

internal fun processExitReasonName(code: Int): String = when (code) {
    1 -> "REASON_EXIT_SELF"
    2 -> "REASON_SIGNALED"
    3 -> "REASON_LOW_MEMORY"
    4 -> "REASON_CRASH"
    5 -> "REASON_PERMISSION_CHANGE"
    6 -> "REASON_EXCEPTION"
    7 -> "REASON_USER_REQUESTED"
    8 -> "REASON_USER_STOPPED"
    9 -> "REASON_OTHER"
    10 -> "REASON_ANR"
    11 -> "REASON_DEPENDENCY_DIED"
    12 -> "REASON_DEADLOCK"
    13 -> "REASON_CRASH_NATIVE"
    14 -> "REASON_SUSPENDED"
    15 -> "REASON_INITIALIZATION_FAILURE"
    16 -> "REASON_QUIET"
    17 -> "REASON_MEMORY_PRESSURE"
    18 -> "REASON_FREEZER"
    19 -> "REASON_PACKAGE_UPDATED"
    else -> "UNKNOWN($code)"
}
