package com.projectlumen.app

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.core.crash.CrashReportStore
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.TimerForegroundService
import java.net.HttpURLConnection
import java.net.URL

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
            val update = runCatching { fetchLatestReleaseAsset() }.getOrNull() ?: return@Thread
            val currentVersion = currentVersionName()
            if (update.tagName.isNotBlank() && compareVersions(update.tagName, currentVersion) > 0) {
                notifications.showUpdateAvailable(update.tagName, update.apkName, update.apkUrl)
            }
        }.start()
    }

    private fun fetchLatestReleaseAsset(): ReleaseAssetInfo? {
        val body = URL(PROJECT_LUMEN_RELEASE_API)
            .openConnection()
            .let { it as HttpURLConnection }
            .run {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Project-Lumen")
                inputStream.bufferedReader().use { it.readText() }
            }

        val assets = body.extractArrayObjects("assets")
        val apkAssets = assets.filter { it.extractJsonString("name")?.endsWith(".apk", ignoreCase = true) == true }
        if (apkAssets.size != 1) return null
        val asset = apkAssets.single()
        return ReleaseAssetInfo(
            tagName = body.extractJsonString("tag_name") ?: return null,
            apkName = asset.extractJsonString("name") ?: return null,
            apkUrl = asset.extractJsonString("browser_download_url") ?: return null,
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

    private fun currentVersionName(): String {
        return try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName ?: BuildConfig.VERSION_NAME
        } catch (_: PackageManager.NameNotFoundException) {
            BuildConfig.VERSION_NAME
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val leftParts = normalizeVersion(left)
        val rightParts = normalizeVersion(right)
        val size = maxOf(leftParts.size, rightParts.size)
        for (i in 0 until size) {
            val l = leftParts.getOrElse(i) { 0 }
            val r = rightParts.getOrElse(i) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private fun normalizeVersion(version: String): List<Int> {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        return cleaned.split('.', '-', '_')
            .mapNotNull { part -> part.toIntOrNull() }
            .ifEmpty { listOf(0) }
    }

    private data class ReleaseAssetInfo(val tagName: String, val apkName: String, val apkUrl: String)

    private companion object {
        const val PROJECT_LUMEN_RELEASE_API = "https://api.github.com/repos/Chloemlla/Project-Lumen/releases/latest"
    }
}
