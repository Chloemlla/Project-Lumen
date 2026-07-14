package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Style
import androidx.compose.ui.graphics.vector.ImageVector
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity

internal enum class PermissionSetupTarget {
    STATISTICS,
    DIAGNOSTICS,
    NOTIFICATIONS,
    EXACT_ALARM,
    FULL_SCREEN,
    KEEP_ALIVE,
    DISTANCE_CAMERA,
    BLINK_CAMERA,
    AMBIENT_LIGHT,
    BRIGHTNESS,
    OVERLAY,
    SHIZUKU,
}

internal data class PermissionControlTile(
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val target: PermissionSetupTarget,
    val checked: Boolean,
    val ready: Boolean,
)

internal fun privacyControlTiles(
    settings: AppSettingsEntity,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
): List<PermissionControlTile> {
    return listOf(
        PermissionControlTile(
            icon = Icons.Outlined.BarChart,
            titleRes = R.string.enable_statistics,
            detailRes = R.string.settings_privacy_statistics_detail,
            target = PermissionSetupTarget.STATISTICS,
            checked = settings.statsEnabled,
            ready = true,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.Info,
            titleRes = R.string.enable_diagnostic_telemetry_upload,
            detailRes = R.string.settings_privacy_diagnostics_detail,
            target = PermissionSetupTarget.DIAGNOSTICS,
            checked = settings.diagnosticTelemetryUploadEnabled,
            ready = true,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.NotificationsActive,
            titleRes = R.string.eye_care_permission_notifications,
            detailRes = R.string.eye_care_permission_notifications_detail,
            target = PermissionSetupTarget.NOTIFICATIONS,
            checked = settings.notificationEnabled,
            ready = !permissionRequirements.notification,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.Schedule,
            titleRes = R.string.enable_keep_alive,
            detailRes = R.string.settings_privacy_keep_alive_detail,
            target = PermissionSetupTarget.KEEP_ALIVE,
            checked = settings.keepAliveEnabled,
            ready = true,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.PhotoCamera,
            titleRes = R.string.enable_proximity_monitoring,
            detailRes = R.string.settings_privacy_camera_distance_detail,
            target = PermissionSetupTarget.DISTANCE_CAMERA,
            checked = settings.proximityMonitoringEnabled,
            ready = !permissionRequirements.camera,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.PhotoCamera,
            titleRes = R.string.enable_blink_monitoring,
            detailRes = R.string.settings_privacy_camera_blink_detail,
            target = PermissionSetupTarget.BLINK_CAMERA,
            checked = settings.blinkMonitoringEnabled,
            ready = !permissionRequirements.camera,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.NotificationsActive,
            titleRes = R.string.enable_ambient_light_monitoring,
            detailRes = R.string.settings_privacy_ambient_light_detail,
            target = PermissionSetupTarget.AMBIENT_LIGHT,
            checked = settings.ambientLightMonitoringEnabled,
            ready = true,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.Style,
            titleRes = R.string.enable_auto_brightness,
            detailRes = R.string.eye_care_permission_write_settings_detail,
            target = PermissionSetupTarget.BRIGHTNESS,
            checked = settings.autoBrightnessEnabled,
            ready = !permissionRequirements.writeSettings || usesShizukuNativeBrightness(settings),
        ),
        PermissionControlTile(
            icon = Icons.Outlined.Style,
            titleRes = R.string.enable_global_overlay,
            detailRes = R.string.eye_care_permission_overlay_detail,
            target = PermissionSetupTarget.OVERLAY,
            checked = settings.globalOverlayEnabled,
            ready = !permissionRequirements.overlay,
        ),
        PermissionControlTile(
            icon = Icons.Outlined.Lock,
            titleRes = R.string.enable_shizuku_advanced_mode,
            detailRes = R.string.eye_care_permission_shizuku_detail,
            target = PermissionSetupTarget.SHIZUKU,
            checked = settings.shizukuAdvancedModeEnabled,
            ready = shizukuReady,
        ),
    )
}

internal fun firstMissingPermissionTarget(
    settings: AppSettingsEntity,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
): PermissionSetupTarget? {
    return when {
        settings.notificationEnabled && permissionRequirements.notification -> PermissionSetupTarget.NOTIFICATIONS
        settings.notificationEnabled && permissionRequirements.exactAlarm -> PermissionSetupTarget.EXACT_ALARM
        settings.notificationEnabled && permissionRequirements.fullScreenIntent -> PermissionSetupTarget.FULL_SCREEN
        settings.proximityMonitoringEnabled && permissionRequirements.camera -> PermissionSetupTarget.DISTANCE_CAMERA
        settings.blinkMonitoringEnabled && permissionRequirements.camera -> PermissionSetupTarget.BLINK_CAMERA
        settings.autoBrightnessEnabled && permissionRequirements.writeSettings && !usesShizukuNativeBrightness(settings) -> PermissionSetupTarget.BRIGHTNESS
        settings.globalOverlayEnabled && permissionRequirements.overlay -> PermissionSetupTarget.OVERLAY
        settings.shizukuAdvancedModeEnabled && !shizukuReady -> PermissionSetupTarget.SHIZUKU
        else -> null
    }
}

internal fun privacyReadinessScore(
    settings: AppSettingsEntity,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
): Int {
    val checks = listOf(
        settings.statsEnabled,
        !settings.notificationEnabled || !permissionRequirements.notification,
        !settings.notificationEnabled || !permissionRequirements.exactAlarm,
        !settings.notificationEnabled || !permissionRequirements.fullScreenIntent,
        settings.keepAliveEnabled,
        !settings.proximityMonitoringEnabled || !permissionRequirements.camera,
        !settings.blinkMonitoringEnabled || !permissionRequirements.camera,
        settings.ambientLightMonitoringEnabled,
        !settings.autoBrightnessEnabled || !permissionRequirements.writeSettings || usesShizukuNativeBrightness(settings),
        !settings.globalOverlayEnabled || !permissionRequirements.overlay,
        !settings.shizukuAdvancedModeEnabled || shizukuReady,
    )
    return (checks.count { it } * 100f / checks.size).toInt()
}

internal fun privacyActionNeededCount(
    settings: AppSettingsEntity,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
): Int {
    val pending = listOf(
        settings.notificationEnabled && permissionRequirements.notification,
        settings.notificationEnabled && permissionRequirements.exactAlarm,
        settings.notificationEnabled && permissionRequirements.fullScreenIntent,
        settings.proximityMonitoringEnabled && permissionRequirements.camera,
        settings.blinkMonitoringEnabled && permissionRequirements.camera,
        settings.autoBrightnessEnabled && permissionRequirements.writeSettings && !usesShizukuNativeBrightness(settings),
        settings.globalOverlayEnabled && permissionRequirements.overlay,
        settings.shizukuAdvancedModeEnabled && !shizukuReady,
    )
    return pending.count { it }
}

internal fun usesShizukuNativeBrightness(settings: AppSettingsEntity): Boolean {
    return settings.shizukuAdvancedModeEnabled && settings.shizukuNativeEyeProtectionEnabled
}
