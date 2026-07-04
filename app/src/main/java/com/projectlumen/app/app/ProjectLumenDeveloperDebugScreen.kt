package com.projectlumen.app.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.api.CertificatePinPolicy
import com.projectlumen.app.core.api.ProjectLumenApiConfig
import com.projectlumen.app.core.api.ProjectLumenApiTrace
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
    val remoteState = viewModel.remoteState.collectAsStateWithLifecycle().value
    val apiTraces = viewModel.apiDiagnostics.collectAsStateWithLifecycle().value
    val luxHistory = remember { mutableStateListOf<Float>() }
    var purchaseProductId by rememberSaveable { mutableStateOf("project_lumen_pro") }
    var purchaseToken by rememberSaveable { mutableStateOf("") }
    val normalizedPurchaseProductId = purchaseProductId.trim()
    val normalizedPurchaseToken = purchaseToken.trim()
    LaunchedEffect(runtime.ambientLastLux, uiState.nowMillis) {
        luxHistory += runtime.ambientLastLux.coerceAtLeast(0f)
        while (luxHistory.size > 60) luxHistory.removeAt(0)
    }
    LumenPage {
        SettingsSection(R.string.developer_section_realtime_debug, Icons.Outlined.Visibility) {
            SwitchRow(
                R.string.developer_overlay_enabled,
                Icons.Outlined.Visibility,
                settings.developerDebugOverlayEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerDebugOverlayEnabled = it) }
                if (it && needsOverlayPermission(context)) {
                    openOverlaySettings(context)
                }
            }
            SwitchRow(
                R.string.developer_preview_enabled,
                Icons.Outlined.PhotoCamera,
                settings.developerDebugPreviewEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerDebugPreviewEnabled = it) }
            }
            if (settings.developerDebugOverlayEnabled && permissionRequirements.overlay) {
                OutlinedButton(
                    onClick = { openOverlaySettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DeveloperButtonLabel(Icons.Outlined.Visibility, R.string.open_system_settings)
                }
            }
            DeveloperMetricRow(R.string.developer_ai_inference, "${runtime.proximityDebugInferenceMillis} ms")
            DeveloperMetricRow(R.string.developer_camera_latency, "${runtime.proximityDebugCameraLatencyMillis} ms")
            DeveloperMetricRow(R.string.developer_face_width, "${runtime.proximityDebugFaceWidthPx} px")
            DeveloperMetricRow(R.string.developer_face_ratio, "${runtime.proximityLastRatioPercent}%")
        }

        SettingsSection(R.string.developer_section_triggers, Icons.Outlined.Schedule) {
            NumberSlider(
                R.string.developer_tick_interval,
                Icons.Outlined.Schedule,
                settings.developerTickIntervalSeconds,
                10f..1800f,
                178,
                developerIntervalLabel(settings.developerTickIntervalSeconds),
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerTickIntervalSeconds = it.coerceIn(10, 1800)) }
            }
            SwitchRow(
                R.string.developer_time_trigger,
                Icons.Outlined.Schedule,
                settings.developerTimeTriggerEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerTimeTriggerEnabled = it) }
            }
            SwitchRow(
                R.string.developer_unlock_trigger,
                Icons.Outlined.Settings,
                settings.developerUnlockTriggerEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerUnlockTriggerEnabled = it) }
            }
            SwitchRow(
                R.string.developer_stillness_trigger,
                Icons.Outlined.Sensors,
                settings.developerStillnessTriggerEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerStillnessTriggerEnabled = it) }
            }
            SwitchRow(
                R.string.developer_shake_suppression,
                Icons.Outlined.Sensors,
                settings.developerShakeSuppressionEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
                viewModel.updateSettings { current -> current.copy(developerShakeSuppressionEnabled = it) }
            }
        }

        SettingsSection(R.string.developer_section_system, Icons.Outlined.BatterySaver) {
            DeveloperMetricRow(
                R.string.developer_battery_optimization,
                if (isIgnoringBatteryOptimizations(context)) {
                    stringResource(R.string.developer_battery_whitelisted)
                } else {
                    stringResource(R.string.developer_battery_limited)
                },
            )
            OutlinedButton(
                onClick = { openBatteryOptimizationSettings(context) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                DeveloperButtonLabel(Icons.Outlined.BatterySaver, R.string.developer_open_battery_settings)
            }
            DeveloperMetricRow(R.string.developer_service_uptime, serviceUptimeLabel(runtime.foregroundServiceStartedAt, runtime.foregroundServiceStoppedAt, uiState.nowMillis))
            DeveloperMetricRow(R.string.developer_service_last_restart, timestampLabel(runtime.foregroundServiceLastStickyRestartAt))
            DeveloperMetricRow(R.string.developer_service_task_removed, timestampLabel(runtime.foregroundServiceLastTaskRemovedAt))
            DeveloperMetricRow(R.string.shizuku_status, developerShizukuStatusLabel(shizukuState))
            DeveloperMetricRow(R.string.shizuku_foreground_context, developerShizukuContextLabel(shizukuState))
            DeveloperMetricRow(R.string.shizuku_system_guards, developerShizukuSystemGuardLabel(settings, shizukuState))
            OutlinedButton(
                onClick = viewModel::refreshShizukuState,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DeveloperButtonLabel(Icons.Outlined.Settings, R.string.shizuku_refresh_status)
            }
            DeveloperMetricRow(R.string.developer_low_memory_last, timestampLabel(runtime.developerLastLowMemorySimulatedAt))
            Button(
                onClick = viewModel::simulateLowMemory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                DeveloperButtonLabel(Icons.Outlined.Memory, R.string.developer_simulate_low_memory)
            }
        }

        SettingsSection(R.string.developer_section_api_security, Icons.Outlined.Lock) {
            DeveloperMetricRow(R.string.developer_security_api_base, ProjectLumenApiConfig.baseUrl)
            DeveloperMetricRow(R.string.developer_security_cleartext, stringResource(R.string.developer_security_cleartext_blocked))
            DeveloperMetricRow(
                R.string.developer_security_api_pins,
                securityPinStatus(
                    pinCount = configuredPinCount(ProjectLumenApiConfig.apiCertificatePins),
                    required = false,
                    optional = true,
                ),
            )
            DeveloperMetricRow(R.string.developer_security_translation_base, ProjectLumenApiConfig.translationBaseUrl)
            DeveloperMetricRow(
                R.string.developer_security_translation_pins,
                securityPinStatus(
                    pinCount = configuredPinCount(ProjectLumenApiConfig.translationCertificatePins),
                    required = false,
                    optional = true,
                ),
            )
            DeveloperMetricRow(R.string.developer_security_request_signing, stringResource(R.string.developer_security_request_signing_enabled))
            DeveloperMetricRow(R.string.developer_security_credentials, secureCredentialStatus(context))
            DeveloperMetricRow(
                R.string.developer_security_webview_bridge,
                if (BuildConfig.DEBUG) {
                    stringResource(R.string.developer_security_webview_debug_only)
                } else {
                    stringResource(R.string.developer_security_webview_disabled)
                },
            )
            SwitchRow(
                R.string.enable_diagnostic_face_analysis_upload,
                Icons.Outlined.PhotoCamera,
                settings.diagnosticFaceAnalysisUploadEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) { enabled ->
                viewModel.updateSettings { current ->
                    current.copy(
                        diagnosticTelemetryUploadEnabled = if (enabled) true else current.diagnosticTelemetryUploadEnabled,
                        diagnosticFaceAnalysisUploadEnabled = enabled,
                    )
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = purchaseProductId,
                onValueChange = { purchaseProductId = it },
                enabled = !remoteState.busy,
                singleLine = true,
                label = { Text(stringResource(R.string.remote_cloud_product_id)) },
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = purchaseToken,
                onValueChange = { purchaseToken = it },
                enabled = !remoteState.busy,
                singleLine = true,
                label = { Text(stringResource(R.string.remote_cloud_purchase_token)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = remoteState.signedIn &&
                    !remoteState.busy &&
                    normalizedPurchaseProductId.isNotBlank() &&
                    normalizedPurchaseToken.isNotBlank(),
                onClick = {
                    viewModel.verifyGooglePurchase(normalizedPurchaseProductId, normalizedPurchaseToken)
                    purchaseToken = ""
                },
            ) {
                DeveloperButtonLabel(Icons.Outlined.CheckCircle, R.string.remote_cloud_verify_purchase)
            }
            DeveloperMetricRow(R.string.developer_api_last_operation, remoteState.lastOperation)
            if (remoteState.errorMessage.isNotBlank()) {
                DeveloperMetricRow(R.string.developer_api_last_error, remoteState.errorMessage)
            }
            DeveloperMetricRow(R.string.developer_api_recent_count, apiTraces.size.toString())
            LumenFlowRow {
                Button(onClick = viewModel::checkRemoteHealth) {
                    DeveloperButtonLabel(Icons.Outlined.Sync, R.string.developer_api_probe_health)
                }
                OutlinedButton(
                    onClick = viewModel::clearApiDiagnostics,
                    enabled = apiTraces.isNotEmpty(),
                ) {
                    DeveloperButtonLabel(Icons.Outlined.DeleteSweep, R.string.developer_api_clear_logs)
                }
            }
            if (apiTraces.isEmpty()) {
                DeveloperNote(stringResource(R.string.developer_api_no_requests))
            } else {
                apiTraces.take(MAX_API_TRACE_CARDS).forEach { trace ->
                    DeveloperApiTraceCard(trace)
                }
            }
        }

        SettingsSection(R.string.developer_section_raw_sensors, Icons.Outlined.Sensors) {
            DeveloperMetricRow(R.string.developer_lux, "%.1f lux".format(runtime.ambientLastLux))
            LuxCurve(luxHistory)
            DeveloperMetricRow(R.string.developer_pitch, "${runtime.sensorPitchDegrees.roundToInt()} deg")
            DeveloperMetricRow(R.string.developer_roll, "${runtime.sensorRollDegrees.roundToInt()} deg")
            DeveloperMetricRow(R.string.developer_yaw, "${runtime.sensorYawDegrees.roundToInt()} deg")
            DeveloperMetricRow(R.string.developer_acceleration, "%.2f m/s2".format(runtime.sensorLastAccelerationMagnitude))
        }

        SettingsSection(R.string.developer_section_crash, Icons.Outlined.BugReport) {
            DeveloperNote(stringResource(R.string.developer_crash_preview_message))
            Button(
                onClick = { onPreviewCrashReport(createDeveloperCrashPreview()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                DeveloperButtonLabel(Icons.Outlined.BugReport, R.string.developer_preview_crash_page)
            }
        }

        SettingsSection(R.string.developer_section_mode, Icons.Outlined.Code) {
            SwitchRow(
                R.string.developer_mode_enabled,
                Icons.Outlined.Code,
                settings.developerModeEnabled,
                labelMaxLines = Int.MAX_VALUE,
            ) {
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
private fun DeveloperApiTraceCard(trace: ProjectLumenApiTrace) {
    val statusColor = if (trace.successful) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.developer_api_trace_title, trace.method, trace.path),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
        )
        Text(
            text = traceStatusLabel(trace),
            style = MaterialTheme.typography.bodySmall,
            color = statusColor,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
        )
        ApiTraceLine(R.string.developer_api_trace_url, trace.url)
        ApiTraceLine(
            R.string.developer_api_trace_security,
            stringResource(
                R.string.developer_api_trace_security_value,
                trace.signed.toString(),
                trace.authorizationAttached.toString(),
            ),
        )
        ApiTraceLine(R.string.developer_api_trace_request, trace.requestBodyPreview.ifBlank { "-" })
        ApiTraceLine(R.string.developer_api_trace_response, trace.responseBodyPreview.ifBlank { "-" })
        if (trace.errorMessage.isNotBlank() || trace.errorType.isNotBlank()) {
            ApiTraceLine(
                R.string.developer_api_trace_error,
                listOf(trace.errorType, trace.errorMessage)
                    .filter { it.isNotBlank() }
                    .joinToString(": "),
            )
        }
    }
}

@Composable
private fun ApiTraceLine(@StringRes labelRes: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        SelectionContainer {
            Text(
                text = smartWrapDebugText(value),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true,
            )
        }
    }
}

@Composable
private fun traceStatusLabel(trace: ProjectLumenApiTrace): String {
    return if (trace.statusCode != null) {
        stringResource(
            R.string.developer_api_trace_status,
            trace.statusCode,
            trace.durationMillis,
            timestampLabel(trace.startedAtMillis),
        )
    } else {
        stringResource(
            R.string.developer_api_trace_no_status,
            trace.durationMillis,
            timestampLabel(trace.startedAtMillis),
        )
    }
}

@Composable
private fun DeveloperMetricRow(@StringRes labelRes: Int, value: String) {
    DeveloperMetricRow(label = stringResource(labelRes), value = value)
}

@Composable
private fun DeveloperMetricRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
        )
        SelectionContainer {
            Text(
                text = smartWrapDebugText(value),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                softWrap = true,
            )
        }
    }
}

@Composable
private fun DeveloperNote(message: String) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenCardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        softWrap = true,
    )
}

