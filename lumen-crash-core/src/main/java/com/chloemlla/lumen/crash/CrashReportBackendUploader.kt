package com.chloemlla.lumen.crash

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/**
 * Uploads a complete [CrashReport] to the dedicated Lumen crash-report backend endpoint.
 *
 * This is a best-effort background upload: it never throws and never calls Process.kill.
 * The returned [CrashUploadOutcome] tells the caller whether another attempt is worth making.
 * The host supplies authentication and device identity through [LumenCrashConfig] so the SDK
 * remains generic for hosts without a Lumen backend.
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
     * @return Whether the report was stored, permanently refused, or worth retrying.
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
    ): CrashUploadOutcome {
        return try {
            AuthorIntegrity.verifyOrThrow("backend-upload")

            val endpoint = normalizeBaseUrl(baseUrl) + LumenCrashDefaults.DEFAULT_CRASH_BACKEND_ENDPOINT_PATH
            val body = buildRequestBody(report, deviceInstallationId, packageName, versionCode)
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
                val responseText = readBody(
                    if (status in 200..299) connection.inputStream else connection.errorStream,
                )
                classifyResponse(status, responseText)
            } finally {
                runCatching { connection.disconnect() }
            }
        } catch (_: IOException) {
            // Network-level failure: the report is still worth uploading on a later launch.
            CrashUploadOutcome.RETRYABLE
        } catch (_: Throwable) {
            // Integrity, configuration (non-HTTPS base URL), or serialization failure:
            // retrying the same report against the same config cannot succeed.
            CrashUploadOutcome.REJECTED
        }
    }

    /**
     * Builds the ingest payload.
     *
     * The backend persists [CrashReport.systemInfo] but has no column for `exitReason`, so a
     * PRIOR_EXIT report's kill reason would never reach the crash dashboard. Fold it into the
     * uploaded system info instead of mutating the locally stored report.
     */
    internal fun buildRequestBody(
        report: CrashReport,
        deviceInstallationId: String,
        packageName: String,
        versionCode: Int,
    ): JSONObject = report.toJson().apply {
        put("deviceInstallationId", deviceInstallationId)
        put("packageName", packageName)
        put("versionCode", versionCode)
        val exitReason = report.exitReason?.trim()
        if (!exitReason.isNullOrEmpty()) {
            put("systemInfo", "Exit reason: $exitReason\n${report.systemInfo}")
        }
    }

    /**
     * Maps an ingest response onto [CrashUploadOutcome].
     *
     * The route answers `HTTP 200 {"accepted": false}` when its own persistence fails, and
     * `HTTP 429` once the per-device hourly quota is spent. Both deserve another attempt,
     * while a rejected payload (`HTTP 400`) does not.
     */
    internal fun classifyResponse(status: Int, responseText: String): CrashUploadOutcome {
        if (status in 200..299) {
            val accepted = runCatching { JSONObject(responseText).optBoolean("accepted", true) }
                .getOrDefault(true)
            return if (accepted) CrashUploadOutcome.ACCEPTED else CrashUploadOutcome.RETRYABLE
        }
        return when {
            status == 408 || status == 429 -> CrashUploadOutcome.RETRYABLE
            status in 500..599 -> CrashUploadOutcome.RETRYABLE
            status in 400..499 -> CrashUploadOutcome.REJECTED
            else -> CrashUploadOutcome.RETRYABLE
        }
    }

    internal fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Crash backend base URL must use HTTPS."
        }
        return trimmed
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        return runCatching {
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
                reader.readText()
            }
        }.getOrDefault("")
    }
}
