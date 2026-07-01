package com.projectlumen.app.core.api

import android.content.Context
import com.projectlumen.app.core.security.ProjectLumenRequestSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

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
    context: Context,
    private val baseUrl: String = ProjectLumenApiConfig.translationBaseUrl,
    private val httpClient: OkHttpClient = SecureOkHttpFactory.create(
        baseUrl = baseUrl,
        certificatePins = ProjectLumenApiConfig.translationCertificatePins,
        requireCertificatePins = false,
    ),
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
        val url = resolveUrl(path)
        val bodyText = body?.toString()
        val requestBody = bodyText?.toRequestBody(JSON_MEDIA_TYPE)
        val requestBuilder = Request.Builder()
            .url(url)
            .method(method, requestBody)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
        ProjectLumenRequestSigner.headers(method, url, bodyText)
            .forEach { (name, value) -> requestBuilder.header(name, value) }

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

    private companion object {
        private const val USER_AGENT = "Project-Lumen-Android"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
