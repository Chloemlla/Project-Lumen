package com.chloemlla.lumen.crash

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Uploads crash-report text to a LogPaste-compatible endpoint and returns a shareable URL.
 *
 * Default host is https://paste.gentoo.zip (MIT LogPaste).
 * Protocol: multipart form field `_` with the report body, same as:
 * `curl -F '_=<-' https://paste.gentoo.zip`
 */
object CrashReportPasteUploader {
    const val DEFAULT_BASE_URL: String = "https://paste.gentoo.zip"

    fun uploadText(
        text: String,
        baseUrl: String = DEFAULT_BASE_URL,
        connectTimeoutMillis: Int = 15_000,
        readTimeoutMillis: Int = 30_000,
    ): String {
        AuthorIntegrity.verifyOrThrow("paste-upload")
        val payload = text.trim()
        require(payload.isNotEmpty()) { "Crash report text is empty." }

        val endpoint = normalizeBaseUrl(baseUrl)
        val boundary = "----LumenCrashPasteBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val body = buildMultipartBody(boundary = boundary, fieldName = "_", value = payload)

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "text/plain, */*")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Content-Length", body.toByteArray(StandardCharsets.UTF_8).size.toString())
            setRequestProperty("User-Agent", "lumen-crash-sdk")
        }

        try {
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val status = connection.responseCode
            val responseText = readBody(
                if (status in 200..299) connection.inputStream else connection.errorStream,
            ).trim()

            if (status !in 200..299) {
                throw IOException("Paste upload failed with HTTP $status: ${responseText.take(200)}")
            }
            return resolveShareableUrl(endpoint, responseText)
        } finally {
            connection.disconnect()
        }
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

        require(id.matches(Regex("^[A-Za-z0-9_-]+$"))) {
            "Paste upload returned an unexpected id: ${id.take(64)}"
        }
        return "${normalizeBaseUrl(baseUrl)}/$id"
    }

    private fun buildMultipartBody(boundary: String, fieldName: String, value: String): String {
        return buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
            append(value).append("\r\n")
            append("--").append(boundary).append("--\r\n")
        }
    }

    private fun readBody(stream: java.io.InputStream?): String {
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}
