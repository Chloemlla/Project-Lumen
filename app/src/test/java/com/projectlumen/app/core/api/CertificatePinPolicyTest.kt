package com.projectlumen.app.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificatePinPolicyTest {
    @Test
    fun parseNormalizesPinsAndSupportsMultipleSeparators() {
        val pins = CertificatePinPolicy.parse(
            "abc=; sha256/def=\n ghi=",
        )

        assertEquals(
            listOf("sha256/abc=", "sha256/def=", "sha256/ghi="),
            pins,
        )
    }

    @Test
    fun parseDropsBlankPinsAndDeduplicatesConfiguredPins() {
        val pins = CertificatePinPolicy.parse(
            "sha256/abc=,, abc=;\n",
        )

        assertEquals(listOf("sha256/abc="), pins)
    }
}
