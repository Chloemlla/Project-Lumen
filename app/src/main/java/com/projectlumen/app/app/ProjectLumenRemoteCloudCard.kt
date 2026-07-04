package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R

@Composable
internal fun RemoteCloudAccountCard(
    state: ProjectLumenRemoteUiState,
    cloudSyncAllowed: Boolean,
    onCheckHealth: () -> Unit,
    onStartEmailLogin: (String) -> Unit,
    onVerifyEmailLogin: (String) -> Unit,
    onRefreshAccount: () -> Unit,
    onSyncNow: () -> Unit,
    onUploadBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onSignOut: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    val normalizedEmail = email.trim()
    val normalizedCode = code.trim()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            email = ""
            code = ""
        }
    }
    LaunchedEffect(state.waitingForCode) {
        if (!state.waitingForCode) {
            code = ""
        }
    }

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
            SectionHeader(Icons.Outlined.CloudUpload, R.string.remote_cloud_title)
            Text(
                stringResource(R.string.remote_cloud_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                StatusLine(Icons.Outlined.Sync, stringResource(R.string.remote_cloud_busy))
            }
            MetricRow(R.string.remote_cloud_api_base, com.projectlumen.app.core.api.ProjectLumenApiConfig.baseUrl)
            MetricRow(
                R.string.remote_cloud_account,
                when {
                    state.signedInEmail.isNotBlank() -> state.signedInEmail
                    state.signedIn -> stringResource(R.string.remote_cloud_stored_session)
                    else -> stringResource(R.string.remote_cloud_signed_out)
                },
            )
            if (state.signedIn) {
                MetricRow(R.string.remote_cloud_tier, state.cloudTier.ifBlank { "-" })
                MetricRow(R.string.remote_cloud_required_tier, stringResource(R.string.remote_cloud_commercial_plus))
                MetricRow(R.string.remote_cloud_entitlements, state.entitlementCount.toString())
                MetricRow(R.string.remote_cloud_sync_cursor, state.syncCursor.toString())
                if (state.latestBackupUploadedAt > 0L) {
                    MetricRow(R.string.remote_cloud_latest_backup, state.latestBackupUploadedAt.toString())
                }
                if (!cloudSyncAllowed) {
                    StatusLine(Icons.Outlined.Lock, stringResource(R.string.remote_cloud_commercial_required))
                }
            }
            if (state.lastOperation.isNotBlank()) {
                StatusLine(Icons.Outlined.CheckCircle, state.lastOperation)
            }
            if (state.errorMessage.isNotBlank()) {
                StatusLine(Icons.Outlined.Lock, state.errorMessage)
            }

            AnimatedVisibility(visible = !state.signedIn) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = email,
                        onValueChange = { email = it },
                        enabled = !state.busy,
                        singleLine = true,
                        label = { Text(stringResource(R.string.remote_cloud_email)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    LumenFlowRow {
                        Button(
                            enabled = !state.busy && normalizedEmail.isNotBlank(),
                            onClick = { onStartEmailLogin(normalizedEmail) },
                        ) {
                            ButtonLabel(Icons.Outlined.Person, R.string.remote_cloud_request_code)
                        }
                        OutlinedButton(enabled = !state.busy, onClick = onCheckHealth) {
                            ButtonLabel(Icons.Outlined.Refresh, R.string.remote_cloud_check_health)
                        }
                    }
                    AnimatedVisibility(visible = state.waitingForCode) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = code,
                                onValueChange = { code = it },
                                enabled = !state.busy,
                                singleLine = true,
                                label = { Text(stringResource(R.string.remote_cloud_code)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            if (state.devCode.isNotBlank()) {
                                StatusLine(Icons.Outlined.Lock, stringResource(R.string.remote_cloud_dev_code, state.devCode))
                            }
                            Button(
                                enabled = !state.busy && normalizedCode.isNotBlank(),
                                onClick = { onVerifyEmailLogin(normalizedCode) },
                            ) {
                                ButtonLabel(Icons.Outlined.CheckCircle, R.string.remote_cloud_verify_code)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.signedIn) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LumenFlowRow {
                        Button(enabled = !state.busy && cloudSyncAllowed, onClick = onSyncNow) {
                            ButtonLabel(Icons.Outlined.Sync, R.string.remote_cloud_sync_now)
                        }
                        OutlinedButton(enabled = !state.busy && cloudSyncAllowed, onClick = onUploadBackup) {
                            ButtonLabel(Icons.Outlined.CloudUpload, R.string.remote_cloud_upload_backup)
                        }
                        OutlinedButton(enabled = !state.busy && cloudSyncAllowed, onClick = onRestoreBackup) {
                            ButtonLabel(Icons.Outlined.Refresh, R.string.remote_cloud_restore_backup)
                        }
                    }
                    LumenFlowRow {
                        OutlinedButton(enabled = !state.busy, onClick = onRefreshAccount) {
                            ButtonLabel(Icons.Outlined.Refresh, R.string.remote_cloud_refresh_account)
                        }
                        OutlinedButton(enabled = !state.busy, onClick = onCheckHealth) {
                            ButtonLabel(Icons.Outlined.Refresh, R.string.remote_cloud_check_health)
                        }
                        OutlinedButton(enabled = !state.busy, onClick = onSignOut) {
                            ButtonLabel(Icons.Outlined.Lock, R.string.remote_cloud_sign_out)
                        }
                    }
                }
            }
        }
    }
}
