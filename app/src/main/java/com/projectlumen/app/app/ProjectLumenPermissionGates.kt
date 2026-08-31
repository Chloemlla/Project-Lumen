package com.projectlumen.app.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.projectlumen.app.R

@Composable
internal fun rememberNotificationPermissionGate(): ((() -> Unit) -> Unit) {
    return rememberRuntimePermissionGate(
        permission = POST_NOTIFICATIONS_PERMISSION,
        permissionNeeded = ::needsNotificationPermission,
        titleRes = R.string.notification_permission_needed,
        messageRes = R.string.notification_permission_denied_message,
    )
}

@Composable
internal fun rememberCameraPermissionGate(): ((() -> Unit) -> Unit) {
    return rememberRuntimePermissionGate(
        permission = Manifest.permission.CAMERA,
        permissionNeeded = ::needsCameraPermission,
        titleRes = R.string.camera_permission_needed,
        messageRes = R.string.camera_permission_denied_message,
    )
}

@Composable
private fun rememberRuntimePermissionGate(
    permission: String,
    permissionNeeded: (Context) -> Boolean,
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var alreadyRequested by rememberSaveable { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        alreadyRequested = true
        when {
            granted -> action?.invoke()
            isPermissionBlocked(context, permission) -> showRecoveryDialog = true
            else -> Toast.makeText(context, context.getString(messageRes), Toast.LENGTH_LONG).show()
        }
    }
    if (showRecoveryDialog) {
        AlertDialog(
            onDismissRequest = { showRecoveryDialog = false },
            title = { Text(stringResource(titleRes)) },
            text = { Text(stringResource(messageRes)) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        showRecoveryDialog = false
                        openAppDetailsSettings(context)
                    },
                ) {
                    Text(stringResource(R.string.open_system_settings))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRecoveryDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
    return { action ->
        when {
            !permissionNeeded(context) -> action()
            // A permanently denied permission never raises the system dialog again, so the
            // only way out of the funnel is the app details page.
            alreadyRequested && isPermissionBlocked(context, permission) -> showRecoveryDialog = true
            else -> {
                pendingAction = action
                permissionLauncher.launch(permission)
            }
        }
    }
}

private fun isPermissionBlocked(context: Context, permission: String): Boolean {
    val activity = context.findActivity() ?: return true
    return !activity.shouldShowRequestPermissionRationale(permission)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
