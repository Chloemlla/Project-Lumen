package com.projectlumen.app.core.api

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import com.projectlumen.app.core.security.ProjectLumenIntegrityProvider
import com.projectlumen.app.core.security.ProjectLumenRequestSigner
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ProjectLumenApiClient(
    context: Context,
    private val baseUrl: String = ProjectLumenApiConfig.baseUrl,
    private val httpClient: OkHttpClient = SecureOkHttpFactory.create(
        baseUrl = baseUrl,
        certificatePins = ProjectLumenApiConfig.apiCertificatePins,
    ),
    private val integrityProvider: ProjectLumenIntegrityProvider = ProjectLumenIntegrityProvider(context),
) {
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

    suspend fun fetchEntitlements(accessToken: String): RemoteEntitlementSnapshot = request(
        method = "GET",
        path = "v1/entitlements",
        accessToken = accessToken,
    ) { it.toEntitlementSnapshot() }

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
        if (requiresIntegrityToken(path)) {
            integrityProvider.tokenForRequest(path, bodyText)
                ?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header(ProjectLumenRequestSigner.HEADER_INTEGRITY, it) }
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseText = readResponseText(response)
            if (response.code !in 200..299) {
                throw ProjectLumenApiException(response.code, parseErrorMessage(responseText, response.code))
            }
            parse(responseText.toJsonObject())
        }
    }

    private fun resolveUrl(path: String) = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}".toHttpUrl()

    private fun readResponseText(response: Response): String {
        return response.body?.string().orEmpty()
    }

    private fun requiresIntegrityToken(path: String): Boolean {
        val normalizedPath = path.substringBefore('?').trimStart('/')
        return normalizedPath.startsWith("v1/auth/") ||
            normalizedPath == "v1/purchases/google/verify"
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
