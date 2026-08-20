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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
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
    backendFeaturesVisible: Boolean,
    activeTarget: PermissionSetupTarget?,
    onConfigureTarget: (PermissionSetupTarget) -> Unit,
    onTargetCheckedChange: (PermissionSetupTarget, Boolean) -> Unit,
) {
    val settings = uiState.settings
    val nextTarget = firstMissingPermissionTarget(settings, permissionRequirements, shizukuReady)
    val readinessScore = privacyReadinessScore(settings, permissionRequirements, shizukuReady)
    val actionNeededCount = privacyActionNeededCount(settings, permissionRequirements, shizukuReady)

    SettingsSection(
        titleRes = R.string.eye_care_privacy_permissions,
        icon = Icons.Outlined.Lock,
        initiallyExpanded = false,
        forceExpanded = activeTarget != null,
        headerAccessory = {
            PrivacyReadinessBadge(
                readinessScore = readinessScore,
                actionNeededCount = actionNeededCount,
            )
        },
        summary = {
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
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onConfigureTarget(nextTarget) },
                ) {
                    ButtonLabel(Icons.Outlined.CheckCircle, R.string.settings_permission_fix_next)
                }
            }
        },
    ) {
            Text(
                stringResource(R.string.settings_privacy_quick_tiles),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            PermissionControlTileGrid(
                tiles = privacyControlTiles(
                    settings = settings,
                    permissionRequirements = permissionRequirements,
                    shizukuReady = shizukuReady,
                    backendFeaturesVisible = backendFeaturesVisible,
                ),
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
                icon = Icons.Outlined.BarChart,
                titleRes = R.string.device_insights_usage_access_title,
                detailRes = R.string.device_insights_usage_access_detail,
                target = PermissionSetupTarget.USAGE_ACCESS,
                ready = !permissionRequirements.usageAccess,
                sensitive = true,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            if (backendFeaturesVisible) {
                PrivacyPermissionRow(
                    icon = Icons.Outlined.Info,
                    titleRes = R.string.enable_diagnostic_telemetry_upload,
                    detailRes = R.string.settings_privacy_diagnostics_detail,
                    target = PermissionSetupTarget.DIAGNOSTICS,
                    switchChecked = settings.diagnosticTelemetryUploadEnabled,
                    ready = true,
                    sensitive = true,
                    activeTarget = activeTarget,
                    onConfigureTarget = onConfigureTarget,
                    onTargetCheckedChange = onTargetCheckedChange,
                )
            }
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
                sensitive = true,
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
                sensitive = true,
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
                sensitive = true,
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
                sensitive = true,
                activeTarget = activeTarget,
                onConfigureTarget = onConfigureTarget,
                onTargetCheckedChange = onTargetCheckedChange,
            )
            StatusLine(Icons.Outlined.Info, stringResource(R.string.eye_care_privacy_boundary))
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
    val tone = when {
        !tile.checked -> PrivacyPermissionTone.Off
        actionNeeded -> PrivacyPermissionTone.Attention
        else -> PrivacyPermissionTone.Ready
    }
    val statusRes = when (tone) {
        PrivacyPermissionTone.Off -> R.string.settings_permission_off
        PrivacyPermissionTone.Attention -> R.string.settings_permission_action_needed
        PrivacyPermissionTone.Ready -> R.string.settings_permission_ready
    }
    val tileColor = when (tone) {
        PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f)
        PrivacyPermissionTone.Ready -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
        PrivacyPermissionTone.Off -> lumenNestedContainerColor
    }
    val borderColor = when (tone) {
        PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
        PrivacyPermissionTone.Ready -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        PrivacyPermissionTone.Off -> Color.Transparent
    }
    val statusColor = privacyPermissionStatusColor(tone)

    Column(
        modifier = Modifier
            .widthIn(min = 158.dp)
            .heightIn(min = LumenMinTouchTargetHeight)
            .clip(LumenPreferenceShape)
            .background(tileColor)
            .border(if (borderColor == Color.Transparent) 0.dp else 1.dp, borderColor, LumenPreferenceShape)
            .toggleable(
                value = tile.checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrivacyPermissionChip(tile.icon, tone)
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
            color = statusColor,
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
    sensitive: Boolean = false,
    activeTarget: PermissionSetupTarget?,
    onConfigureTarget: (PermissionSetupTarget) -> Unit,
    onTargetCheckedChange: (PermissionSetupTarget, Boolean) -> Unit,
) {
    val active = activeTarget == target
    val actionNeeded = featureEnabled && !ready
    val tone = when {
        !featureEnabled -> PrivacyPermissionTone.Off
        actionNeeded -> PrivacyPermissionTone.Attention
        else -> PrivacyPermissionTone.Ready
    }
    val statusRes = when (tone) {
        PrivacyPermissionTone.Off -> R.string.settings_permission_off
        PrivacyPermissionTone.Attention -> R.string.settings_permission_action_needed
        PrivacyPermissionTone.Ready -> R.string.settings_permission_ready
    }
    val statusColor = privacyPermissionStatusColor(tone)
    // A camera, overlay or Shizuku grant must not look like an ordinary toggle, so a live
    // sensitive capability keeps a tertiary tint while an unmet one escalates to error.
    val rowColor = when {
        active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        tone == PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.38f)
        sensitive && featureEnabled -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f)
        else -> lumenNestedContainerColor
    }
    val rowBorderColor = when {
        tone == PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.error.copy(alpha = 0.42f)
        sensitive -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f)
        else -> Color.Transparent
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(rowColor)
            .border(
                if (rowBorderColor == Color.Transparent) 0.dp else 1.dp,
                rowBorderColor,
                LumenPreferenceShape,
            )
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SettingsPreferenceInnerGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PrivacyPermissionChip(icon, tone)
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
private fun PrivacyReadinessBadge(
    readinessScore: Int,
    actionNeededCount: Int,
) {
    val label: String
    val containerColor: androidx.compose.ui.graphics.Color
    val contentColor: androidx.compose.ui.graphics.Color
    when {
        actionNeededCount > 0 -> {
            label = stringResource(R.string.settings_privacy_badge_action_needed, actionNeededCount)
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        }
        readinessScore >= 100 -> {
            label = stringResource(R.string.settings_privacy_badge_all_ready)
            containerColor = MaterialTheme.colorScheme.primaryContainer
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        }
        else -> {
            label = stringResource(R.string.percent_value, readinessScore)
            containerColor = MaterialTheme.colorScheme.secondaryContainer
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = contentColor,
    )
}

/** Whether a permission row/tile is satisfied, waiting on the user, or switched off. */
private enum class PrivacyPermissionTone {
    Ready,
    Attention,
    Off,
}

@Composable
private fun privacyPermissionStatusColor(tone: PrivacyPermissionTone): Color = when (tone) {
    PrivacyPermissionTone.Ready -> MaterialTheme.colorScheme.primary
    PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.error
    PrivacyPermissionTone.Off -> MaterialTheme.colorScheme.onSurfaceVariant
}

// Keeps LumenIconChip's 36dp/LumenIconChipShape geometry but swaps in the semantic
// container pair, so a badge carries the permission's state instead of only its topic.
@Composable
private fun PrivacyPermissionChip(icon: ImageVector, tone: PrivacyPermissionTone) {
    val containerColor = when (tone) {
        PrivacyPermissionTone.Ready -> MaterialTheme.colorScheme.primaryContainer
        PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.errorContainer
        PrivacyPermissionTone.Off -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (tone) {
        PrivacyPermissionTone.Ready -> MaterialTheme.colorScheme.onPrimaryContainer
        PrivacyPermissionTone.Attention -> MaterialTheme.colorScheme.onErrorContainer
        PrivacyPermissionTone.Off -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(LumenIconChipShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}
