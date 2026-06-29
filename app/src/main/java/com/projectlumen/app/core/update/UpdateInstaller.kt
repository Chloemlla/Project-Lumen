package com.projectlumen.app.core.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class UpdateInstaller(private val context: Context) {
    suspend fun downloadApk(
        asset: ReleaseAsset,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val targetFile = File(context.cacheDir, buildCacheFileName(asset.name))
        val connection = openHttpConnection(asset.downloadUrl).apply {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("APK download failed with HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            connection.inputStream.buffered().use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        onProgress?.invoke(downloadedBytes, totalBytes)
                    }
                }
            }
            targetFile
        } finally {
            connection.disconnect()
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            true
        } else {
            context.packageManager.canRequestPackageInstalls()
        }
    }

    fun createInstallPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        context.startActivity(intent)
    }

    private fun buildCacheFileName(assetName: String): String {
        val baseName = assetName.substringAfterLast('/').ifBlank { "project_lumen_update.apk" }
        return baseName.replace(UNSAFE_FILE_CHARS, "_")
    }

    private fun openHttpConnection(url: String): HttpURLConnection {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
            ?: return URL(url).openConnection() as HttpURLConnection
        return network.openConnection(URL(url)) as HttpURLConnection
    }

    private companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 30_000
        private const val USER_AGENT = "Project-Lumen"
        private val UNSAFE_FILE_CHARS = Regex("""[^A-Za-z0-9._-]""")
    }
}
