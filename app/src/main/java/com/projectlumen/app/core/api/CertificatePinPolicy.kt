package com.projectlumen.app.core.api

internal object CertificatePinPolicy {
    fun parse(certificatePins: String): List<String> {
        return certificatePins
            .split(',', ';', '\n')
            .mapNotNull { candidate -> normalize(candidate.trim()) }
            .distinct()
    }

    /** A malformed pin would otherwise reach OkHttp and throw on every client build. */
    private fun normalize(pin: String): String? {
        if (!SHA256_PIN_REGEX.matches(pin)) return null
        return if (pin.startsWith(PIN_PREFIX)) pin else "$PIN_PREFIX$pin"
    }

    private const val PIN_PREFIX = "sha256/"
    private val SHA256_PIN_REGEX = Regex("""^(?:sha256/)?[A-Za-z0-9+/]{43}=$""")
}
