package com.projectlumen.app.core.api

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ProjectLumenApiClient(
    private val context: Context,
    private val baseUrl: String = ProjectLumenApiConfig.baseUrl,
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
        val connection = openHttpConnection(resolveUrl(path)).apply {
            requestMethod = method
            connectTimeout = ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS
            readTimeout = ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
            accessToken
                ?.takeIf { it.isNotBlank() }
                ?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                }
            }
            val responseCode = connection.responseCode
            val responseText = readResponseText(connection, responseCode)
            if (responseCode !in 200..299) {
                throw ProjectLumenApiException(responseCode, parseErrorMessage(responseText, responseCode))
            }
            parse(responseText.toJsonObject())
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveUrl(path: String): String {
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    private fun readResponseText(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
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

    private fun openHttpConnection(url: String): HttpURLConnection {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivityManager?.activeNetwork
            ?: return URL(url).openConnection() as HttpURLConnection
        return network.openConnection(URL(url)) as HttpURLConnection
    }

    private companion object {
        private const val USER_AGENT = "Project-Lumen-Android"
    }
}
