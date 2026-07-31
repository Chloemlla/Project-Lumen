package com.projectlumen.app.core.services

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceStartEligibilityTest {
    @Test
    fun preAndroidTwelveAllowsBackgroundStartAttempt() {
        assertTrue(
            ForegroundServiceStartEligibility.canStartFromForegroundProcess(
                sdkInt = Build.VERSION_CODES.R,
                processForegroundStarted = false,
            ),
        )
    }

    @Test
    fun androidTwelveAndLaterBlocksBackgroundStartAttempt() {
        assertFalse(
            ForegroundServiceStartEligibility.canStartFromForegroundProcess(
                sdkInt = Build.VERSION_CODES.S,
                processForegroundStarted = false,
            ),
        )
    }

    @Test
    fun androidTwelveAndLaterAllowsForegroundStartAttempt() {
        assertTrue(
            ForegroundServiceStartEligibility.canStartFromForegroundProcess(
                sdkInt = Build.VERSION_CODES.S,
                processForegroundStarted = true,
            ),
        )
    }
}
