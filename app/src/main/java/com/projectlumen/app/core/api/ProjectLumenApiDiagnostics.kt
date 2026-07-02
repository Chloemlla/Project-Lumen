package com.projectlumen.app.core.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

data class ProjectLumenApiTrace(
    val id: Long,
    val startedAtMillis: Long,
    val method: String,
    val url: String,
    val path: String,
    val signed: Boolean,
    val integrityRequested: Boolean,
    val authorizationAttached: Boolean,
    val requestBodyPreview: String,
    val statusCode: Int?,
    val durationMillis: Long,
    val responseBodyPreview: String,
    val errorType: String,
    val errorMessage: String,
) {
    val successful: Boolean
        get() = statusCode != null && statusCode in 200..299 && errorMessage.isBlank()
}

object ProjectLumenApiDiagnostics {
    private val nextId = AtomicLong(0L)
    private val lock = Any()
    private val _traces = MutableStateFlow<List<ProjectLumenApiTrace>>(emptyList())

    val traces: StateFlow<List<ProjectLumenApiTrace>> = _traces.asStateFlow()

    fun clear() {
        synchronized(lock) {
            _traces.value = emptyList()
        }
    }

    fun record(
        startedAtMillis: Long,
        method: String,
        url: String,
        path: String,
        signed: Boolean,
        integrityRequested: Boolean,
        authorizationAttached: Boolean,
        requestBodyText: String?,
        statusCode: Int?,
        durationMillis: Long,
        responseBodyText: String?,
        error: Throwable? = null,
        errorMessage: String = "",
    ) {
        val trace = ProjectLumenApiTrace(
            id = nextId.incrementAndGet(),
            startedAtMillis = startedAtMillis,
            method = method.uppercase(),
            url = url,
            path = path,
            signed = signed,
            integrityRequested = integrityRequested,
            authorizationAttached = authorizationAttached,
            requestBodyPreview = previewBody(requestBodyText),
            statusCode = statusCode,
            durationMillis = durationMillis.coerceAtLeast(0L),
            responseBodyPreview = previewBody(responseBodyText),
            errorType = error?.javaClass?.simpleName.orEmpty(),
            errorMessage = errorMessage.ifBlank { error?.message.orEmpty() },
        )
        synchronized(lock) {
            _traces.value = (listOf(trace) + _traces.value).take(MAX_TRACES)
        }
    }

    private fun previewBody(text: String?): String {
        val normalized = text?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        val redacted = redactJson(normalized) ?: redactPlainText(normalized)
        return if (redacted.length <= MAX_PREVIEW_CHARS) {
            redacted
        } else {
            redacted.take(MAX_PREVIEW_CHARS) + "...[truncated]"
        }
    }

    private fun redactJson(text: String): String? {
        return runCatching {
            val trimmed = text.trim()
            when {
                trimmed.startsWith("{") -> redactObject(JSONObject(trimmed)).toString()
                trimmed.startsWith("[") -> redactArray(JSONArray(trimmed)).toString()
                else -> null
            }
        }.getOrNull()
    }

    private fun redactObject(source: JSONObject): JSONObject {
        val redacted = JSONObject()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            redacted.put(key, redactValue(key, source.opt(key)))
        }
        return redacted
    }

    private fun redactArray(source: JSONArray): JSONArray {
        val redacted = JSONArray()
        for (index in 0 until source.length()) {
            redacted.put(redactValue("", source.opt(index)))
        }
        return redacted
    }

    private fun redactValue(key: String, value: Any?): Any? {
        if (key.isSensitiveJsonKey()) return REDACTED_VALUE
        return when (value) {
            is JSONObject -> redactObject(value)
            is JSONArray -> redactArray(value)
            else -> value
        }
    }

    private fun redactPlainText(text: String): String {
        return SENSITIVE_TEXT_PATTERNS.fold(text) { current, pattern ->
            pattern.replace(current) { match ->
                "${match.groupValues[1]}${REDACTED_VALUE}${match.groupValues[3]}"
            }
        }
    }

    private fun String.isSensitiveJsonKey(): Boolean {
        val normalized = lowercase()
        return SENSITIVE_KEY_FRAGMENTS.any { normalized.contains(it) }
    }

    private const val MAX_TRACES = 30
    private const val MAX_PREVIEW_CHARS = 1_200
    private const val REDACTED_VALUE = "[redacted]"
    private val SENSITIVE_KEY_FRAGMENTS = listOf(
        "token",
        "authorization",
        "password",
        "secret",
        "code",
        "email",
        "rawpayload",
        "backup",
    )
    private val SENSITIVE_TEXT_PATTERNS = listOf(
        Regex("(?i)(\"(?:accessToken|refreshToken|purchaseToken|devCode|code|email)\"\\s*:\\s*\")([^\"]*)(\")"),
        Regex("(?i)((?:Bearer\\s+))([^\\s\"]+)(\")?"),
    )
}
