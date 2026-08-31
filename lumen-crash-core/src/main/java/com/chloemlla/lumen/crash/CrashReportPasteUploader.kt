package com.chloemlla.lumen.crash

import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

private val pasteIdRegex = Regex("^[A-Za-z0-9_-]+$")

/**
 * Uploads crash-report text to a LogPaste-compatible endpoint and returns a shareable URL.
 *
 * Default host is https://paste.gentoo.zip (MIT LogPaste).
 * Protocol: multipart form field `_` with the report body, same as:
 * `curl -F '_=<-' https://paste.gentoo.zip`
 */
object CrashReportPasteUploader {
    const val DEFAULT_BASE_URL: String = "https://paste.gentoo.zip"

    /**
     * Optional host hook: when it returns true, open connections with
     * [Proxy.NO_PROXY] so a VPN process-binding path does not also stack the
     * JVM/system HTTP proxy. Set from the host app when its current network
     * environment requires a direct connection; defaults to null / no skip.
     */
    @Volatile
    var shouldSkipManualProxy: (() -> Boolean)? = null

    fun uploadText(
        text: String,
        baseUrl: String = DEFAULT_BASE_URL,
        connectTimeoutMillis: Int = 15_000,
        readTimeoutMillis: Int = 30_000,
    ): String {
        // Throw only checked-style failures for callers to catch; never call Process.kill/exit.
        return try {
            AuthorIntegrity.verifyOrThrow("paste-upload")
            val payload = text.trim()
            require(payload.isNotEmpty()) { "Crash report text is empty." }

            val endpoint = normalizeBaseUrl(baseUrl)
            val boundary = "----LumenCrashPasteBoundary${UUID.randomUUID().toString().replace("-", "")}"
            val bodyBytes = buildMultipartBody(boundary = boundary, fieldName = "_", value = payload)
                .toByteArray(StandardCharsets.UTF_8)

            val first = post(endpoint, bodyBytes, boundary, connectTimeoutMillis, readTimeoutMillis)
            val redirectLocation = first.redirectLocation
            val response = if (redirectLocation == null) {
                first
            } else {
                // Followed manually and at most once: the platform HttpURLConnection follows an
                // https -> http downgrade by default, which would resend the report in clear text.
                val redirected = resolveHttpsRedirect(endpoint, redirectLocation)
                    ?: throw IOException("Paste upload refused a non-HTTPS redirect.")
                post(redirected, bodyBytes, boundary, connectTimeoutMillis, readTimeoutMillis)
            }

            if (response.status !in 200..299) {
                throw IOException(
                    "Paste upload failed with HTTP ${response.status}: ${response.body.take(200)}",
                )
            }
            resolveShareableUrl(endpoint, response.body)
        } catch (error: Throwable) {
            // Normalize all failures (including Error subclasses from flaky runtimes) so UI
            // callers can treat paste upload as non-fatal best-effort work.
            if (error is IOException) throw error
            throw IOException("Paste upload failed: ${error.message ?: error::class.java.simpleName}", error)
        }
    }

    private class HttpAttempt(
        val status: Int,
        val body: String,
        val redirectLocation: String?,
    )

    private fun post(
        endpoint: String,
        bodyBytes: ByteArray,
        boundary: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int,
    ): HttpAttempt {
        val url = URL(endpoint)
        val forceDirect = runCatching { shouldSkipManualProxy?.invoke() == true }.getOrDefault(false)
        val rawConnection =
            if (forceDirect) {
                // Clash VPN path: process is bound to VPN; never stack system/app proxy.
                url.openConnection(Proxy.NO_PROXY)
            } else {
                url.openConnection()
            }
        val connection = (rawConnection as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = connectTimeoutMillis.coerceAtLeast(1_000)
            readTimeout = readTimeoutMillis.coerceAtLeast(1_000)
            instanceFollowRedirects = false
            setRequestProperty("Accept", "text/plain, */*")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty(
                "Content-Length",
                bodyBytes.size.toString(),
            )
            setRequestProperty("User-Agent", "lumen-crash-sdk")
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(bodyBytes)
                stream.flush()
            }

            val status = connection.responseCode
            val responseText = readBody(
                if (status in 200..299) connection.inputStream else connection.errorStream,
            ).trim()
            val location = if (status in 300..399) {
                connection.getHeaderField("Location")?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            return HttpAttempt(status, responseText, location)
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun resolveHttpsRedirect(currentUrl: String, location: String): String? {
        val resolved = runCatching { URL(URL(currentUrl), location).toString() }.getOrNull() ?: return null
        return resolved.takeIf { it.startsWith("https://", ignoreCase = true) }
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Paste upload base URL must use HTTPS."
        }
        return trimmed
    }

    internal fun resolveShareableUrl(baseUrl: String, responseText: String): String {
        val body = responseText.trim()
        require(body.isNotEmpty()) { "Paste upload returned an empty response." }

        // Some deployments return the full URL; others return only the paste id.
        if (body.startsWith("https://", ignoreCase = true) || body.startsWith("http://", ignoreCase = true)) {
            val firstToken = body.lineSequence().first().trim()
            require(firstToken.startsWith("https://", ignoreCase = true)) {
                "Paste upload returned a non-HTTPS URL."
            }
            return firstToken.trimEnd('/')
        }

        val id = body.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?.trim('/')
            ?: throw IOException("Paste upload response did not contain a paste id.")

        require(id.matches(pasteIdRegex)) {
            "Paste upload returned an unexpected id: ${id.take(64)}"
        }
        return "${normalizeBaseUrl(baseUrl)}/$id"
    }

    private fun buildMultipartBody(boundary: String, fieldName: String, value: String): String {
        return buildString(value.length + boundary.length * 2 + 160) {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
            append(value).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
    }

    private fun readBody(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(READ_CHUNK_CHARS)
            val text = StringBuilder()
            // The response only carries a paste id or URL, so a larger body is never useful.
            while (text.length < MAX_RESPONSE_CHARS) {
                val limit = minOf(buffer.size, MAX_RESPONSE_CHARS - text.length)
                val read = reader.read(buffer, 0, limit)
                if (read <= 0) break
                text.append(buffer, 0, read)
            }
            text.toString()
        }
    }

    private const val MAX_RESPONSE_CHARS = 64 * 1024
    private const val READ_CHUNK_CHARS = 4 * 1024
}
