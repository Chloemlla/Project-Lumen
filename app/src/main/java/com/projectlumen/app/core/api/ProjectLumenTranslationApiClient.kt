package com.projectlumen.app.core.api

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class TranslationConfig(
    val enabled: Boolean,
    val requiresApiKey: Boolean,
    val baseUrl: String,
    val endpointPath: String,
)

data class TranslationResult(
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val alternatives: List<String>,
)

class ProjectLumenTranslationApiClient(
    private val context: Context,
    private val baseUrl: String = ProjectLumenApiConfig.translationBaseUrl,
) {
    suspend fun fetchConfig(): TranslationConfig = request(
        method = "GET",
        path = "api/public/deeplx/config",
    ) { json ->
        TranslationConfig(
            enabled = json.optBoolean("enabled", false),
            requiresApiKey = json.optBoolean("requiresApiKey", false),
            baseUrl = json.optString("baseUrl"),
            endpointPath = json.optString("endpointPath"),
        )
    }

    suspend fun translate(
        text: String,
        targetLang: String,
        sourceLang: String = "auto",
    ): TranslationResult = request(
        method = "POST",
        path = "api/public/deeplx/translate",
        body = JSONObject()
            .put("text", text.trim())
            .put("sourceLang", sourceLang)
            .put("targetLang", targetLang),
    ) { json ->
        val alternativesJson = json.optJSONArray("alternatives")
        val alternatives = buildList {
            if (alternativesJson != null) {
                for (index in 0 until alternativesJson.length()) {
                    alternativesJson.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        TranslationResult(
            translatedText = json.getString("translatedText"),
            sourceLang = json.optString("sourceLang", sourceLang),
            targetLang = json.optString("targetLang", targetLang),
            alternatives = alternatives,
        )
    }

    private suspend fun <T> request(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONObject) -> T,
    ): T = withContext(Dispatchers.IO) {
        val connection = openHttpConnection(resolveUrl(path)).apply {
            requestMethod = method
            connectTimeout = ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS
            readTimeout = ProjectLumenApiConfig.REQUEST_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
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
        val errorObject = json?.optJSONObject("error")
        val message = errorObject?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json?.optString("error")?.takeIf { it.isNotBlank() }
            ?: json?.optString("message")?.takeIf { it.isNotBlank() }
        return message ?: when (responseCode) {
            429 -> "Translation request limit reached."
            503 -> "Translation service is not configured."
            else -> "Translation failed with HTTP $responseCode."
        }
    }

    private fun String.toJsonObject(): JSONObject {
        if (isBlank()) return JSONObject()
        return runCatching { JSONObject(this) }
            .getOrElse { throw IOException("Translation API returned invalid JSON.") }
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
