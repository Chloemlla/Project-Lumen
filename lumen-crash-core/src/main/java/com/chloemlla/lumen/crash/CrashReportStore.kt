package com.chloemlla.lumen.crash

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Persists the latest crash report outside app-private internal storage.
 *
 * Primary locations are app-specific external directories so the report is not kept only
 * under internal private paths (`filesDir` / `noBackupFilesDir` / `cacheDir`).
 * Legacy private copies are still readable/cleared for migration.
 */
class CrashReportStore(context: Context) {
    private val appContext = context.applicationContext

    fun save(report: CrashReport) {
        AuthorIntegrity.verifyOrThrow("store-save")
        val payload = report.toJson().toString()
        val targets = writableTargets()
        if (targets.isEmpty()) {
            throw IOException("No external crash report directory is available.")
        }

        val failures = mutableListOf<Throwable>()
        var saved = false
        targets.forEach { file ->
            runCatching {
                file.writeAtomically(payload)
                saved = true
            }.onFailure(failures::add)
        }
        if (!saved) {
            throw IOException("Unable to persist crash report.", failures.firstOrNull())
        }

        // Avoid leaving stale private copies after a successful external write.
        clearLegacyPrivateCopies()
    }

    fun load(): CrashReport? {
        // Prefer external locations first, then migrate any legacy private copy.
        externalTargets().forEach { file ->
            readReport(file)?.let { return it }
        }
        legacyPrivateTargets().forEach { file ->
            val report = readReport(file) ?: return@forEach
            runCatching { save(report) }
            return report
        }
        return null
    }

    fun clear() {
        (externalTargets() + legacyPrivateTargets()).forEach { file ->
            if (file.exists()) {
                file.delete()
            }
            file.parentFile
                ?.takeIf { it.name == DIR_NAME && it.isDirectory && it.list().isNullOrEmpty() }
                ?.delete()
        }
    }

    private fun writableTargets(): List<File> = externalTargets()

    private fun externalTargets(): List<File> {
        val dirs = listOfNotNull(
            appContext.getExternalFilesDir(DIR_NAME),
            appContext.getExternalFilesDir(null)?.resolve(DIR_NAME),
            appContext.externalCacheDir?.resolve(DIR_NAME),
        ).distinctBy { it.absolutePath }

        return dirs.map { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, FILE_NAME)
        }
    }

    private fun legacyPrivateTargets(): List<File> = listOf(
        File(appContext.filesDir, FILE_NAME),
        File(appContext.noBackupFilesDir, FILE_NAME),
        File(appContext.cacheDir, FILE_NAME),
        File(appContext.filesDir, "$DIR_NAME/$FILE_NAME"),
        File(appContext.noBackupFilesDir, "$DIR_NAME/$FILE_NAME"),
        File(appContext.cacheDir, "$DIR_NAME/$FILE_NAME"),
    )

    private fun clearLegacyPrivateCopies() {
        legacyPrivateTargets().forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun readReport(file: File): CrashReport? {
        if (!file.exists()) return null
        return runCatching {
            crashReportFromJson(JSONObject(file.readText(Charsets.UTF_8)))
        }.getOrNull()
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
        const val DIR_NAME = "lumen-crash"
        const val FILE_NAME = "crash_report.json"
    }
}
