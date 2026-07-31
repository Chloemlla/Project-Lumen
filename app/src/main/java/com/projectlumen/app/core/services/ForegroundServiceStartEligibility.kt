package com.projectlumen.app.core.services

import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Conservative process-visibility gate for foreground services that have no reliable background
 * start exemption. Callers still need to handle a platform refusal because lifecycle state can
 * change between this check and the framework call.
 */
internal object ForegroundServiceStartEligibility {
    fun canStartFromForegroundProcess(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return canStartFromForegroundProcess(
            sdkInt = Build.VERSION.SDK_INT,
            processForegroundStarted = ProcessLifecycleOwner.get()
                .lifecycle
                .currentState
                .isAtLeast(Lifecycle.State.STARTED),
        )
    }

    internal fun canStartFromForegroundProcess(
        sdkInt: Int,
        processForegroundStarted: Boolean,
    ): Boolean {
        return sdkInt < Build.VERSION_CODES.S || processForegroundStarted
    }
}
