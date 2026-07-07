package com.projectlumen.app.core.proximity

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityCameraForegroundEligibilityTest {
    @Test
    fun preAndroidTwelveAllowsGrantedCameraFromBackground() {
        assertTrue(
            ProximityCameraForegroundEligibility.canStartCameraForegroundService(
                hasCameraPermission = true,
                sdkInt = Build.VERSION_CODES.R,
                processForegroundStarted = false,
            ),
        )
    }

    @Test
    fun androidTwelveAndLaterRequiresForegroundStartedProcess() {
        assertFalse(
            ProximityCameraForegroundEligibility.canStartCameraForegroundService(
                hasCameraPermission = true,
                sdkInt = Build.VERSION_CODES.S,
                processForegroundStarted = false,
            ),
        )
        assertTrue(
            ProximityCameraForegroundEligibility.canStartCameraForegroundService(
                hasCameraPermission = true,
                sdkInt = Build.VERSION_CODES.S,
                processForegroundStarted = true,
            ),
        )
    }

    @Test
    fun missingCameraPermissionBlocksAllSdkVersions() {
        assertFalse(
            ProximityCameraForegroundEligibility.canStartCameraForegroundService(
                hasCameraPermission = false,
                sdkInt = Build.VERSION_CODES.R,
                processForegroundStarted = true,
            ),
        )
        assertFalse(
            ProximityCameraForegroundEligibility.canStartCameraForegroundService(
                hasCameraPermission = false,
                sdkInt = Build.VERSION_CODES.S,
                processForegroundStarted = true,
            ),
        )
    }
}
