package com.chloemlla.lumen.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashDeviceIdTest {
    @Test
    fun sameDeviceEnvironmentAlwaysDerivesTheSameId() {
        val first = CrashDeviceId.derive(ANDROID_ID, PACKAGE, traits())
        val second = CrashDeviceId.derive(" ${ANDROID_ID.uppercase()} ", PACKAGE, traits())

        assertEquals(first, second)
        assertEquals(32, first!!.length)
        assertTrue(first.all { it in "0123456789abcdef" })
    }

    @Test
    fun differentDevicesOrHostAppsDeriveDifferentIds() {
        val baseline = CrashDeviceId.derive(ANDROID_ID, PACKAGE, traits())

        assertNotEquals(baseline, CrashDeviceId.derive("b1b2b3b4b5b6b7b8", PACKAGE, traits()))
        assertNotEquals(baseline, CrashDeviceId.derive(ANDROID_ID, "com.other.host", traits()))
        assertNotEquals(baseline, CrashDeviceId.derive(ANDROID_ID, PACKAGE, traits(model = "Pixel 9")))
    }

    @Test
    fun unusableAndroidIdsFallBackToTheRandomPath() {
        assertNull(CrashDeviceId.derive(null, PACKAGE, traits()))
        assertNull(CrashDeviceId.derive("   ", PACKAGE, traits()))
        assertNull(CrashDeviceId.derive("0000000000000000", PACKAGE, traits()))
        assertNull(CrashDeviceId.derive("9774D56D682E549C", PACKAGE, traits()))
    }

    @Test
    fun isUsableAndroidIdRejectsPlaceholders() {
        assertTrue(CrashDeviceId.isUsableAndroidId(ANDROID_ID))
        assertFalse(CrashDeviceId.isUsableAndroidId(""))
        assertFalse(CrashDeviceId.isUsableAndroidId("00000000"))
        assertFalse(CrashDeviceId.isUsableAndroidId("android_id"))
    }

    private fun traits(model: String = "V2266A"): List<String> =
        listOf("vivo", "vivo", "PD2266", "kalama", "qcom", model)

    private companion object {
        const val ANDROID_ID = "a1b2c3d4e5f60718"
        const val PACKAGE = "com.projectlumen.app"
    }
}
