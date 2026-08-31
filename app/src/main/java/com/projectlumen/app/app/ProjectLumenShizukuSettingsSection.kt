package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.shizuku.ShizukuCapabilityState
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun ShizukuAdvancedSettingsSection(
    settings: AppSettingsEntity,
    state: ShizukuCapabilityState,
    viewModel: ProjectLumenViewModel,
    backendFeaturesVisible: Boolean,
    forceExpanded: Boolean = false,
) {
    SettingsSection(
        R.string.section_shizuku_advanced,
        Icons.Outlined.Lock,
        initiallyExpanded = false,
        forceExpanded = forceExpanded,
    ) {
        ShizukuNotice(
            Icons.Outlined.Lock,
            stringResource(R.string.shizuku_advanced_summary),
            ShizukuNoticeTone.Elevated,
        )
        ShizukuStatusSummary(settings, state)
        SwitchRow(R.string.enable_shizuku_advanced_mode, Icons.Outlined.Lock, settings.shizukuAdvancedModeEnabled) { enabled ->
            viewModel.updateSettings { current ->
                current.copy(shizukuAdvancedModeEnabled = enabled)
            }
            if (enabled) viewModel.requestShizukuAuthorization()
        }
        AnimatedVisibility(
            visible = settings.shizukuAdvancedModeEnabled,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
                ShizukuQuickActions(settings, state, viewModel)
                if (backendFeaturesVisible) {
                    ShizukuDiagnosticUploadSettings(settings, state, viewModel)
                }
                ShizukuNativeEyeProtectionSettings(settings, viewModel)
                ShizukuSamplingGuardSettings(settings, viewModel)
                Text(
                    stringResource(R.string.shizuku_privacy_boundary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShizukuStatusSummary(settings: AppSettingsEntity, state: ShizukuCapabilityState) {
    ShizukuAuthorizationBanner(state)
    if (state.lastCheckedAt > 0L) {
        StatusLine(Icons.Outlined.Info, shizukuStatusDetailLabel(state))
    }
    if (state.lastError.isNotBlank()) {
        ShizukuNotice(
            Icons.Outlined.WarningAmber,
            stringResource(R.string.shizuku_last_error, state.lastError),
            ShizukuNoticeTone.Critical,
        )
    }
    if (state.foregroundPackage.isNotBlank()) {
        StatusLine(Icons.Outlined.PhotoCamera, shizukuForegroundLabel(state))
    }
    if (state.ready) {
        StatusLine(Icons.Outlined.Settings, shizukuSystemGuardLabel(settings, state))
    }
    if (state.nativeEyeProtectionApplied) {
        StatusLine(
            Icons.Outlined.Style,
            stringResource(
                R.string.shizuku_native_eye_protection_applied,
                state.nativeColorTemperatureKelvin,
                state.nativeBrightnessPercent,
                state.nativeExtraDimPercent,
            ),
        )
    }
}

@Composable
private fun ShizukuDiagnosticUploadSettings(
    settings: AppSettingsEntity,
    state: ShizukuCapabilityState,
    viewModel: ProjectLumenViewModel,
) {
    ShizukuGroupTitle(R.string.shizuku_group_diagnostics)
    SwitchRow(
        R.string.enable_diagnostic_telemetry_upload,
        Icons.Outlined.Info,
        settings.diagnosticTelemetryUploadEnabled,
    ) { enabled ->
        viewModel.updateSettings { current ->
            current.copy(
                diagnosticTelemetryUploadEnabled = enabled,
                diagnosticCrashReportUploadEnabled = if (enabled) {
                    current.diagnosticCrashReportUploadEnabled
                } else {
                    false
                },
                shizukuAppInventoryUploadEnabled = if (enabled) {
                    current.shizukuAppInventoryUploadEnabled
                } else {
                    false
                },
                diagnosticFaceAnalysisUploadEnabled = if (enabled) {
                    current.diagnosticFaceAnalysisUploadEnabled
                } else {
                    false
                },
            )
        }
    }
    AnimatedVisibility(
        visible = settings.diagnosticTelemetryUploadEnabled,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
            Text(
                stringResource(R.string.diagnostic_telemetry_upload_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LumenFlowRow {
                Button(onClick = viewModel::uploadDiagnosticsNow) {
                    ButtonLabel(Icons.Outlined.Refresh, R.string.upload_diagnostics_now)
                }
            }
            SwitchRow(
                R.string.enable_diagnostic_crash_upload,
                Icons.Outlined.WarningAmber,
                settings.diagnosticCrashReportUploadEnabled,
            ) { enabled ->
                viewModel.updateSettings { current ->
                    current.copy(diagnosticCrashReportUploadEnabled = enabled)
                }
            }
            SwitchRow(
                R.string.enable_shizuku_app_inventory_upload,
                Icons.Outlined.Settings,
                settings.shizukuAppInventoryUploadEnabled,
            ) { enabled ->
                viewModel.updateSettings { current ->
                    current.copy(
                        diagnosticTelemetryUploadEnabled = true,
                        shizukuAdvancedModeEnabled = if (enabled) true else current.shizukuAdvancedModeEnabled,
                        shizukuAppInventoryUploadEnabled = enabled,
                    )
                }
                if (enabled) viewModel.requestShizukuAuthorization()
            }
            if (settings.shizukuAppInventoryUploadEnabled && !state.ready) {
                ShizukuNotice(
                    Icons.Outlined.Lock,
                    stringResource(R.string.shizuku_app_inventory_authorization_needed),
                    ShizukuNoticeTone.Attention,
                )
            }
            Text(
                stringResource(R.string.diagnostic_security_policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShizukuQuickActions(
    settings: AppSettingsEntity,
    state: ShizukuCapabilityState,
    viewModel: ProjectLumenViewModel,
) {
    ShizukuGroupTitle(R.string.shizuku_quick_actions)
    LumenFlowRow {
        Button(onClick = {
            applyShizukuCorePreset(viewModel)
        }) {
            ButtonLabel(Icons.Outlined.CheckCircle, R.string.shizuku_quick_enable_core)
        }
        OutlinedButton(onClick = {
            applyShizukuSmartGuardPreset(viewModel)
        }) {
            ButtonLabel(Icons.Outlined.Schedule, R.string.shizuku_quick_smart_guards)
        }
        OutlinedButton(onClick = {
            applyShizukuComfortShieldPreset(viewModel)
        }) {
            ButtonLabel(Icons.Outlined.Spa, R.string.shizuku_quick_comfort_shield)
        }
        if (settings.shizukuNativeEyeProtectionEnabled || state.nativeEyeProtectionApplied) {
            OutlinedButton(onClick = {
                disableShizukuNativeShield(viewModel)
            }) {
                ButtonLabel(Icons.Outlined.Style, R.string.shizuku_quick_native_off)
            }
        }
        OutlinedButton(onClick = viewModel::refreshShizukuState) {
            ButtonLabel(Icons.Outlined.Refresh, R.string.shizuku_refresh_status)
        }
        if (!state.ready) {
            OutlinedButton(onClick = viewModel::requestShizukuAuthorization) {
                ButtonLabel(Icons.Outlined.Lock, R.string.shizuku_authorize)
            }
        }
    }
}

@Composable
private fun ShizukuNativeEyeProtectionSettings(
    settings: AppSettingsEntity,
    viewModel: ProjectLumenViewModel,
) {
    ShizukuGroupTitle(R.string.shizuku_group_native_eye)
    SwitchRow(
        R.string.enable_shizuku_native_eye_protection,
        Icons.Outlined.Style,
        settings.shizukuNativeEyeProtectionEnabled,
    ) { enabled ->
        viewModel.updateSettings { current -> current.copy(shizukuNativeEyeProtectionEnabled = enabled) }
        if (enabled) viewModel.requestShizukuAuthorization()
    }
    AnimatedVisibility(
        visible = settings.shizukuNativeEyeProtectionEnabled,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
            Text(
                stringResource(R.string.shizuku_native_eye_protection_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberSlider(
                R.string.shizuku_native_color_temperature,
                Icons.Outlined.Style,
                settings.shizukuNativeColorTemperatureKelvin,
                1800f..6500f,
                46,
                stringResource(R.string.kelvin_value, settings.shizukuNativeColorTemperatureKelvin),
            ) {
                viewModel.updateSettings { current ->
                    current.copy(shizukuNativeColorTemperatureKelvin = it)
                }
            }
            NumberSlider(
                R.string.shizuku_native_brightness,
                Icons.Outlined.Settings,
                settings.shizukuNativeBrightnessPercent,
                1f..100f,
                98,
                stringResource(R.string.percent_value, settings.shizukuNativeBrightnessPercent),
            ) {
                viewModel.updateSettings { current ->
                    current.copy(shizukuNativeBrightnessPercent = it)
                }
            }
            SwitchRow(
                R.string.shizuku_native_extra_dim,
                Icons.Outlined.WarningAmber,
                settings.shizukuNativeExtraDimEnabled,
            ) {
                viewModel.updateSettings { current -> current.copy(shizukuNativeExtraDimEnabled = it) }
            }
            AnimatedVisibility(
                visible = settings.shizukuNativeExtraDimEnabled,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NumberSlider(
                    R.string.shizuku_native_extra_dim_intensity,
                    Icons.Outlined.WarningAmber,
                    settings.shizukuNativeExtraDimPercent,
                    1f..100f,
                    98,
                    stringResource(R.string.percent_value, settings.shizukuNativeExtraDimPercent),
                ) {
                    viewModel.updateSettings { current ->
                        current.copy(shizukuNativeExtraDimPercent = it)
                    }
                }
            }
            Text(
                stringResource(R.string.shizuku_native_boundary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShizukuSamplingGuardSettings(
    settings: AppSettingsEntity,
    viewModel: ProjectLumenViewModel,
) {
    ShizukuGroupTitle(R.string.shizuku_group_sampling_guards)
    SwitchRow(
        R.string.shizuku_context_aware_sampling,
        Icons.Outlined.PhotoCamera,
        settings.shizukuContextAwareSamplingEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuContextAwareSamplingEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_service_recovery,
        Icons.Outlined.Schedule,
        settings.shizukuServiceRecoveryEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuServiceRecoveryEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_screen_off_guard,
        Icons.Outlined.Lock,
        settings.shizukuScreenOffGuardEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuScreenOffGuardEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_low_battery_guard,
        Icons.Outlined.WarningAmber,
        settings.shizukuLowBatteryGuardEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuLowBatteryGuardEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_power_save_guard,
        Icons.Outlined.Schedule,
        settings.shizukuPowerSaveGuardEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuPowerSaveGuardEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_dnd_guard,
        Icons.Outlined.NotificationsActive,
        settings.shizukuDndGuardEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuDndGuardEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_thermal_guard,
        Icons.Outlined.WarningAmber,
        settings.shizukuThermalGuardEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuThermalGuardEnabled = it) }
    }
    SwitchRow(
        R.string.shizuku_camera_privacy_guard,
        Icons.Outlined.PhotoCamera,
        settings.shizukuCameraPrivacyGuardEnabled,
    ) {
        viewModel.updateSettings { current -> current.copy(shizukuCameraPrivacyGuardEnabled = it) }
    }
}

@Composable
private fun ShizukuGroupTitle(labelRes: Int) {
    Text(
        stringResource(labelRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * How loudly a Shizuku notice should read: [Elevated] for the privilege this section grants,
 * [Attention] for something the user still has to do, [Critical] for a failure.
 */
private enum class ShizukuNoticeTone {
    Elevated,
    Attention,
    Critical,
}

@Composable
private fun ShizukuNotice(icon: ImageVector, text: String, tone: ShizukuNoticeTone) {
    val containerColor = when (tone) {
        ShizukuNoticeTone.Elevated -> MaterialTheme.colorScheme.tertiaryContainer
        ShizukuNoticeTone.Attention -> MaterialTheme.colorScheme.secondaryContainer
        ShizukuNoticeTone.Critical -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (tone) {
        ShizukuNoticeTone.Elevated -> MaterialTheme.colorScheme.onTertiaryContainer
        ShizukuNoticeTone.Attention -> MaterialTheme.colorScheme.onSecondaryContainer
        ShizukuNoticeTone.Critical -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(containerColor)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
    }
}

@Composable
private fun ShizukuAuthorizationBanner(state: ShizukuCapabilityState) {
    val containerColor = if (state.ready) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (state.ready) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(containerColor)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (state.ready) Icons.Outlined.CheckCircle else Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.shizuku_status),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
            )
            Text(
                shizukuStatusLabel(state),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}

private fun applyShizukuCorePreset(viewModel: ProjectLumenViewModel) {
    viewModel.updateSettings { current ->
        current.copy(
            shizukuAdvancedModeEnabled = true,
            shizukuContextAwareSamplingEnabled = true,
            shizukuServiceRecoveryEnabled = true,
        )
    }
    viewModel.requestShizukuAuthorization()
}

private fun applyShizukuSmartGuardPreset(viewModel: ProjectLumenViewModel) {
    viewModel.updateSettings { current ->
        current.copy(
            shizukuAdvancedModeEnabled = true,
            shizukuContextAwareSamplingEnabled = true,
            shizukuServiceRecoveryEnabled = true,
            shizukuScreenOffGuardEnabled = true,
            shizukuLowBatteryGuardEnabled = true,
            shizukuPowerSaveGuardEnabled = true,
            shizukuDndGuardEnabled = true,
            shizukuThermalGuardEnabled = true,
            shizukuCameraPrivacyGuardEnabled = true,
        )
    }
    viewModel.requestShizukuAuthorization()
}

private fun applyShizukuComfortShieldPreset(viewModel: ProjectLumenViewModel) {
    viewModel.updateSettings { current ->
        current.copy(
            shizukuAdvancedModeEnabled = true,
            shizukuNativeEyeProtectionEnabled = true,
            shizukuNativeColorTemperatureKelvin = 3800,
            shizukuNativeBrightnessPercent = 35,
            shizukuNativeExtraDimEnabled = true,
            shizukuNativeExtraDimPercent = 20,
        )
    }
    viewModel.requestShizukuAuthorization()
}

private fun disableShizukuNativeShield(viewModel: ProjectLumenViewModel) {
    viewModel.updateSettings { current ->
        current.copy(
            shizukuNativeEyeProtectionEnabled = false,
            shizukuNativeExtraDimEnabled = false,
        )
    }
}

@Composable
private fun shizukuStatusDetailLabel(state: ShizukuCapabilityState): String {
    val checkedAt = shizukuCheckedAtLabel(state.lastCheckedAt)
    return if (state.ready) {
        stringResource(R.string.shizuku_status_detail_ready, state.serverVersion, state.serverUid, checkedAt)
    } else {
        stringResource(R.string.shizuku_status_detail_checked, checkedAt)
    }
}

@Composable
private fun shizukuForegroundLabel(state: ShizukuCapabilityState): String {
    return if (state.foregroundShouldDeferSampling) {
        stringResource(
            R.string.shizuku_context_deferred_detail,
            state.foregroundCategory,
            state.foregroundPackage,
        )
    } else {
        stringResource(R.string.shizuku_context_normal_detail, state.foregroundPackage)
    }
}

@Composable
private fun shizukuCheckedAtLabel(lastCheckedAt: Long): String {
    if (lastCheckedAt <= 0L) return stringResource(R.string.shizuku_checked_never)
    return Instant.ofEpochMilli(lastCheckedAt)
        .atZone(ZoneId.systemDefault())
        .format(updateDialogTimeFormatter)
}
