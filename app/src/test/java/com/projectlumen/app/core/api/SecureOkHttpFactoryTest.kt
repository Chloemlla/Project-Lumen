package com.projectlumen.app.core.api

import okhttp3.CertificatePinner
import org.junit.Assert.assertEquals
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

        assertEquals(CertificatePinner.DEFAULT, client.certificatePinner)
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
