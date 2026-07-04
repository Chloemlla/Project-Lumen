package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R

@Composable
internal fun SettingsPrivacyPermissionCenter(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
    activeTarget: PermissionSetupTarget?,
    onConfigureTarget: (PermissionSetupTarget) -> Unit,
    onTargetCheckedChange: (PermissionSetupTarget, Boolean) -> Unit,
) {
    val settings = uiState.settings
    val nextTarget = firstMissingPermissionTarget(settings, permissionRequirements, shizukuReady)
    val readinessScore = privacyReadinessScore(settings, permissionRequirements, shizukuReady)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Lock, R.string.eye_care_privacy_permissions)
            Text(
                stringResource(R.string.settings_privacy_permission_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MetricRow(R.string.eye_care_config_score, stringResource(R.string.percent_value, readinessScore))
            LinearProgressIndicator(
                progress = { readinessScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            if (nextTarget != null) {
                Button(onClick = { onConfigureTarget(nextTarget) }) {
                    ButtonLabel(Icons.Outlined.CheckCircle, R.string.settings_permission_fix_next)
                }
            }
            Text(
                stringResource(R.string.settings_privacy_quick_tiles),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            PermissionControlTileGrid(
                tiles = privacyControlTiles(settings, permissionRequirements, shizukuReady),
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.BarChart,
                titleRes = R.string.enable_statistics,
                detailRes = R.string.settings_privacy_statistics_detail,
                target = PermissionSetupTarget.STATISTICS,
                switchChecked = settings.statsEnabled,
                ready = true,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.Info,
                titleRes = R.string.enable_diagnostic_telemetry_upload,
                detailRes = R.string.settings_privacy_diagnostics_detail,
                target = PermissionSetupTarget.DIAGNOSTICS,
                switchChecked = settings.diagnosticTelemetryUploadEnabled,
                ready = true,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.NotificationsActive,
                titleRes = R.string.eye_care_permission_notifications,
                detailRes = R.string.eye_care_permission_notifications_detail,
                target = PermissionSetupTarget.NOTIFICATIONS,
                switchChecked = settings.notificationEnabled,
                ready = !permissionRequirements.notification,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.Schedule,
                titleRes = R.string.eye_care_permission_exact_alarm,
                detailRes = R.string.eye_care_permission_exact_alarm_detail,
                target = PermissionSetupTarget.EXACT_ALARM,
                featureEnabled = settings.notificationEnabled,
                ready = !permissionRequirements.exactAlarm,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.NotificationsActive,
                titleRes = R.string.eye_care_permission_full_screen,
                detailRes = R.string.eye_care_permission_full_screen_detail,
                target = PermissionSetupTarget.FULL_SCREEN,
                featureEnabled = settings.notificationEnabled,
                ready = !permissionRequirements.fullScreenIntent,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.Schedule,
                titleRes = R.string.enable_keep_alive,
                detailRes = R.string.settings_privacy_keep_alive_detail,
                target = PermissionSetupTarget.KEEP_ALIVE,
                switchChecked = settings.keepAliveEnabled,
                ready = true,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.PhotoCamera,
                titleRes = R.string.enable_proximity_monitoring,
                detailRes = R.string.settings_privacy_camera_distance_detail,
                target = PermissionSetupTarget.DISTANCE_CAMERA,
                switchChecked = settings.proximityMonitoringEnabled,
                ready = !permissionRequirements.camera,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.PhotoCamera,
                titleRes = R.string.enable_blink_monitoring,
                detailRes = R.string.settings_privacy_camera_blink_detail,
                target = PermissionSetupTarget.BLINK_CAMERA,
                switchChecked = settings.blinkMonitoringEnabled,
                ready = !permissionRequirements.camera,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.NotificationsActive,
                titleRes = R.string.enable_ambient_light_monitoring,
                detailRes = R.string.settings_privacy_ambient_light_detail,
                target = PermissionSetupTarget.AMBIENT_LIGHT,
                switchChecked = settings.ambientLightMonitoringEnabled,
                ready = true,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.Style,
                titleRes = R.string.enable_auto_brightness,
                detailRes = R.string.eye_care_permission_write_settings_detail,
                target = PermissionSetupTarget.BRIGHTNESS,
                switchChecked = settings.autoBrightnessEnabled,
                ready = !permissionRequirements.writeSettings || usesShizukuNativeBrightness(settings),
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.Style,
                titleRes = R.string.enable_global_overlay,
                detailRes = R.string.eye_care_permission_overlay_detail,
                target = PermissionSetupTarget.OVERLAY,
                switchChecked = settings.globalOverlayEnabled,
                ready = !permissionRequirements.overlay,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            PrivacyPermissionRow(
                icon = Icons.Outlined.Lock,
                titleRes = R.string.enable_shizuku_advanced_mode,
                detailRes = R.string.eye_care_permission_shizuku_detail,
                target = PermissionSetupTarget.SHIZUKU,
                switchChecked = settings.shizukuAdvancedModeEnabled,
                ready = shizukuReady,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            StatusLine(Icons.Outlined.Info, stringResource(R.string.eye_care_privacy_boundary))
        }
    }
}

@Composable
private fun PermissionControlTileGrid(
    tiles: List<PermissionControlTile>,
    onTargetCheckedChange: (PermissionSetupTarget, Boolean) -> Unit,
) {
    LumenFlowRow {
        tiles.forEach { tile ->
            PermissionControlTileItem(
                tile = tile,
                onCheckedChange = { onTargetCheckedChange(tile.target, it) },
            )
        }
    }
}

@Composable
private fun PermissionControlTileItem(
    tile: PermissionControlTile,
    onCheckedChange: (Boolean) -> Unit,
) {
    val actionNeeded = tile.checked && !tile.ready
    val statusRes = when {
        !tile.checked -> R.string.settings_permission_off
        actionNeeded -> R.string.settings_permission_action_needed
        else -> R.string.settings_permission_ready
    }
    val tileColor = when {
        actionNeeded -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
        tile.checked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    }
    val borderColor = when {
        actionNeeded -> MaterialTheme.colorScheme.error
        tile.checked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val iconTint = when {
        actionNeeded -> MaterialTheme.colorScheme.error
        tile.checked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .widthIn(min = 158.dp)
            .clip(LumenCardShape)
            .background(tileColor)
            .border(1.dp, borderColor, LumenCardShape)
            .toggleable(
                value = tile.checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PermissionIcon(tile.icon, iconTint)
            Switch(
                checked = tile.checked,
                onCheckedChange = null,
            )
        }
        Text(
            stringResource(tile.titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(tile.detailRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(statusRes),
            style = MaterialTheme.typography.labelLarge,
            color = iconTint,
        )
    }
}

@Composable
private fun PrivacyPermissionRow(
    icon: ImageVector,
    @StringRes titleRes: Int,
    @StringRes detailRes: Int,
    target: PermissionSetupTarget,
    switchChecked: Boolean? = null,
    featureEnabled: Boolean = switchChecked ?: true,
    ready: Boolean,
    activeTarget: PermissionSetupTarget?,
    onConfigureTarget: (PermissionSetupTarget) -> Unit,
    onTargetCheckedChange: (PermissionSetupTarget, Boolean) -> Unit,
) {
    val active = activeTarget == target
    val actionNeeded = featureEnabled && !ready
    val statusRes = when {
        !featureEnabled -> R.string.settings_permission_off
        actionNeeded -> R.string.settings_permission_action_needed
        else -> R.string.settings_permission_ready
    }
    val statusColor = when {
        actionNeeded -> MaterialTheme.colorScheme.error
        featureEnabled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (active) 0.72f else 0.38f))
            .border(
                width = 1.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = LumenCardShape,
            )
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PermissionIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(detailRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (switchChecked != null) {
                Switch(
                    checked = switchChecked,
                    onCheckedChange = { onTargetCheckedChange(target, it) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(statusRes),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = statusColor,
            )
            OutlinedButton(onClick = { onConfigureTarget(target) }) {
                ButtonLabel(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    if (actionNeeded) R.string.settings_permission_resolve else R.string.settings_permission_configure,
                )
            }
        }
        if (active) {
            Text(
                stringResource(R.string.settings_permission_return_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionIcon(icon: ImageVector) {
    PermissionIcon(icon, MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
private fun PermissionIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
