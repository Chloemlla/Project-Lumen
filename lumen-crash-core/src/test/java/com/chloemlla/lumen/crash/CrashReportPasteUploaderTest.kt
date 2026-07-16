package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportPasteUploaderTest {
    @Test
    fun normalizeBaseUrlTrimsSlashAndRequiresHttps() {
        assertEquals(
            "https://paste.gentoo.zip",
            CrashReportPasteUploader.normalizeBaseUrl("https://paste.gentoo.zip/"),
        )
        runCatching {
            CrashReportPasteUploader.normalizeBaseUrl("http://paste.gentoo.zip")
        }.onSuccess {
            error("Expected non-HTTPS URL rejection.")
        }
    }

    @Test
    fun resolveShareableUrlAcceptsIdOrFullUrl() {
        assertEquals(
            "https://paste.gentoo.zip/uIrngzdh",
            CrashReportPasteUploader.resolveShareableUrl(
                baseUrl = "https://paste.gentoo.zip",
                responseText = "uIrngzdh",
            ),
        )
        assertEquals(
            "https://paste.gentoo.zip/uIrngzdh",
            CrashReportPasteUploader.resolveShareableUrl(
                baseUrl = "https://paste.gentoo.zip/",
                responseText = "https://paste.gentoo.zip/uIrngzdh\n",
            ),
        )
    }

    @Test
    fun resolveShareableUrlRejectsUnexpectedIds() {
        val result = runCatching {
            CrashReportPasteUploader.resolveShareableUrl(
                baseUrl = "https://paste.gentoo.zip",
                responseText = "<html>nope</html>",
            )
        }
        assertTrue(result.isFailure)
    }
}
