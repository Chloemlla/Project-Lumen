package com.projectlumen.app.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectlumen.app.R
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.shizuku.ShizukuCapabilityState
import kotlin.math.roundToInt

@Composable
internal fun DeveloperDebugScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    onPreviewCrashReport: (CrashReport) -> Unit,
) {
    val settings = uiState.settings
    val runtime = uiState.runtime
    val context = LocalContext.current
    val permissionRequirements = rememberPermissionRequirements()
    val shizukuState = viewModel.shizukuState.collectAsStateWithLifecycle().value
    val luxHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(runtime.ambientLastLux, uiState.nowMillis) {
        luxHistory += runtime.ambientLastLux.coerceAtLeast(0f)
        while (luxHistory.size > 60) luxHistory.removeAt(0)
    }
    LumenPage {
        SettingsSection(R.string.developer_section_realtime_debug, Icons.Outlined.Visibility) {
            SwitchRow(R.string.developer_overlay_enabled, Icons.Outlined.Visibility, settings.developerDebugOverlayEnabled) {
                viewModel.updateSettings { current -> current.copy(developerDebugOverlayEnabled = it) }
                if (it && needsOverlayPermission(context)) {
                    openOverlaySettings(context)
                }
            }
            SwitchRow(R.string.developer_preview_enabled, Icons.Outlined.PhotoCamera, settings.developerDebugPreviewEnabled) {
                viewModel.updateSettings { current -> current.copy(developerDebugPreviewEnabled = it) }
            }
            if (settings.developerDebugOverlayEnabled && permissionRequirements.overlay) {
                OutlinedButton(onClick = { openOverlaySettings(context) }) {
                    ButtonLabel(Icons.Outlined.Visibility, R.string.open_system_settings)
                }
            }
            MetricRow(R.string.developer_ai_inference, "${runtime.proximityDebugInferenceMillis} ms")
            MetricRow(R.string.developer_camera_latency, "${runtime.proximityDebugCameraLatencyMillis} ms")
            MetricRow(R.string.developer_face_width, "${runtime.proximityDebugFaceWidthPx} px")
            MetricRow(R.string.developer_face_ratio, "${runtime.proximityLastRatioPercent}%")
        }

        SettingsSection(R.string.developer_section_triggers, Icons.Outlined.Schedule) {
            NumberSlider(
                R.string.developer_tick_interval,
                Icons.Outlined.Schedule,
                settings.developerTickIntervalSeconds,
                10f..1800f,
                178,
                developerIntervalLabel(settings.developerTickIntervalSeconds),
            ) {
                viewModel.updateSettings { current -> current.copy(developerTickIntervalSeconds = it.coerceIn(10, 1800)) }
            }
            SwitchRow(R.string.developer_time_trigger, Icons.Outlined.Schedule, settings.developerTimeTriggerEnabled) {
                viewModel.updateSettings { current -> current.copy(developerTimeTriggerEnabled = it) }
            }
            SwitchRow(R.string.developer_unlock_trigger, Icons.Outlined.Settings, settings.developerUnlockTriggerEnabled) {
                viewModel.updateSettings { current -> current.copy(developerUnlockTriggerEnabled = it) }
            }
            SwitchRow(R.string.developer_stillness_trigger, Icons.Outlined.Sensors, settings.developerStillnessTriggerEnabled) {
                viewModel.updateSettings { current -> current.copy(developerStillnessTriggerEnabled = it) }
            }
            SwitchRow(R.string.developer_shake_suppression, Icons.Outlined.Sensors, settings.developerShakeSuppressionEnabled) {
                viewModel.updateSettings { current -> current.copy(developerShakeSuppressionEnabled = it) }
            }
        }

        SettingsSection(R.string.developer_section_system, Icons.Outlined.BatterySaver) {
            MetricRow(
                R.string.developer_battery_optimization,
                if (isIgnoringBatteryOptimizations(context)) {
                    stringResource(R.string.developer_battery_whitelisted)
                } else {
                    stringResource(R.string.developer_battery_limited)
                },
            )
            OutlinedButton(onClick = { openBatteryOptimizationSettings(context) }) {
                ButtonLabel(Icons.Outlined.BatterySaver, R.string.developer_open_battery_settings)
            }
            MetricRow(R.string.developer_service_uptime, serviceUptimeLabel(runtime.foregroundServiceStartedAt, runtime.foregroundServiceStoppedAt, uiState.nowMillis))
            MetricRow(R.string.developer_service_last_restart, timestampLabel(runtime.foregroundServiceLastStickyRestartAt))
            MetricRow(R.string.developer_service_task_removed, timestampLabel(runtime.foregroundServiceLastTaskRemovedAt))
            MetricRow(R.string.shizuku_status, developerShizukuStatusLabel(shizukuState))
            MetricRow(R.string.shizuku_foreground_context, developerShizukuContextLabel(shizukuState))
            MetricRow(R.string.shizuku_system_guards, developerShizukuSystemGuardLabel(settings, shizukuState))
            OutlinedButton(onClick = viewModel::refreshShizukuState) {
                ButtonLabel(Icons.Outlined.Settings, R.string.shizuku_refresh_status)
            }
            MetricRow(R.string.developer_low_memory_last, timestampLabel(runtime.developerLastLowMemorySimulatedAt))
            Button(onClick = viewModel::simulateLowMemory) {
                ButtonLabel(Icons.Outlined.Memory, R.string.developer_simulate_low_memory)
            }
        }

        SettingsSection(R.string.developer_section_raw_sensors, Icons.Outlined.Sensors) {
            MetricRow(R.string.developer_lux, "%.1f lux".format(runtime.ambientLastLux))
            LuxCurve(luxHistory)
            MetricRow(R.string.developer_pitch, "${runtime.sensorPitchDegrees.roundToInt()} deg")
            MetricRow(R.string.developer_roll, "${runtime.sensorRollDegrees.roundToInt()} deg")
            MetricRow(R.string.developer_yaw, "${runtime.sensorYawDegrees.roundToInt()} deg")
            MetricRow(R.string.developer_acceleration, "%.2f m/s2".format(runtime.sensorLastAccelerationMagnitude))
        }

        SettingsSection(R.string.developer_section_crash, Icons.Outlined.BugReport) {
            Text(stringResource(R.string.developer_crash_preview_message))
            Button(onClick = { onPreviewCrashReport(createDeveloperCrashPreview()) }) {
                ButtonLabel(Icons.Outlined.BugReport, R.string.developer_preview_crash_page)
            }
        }

        SettingsSection(R.string.developer_section_mode, Icons.Outlined.Code) {
            SwitchRow(R.string.developer_mode_enabled, Icons.Outlined.Code, settings.developerModeEnabled) {
                viewModel.updateSettings { current ->
                    current.copy(
                        developerModeEnabled = it,
                        developerDebugOverlayEnabled = if (it) current.developerDebugOverlayEnabled else false,
                        developerDebugPreviewEnabled = if (it) current.developerDebugPreviewEnabled else false,
                    )
                }
            }
        }
    }
}

