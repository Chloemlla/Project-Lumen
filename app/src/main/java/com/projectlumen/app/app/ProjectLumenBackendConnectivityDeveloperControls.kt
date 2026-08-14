package com.projectlumen.app.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.projectlumen.app.R
import com.projectlumen.app.core.api.BackendCapabilityDecision
import com.projectlumen.app.core.api.BackendConnectivityState
import com.projectlumen.app.core.api.BackendHealthStatus
import com.projectlumen.app.ui.theme.GlassButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun BackendConnectivityDeveloperControls(
    state: BackendConnectivityState,
    decision: BackendCapabilityDecision,
    onRefresh: () -> Unit,
    onForceEnabledChange: (Boolean) -> Unit,
) {
    val lastErrorLabel = if (state.lastErrorCode.isBlank()) {
        stringResource(R.string.backend_connectivity_none)
    } else {
        state.lastErrorCode
    }
    DeveloperMetricRow(
        R.string.backend_connectivity_actual_status,
        backendHealthStatusLabel(state.status),
    )
    DeveloperMetricRow(
        R.string.backend_connectivity_effective_status,
        backendEffectiveStatusLabel(decision),
    )
    DeveloperMetricRow(
        R.string.backend_connectivity_last_checked,
        backendTimestampLabel(state.lastCheckedAtMillis),
    )
    DeveloperMetricRow(
        R.string.backend_connectivity_last_reachable,
        backendTimestampLabel(state.lastReachableAtMillis),
    )
    DeveloperMetricRow(
        R.string.backend_connectivity_failures,
        state.consecutiveFailures.toString(),
    )
    DeveloperMetricRow(
        R.string.backend_connectivity_last_error,
        lastErrorLabel,
    )
    SwitchRow(
        labelRes = R.string.backend_connectivity_force_enable,
        icon = Icons.Outlined.WarningAmber,
        checked = state.developerForceEnabled,
        labelMaxLines = Int.MAX_VALUE,
        onCheckedChange = onForceEnabledChange,
    )
    DeveloperNote(stringResource(R.string.backend_connectivity_force_enable_warning))
    GlassButton(
        onClick = onRefresh,
        enabled = state.status != BackendHealthStatus.CHECKING,
        modifier = Modifier.fillMaxWidth(),
    ) {
        DeveloperButtonLabel(Icons.Outlined.Sync, R.string.backend_connectivity_refresh)
    }
}

@Composable
private fun backendHealthStatusLabel(status: BackendHealthStatus): String {
    val labelRes = when (status) {
        BackendHealthStatus.UNKNOWN -> R.string.backend_connectivity_status_unknown
        BackendHealthStatus.CHECKING -> R.string.backend_connectivity_status_checking
        BackendHealthStatus.REACHABLE -> R.string.backend_connectivity_status_reachable
        BackendHealthStatus.UNREACHABLE -> R.string.backend_connectivity_status_unreachable
    }
    return stringResource(labelRes)
}

@Composable
private fun backendEffectiveStatusLabel(decision: BackendCapabilityDecision): String {
    val labelRes = when {
        decision.forced -> R.string.backend_connectivity_effective_forced
        decision.executable -> R.string.backend_connectivity_effective_available
        else -> R.string.backend_connectivity_effective_blocked
    }
    return stringResource(labelRes)
}

@Composable
private fun backendTimestampLabel(value: Long): String {
    if (value <= 0L) return stringResource(R.string.backend_connectivity_never)
    return Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(BACKEND_TIMESTAMP_FORMATTER)
}

private val BACKEND_TIMESTAMP_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
