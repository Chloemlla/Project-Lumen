package com.projectlumen.app.core.api

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.projectlumen.app.core.security.ProjectLumenRequestSigner
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

class ProjectLumenApiClient(
    private val baseUrl: String = ProjectLumenApiConfig.baseUrl,
    private val httpClient: OkHttpClient = SecureOkHttpFactory.create(
        baseUrl = ProjectLumenApiConfig.normalizeApiBaseUrl(baseUrl),
        certificatePins = ProjectLumenApiConfig.apiCertificatePins,
    ),
) {
    private val resolvedBaseUrl = ProjectLumenApiConfig.normalizeApiBaseUrl(baseUrl)

    suspend fun health(): ApiHealth = request(
        method = "GET",
        path = "health",
    ) { it.toApiHealth() }

    suspend fun startEmailLogin(email: String): EmailLoginStart = request(
        method = "POST",
        path = "v1/auth/email/start",
        body = JSONObject().put("email", email),
    ) { it.toEmailLoginStart() }

    suspend fun verifyEmailLogin(
        email: String,
        requestId: String,
        code: String,
        deviceInstallationId: String,
    ): AuthSession = request(
        method = "POST",
        path = "v1/auth/email/verify",
        body = JSONObject()
            .put("email", email)
            .put("requestId", requestId)
            .put("code", code)
            .put("deviceInstallationId", deviceInstallationId),
    ) { it.toAuthSession() }

    suspend fun refreshSession(
        refreshToken: String,
        deviceInstallationId: String,
    ): AuthSession = request(
        method = "POST",
        path = "v1/auth/session/refresh",
        body = JSONObject()
            .put("refreshToken", refreshToken)
            .put("deviceInstallationId", deviceInstallationId),
    ) { it.toAuthSession() }

    suspend fun fetchMe(accessToken: String): ProjectLumenApiUser = request(
        method = "GET",
        path = "v1/me",
        accessToken = accessToken,
    ) { it.getJSONObject("user").toApiUser() }

    suspend fun registerDevice(
        accessToken: String,
        deviceInstallationId: String,
        deviceFingerprint: String,
        model: String,
        versionCode: Long,
        localSecurityConfig: String,
    ): RemoteDeviceRegistrationResult = request(
        method = "POST",
        path = "v1/devices/register",
        accessToken = accessToken,
        body = JSONObject()
            .put("deviceInstallationId", deviceInstallationId)
            .put("deviceFingerprint", deviceFingerprint)
            .put("model", model)
            .put("versionCode", versionCode)
            .put("localSecurityConfig", localSecurityConfig),
    ) { it.toRemoteDeviceRegistrationResult() }

    suspend fun fetchEntitlements(accessToken: String): RemoteEntitlementSnapshot = request(
        method = "GET",
        path = "v1/entitlements",
        accessToken = accessToken,
    ) { it.toEntitlementSnapshot() }

    suspend fun fetchFeatureFlags(accessToken: String): RemoteFeatureFlagSnapshot = request(
        method = "GET",
        path = "v1/config/feature-flags",
        accessToken = accessToken,
    ) { it.toRemoteFeatureFlagSnapshot() }

    suspend fun fetchConfigSync(
        cursor: Long = 0L,
        version: Long = 1L,
        channel: String = "stable",
    ): RemoteConfigSyncSnapshot = request(
        method = "GET",
        path = "v1/config/sync?cursor=$cursor&version=$version&channel=${queryEncode(channel)}",
    ) { it.toRemoteConfigSyncSnapshot() }

    suspend fun checkRemoteRelease(
        currentVersionCode: Long,
        abi: String = "universal",
        channel: String = "stable",
        rolloutKey: String = "",
        accessToken: String? = null,
    ): RemoteReleaseCheck = request(
        method = "GET",
        path = buildString {
            append("v1/releases/check?currentVersionCode=")
            append(currentVersionCode)
            append("&abi=")
            append(queryEncode(abi))
            append("&channel=")
            append(queryEncode(channel))
            if (rolloutKey.isNotBlank()) {
                append("&rolloutKey=")
                append(queryEncode(rolloutKey))
            }
        },
        accessToken = accessToken,
    ) { it.toRemoteReleaseCheck() }

    suspend fun verifyGooglePurchase(
        accessToken: String,
        productId: String,
        purchaseToken: String,
        deviceInstallationId: String,
    ): RemotePurchaseVerification = request(
        method = "POST",
        path = "v1/purchases/google/verify",
        accessToken = accessToken,
        body = JSONObject()
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
            .put("deviceInstallationId", deviceInstallationId),
    ) { it.toRemotePurchaseVerification() }

    suspend fun fetchSyncChanges(
        accessToken: String,
        sinceCursor: Long = 0L,
    ): RemoteSyncChangesPage = request(
        method = "GET",
        path = "v1/sync/changes?since=$sinceCursor",
        accessToken = accessToken,
    ) { it.toSyncChangesPage() }

    suspend fun pushSyncChanges(
        accessToken: String,
        deviceInstallationId: String,
        cursor: Long = 0L,
        changes: List<RemoteSyncChange>,
    ): RemoteSyncPushResult = request(
        method = "POST",
        path = "v1/sync/push",
        accessToken = accessToken,
        body = JSONObject()
            .put("deviceInstallationId", deviceInstallationId)
            .put("cursor", cursor)
            .put("changes", JSONArray(changes.map { it.toJson() })),
    ) { it.toSyncPushResult() }

    suspend fun uploadTelemetry(
        accessToken: String,
        upload: RemoteTelemetryUpload,
    ): RemoteTelemetryUploadResult = request(
        method = "POST",
        path = "v1/telemetry",
        accessToken = accessToken,
        body = upload.toJson(),
    ) { it.toTelemetryUploadResult() }

    suspend fun uploadFaceAnalysisFrame(
        accessToken: String,
        upload: RemoteFaceAnalysisFrameUpload,
    ): RemoteFaceAnalysisFrameUploadResult = request(
        method = "POST",
        path = "v1/face-analysis/frames",
        accessToken = accessToken,
        body = upload.toJson(),
    ) { it.toFaceAnalysisFrameUploadResult() }

    suspend fun uploadBackup(
        accessToken: String,
        deviceInstallationId: String,
        backupJson: JSONObject,
    ): RemoteBackupMetadata = request(
        method = "POST",
        path = "v1/backups",
        accessToken = accessToken,
        body = JSONObject()
            .put("deviceInstallationId", deviceInstallationId)
            .put("schemaVersion", backupJson.optInt("schemaVersion", 1))
            .put("exportedAt", backupJson.optLong("exportedAt", System.currentTimeMillis()))
            .put("backup", backupJson),
    ) { it.toRemoteBackupMetadata() }

    suspend fun fetchLatestBackup(accessToken: String): RemoteBackup? = request(
        method = "GET",
        path = "v1/backups/latest",
        accessToken = accessToken,
    ) { it.optRemoteBackup() }

    private suspend fun <T> request(
        method: String,
        path: String,
        body: JSONObject? = null,
        accessToken: String? = null,
        parse: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = resolveUrl(path)
        val bodyText = body?.toString()
        val requestBody = bodyText?.toRequestBody(JSON_MEDIA_TYPE)
        val startedAtMillis = System.currentTimeMillis()
        val startedAtElapsed = SystemClock.elapsedRealtime()
        val requestBuilder = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
        accessToken
            ?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Authorization", "Bearer $it") }
        ProjectLumenRequestSigner.headers(method, url, bodyText)
            .forEach { (name, value) -> requestBuilder.header(name, value) }
        val request = requestBuilder.build()
        var traceRecorded = false

        fun recordTrace(
            statusCode: Int? = null,
            responseText: String? = null,
            error: Throwable? = null,
            errorMessage: String = "",
        ) {
            traceRecorded = true
            ProjectLumenApiDiagnostics.record(
                startedAtMillis = startedAtMillis,
                method = method,
                url = url.toString(),
                path = url.encodedPath + url.encodedQuery.orEmpty().let { query ->
                    if (query.isBlank()) "" else "?$query"
                },
                signed = true,
                authorizationAttached = !accessToken.isNullOrBlank(),
                requestBodyText = bodyText,
                statusCode = statusCode,
                durationMillis = SystemClock.elapsedRealtime() - startedAtElapsed,
                responseBodyText = responseText,
                error = error,
                errorMessage = errorMessage,
            )
        }

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseText = readResponseText(response)
                if (response.code !in 200..299) {
                    val message = parseErrorMessage(responseText, response.code)
                    recordTrace(
                        statusCode = response.code,
                        responseText = responseText,
                        errorMessage = message,
                    )
                    throw ProjectLumenApiException(response.code, message)
                }
                try {
                    parse(responseText.toJsonObject()).also {
                        recordTrace(statusCode = response.code, responseText = responseText)
                    }
                } catch (error: Throwable) {
                    recordTrace(
                        statusCode = response.code,
                        responseText = responseText,
                        error = error,
                    )
                    throw error
                }
            }
        } catch (error: Throwable) {
            if (!traceRecorded) {
                recordTrace(error = error)
            }
            throw error
        }
    }

    private fun resolveUrl(path: String) =
        "${resolvedBaseUrl.trimEnd('/')}/${path.trimStart('/')}".toHttpUrl()

    private fun queryEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private fun readResponseText(response: Response): String {
        return response.body?.string().orEmpty()
    }

    private fun parseErrorMessage(responseText: String, responseCode: Int): String {
        val json = runCatching { JSONObject(responseText) }.getOrNull()
        val error = json?.optJSONObject("error")
        return error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "Project Lumen API request failed with HTTP $responseCode"
    }

    private fun String.toJsonObject(): JSONObject {
        if (isBlank()) return JSONObject()
        return runCatching { JSONObject(this) }
            .getOrElse { throw IOException("Project Lumen API returned invalid JSON.") }
    }

    private companion object {
        private const val USER_AGENT = "Project-Lumen-Android"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
