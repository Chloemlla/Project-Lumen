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
    /** Which runtime produced the failure; see [CrashComponent]. */
    val component: CrashComponent = CrashComponent.UNKNOWN,
    /** Signal number (or `exit()` status) reported by `ApplicationExitInfo`; 0 when unknown. */
    val exitStatus: Int = 0,
    /** Library names found in the stack/tombstone, used as a symbolization hint. */
    val nativeLibraries: List<String> = emptyList(),
    /** Who supplied the failure when [kind] is [CrashReportKind.EXTERNAL]. */
    val externalSource: String? = null,
) {
    fun toClipboardText(): String {
        AuthorIntegrity.verifyOrThrow("export-clipboard")
        val author = AuthorIntegrity.verifiedAuthorBlock()
        return buildString(
            systemInfo.length + stackTrace.length + recentEvents.sumOf { it.length + 1 } + 512,
        ) {
            appendLine("Report ID: $reportId")
            appendLine("Report type: ${kind.wireValue}")
            appendLine("Component: ${component.wireValue}")
            externalSource?.let { appendLine("Captured by: $it") }
            appendLine("Crash time: $crashedAtText")
            if (durationMillis > 0L) {
                appendLine("Unresponsive duration: ${durationMillis} ms")
            }
            if (exitReason != null) {
                appendLine("Exit reason: $exitReason")
            }
            if (exitStatus > 0) {
                appendLine("Exit status: ${ExitSignals.describe(exitStatus) ?: exitStatus.toString()}")
            }
            if (nativeLibraries.isNotEmpty()) {
                appendLine("Native libraries: ${nativeLibraries.joinToString()}")
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
            val process = processName()
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace, kind),
                crashedAtMillis = nowMillis,
                crashedAtText = formatTime(nowMillis),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = Thread.currentThread().name,
                processName = process,
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = kind,
                component = CrashComponentClassifier.classify(kind, process, exceptionType, stackTrace),
                nativeLibraries = NativeLibraries.extract(stackTrace),
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
            val process = processName()
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace, kind),
                crashedAtMillis = nowMillis,
                crashedAtText = formatTime(nowMillis),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = Thread.currentThread().name,
                processName = process,
                systemInfo = "Crash report construction failed: ${reportFailure::class.java.name}\n" +
                    buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = kind,
                component = CrashComponentClassifier.classify(kind, process, exceptionType, stackTrace),
                nativeLibraries = NativeLibraries.extract(stackTrace),
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
                CrashReportKind.PRIOR_EXIT,
                CrashReportKind.EXTERNAL -> error("Unreachable crash watchdog kind")
            }
            val rootCause = when (kind) {
                CrashReportKind.ANR, CrashReportKind.FREEZE ->
                    "Main thread did not process a heartbeat for $duration ms"
                CrashReportKind.STARTUP_HANG ->
                    "Application did not report its first rendered frame within $duration ms"
                CrashReportKind.CRASH,
                CrashReportKind.NON_FATAL,
                CrashReportKind.PRIOR_EXIT,
                CrashReportKind.EXTERNAL -> error("Unreachable crash watchdog kind")
            }
            val stackTrace = sanitize(threadDump).ifBlank { "<thread dump unavailable>" }
            val process = processName()
            return CrashReport(
                reportId = reportId(nowMillis, exceptionType, rootCause, stackTrace, kind),
                crashedAtMillis = nowMillis,
                crashedAtText = formatTime(nowMillis),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = mainThread.name.ifBlank { "main" },
                processName = process,
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = kind,
                durationMillis = duration,
                component = CrashComponentClassifier.classify(kind, process, exceptionType, stackTrace),
                nativeLibraries = NativeLibraries.extract(stackTrace),
            )
        }

        fun fromPriorExit(
            exitTimestampMillis: Long,
            appInfo: CrashAppInfo,
            exitProcessName: String,
            reasonCode: Int,
            description: String,
            trace: String,
            exitStatus: Int = 0,
        ): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-prior-exit")
            val author = AuthorIntegrity.verifiedAuthorBlock()
            val reasonName = processExitReasonName(reasonCode)
            val exceptionType = reasonName
            val rootCause = description.ifBlank { reasonName }
            val stackTrace = sanitize(trace).ifBlank { "<exit trace unavailable>" }
            val process = exitProcessName.ifBlank { "unknown" }
            // Only a signaled death carries a meaningful signal; an ANR is also delivered as
            // SIGKILL by the system, which would read as "killed by SIGKILL" and add nothing.
            val signalText = when (reasonCode) {
                2, 5 -> ExitSignals.describe(exitStatus)
                else -> null
            }
            return CrashReport(
                reportId = reportId(exitTimestampMillis, exceptionType, rootCause, stackTrace, CrashReportKind.PRIOR_EXIT),
                crashedAtMillis = exitTimestampMillis,
                crashedAtText = formatTime(exitTimestampMillis),
                exceptionType = exceptionType,
                rootCause = rootCause,
                threadName = "previous-process",
                processName = process,
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = stackTrace,
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = CrashReportKind.PRIOR_EXIT,
                exitReason = listOfNotNull(
                    reasonName,
                    signalText,
                    description.takeIf { it.isNotBlank() },
                ).joinToString(" / "),
                component = CrashComponentClassifier.classify(
                    CrashReportKind.PRIOR_EXIT,
                    process,
                    exceptionType,
                    stackTrace,
                ),
                exitStatus = exitStatus,
                nativeLibraries = NativeLibraries.extract(stackTrace),
            )
        }

        /**
         * Builds a report from failure data the host observed itself (JS error, Rust panic hook
         * line, Flutter error, native log line). See [LumenCrash.recordExternalFailure].
         */
        fun fromExternal(
            source: String,
            message: String,
            stackTrace: String?,
            occurredAtMillis: Long,
            threadName: String?,
            appInfo: CrashAppInfo,
        ): CrashReport {
            AuthorIntegrity.verifyOrThrow("from-external")
            val author = AuthorIntegrity.verifiedAuthorBlock()
            val safeSource = sanitize(source).trim().take(EXTERNAL_SOURCE_MAX_CHARS).ifBlank { "host" }
            val safeMessage = sanitize(message).trim().take(ROOT_CAUSE_MAX_CHARS)
                .ifBlank { "External failure reported by $safeSource" }
            val safeStack = sanitize(stackTrace.orEmpty()).trim().take(STACK_TRACE_MAX_CHARS)
            val time = if (occurredAtMillis > 0L) occurredAtMillis else System.currentTimeMillis()
            val process = processName()
            return CrashReport(
                reportId = reportId(
                    time,
                    "ExternalFailure",
                    "$safeSource|$safeMessage",
                    safeStack,
                    CrashReportKind.EXTERNAL,
                ),
                crashedAtMillis = time,
                crashedAtText = formatTime(time),
                exceptionType = "ExternalFailure",
                rootCause = safeMessage,
                threadName = threadName?.trim()?.takeIf { it.isNotBlank() }
                    ?: Thread.currentThread().name,
                processName = process,
                systemInfo = buildSystemInfo(appInfo),
                stackTrace = safeStack.ifBlank { "<no stack trace supplied by $safeSource>" },
                recentEvents = CrashBreadcrumbs.snapshot(),
                authorName = author.authorName,
                authorUrl = author.authorUrl,
                authorFingerprint = author.authorFingerprint,
                kind = CrashReportKind.EXTERNAL,
                component = CrashComponentClassifier.classify(
                    CrashReportKind.EXTERNAL,
                    process,
                    "ExternalFailure",
                    safeStack,
                ),
                nativeLibraries = NativeLibraries.extract(safeStack),
                externalSource = safeSource,
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
        private const val ROOT_CAUSE_MAX_CHARS = 1_024
        private const val STACK_TRACE_MAX_CHARS = 32 * 1024
        private const val EXTERNAL_SOURCE_MAX_CHARS = 120
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
        component = CrashComponent.fromWireValue(
            json.optString("component").takeIf { it.isNotBlank() },
        ),
        exitStatus = json.optInt("exitStatus", 0).coerceAtLeast(0),
        nativeLibraries = buildList {
            val libraries = json.optJSONArray("nativeLibraries") ?: return@buildList
            for (index in 0 until libraries.length()) {
                libraries.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        },
        externalSource = json.optString("externalSource").takeIf { it.isNotBlank() },
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
        put("component", component.wireValue)
        if (exitStatus > 0) put("exitStatus", exitStatus)
        if (nativeLibraries.isNotEmpty()) {
            put("nativeLibraries", JSONArray().apply {
                nativeLibraries.forEach { library -> put(library) }
            })
        }
        externalSource?.let { put("externalSource", it) }
        put("authorName", author.authorName)
        put("authorUrl", author.authorUrl)
        put("authorFingerprint", author.authorFingerprint)
    }
}

/**
 * Maps an `ApplicationExitInfo` reason code to its constant name.
 *
 * The values are the ones published in the Android reference for `ApplicationExitInfo`
 * (`REASON_UNKNOWN` = 0 … `REASON_ANOMALY` = 18). Keep this table in step with the reference
 * rather than with the ordering of the constants in the platform source: a table that is off by
 * one labels a native crash as a permission change and a system reap as a native crash.
 */
internal fun processExitReasonName(code: Int): String = when (code) {
    0 -> "REASON_UNKNOWN"
    1 -> "REASON_EXIT_SELF"
    2 -> "REASON_SIGNALED"
    3 -> "REASON_LOW_MEMORY"
    4 -> "REASON_CRASH"
    5 -> "REASON_CRASH_NATIVE"
    6 -> "REASON_ANR"
    7 -> "REASON_INITIALIZATION_FAILURE"
    8 -> "REASON_PERMISSION_CHANGE"
    9 -> "REASON_EXCESSIVE_RESOURCE_USAGE"
    10 -> "REASON_USER_REQUESTED"
    11 -> "REASON_USER_STOPPED"
    12 -> "REASON_DEPENDENCY_DIED"
    13 -> "REASON_OTHER"
    14 -> "REASON_FREEZER"
    15 -> "REASON_PACKAGE_STATE_CHANGE"
    16 -> "REASON_PACKAGE_UPDATED"
    17 -> "REASON_MEMORY_LIMITER"
    18 -> "REASON_ANOMALY"
    else -> "UNKNOWN($code)"
}
