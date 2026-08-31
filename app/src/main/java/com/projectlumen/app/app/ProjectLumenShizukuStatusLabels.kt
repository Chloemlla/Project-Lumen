package com.projectlumen.app.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.shizuku.ShizukuCapabilityState

@Composable
internal fun shizukuStatusLabel(state: ShizukuCapabilityState): String {
    return when {
        state.ready -> stringResource(R.string.shizuku_status_ready)
        !state.binderAvailable -> stringResource(R.string.shizuku_status_no_service)
        !state.permissionGranted -> stringResource(R.string.shizuku_status_permission_needed)
        else -> stringResource(R.string.shizuku_status_unavailable)
    }
}

@Composable
internal fun shizukuSystemGuardLabel(settings: AppSettingsEntity, state: ShizukuCapabilityState): String {
    val reasons = shizukuSystemGuardReasons(settings, state)
    return if (reasons.isEmpty()) {
        stringResource(R.string.shizuku_system_normal)
    } else {
        stringResource(R.string.shizuku_system_deferred, reasons.joinToString(", "))
    }
}

@Composable
internal fun shizukuSystemGuardReasons(settings: AppSettingsEntity, state: ShizukuCapabilityState): List<String> {
    val reasons = mutableListOf<String>()
    if (settings.shizukuScreenOffGuardEnabled && !state.deviceInteractive) {
        reasons += stringResource(R.string.shizuku_guard_reason_screen_off)
    }
    if (settings.shizukuLowBatteryGuardEnabled && state.lowBatteryActive) {
        reasons += stringResource(R.string.shizuku_guard_reason_low_battery)
    }
    if (settings.shizukuPowerSaveGuardEnabled && state.powerSaveActive) {
        reasons += stringResource(R.string.shizuku_guard_reason_power_save)
    }
    if (settings.shizukuDndGuardEnabled && state.dndActive) {
        reasons += stringResource(R.string.shizuku_guard_reason_dnd)
    }
    if (settings.shizukuThermalGuardEnabled && state.thermalStatus >= SHIZUKU_THERMAL_GUARD_STATUS_THRESHOLD) {
        reasons += stringResource(R.string.shizuku_guard_reason_thermal, state.thermalStatus)
    }
    if (settings.shizukuCameraPrivacyGuardEnabled && state.cameraPrivacyEnabled) {
        reasons += stringResource(R.string.shizuku_guard_reason_camera_privacy)
    }
    return reasons
}

// PowerManager.THERMAL_STATUS_MODERATE: sampling defers from "moderate" upwards.
private const val SHIZUKU_THERMAL_GUARD_STATUS_THRESHOLD = 2
