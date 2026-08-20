package com.projectlumen.app.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.shizuku.ShizukuCapabilityState
import com.projectlumen.app.core.shizuku.ShizukuNetworkApp
import com.projectlumen.app.core.shizuku.ShizukuNetworkAppTypes

@Composable
internal fun ShizukuNetworkControlsSection(
    shizukuState: ShizukuCapabilityState,
    networkApps: List<ShizukuNetworkApp>,
    records: List<AppNetworkControlEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onAuthorize: () -> Unit,
    onRestrict: (ShizukuNetworkApp) -> Unit,
    onRestore: (AppNetworkControlEntity) -> Unit,
) {
    val normalizedQuery = query.trim()
    val recordsByPackage = records.associateBy { it.packageName }
    val activeRecords = records.filter { it.hasActiveNetworkRestriction }
    val filteredApps = networkApps
        .filter { app ->
            normalizedQuery.isBlank() ||
                app.packageName.contains(normalizedQuery, ignoreCase = true) ||
                app.uid.toString().contains(normalizedQuery)
        }
        .take(MAX_NETWORK_APP_CARDS)
    SettingsSection(R.string.developer_section_shizuku_network_controls, Icons.Outlined.Lock) {
        DeveloperNote(stringResource(R.string.developer_shizuku_network_boundary))
        DeveloperMetricRow(R.string.shizuku_status, developerShizukuStatusLabel(shizukuState))
        DeveloperMetricRow(
            R.string.developer_shizuku_network_apps_count,
            stringResource(
                R.string.developer_shizuku_network_apps_count_value,
                networkApps.size,
                activeRecords.size,
            ),
        )
        LumenFlowRow {
            OutlinedButton(onClick = onRefresh, enabled = shizukuState.ready) {
                DeveloperButtonLabel(Icons.Outlined.Sync, R.string.developer_shizuku_network_refresh_apps)
            }
            if (!shizukuState.ready) {
                OutlinedButton(onClick = onAuthorize, enabled = shizukuState.binderAvailable) {
                    DeveloperButtonLabel(Icons.Outlined.Lock, R.string.shizuku_authorize)
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text(stringResource(R.string.developer_shizuku_network_search_label)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        if (records.isEmpty()) {
            DeveloperNote(stringResource(R.string.developer_shizuku_network_no_records))
        } else {
            records.take(MAX_NETWORK_RECORD_CARDS).forEach { record ->
                DeveloperNetworkControlRecordCard(record = record, onRestore = { onRestore(record) })
            }
        }
        if (filteredApps.isEmpty()) {
            DeveloperNote(stringResource(R.string.developer_shizuku_network_no_apps))
        } else {
            filteredApps.forEach { app ->
                DeveloperNetworkAppCard(
                    app = app,
                    record = recordsByPackage[app.packageName],
                    onRestrict = { onRestrict(app) },
                )
            }
        }
    }
}

@Composable
private fun DeveloperNetworkAppCard(
    app: ShizukuNetworkApp,
    record: AppNetworkControlEntity?,
    onRestrict: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LumenPreferenceShape)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = app.packageName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
        )
        Text(
            text = stringResource(
                R.string.developer_shizuku_network_app_detail,
                app.uid,
                networkAppTypeLabel(app.appType),
                networkAppStatusLabel(app, record),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = record?.hasActiveNetworkRestriction != true,
            onClick = onRestrict,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            DeveloperButtonLabel(Icons.Outlined.Lock, R.string.developer_shizuku_network_restrict)
        }
    }
}

@Composable
private fun DeveloperNetworkControlRecordCard(
    record: AppNetworkControlEntity,
    onRestore: () -> Unit,
) {
    val restrictionActive = record.hasActiveNetworkRestriction
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(lumenNestedContainerColor)
            .border(
                1.dp,
                if (restrictionActive) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                LumenPreferenceShape,
            )
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = record.packageName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            softWrap = true,
        )
        Text(
            text = stringResource(
                R.string.developer_shizuku_network_record_detail,
                record.uid,
                networkAppTypeLabel(record.appType),
                networkRecordStatusLabel(record),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (restrictionActive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            softWrap = true,
        )
        Text(
            text = networkGuardStatusLabel(record),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = true,
        )
        if (record.lastError.isNotBlank()) {
            ApiTraceLine(R.string.developer_shizuku_network_last_error, record.lastError)
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = restrictionActive,
            onClick = onRestore,
        ) {
            DeveloperButtonLabel(Icons.Outlined.Sync, R.string.developer_shizuku_network_restore)
        }
    }
}

@Composable
internal fun developerShizukuStatusLabel(state: ShizukuCapabilityState): String {
    return when {
        state.ready -> stringResource(R.string.shizuku_status_ready)
        !state.binderAvailable -> stringResource(R.string.shizuku_status_no_service)
        !state.permissionGranted -> stringResource(R.string.shizuku_status_permission_needed)
        else -> stringResource(R.string.shizuku_status_unavailable)
    }
}

@Composable
private fun networkAppTypeLabel(appType: String): String {
    return when (appType) {
        ShizukuNetworkAppTypes.SYSTEM -> stringResource(R.string.developer_shizuku_network_system_app)
        else -> stringResource(R.string.developer_shizuku_network_user_app)
    }
}

@Composable
private fun networkAppStatusLabel(app: ShizukuNetworkApp, record: AppNetworkControlEntity?): String {
    return when {
        record?.hasActiveNetworkRestriction == true -> stringResource(R.string.developer_shizuku_network_restricted)
        app.restrictedByUidPolicy -> stringResource(R.string.developer_shizuku_network_uid_policy_active)
        else -> stringResource(R.string.developer_shizuku_network_available)
    }
}

@Composable
private fun networkRecordStatusLabel(record: AppNetworkControlEntity): String {
    return when {
        record.hasActiveNetworkRestriction -> stringResource(R.string.developer_shizuku_network_restricted)
        record.lastError.isNotBlank() -> stringResource(R.string.developer_shizuku_network_failed)
        else -> stringResource(R.string.developer_shizuku_network_restored)
    }
}

@Composable
private fun networkGuardStatusLabel(record: AppNetworkControlEntity): String {
    val uidPolicy = if (record.uidPolicyApplied) {
        stringResource(R.string.developer_shizuku_network_uid_policy_active)
    } else {
        stringResource(R.string.developer_shizuku_network_uid_policy_inactive)
    }
    val delegatedGuard = when (record.delegatedNetworkGuardDisplayStatus) {
        DelegatedNetworkGuardDisplayStatus.ACTIVE ->
            stringResource(R.string.developer_shizuku_network_delegated_guard_active)
        DelegatedNetworkGuardDisplayStatus.CLEARED ->
            stringResource(R.string.developer_shizuku_network_delegated_guard_cleared)
        DelegatedNetworkGuardDisplayStatus.UNSUPPORTED ->
            stringResource(R.string.developer_shizuku_network_delegated_guard_unsupported)
        DelegatedNetworkGuardDisplayStatus.NOT_ATTEMPTED ->
            stringResource(R.string.developer_shizuku_network_delegated_guard_not_attempted)
    }
    return stringResource(R.string.developer_shizuku_network_guard_status, uidPolicy, delegatedGuard)
}

private const val MAX_NETWORK_APP_CARDS = 12
private const val MAX_NETWORK_RECORD_CARDS = 12
