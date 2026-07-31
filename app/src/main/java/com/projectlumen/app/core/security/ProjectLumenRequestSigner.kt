package com.projectlumen.app.core.security

import com.projectlumen.app.BuildConfig
import okhttp3.HttpUrl
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
        val canonical = ProjectLumenRequestCanonicalizer.canonicalPayload(
            method = method,
            url = url,
            bodyText = bodyText.orEmpty(),
            timestamp = timestamp,
            nonce = nonce,
        )
        return mapOf(
            HEADER_TIMESTAMP to timestamp,
            HEADER_NONCE to nonce,
            HEADER_SIGNATURE to signatureHex(canonical),
        )
    }

    private fun signatureHex(canonicalPayload: String): String {
        val nativeResult = NativeSecurityBridge.signCanonicalPayloadOrNull(
            canonicalPayloadUtf8 = canonicalPayload.toByteArray(Charsets.UTF_8),
            debugAllowed = BuildConfig.DEBUG,
        )
        val nativeSignature = nativeResult
            ?.takeIf { result -> result.verdict.isAllowed }
            ?.signatureHex
            ?.takeIf(::isLowercaseSha256Hex)
        if (nativeSignature != null) return nativeSignature

        if (!BuildConfig.DEBUG) {
            val reason = when {
                nativeResult == null -> "native_load_or_jni_failure"
                !nativeResult.verdict.isAllowed -> nativeResult.verdict.diagnosticSummary()
                else -> "native_signature_invalid"
            }
            error("Project Lumen native request signing was rejected: $reason.")
        }
        return debugFallbackHmacSha256Hex(canonicalPayload)
    }

    private fun debugFallbackHmacSha256Hex(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(
            SecretKeySpec(
                DEBUG_FALLBACK_REQUEST_SIGNING_SECRET.toByteArray(Charsets.UTF_8),
                "HmacSHA256",
            ),
        )
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun isLowercaseSha256Hex(value: String): Boolean {
        return value.length == 64 && value.all { character ->
            character in '0'..'9' || character in 'a'..'f'
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    private const val DEBUG_FALLBACK_REQUEST_SIGNING_SECRET =
        "project-lumen-local-request-signing-key"
}
