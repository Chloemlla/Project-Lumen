package com.projectlumen.app.core.security

import okhttp3.HttpUrl
import java.security.MessageDigest

internal object ProjectLumenRequestCanonicalizer {
    fun canonicalPayload(
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
        return values.entries.joinToString(separator = "\n") { (key, value) -> "$key=$value" }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
