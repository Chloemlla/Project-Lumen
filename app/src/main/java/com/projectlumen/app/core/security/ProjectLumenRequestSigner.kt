package com.projectlumen.app.core.security

import com.projectlumen.app.BuildConfig
import okhttp3.HttpUrl
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ProjectLumenRequestSigner {
    const val HEADER_TIMESTAMP = "X-Lumen-Timestamp"
    const val HEADER_NONCE = "X-Lumen-Nonce"
    const val HEADER_SIGNATURE = "X-Lumen-Signature"

    fun headers(method: String, url: HttpUrl, bodyText: String?): Map<String, String> {
        val timestamp = (System.currentTimeMillis() / 1_000L).toString()
        val nonce = UUID.randomUUID().toString().replace("-", "")
        val canonical = canonicalPayload(
            method = method,
            url = url,
            bodyText = bodyText.orEmpty(),
            timestamp = timestamp,
            nonce = nonce,
        )
        return mapOf(
            HEADER_TIMESTAMP to timestamp,
            HEADER_NONCE to nonce,
            HEADER_SIGNATURE to hmacSha256Hex(canonical),
        )
    }

    private fun canonicalPayload(
        method: String,
        url: HttpUrl,
        bodyText: String,
        timestamp: String,
        nonce: String,
    ): String {
        val values = sortedMapOf(
            "bodySha256" to sha256Hex(bodyText.toByteArray(Charsets.UTF_8)),
            "method" to method.uppercase(),
            "nonce" to nonce,
            "path" to url.encodedPath,
            "query" to url.encodedQuery.orEmpty(),
            "timestamp" to timestamp,
        )
        return values.entries.joinToString(separator = "\n") { "${it.key}=${it.value}" }
    }

    private fun hmacSha256Hex(payload: String): String {
        val nativeSecret = NativeSecurityBridge.requestSigningSecretOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val secret = nativeSecret ?: if (BuildConfig.DEBUG) {
            FALLBACK_REQUEST_SIGNING_SECRET
        } else {
            error("Project Lumen native request signing bridge is unavailable.")
        }
        require(secret.isNotBlank()) { "Project Lumen request signing secret is not configured." }
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

    private const val FALLBACK_REQUEST_SIGNING_SECRET = "project-lumen-local-request-signing-key"
}
