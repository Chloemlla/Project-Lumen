package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.toast.LumenToastKind
import com.projectlumen.app.core.toast.showLumenToast
import com.projectlumen.app.ui.theme.GlassOutlinedButton
import com.projectlumen.app.ui.theme.lumenGlass

/** Destinations opened from the legal hub; the host NavHost maps each key to a screen. */
internal enum class LegalDocKey { TERMS, PRIVACY, MEMBERSHIP, PERSONAL, PERMISSIONS }

@Composable
internal fun LegalHubScreen(
    viewModel: ProjectLumenViewModel,
    onOpenDoc: (LegalDocKey) -> Unit,
) {
    val context = LocalContext.current
    val withdrawConsentSuccess = stringResource(R.string.legal_withdraw_consent_success)
    val withdrawPrivacySuccess = stringResource(R.string.legal_withdraw_privacy_success)
    var showWithdrawConsentDialog by rememberSaveable { mutableStateOf(false) }
    var showWithdrawPrivacyDialog by rememberSaveable { mutableStateOf(false) }

    LumenPage {
        SectionHeader(Icons.Outlined.Gavel, R.string.legal_center_title)
        Text(
            text = stringResource(R.string.legal_center_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            text = stringResource(R.string.legal_center_section_legal),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
            border = lumenCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegalRow(Icons.Outlined.Description, R.string.legal_center_user_agreement) {
                    onOpenDoc(LegalDocKey.TERMS)
                }
                HorizontalDivider()
                LegalRow(Icons.Outlined.PrivacyTip, R.string.legal_center_privacy_policy) {
                    onOpenDoc(LegalDocKey.PRIVACY)
                }
                HorizontalDivider()
                LegalRow(Icons.Outlined.WorkspacePremium, R.string.legal_center_membership_agreement) {
                    onOpenDoc(LegalDocKey.MEMBERSHIP)
                }
                HorizontalDivider()
                LegalRow(Icons.AutoMirrored.Outlined.ListAlt, R.string.legal_center_personal_info_collection) {
                    onOpenDoc(LegalDocKey.PERSONAL)
                }
            }
        }

        Text(
            text = stringResource(R.string.legal_center_section_consent),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
            border = lumenCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegalRow(Icons.Outlined.Refresh, R.string.legal_center_withdraw_consent) {
                    showWithdrawConsentDialog = true
                }
                HorizontalDivider()
                LegalRow(Icons.Outlined.CheckCircle, R.string.legal_center_withdraw_privacy_consent) {
                    showWithdrawPrivacyDialog = true
                }
            }
        }

        Text(
            text = stringResource(R.string.legal_center_section_other),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
            border = lumenCardBorder(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LegalRow(Icons.Outlined.Code, R.string.legal_center_oss_notice) {
                    viewModel.reopenOssNotice()
                }
                HorizontalDivider()
                LegalRow(Icons.Outlined.Lock, R.string.legal_center_app_permissions) {
                    onOpenDoc(LegalDocKey.PERMISSIONS)
                }
            }
        }
    }

    if (showWithdrawConsentDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawConsentDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            title = { Text(stringResource(R.string.legal_withdraw_consent_dialog_title)) },
            text = { Text(stringResource(R.string.legal_withdraw_consent_dialog_message)) },
            confirmButton = {
                GlassOutlinedButton(onClick = {
                    viewModel.withdrawDataConsent()
                    context.showLumenToast(
                        withdrawConsentSuccess,
                        kind = LumenToastKind.SUCCESS,
                        long = true,
                    )
                    showWithdrawConsentDialog = false
                }) {
                    Text(stringResource(R.string.legal_withdraw_confirm))
                }
            },
            dismissButton = {
                GlassOutlinedButton(onClick = { showWithdrawConsentDialog = false }) {
                    Text(stringResource(R.string.legal_withdraw_cancel))
                }
            },
        )
    }

    if (showWithdrawPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawPrivacyDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            title = { Text(stringResource(R.string.legal_withdraw_privacy_dialog_title)) },
            text = { Text(stringResource(R.string.legal_withdraw_privacy_dialog_message)) },
            confirmButton = {
                GlassOutlinedButton(onClick = {
                    viewModel.withdrawPrivacyPolicyConsent()
                    context.showLumenToast(
                        withdrawPrivacySuccess,
                        kind = LumenToastKind.SUCCESS,
                        long = true,
                    )
                    showWithdrawPrivacyDialog = false
                }) {
                    Text(stringResource(R.string.legal_withdraw_confirm))
                }
            },
            dismissButton = {
                GlassOutlinedButton(onClick = { showWithdrawPrivacyDialog = false }) {
                    Text(stringResource(R.string.legal_withdraw_cancel))
                }
            },
        )
    }
}

