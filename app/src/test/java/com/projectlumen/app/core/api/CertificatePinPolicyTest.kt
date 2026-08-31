package com.projectlumen.app.core.api

import org.junit.Assert.assertEquals
import org.junit.Test

class CertificatePinPolicyTest {
    @Test
    fun parseNormalizesPinsAndSupportsMultipleSeparators() {
        val pins = CertificatePinPolicy.parse(
            "$FIRST_PIN; sha256/$SECOND_PIN\n $THIRD_PIN",
        )

        assertEquals(
            listOf("sha256/$FIRST_PIN", "sha256/$SECOND_PIN", "sha256/$THIRD_PIN"),
            pins,
        )
    }

    @Test
    fun parseDropsBlankPinsAndDeduplicatesConfiguredPins() {
        val pins = CertificatePinPolicy.parse(
            "sha256/$FIRST_PIN,, $FIRST_PIN;\n",
        )

        assertEquals(listOf("sha256/$FIRST_PIN"), pins)
    }

    @Test
    fun parseDropsPinsThatAreNotBase64Sha256Digests() {
        val pins = CertificatePinPolicy.parse(
            "abc=, sha256/not-a-digest, 0123456789abcdef, $FIRST_PIN",
        )

        assertEquals(listOf("sha256/$FIRST_PIN"), pins)
    }

    private companion object {
        private const val FIRST_PIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        private const val SECOND_PIN = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
        private const val THIRD_PIN = "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC="
    }
}