@Composable
private fun DeveloperButtonLabel(icon: ImageVector, @StringRes labelRes: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            softWrap = true,
        )
    }
}

private fun smartWrapDebugText(value: String): String {
    val normalized = value.trim().ifBlank { "-" }
    if (normalized.length <= DEBUG_WRAP_COLUMN) return normalized
    val separated = normalized
        .replace("://", "://\n")
        .lineSequence()
        .joinToString("\n") { segment ->
            if (segment.endsWith("://")) {
                segment
            } else {
                segment
                    .replace("/", "/\n")
                    .replace("?", "?\n")
                    .replace("&", "&\n")
                    .replace(", ", ",\n")
                    .replace("; ", ";\n")
                    .replace(" | ", "\n")
            }
        }
    return separated
        .lineSequence()
        .flatMap { line ->
            if (line.length <= DEBUG_WRAP_COLUMN) {
                sequenceOf(line)
            } else {
                line.chunked(DEBUG_WRAP_COLUMN).asSequence()
            }
        }
        .joinToString("\n")
}

@Composable
private fun securityPinStatus(pinCount: Int, required: Boolean, optional: Boolean): String {
    return when {
        pinCount >= 2 -> stringResource(R.string.developer_security_pins_ready, pinCount)
        pinCount > 0 -> stringResource(R.string.developer_security_pins_partial, pinCount)
        optional -> stringResource(R.string.developer_security_pins_optional)
        required -> stringResource(R.string.developer_security_pins_missing)
        else -> stringResource(R.string.developer_security_pins_debug_missing)
    }
}

private fun configuredPinCount(pins: String): Int {
    return CertificatePinPolicy.parse(pins).size
}

@Composable
private fun secureCredentialStatus(context: Context): String {
    val hasAccessToken = remember(context) {
        val application = context.applicationContext as? ProjectLumenApplication
        val session = runCatching { application?.secureCredentials?.load() }.getOrNull()
        session?.accessToken?.isNotBlank() == true
    }
    return if (hasAccessToken) {
        stringResource(R.string.developer_security_credentials_present)
    } else {
        stringResource(R.string.developer_security_credentials_empty)
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

private const val DEBUG_WRAP_COLUMN = 36
private const val MAX_API_TRACE_CARDS = 8
