package com.projectlumen.app.core.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.provider.Settings
import androidx.core.content.FileProvider
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.core.share.SecureShareIntents
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
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
        asset.sizeBytes?.let { declaredBytes ->
            if (declaredBytes > MAX_APK_BYTES) {
                throw IOException("${asset.name} is larger than the ${MAX_APK_BYTES / BYTES_PER_MB} MB download limit.")
            }
            if (context.cacheDir.usableSpace < declaredBytes * 2) {
                throw IOException("Not enough free space to download ${asset.name}.")
            }
        }
        val targetFile = File(context.cacheDir, buildCacheFileName(asset.name))
        pruneStaleDownloads(targetFile)
        val connection = UpdateEndpointPolicy.open(context, asset.downloadUrl) {
            requestMethod = "GET"
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/octet-stream")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("APK download failed with HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            if (totalBytes != null && totalBytes > MAX_APK_BYTES) {
                throw IOException("${asset.name} is larger than the ${MAX_APK_BYTES / BYTES_PER_MB} MB download limit.")
            }
            writeDownload(connection, targetFile, totalBytes, onProgress)
            val actualSha256 = targetFile.sha256()
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("APK SHA256 mismatch for ${asset.name}. Expected $expectedSha256 but got $actualSha256.")
            }
            verifyApkTrust(targetFile, asset.name)
            targetFile
        } catch (error: Throwable) {
            targetFile.delete()
            throw error
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
        context.startActivity(SecureShareIntents.viewApk(context, uri))
    }

    private suspend fun writeDownload(
        connection: HttpURLConnection,
        targetFile: File,
        totalBytes: Long?,
        onProgress: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)?,
    ) {
        var downloadedBytes = 0L
        var reportedBytes = 0L
        var reportedAtMillis = 0L
        connection.inputStream.buffered().use { input ->
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (downloadedBytes > MAX_APK_BYTES) {
                        throw IOException("APK download exceeded the ${MAX_APK_BYTES / BYTES_PER_MB} MB limit.")
                    }
                    val nowMillis = System.currentTimeMillis()
                    val progressed = downloadedBytes - reportedBytes >= PROGRESS_BYTES_STEP ||
                        nowMillis - reportedAtMillis >= PROGRESS_INTERVAL_MILLIS
                    if (progressed) {
                        reportedBytes = downloadedBytes
                        reportedAtMillis = nowMillis
                        onProgress?.invoke(downloadedBytes, totalBytes)
                    }
                }
            }
        }
        onProgress?.invoke(downloadedBytes, totalBytes)
    }

    /**
     * The download URL and its SHA-256 come from the same response, so the hash only
     * proves the transfer was complete. Trust comes from the signing certificate.
     */
    private fun verifyApkTrust(targetFile: File, assetName: String) {
        val packageManager = context.packageManager
        val archive = packageManager
            .getPackageArchiveInfo(targetFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("$assetName is not a readable Android package.")
        if (archive.packageName != context.packageName) {
            throw IOException("$assetName declares package ${archive.packageName}, expected ${context.packageName}.")
        }
        if (archive.longVersionCode < BuildConfig.VERSION_CODE.toLong()) {
            throw IOException("$assetName is older than the installed build (${BuildConfig.VERSION_CODE}).")
        }
        val downloadedSigners = archive.signingInfo?.apkContentsSigners.signatureDigests()
        if (downloadedSigners.isEmpty()) {
            throw IOException("$assetName has no readable signing certificate.")
        }
        val installed = packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
        val trustedSigners = installed?.apkContentsSigners.signatureDigests() +
            installed?.signingCertificateHistory.signatureDigests()
        if (trustedSigners.isEmpty() || downloadedSigners.none { it in trustedSigners }) {
            throw IOException("$assetName is not signed with this app's certificate.")
        }
    }

    private fun Array<Signature>?.signatureDigests(): Set<String> {
        if (this == null) return emptySet()
        return mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHexString()
        }
    }

    private fun pruneStaleDownloads(targetFile: File) {
        context.cacheDir
            .listFiles { file -> file.isFile && file.name.endsWith(".apk", ignoreCase = true) }
            ?.forEach { stale ->
                if (stale.absolutePath != targetFile.absolutePath) stale.delete()
            }
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

    private companion object {
        private const val REQUEST_TIMEOUT_MILLIS = 30_000
        private const val BYTES_PER_MB = 1024L * 1024L
        private const val MAX_APK_BYTES = 512L * 1024L * 1024L
        private const val PROGRESS_BYTES_STEP = 256L * 1024L
        private const val PROGRESS_INTERVAL_MILLIS = 200L
        private val UNSAFE_FILE_CHARS = Regex("""[^A-Za-z0-9._-]""")
        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
