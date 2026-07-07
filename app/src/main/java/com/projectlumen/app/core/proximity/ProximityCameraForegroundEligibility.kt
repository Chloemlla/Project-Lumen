package com.projectlumen.app.core.proximity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner

internal object ProximityCameraForegroundEligibility {
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canStartCameraForegroundService(context: Context): Boolean {
        return canStartCameraForegroundService(
            hasCameraPermission = hasCameraPermission(context),
            sdkInt = Build.VERSION.SDK_INT,
            processForegroundStarted = isProcessForegroundStarted(),
        )
    }

    internal fun canStartCameraForegroundService(
        hasCameraPermission: Boolean,
        sdkInt: Int,
        processForegroundStarted: Boolean,
    ): Boolean {
        return hasCameraPermission &&
            (sdkInt < Build.VERSION_CODES.S || processForegroundStarted)
    }

    private fun isProcessForegroundStarted(): Boolean {
        return ProcessLifecycleOwner.get()
            .lifecycle
            .currentState
            .isAtLeast(Lifecycle.State.STARTED)
    }
}
