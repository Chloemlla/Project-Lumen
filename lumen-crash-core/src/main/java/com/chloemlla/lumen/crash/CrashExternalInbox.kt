package com.chloemlla.lumen.crash

import org.json.JSONObject
import java.io.File

/**
 * Failure data the host observed outside the JVM, handed to the SDK through a directory.
 *
 * This is the bridge for WebView-shell hosts (Tauri, Capacitor, plain `WebView`) and for any
 * host that runs a second runtime:
 *
 * - Rust cannot reach Kotlin from a panic hook without extra JNI plumbing, so the hook appends a
 *   file; the SDK converts it into a report on the next launch (or immediately, when the host
 *   calls [ingest] after recovering).
 * - A JS `window.onerror` handler has no JVM stack, but it has a message and a JS stack — both
 *   survive as-is.
 *
 * Accepted files: `*.json`
 * (`message`/`stackTrace`/`source`/`timestampMillis`/`thread`/`fatal`), plus `*.txt` and `*.log`
 * (first line is the message, the rest is the stack trace). Files are deleted only after the SDK
 * accepted the report, so a failed ingest is retried on the next scan.
 *
 * `fatal` defaults to true because a file in the inbox usually means the process died. A host
 * that funnels recoverable failures through the same directory — a JS `window.onerror` storm, for
 * example — writes `"fatal": false`, and those reports never claim the crash-screen slot.
 *
 * Everything here fails soft: a malformed or oversized file is skipped, never thrown.
 */
object CrashExternalInbox {
    /** Reading stops after this many bytes per file. */
    const val DEFAULT_MAX_FILE_BYTES: Int = 64 * 1024

    /** Files converted per scan; the rest wait for the next one. */
    const val DEFAULT_MAX_FILES_PER_SCAN: Int = 8

    private val supportedExtensions = setOf("json", "txt", "log")

    /**
     * Converts up to [DEFAULT_MAX_FILES_PER_SCAN] pending files in [directory] into reports.
     *
     * Returns the number of files consumed. A fatal file claims the pending-report slot: a file in
     * the inbox usually means the previous process died before the host could tell anyone, which
     * is exactly the case the crash screen exists for. Files marked `"fatal": false` are recorded
     * as non-fatal evidence and leave the slot alone.
     */
    fun ingest(directory: File): Int {
        if (!LumenCrash.isInstalled()) return 0
        if (!directory.isDirectory && !directory.mkdirs()) return 0
        val files = directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            ?.sortedBy { it.lastModified() }
            ?.take(DEFAULT_MAX_FILES_PER_SCAN)
            ?.toList()
            .orEmpty()

        var ingested = 0
        for (file in files) {
            val text = runCatching { readBounded(file) }.getOrNull() ?: continue
            val record = CrashExternalRecordParser.parse(file.name, text) ?: continue
            val report = runCatching {
                LumenCrash.recordExternalFailure(
                    source = record.source,
                    message = record.message,
                    stackTrace = record.stackTrace,
                    occurredAtMillis = record.occurredAtMillis,
                    threadName = record.threadName,
                    claimPendingSlot = record.fatal,
                )
            }.getOrNull() ?: continue
            if (report.kind != CrashReportKind.EXTERNAL) continue
            if (runCatching { file.delete() }.getOrDefault(false)) {
                ingested++
            }
        }
        return ingested
    }

    private fun readBounded(file: File): String? {
        return file.inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_MAX_FILE_BYTES)
            var total = 0
            while (total < buffer.size) {
                val count = stream.read(buffer, total, buffer.size - total)
                if (count <= 0) break
                total += count
            }
            if (total == 0) null else String(buffer, 0, total, Charsets.UTF_8)
        }
    }
}

/** One failure file, already parsed and bounded. */
internal data class CrashExternalRecord(
    val source: String,
    val message: String,
    val stackTrace: String?,
    val occurredAtMillis: Long,
    val threadName: String?,
    /** False for failures the host recovered from; such entries never claim the pending slot. */
    val fatal: Boolean = true,
)

/**
 * Parses inbox files. Pure (no Android calls) so the accepted formats stay unit-tested.
 */
internal object CrashExternalRecordParser {
    fun parse(fileName: String, text: String): CrashExternalRecord? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val looksJson = fileName.endsWith(".json", ignoreCase = true) || trimmed.startsWith("{")
        return if (looksJson) parseJson(trimmed) else parsePlainText(fileName, trimmed)
    }

    private fun parseJson(text: String): CrashExternalRecord? {
        return runCatching<CrashExternalRecord?> {
            val json = JSONObject(text)
            val message = listOf(
                json.optString("message"),
                json.optString("msg"),
                json.optString("error"),
            ).firstOrNull { it.isNotBlank() }?.trim() ?: return@runCatching null
            val stackTrace = listOf(
                json.optString("stackTrace"),
                json.optString("stack"),
                json.optString("backtrace"),
            ).firstOrNull { it.isNotBlank() }
            val occurredAtMillis = listOf(
                json.optLong("timestampMillis", 0L),
                json.optLong("timestamp", 0L),
                json.optLong("occurredAtMillis", 0L),
            ).firstOrNull { it > 0L } ?: 0L
            CrashExternalRecord(
                source = json.optString("source").trim().takeIf { it.isNotBlank() } ?: "host-inbox",
                message = message,
                stackTrace = stackTrace,
                occurredAtMillis = occurredAtMillis,
                threadName = json.optString("thread").trim().takeIf { it.isNotBlank() },
                fatal = json.optBoolean("fatal", json.optBoolean("isFatal", true)),
            )
        }.getOrNull()
    }

    private fun parsePlainText(fileName: String, text: String): CrashExternalRecord? {
        val lines = text.lines()
        val message = lines.firstOrNull()?.trim().orEmpty()
        if (message.isEmpty()) return null
        return CrashExternalRecord(
            source = if (fileName.endsWith(".log", ignoreCase = true)) "native-log" else "host-inbox",
            message = message,
            stackTrace = lines.drop(1).joinToString("\n").trim().takeIf { it.isNotBlank() },
            occurredAtMillis = 0L,
            threadName = null,
            // A plain-text dump has no header, so it is treated as post-mortem evidence.
            fatal = true,
        )
    }
}
