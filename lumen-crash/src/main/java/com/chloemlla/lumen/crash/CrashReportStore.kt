package com.chloemlla.lumen.crash

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

class CrashReportStore(context: Context) {
    private val files = listOf(
        File(context.filesDir, FILE_NAME),
        File(context.noBackupFilesDir, FILE_NAME),
        File(context.cacheDir, FILE_NAME),
    )

    fun save(report: CrashReport) {
        AuthorIntegrity.verifyOrThrow("store-save")
        val payload = report.toJson().toString()
        val failures = mutableListOf<Throwable>()
        var saved = false
        files.forEach { file ->
            runCatching {
                file.writeAtomically(payload)
                saved = true
            }.onFailure(failures::add)
        }
        if (saved) return
        throw IOException("Unable to persist crash report.", failures.firstOrNull())
    }

    fun load(): CrashReport? {
        return files.firstNotNullOfOrNull { file ->
            if (!file.exists()) {
                null
            } else {
                runCatching { crashReportFromJson(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull()
            }
        }
    }

    fun clear() {
        files.forEach { file ->
            if (file.exists()) file.delete()
        }
    }

    private fun File.writeAtomically(payload: String) {
        parentFile?.mkdirs()
        val tempFile = File(parentFile, "$name.tmp")
        tempFile.writeText(payload, Charsets.UTF_8)
        if (exists() && !delete()) {
            throw IOException("Unable to replace existing crash report at $absolutePath.")
        }
        if (!tempFile.renameTo(this)) {
            tempFile.delete()
            writeText(payload, Charsets.UTF_8)
        }
    }

    private companion object {
        const val FILE_NAME = "crash_report.json"
    }
}