@Composable
internal fun LegalDocumentScreen(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
) {
    LumenPage {
        SectionHeader(Icons.AutoMirrored.Outlined.Article, titleRes)
        val body = stringResource(bodyRes)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            body.split('\n')
                .filter { it.isNotBlank() }
                .forEach { paragraph ->
                    Text(
                        text = paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
        }
    }
}

@Composable
internal fun AppPermissionsScreen() {
    LumenPage {
        SectionHeader(Icons.Outlined.Lock, R.string.legal_permissions_title)
        Text(
            text = stringResource(R.string.legal_permissions_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        legalPermissions.forEach { entry ->
            PermissionCard(entry)
        }
    }
}

@Composable
private fun LegalRow(
    icon: ImageVector,
    @StringRes labelRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = stringResource(labelRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCard(entry: PermissionEntry) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .lumenGlass(
                shape = LumenCardShape,
                blurRadius = 14f,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            ),
        shape = LumenCardShape,
        colors = lumenCardColors().copy(containerColor = Color.Transparent),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(entry.nameRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(entry.descRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class PermissionEntry(
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
)

private val legalPermissions = listOf(
    PermissionEntry(R.string.legal_permission_camera_name, R.string.legal_permission_camera_desc),
    PermissionEntry(R.string.legal_permission_notifications_name, R.string.legal_permission_notifications_desc),
    PermissionEntry(R.string.legal_permission_exact_alarm_name, R.string.legal_permission_exact_alarm_desc),
    PermissionEntry(R.string.legal_permission_full_screen_intent_name, R.string.legal_permission_full_screen_intent_desc),
    PermissionEntry(R.string.legal_permission_overlay_name, R.string.legal_permission_overlay_desc),
    PermissionEntry(R.string.legal_permission_write_settings_name, R.string.legal_permission_write_settings_desc),
    PermissionEntry(R.string.legal_permission_usage_access_name, R.string.legal_permission_usage_access_desc),
    PermissionEntry(R.string.legal_permission_boot_name, R.string.legal_permission_boot_desc),
    PermissionEntry(R.string.legal_permission_foreground_service_name, R.string.legal_permission_foreground_service_desc),
    PermissionEntry(R.string.legal_permission_internet_name, R.string.legal_permission_internet_desc),
    PermissionEntry(R.string.legal_permission_network_state_name, R.string.legal_permission_network_state_desc),
    PermissionEntry(R.string.legal_permission_battery_name, R.string.legal_permission_battery_desc),
    PermissionEntry(R.string.legal_permission_biometric_name, R.string.legal_permission_biometric_desc),
    PermissionEntry(R.string.legal_permission_install_packages_name, R.string.legal_permission_install_packages_desc),
    PermissionEntry(R.string.legal_permission_query_all_packages_name, R.string.legal_permission_query_all_packages_desc),
    PermissionEntry(R.string.legal_permission_open_api_access_name, R.string.legal_permission_open_api_access_desc),
    PermissionEntry(R.string.legal_permission_open_api_trigger_name, R.string.legal_permission_open_api_trigger_desc),
)
