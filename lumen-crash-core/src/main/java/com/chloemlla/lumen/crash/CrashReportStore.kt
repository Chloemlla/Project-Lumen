package com.chloemlla.lumen.crash

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Persists the latest crash or watchdog report, preferring app-specific external storage.
 *
 * Primary locations are app-specific external directories so the report is not kept only
 * under internal private paths (`filesDir` / `noBackupFilesDir` / `cacheDir`). When no
 * external target is writable (e.g. `getExternalFilesDir` returns null on the device),
 * [save] falls back to those app-private internal paths so a captured report is never
 * silently dropped with the dying process. Legacy private copies remain readable/cleared
 * for migration.
 */
class CrashReportStore private constructor(
    private val externalTargetsProvider: () -> List<File>,
    private val legacyPrivateTargetsProvider: () -> List<File>,
) {
    private val lock = Any()

    constructor(context: Context) : this(
        externalTargetsProvider = context.applicationContext.let { appContext ->
            { resolveExternalTargets(appContext) }
        },
        legacyPrivateTargetsProvider = context.applicationContext.let { appContext ->
            { resolveLegacyPrivateTargets(appContext) }
        },
    )

    internal constructor(
        externalTargets: List<File>,
        legacyPrivateTargets: List<File>,
    ) : this(
        externalTargetsProvider = { externalTargets },
        legacyPrivateTargetsProvider = { legacyPrivateTargets },
    )

    fun save(report: CrashReport) {
        synchronized(lock) {
            saveLocked(report)
        }
    }

    fun load(): CrashReport? {
        return synchronized(lock) { loadLocked() }
    }

    fun clear() {
        synchronized(lock) {
            (externalTargets() + legacyPrivateTargets()).forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
                file.parentFile
                    ?.takeIf { it.name == DIR_NAME && it.isDirectory && it.list().isNullOrEmpty() }
                    ?.delete()
            }
        }
    }

    private fun saveLocked(report: CrashReport) {
        AuthorIntegrity.verifyOrThrow("store-save")
        val payload = report.toJson().toString()
        val failures = mutableListOf<Throwable>()
        // Prefer app-external storage, then fall back to app-private internal storage when no
        // external target is writable (e.g. getExternalFilesDir is null on the device), so a
        // captured report is never silently dropped with the dying process.
        if (!writeAny(payload, externalTargets(), failures)) {
            if (!writeAny(payload, legacyPrivateTargets(), failures)) {
                throw IOException("Unable to persist crash report.", failures.firstOrNull())
            }
            return
        }
        // External write succeeded; internal copies are now stale.
        clearLegacyPrivateCopies()
    }

    private fun writeAny(
        payload: String,
        targets: List<File>,
        failures: MutableList<Throwable>,
    ): Boolean {
        var saved = false
        targets.forEach { file ->
            runCatching {
                file.writeAtomically(payload)
                saved = true
            }.onFailure(failures::add)
        }
        return saved
    }

    private fun loadLocked(): CrashReport? {
        // Prefer external locations first, then migrate any legacy private copy.
        externalTargets().forEach { file ->
            readReport(file)?.let { return it }
        }
        legacyPrivateTargets().forEach { file ->
            val report = readReport(file) ?: return@forEach
            runCatching { saveLocked(report) }
            return report
        }
        return null
    }

    private fun externalTargets(): List<File> = externalTargetsProvider()

    private fun legacyPrivateTargets(): List<File> = legacyPrivateTargetsProvider()

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
        val parent = parentFile ?: throw IOException("Crash report has no parent directory.")
        parent.mkdirs()
        val tempFile = File.createTempFile("$name.", ".tmp", parent)
        try {
            tempFile.writeText(payload, Charsets.UTF_8)
            if (exists() && !delete()) {
                throw IOException("Unable to replace existing crash report at $absolutePath.")
            }
            if (!tempFile.renameTo(this)) {
                writeText(payload, Charsets.UTF_8)
            }
        } finally {
            tempFile.delete()
        }
    }

    private companion object {
        const val DIR_NAME = "lumen-crash"
        const val FILE_NAME = "crash_report.json"

        fun resolveExternalTargets(appContext: Context): List<File> {
            val dirs = listOfNotNull(
                appContext.getExternalFilesDir(DIR_NAME),
                appContext.getExternalFilesDir(null)?.resolve(DIR_NAME),
                appContext.externalCacheDir?.resolve(DIR_NAME),
            ).distinctBy { it.absolutePath }

            // Directories are created by writeAtomically, so a load() never has to touch
            // external storage beyond the reads it actually needs.
            return dirs.map { dir -> File(dir, FILE_NAME) }
        }

        fun resolveLegacyPrivateTargets(appContext: Context): List<File> = listOf(
            File(appContext.filesDir, FILE_NAME),
            File(appContext.noBackupFilesDir, FILE_NAME),
            File(appContext.cacheDir, FILE_NAME),
            File(appContext.filesDir, "$DIR_NAME/$FILE_NAME"),
            File(appContext.noBackupFilesDir, "$DIR_NAME/$FILE_NAME"),
            File(appContext.cacheDir, "$DIR_NAME/$FILE_NAME"),
        )
    }
}
