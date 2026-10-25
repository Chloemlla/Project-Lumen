package com.projectlumen.app.core.api

import com.projectlumen.app.core.network.ClashPartnerCompat
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureOkHttpFactoryTest {
    @Test
    fun createRejectsHttpBaseUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureOkHttpFactory.create(
                baseUrl = "http://chloemlla.com/api/lumen",
                certificatePins = "",
            )
        }
    }

    @Test
    fun createUsesSystemTrustWhenPinsAreEmpty() {
        // ClashPartnerCompat.shouldSkipManualProxy() is consulted inside create();
        // before Application.start() it must remain false on pure JVM unit tests.
        assertTrue(!ClashPartnerCompat.shouldSkipManualProxy())

        val client = SecureOkHttpFactory.create(
            baseUrl = "https://chloemlla.com/api/lumen",
            certificatePins = "",
        )

        assertTrue(client.certificatePinner.findMatchingPins("chloemlla.com").isEmpty())
        // No process binding in unit tests → system proxy selector stays available.
        assertTrue(client.proxy == null)
    }

    @Test
    fun createCanRequireExplicitCertificatePins() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureOkHttpFactory.create(
                baseUrl = "https://chloemlla.com/api/lumen",
                certificatePins = "",
                requireCertificatePins = true,
            )
        }
    }
}
