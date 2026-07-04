package com.projectlumen.app.core.api

internal object CertificatePinPolicy {
    fun parse(certificatePins: String): List<String> {
        return certificatePins
            .split(',', ';', '\n')
            .map { normalize(it.trim()) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun normalize(pin: String): String {
        if (pin.isBlank()) return ""
        return if (pin.startsWith(PIN_PREFIX)) pin else "$PIN_PREFIX$pin"
    }

    private const val PIN_PREFIX = "sha256/"
}
