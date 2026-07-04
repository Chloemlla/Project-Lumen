package com.projectlumen.app.core.api

import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SecureOkHttpFactoryTest {
    @Test
    fun createRejectsHttpBaseUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureOkHttpFactory.create(
                baseUrl = "http://eye.chloemlla.com/api",
                certificatePins = "",
            )
        }
    }

    @Test
    fun createUsesSystemTrustWhenPinsAreEmpty() {
        val client = SecureOkHttpFactory.create(
            baseUrl = "https://eye.chloemlla.com/api",
            certificatePins = "",
        )

        assertTrue(client.certificatePinner.findMatchingPins("eye.chloemlla.com").isEmpty())
    }

    @Test
    fun createCanRequireExplicitCertificatePins() {
        assertThrows(IllegalArgumentException::class.java) {
            SecureOkHttpFactory.create(
                baseUrl = "https://eye.chloemlla.com/api",
                certificatePins = "",
                requireCertificatePins = true,
            )
        }
    }
}
