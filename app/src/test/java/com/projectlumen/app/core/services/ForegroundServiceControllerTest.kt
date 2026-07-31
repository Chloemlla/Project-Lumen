package com.projectlumen.app.core.services

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundServiceControllerTest {
    @Test
    fun androidTwelveStartNotAllowedIsExpectedSchedulingOutcome() {
        assertTrue(
            ForegroundServiceController.isExpectedBackgroundStartRefusal(
                sdkInt = Build.VERSION_CODES.S,
                throwableClassName = "android.app.ForegroundServiceStartNotAllowedException",
                throwableMessage = "startForegroundService() not allowed due to mAllowStartForeground false",
            ),
        )
    }

    @Test
    fun legacyIllegalStateMessageIsOnlyExpectedOnAndroidTwelveAndLater() {
        assertFalse(
            ForegroundServiceController.isExpectedBackgroundStartRefusal(
                sdkInt = Build.VERSION_CODES.R,
                throwableClassName = IllegalStateException::class.java.name,
                throwableMessage = "startForegroundService() not allowed due to mAllowStartForeground false",
            ),
        )
        assertTrue(
            ForegroundServiceController.isExpectedBackgroundStartRefusal(
                sdkInt = Build.VERSION_CODES.S,
                throwableClassName = IllegalStateException::class.java.name,
                throwableMessage = "startForegroundService() not allowed due to mAllowStartForeground false",
            ),
        )
    }

    @Test
    fun unrelatedFailuresRemainUnexpected() {
        assertFalse(
            ForegroundServiceController.isExpectedBackgroundStartRefusal(
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                throwableClassName = SecurityException::class.java.name,
                throwableMessage = "Missing foreground service permission",
            ),
        )
        assertTrue(
            ForegroundServiceController.shouldRecordFailure(
                sdkInt = Build.VERSION_CODES.VANILLA_ICE_CREAM,
                throwable = SecurityException("Missing foreground service permission"),
                becameIneligible = false,
            ),
        )
    }

    @Test
    fun expectedBackgroundRefusalDoesNotReachCrashRecording() {
        assertFalse(
            ForegroundServiceController.shouldRecordFailure(
                sdkInt = Build.VERSION_CODES.S,
                throwable = IllegalStateException(
                    "startForegroundService() not allowed due to mAllowStartForeground false",
                ),
                becameIneligible = false,
            ),
        )
    }
}
