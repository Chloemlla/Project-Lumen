package com.projectlumen.app.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class PermissionRequirements(
    val notification: Boolean,
    val camera: Boolean,
    val exactAlarm: Boolean,
    val fullScreenIntent: Boolean,
    val overlay: Boolean,
    val writeSettings: Boolean,
)

@Composable
internal fun rememberPermissionRequirements(): PermissionRequirements {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return refreshKey.let {
        context.permissionRequirements()
    }
}

private fun Context.permissionRequirements(): PermissionRequirements {
    return PermissionRequirements(
        notification = needsNotificationPermission(this),
        camera = needsCameraPermission(this),
        exactAlarm = needsExactAlarmSettings(this),
        fullScreenIntent = needsFullScreenIntentSettings(this),
        overlay = needsOverlayPermission(this),
        writeSettings = needsWriteSettingsPermission(this),
    )
}