@Composable
private fun LuxCurve(values: List<Float>) {
    val lineColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val gridColor = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val maxLux = values.maxOrNull()?.coerceAtLeast(10f) ?: 10f
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { index, lux ->
            val x = size.width * index / (values.lastIndex).coerceAtLeast(1).toFloat()
            val y = size.height - (lux.coerceIn(0f, maxLux) / maxLux) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}

private fun developerIntervalLabel(seconds: Int): String {
    return if (seconds < 60) {
        "${seconds}s"
    } else {
        "${seconds / 60}m ${seconds % 60}s"
    }
}

private fun serviceUptimeLabel(startedAt: Long, stoppedAt: Long, nowMillis: Long): String {
    if (startedAt <= 0L || stoppedAt >= startedAt) return "0s"
    val seconds = ((nowMillis - startedAt) / 1000L).coerceAtLeast(0L)
    val minutes = seconds / 60L
    val remainingSeconds = seconds % 60L
    return if (minutes > 0L) "${minutes}m ${remainingSeconds}s" else "${remainingSeconds}s"
}

private fun timestampLabel(value: Long): String {
    return if (value > 0L) {
        java.time.Instant.ofEpochMilli(value)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    } else {
        "-"
    }
}

@Composable
private fun developerShizukuStatusLabel(state: ShizukuCapabilityState): String {
    return when {
        state.ready -> stringResource(R.string.shizuku_status_ready)
        !state.binderAvailable -> stringResource(R.string.shizuku_status_no_service)
        !state.permissionGranted -> stringResource(R.string.shizuku_status_permission_needed)
        else -> stringResource(R.string.shizuku_status_unavailable)
    }
}

@Composable
private fun developerShizukuContextLabel(state: ShizukuCapabilityState): String {
    if (state.foregroundPackage.isBlank()) return "-"
    return if (state.foregroundShouldDeferSampling) {
        stringResource(R.string.shizuku_context_deferred, state.foregroundCategory)
    } else {
        "${state.foregroundPackage}/${state.foregroundActivity.substringAfterLast('.')}"
    }
}

@Composable
private fun developerShizukuSystemGuardLabel(settings: AppSettingsEntity, state: ShizukuCapabilityState): String {
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
    if (settings.shizukuThermalGuardEnabled && state.thermalStatus >= 2) {
        reasons += stringResource(R.string.shizuku_guard_reason_thermal, state.thermalStatus)
    }
    if (settings.shizukuCameraPrivacyGuardEnabled && state.cameraPrivacyEnabled) {
        reasons += stringResource(R.string.shizuku_guard_reason_camera_privacy)
    }
    return if (reasons.isEmpty()) {
        stringResource(R.string.shizuku_system_normal)
    } else {
        stringResource(R.string.shizuku_system_deferred, reasons.joinToString(", "))
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(PowerManager::class.java)
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun openBatteryOptimizationSettings(context: Context) {
    val packageUri = "package:${context.packageName}".toUri()
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)
    runCatching { context.startActivity(requestIntent) }
        .onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { openAppNotificationSettings(context) }
        }
}

private fun createDeveloperCrashPreview(): CrashReport {
    return CrashReport.fromThrowable(
        IllegalStateException(
            "Developer crash page preview. This is a simulated report and no real crash occurred.",
        ),
    )
}
