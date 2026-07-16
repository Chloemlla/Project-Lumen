package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Test

class LumenCrashDefaultsTest {
    @Test
    fun fileProviderAuthorityUsesStableSuffix() {
        assertEquals(
            "com.example.app.lumen.crash.fileprovider",
            LumenCrashDefaults.fileProviderAuthority("com.example.app"),
        )
    }
}
