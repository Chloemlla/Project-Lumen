package com.chloemlla.lumen.crash

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Uploads a complete [CrashReport] to the dedicated Lumen crash-report backend endpoint.
 *
 * This is a best-effort background upload — it never throws, never calls Process.kill,
 * and returns `true` only when the server responds with HTTP 2xx. The host supplies
 * authentication and device identity through [LumenCrashConfig] so the SDK remains
 * generic for hosts without a Lumen backend.
 */
object CrashReportBackendUploader {

    /**
     * Uploads [report] to `{baseUrl}${LumenCrashDefaults.DEFAULT_CRASH_BACKEND_ENDPOINT_PATH}`.
     *
     * @param report The full crash report.
     * @param deviceInstallationId Stable device fingerprint / install ID.
     * @param packageName Android package name of the host app.
     * @param versionCode Host app version code.
     * @param accessToken Optional Bearer token for the endpoint. When null/blank the report is sent anonymously.
     * @param baseUrl HTTPS base URL (trailing slash stripped automatically).
     * @param connectTimeoutMillis Connection timeout in ms (default 15 s).
     * @param readTimeoutMillis Read timeout in ms (default 30 s).
     * @return `true` on HTTP 2xx, `false` on any failure.
     */
    fun upload(
        report: CrashReport,
        deviceInstallationId: String,
        packageName: String,
        versionCode: Int,
        accessToken: String?,
        baseUrl: String,
        connectTimeoutMillis: Int = 15_000,
        readTimeoutMillis: Int = 30_000,
    ): Boolean {
        return try {
            AuthorIntegrity.verifyOrThrow("backend-upload")

            val endpoint = normalizeBaseUrl(baseUrl) + LumenCrashDefaults.DEFAULT_CRASH_BACKEND_ENDPOINT_PATH
            val body = report.toJson().apply {
                put("deviceInstallationId", deviceInstallationId)
                put("packageName", packageName)
                put("versionCode", versionCode)
            }
            val bodyBytes = body.toString().toByteArray(StandardCharsets.UTF_8)

            val url = URL(endpoint)
            val rawConnection = url.openConnection()
            val effectiveToken = accessToken?.trim().orEmpty()
            val connection = (rawConnection as HttpURLConnection).apply {
                requestMethod = "POST"
                doInput = true
                doOutput = true
                useCaches = false
                connectTimeout = connectTimeoutMillis.coerceAtLeast(1_000)
                readTimeout = readTimeoutMillis.coerceAtLeast(1_000)
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json, */*")
                setRequestProperty("Content-Type", "application/json")
                if (effectiveToken.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $effectiveToken")
                }
                setRequestProperty("Content-Length", bodyBytes.size.toString())
                setRequestProperty("User-Agent", "lumen-crash-sdk")
            }

            try {
                connection.outputStream.use { stream ->
                    stream.write(bodyBytes)
                    stream.flush()
                }
                val status = connection.responseCode
                status in 200..299
            } finally {
                runCatching { connection.disconnect() }
            }
        } catch (_: Throwable) {
            // All failures (integrity, network, malformed JSON, etc.) are treated as
            // non-fatal best-effort — the crash-report UI is unaffected.
            false
        }
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Crash backend base URL must use HTTPS."
        }
        return trimmed
    }
}