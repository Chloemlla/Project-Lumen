package com.projectlumen.app.core.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class UpdateInstaller(private val context: Context) {
    suspend fun downloadApk(
        asset: ReleaseAsset,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val expectedSha256 = asset.sha256?.trim()?.lowercase()
        if (expectedSha256.isNullOrBlank()) {
            throw IOException("APK SHA256 checksum is missing for ${asset.name}.")
        }
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
            val actualSha256 = targetFile.sha256()
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                targetFile.delete()
                throw IOException("APK SHA256 mismatch for ${asset.name}. Expected $expectedSha256 but got $actualSha256.")
            }
            targetFile
        } finally {
            connection.disconnect()
        }
    }

    fun canInstallPackages(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createInstallPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
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

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHexString()
    }

    private fun ByteArray.toHexString(): String {
        return buildString(size * 2) {
            for (byte in this@toHexString) {
                val value = byte.toInt() and 0xff
                append(HEX_CHARS[value ushr 4])
                append(HEX_CHARS[value and 0x0f])
            }
        }
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
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
