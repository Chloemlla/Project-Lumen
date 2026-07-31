package com.projectlumen.app.core.proximity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.projectlumen.app.core.services.ForegroundServiceStartEligibility

internal object ProximityCameraForegroundEligibility {
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canStartCameraForegroundService(context: Context): Boolean {
        return hasCameraPermission(context) &&
            ForegroundServiceStartEligibility.canStartFromForegroundProcess()
    }

    internal fun canStartCameraForegroundService(
        hasCameraPermission: Boolean,
        sdkInt: Int,
        processForegroundStarted: Boolean,
    ): Boolean {
        return hasCameraPermission && ForegroundServiceStartEligibility.canStartFromForegroundProcess(
            sdkInt = sdkInt,
            processForegroundStarted = processForegroundStarted,
        )
    }
}
