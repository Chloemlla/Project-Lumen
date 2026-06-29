package com.projectlumen.app

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.core.crash.CrashReportStore
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.TimerForegroundService
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class ProjectLumenApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val notifications: NotificationService by lazy { NotificationService(this) }
    val audio: AudioService by lazy { AudioService(this) }
    val export: ExportService by lazy { ExportService(this) }
    val crashReports: CrashReportStore by lazy { CrashReportStore(this) }

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            runCatching { crashReports.save(CrashReport.fromThrowable(throwable)) }
            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
        notifications.ensureChannels()
        checkStartupUpdate()
    }

    fun startTimerService() {
        ContextCompat.startForegroundService(this, Intent(this, TimerForegroundService::class.java))
    }

    fun stopTimerService() {
        stopService(Intent(this, TimerForegroundService::class.java))
    }

    private fun checkStartupUpdate() {
        Thread {
            val update = runCatching { fetchLatestReleaseInfo() }.getOrNull() ?: return@Thread
            if (BuildConfig.BUILD_TIME < update.releaseCreatedAtMillis) {
                notifications.showUpdateAvailable(update.tagName, update.releaseName, update.body, update.assetName, update.assetUrl)
            }
        }.start()
    }

    private fun fetchLatestReleaseInfo(): ReleaseInfo? {
        val body = URL(PROJECT_LUMEN_RELEASES_API)
            .openConnection()
            .let { it as HttpURLConnection }
            .run {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Project-Lumen")
                if (responseCode !in 200..299) return null
                inputStream.bufferedReader().use { it.readText() }
            }

        val latest = body.takeIf { it.startsWith("[") }?.extractFirstArrayObject() ?: return null
        val tagName = latest.extractJsonString("tag_name") ?: return null
        val releaseName = latest.extractJsonString("name") ?: tagName
        val releaseBody = latest.extractJsonString("body") ?: ""
        val createdAtMillis = latest.extractJsonString("created_at")
            ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?: return null
        val assets = latest.extractArrayObjects("assets")
        val asset = assets.asSequence()
            .mapNotNull { asset ->
                val name = asset.extractJsonString("name") ?: return@mapNotNull null
                val url = asset.extractJsonString("browser_download_url") ?: return@mapNotNull null
                if (!name.contains("android", ignoreCase = true) && !name.endsWith(".apk", ignoreCase = true)) return@mapNotNull null
                ReleaseAsset(name = name, url = url)
            }
            .firstOrNull() ?: return null
        return ReleaseInfo(
            tagName = tagName,
            releaseName = releaseName,
            body = releaseBody,
            releaseCreatedAtMillis = createdAtMillis,
            assetName = asset.name,
            assetUrl = asset.url,
        )
    }

    private fun String.extractJsonString(key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        return regex.find(this)?.groupValues?.getOrNull(1)?.replace("\\\"", "\"")?.replace("\\\\", "\\")
    }

    private fun String.extractArrayObjects(key: String): List<String> {
        val marker = "\"$key\":"
        val start = indexOf(marker)
        if (start < 0) return emptyList()
        val arrayStart = indexOf('[', start)
        if (arrayStart < 0) return emptyList()
        var depth = 0
        var currentStart = -1
        val objects = mutableListOf<String>()
        for (i in arrayStart until length) {
            when (this[i]) {
                '{' -> {
                    if (depth == 0) currentStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && currentStart >= 0) {
                        objects += substring(currentStart, i + 1)
                        currentStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }
        return objects
    }

    private fun String.extractFirstArrayObject(): String? {
        val objects = extractArrayObjects("")
        return objects.firstOrNull()
    }

    private fun String.extractArrayObjects(key: String): List<String> {
        val marker = if (key.isBlank()) "[" else "\"$key\":"
        val start = indexOf(marker)
        if (start < 0) return emptyList()
        val arrayStart = if (key.isBlank()) start else indexOf('[', start)
        if (arrayStart < 0) return emptyList()
        var depth = 0
        var currentStart = -1
        val objects = mutableListOf<String>()
        for (i in arrayStart until length) {
            when (this[i]) {
                '{' -> {
                    if (depth == 0) currentStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && currentStart >= 0) {
                        objects += substring(currentStart, i + 1)
                        currentStart = -1
                    }
                }
                ']' -> if (depth == 0) break
            }
        }
        return objects
    }
    private data class ReleaseInfo(
        val tagName: String,
        val releaseName: String,
        val body: String,
        val releaseCreatedAtMillis: Long,
        val assetName: String,
        val assetUrl: String,
    )

    private data class ReleaseAsset(val name: String, val url: String)

    private companion object {
        const val PROJECT_LUMEN_RELEASES_API = "https://api.github.com/repos/Chloemlla/Project-Lumen/releases"
    }
}
