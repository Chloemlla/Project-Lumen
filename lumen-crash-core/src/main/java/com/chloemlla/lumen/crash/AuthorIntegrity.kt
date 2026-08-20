package com.chloemlla.lumen.crash

import java.security.MessageDigest

/**
 * Strict multi-point author integrity checks. Fail-closed on mismatch.
 */
object AuthorIntegrity {
    private val expectedDigest: ByteArray = hexToBytes(CrashAuthorAttribution.FINGERPRINT_HEX)

    @JvmStatic
    fun fingerprintHex(): String = toHex(payloadDigest())

    @JvmStatic
    fun verifyOrThrow(reason: String = "author-integrity") {
        val failures = mutableListOf<String>()
        if (CrashAuthorAttribution.AUTHOR_NAME != "Chloemlla") {
            failures += "author-name"
        }
        if (CrashAuthorAttribution.AUTHOR_URL != "https://github.com/Chloemlla/") {
            failures += "author-url"
        }
        if (CrashAuthorAttribution.AUTHOR_HANDLE != "chloemlla") {
            failures += "author-handle"
        }
        if (!CrashAuthorAttribution.FOOTER_LABEL.contains(CrashAuthorAttribution.AUTHOR_NAME) ||
            !CrashAuthorAttribution.FOOTER_LABEL.contains(CrashAuthorAttribution.AUTHOR_URL)
        ) {
            failures += "footer-label"
        }
        val liveDigest = payloadDigest()
        if (toHex(liveDigest) != CrashAuthorAttribution.FINGERPRINT_HEX) {
            failures += "fingerprint-const"
        }
        if (!liveDigest.contentEquals(expectedDigest)) {
            failures += "fingerprint-digest"
        }
        if (failures.isNotEmpty()) {
            throw SecurityException(
                "Lumen Crash SDK author integrity failed ($reason): ${failures.joinToString()}",
            )
        }
    }

    fun verifiedAuthorBlock(): AuthorBlock {
        verifyOrThrow("author-block")
        return AuthorBlock(
            authorName = CrashAuthorAttribution.AUTHOR_NAME,
            authorUrl = CrashAuthorAttribution.AUTHOR_URL,
            authorFingerprint = CrashAuthorAttribution.FINGERPRINT_HEX,
            footerLabel = CrashAuthorAttribution.FOOTER_LABEL,
        )
    }

    private fun payloadDigest(): ByteArray =
        MessageDigest.getInstance("SHA-256")
            .digest(CrashAuthorAttribution.payload().toByteArray(Charsets.UTF_8))

    private fun toHex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xFF
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0F])
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid fingerprint hex length." }
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}

data class AuthorBlock(
    val authorName: String,
    val authorUrl: String,
    val authorFingerprint: String,
    val footerLabel: String,
)
