package com.projectlumen.app.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.security.CroootReportFormatter
import com.projectlumen.app.core.security.DeviceSecurityScanner

/**
 * Card that displays CRooot device-security scan results.
 *
 * Shows a summary of the device's security state: root status, hardware integrity,
 * SELinux mode, and TEE attestation. Supports triggering a new scan.
 */
@Composable
internal fun DeviceSecurityScanCard(
    scanState: DeviceSecurityScanState,
    onStartScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = LumenCardShape,
        colors = lumenCardColors(LumenCardEmphasis.Quiet),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(LumenCardEmphasis.Quiet),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineHeader(Icons.Outlined.Security, stringResource(R.string.developer_crooot_scan_title))
                if (scanState is DeviceSecurityScanState.Running) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                }
            }

            when (scanState) {
                is DeviceSecurityScanState.Idle -> {
                    Text(
                        text = stringResource(R.string.developer_crooot_scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DeviceSecurityScanState.Running -> {
                    Text(
                        text = stringResource(R.string.developer_crooot_scanning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DeviceSecurityScanState.Complete -> {
                    SecurityResultSummary(scanState.assessment)
                }
                is DeviceSecurityScanState.Failed -> {
                    DeviceSecurityNotice(
                        icon = Icons.Outlined.Report,
                        text = stringResource(R.string.developer_crooot_scan_failed, scanState.errorMessage),
                        severity = DeviceSecuritySeverity.Critical,
                    )
                }
            }

            Button(
                onClick = onStartScan,
                enabled = scanState !is DeviceSecurityScanState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (scanState is DeviceSecurityScanState.Complete) {
                            R.string.developer_crooot_scan_again
                        } else {
                            R.string.developer_crooot_scan_now
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun SecurityResultSummary(assessment: DeviceSecurityScanner.SecurityAssessment) {
    SecurityLine(
        label = stringResource(R.string.developer_crooot_root_status),
        value = if (assessment.rooted) {
            stringResource(R.string.developer_crooot_value_rooted)
        } else {
            stringResource(R.string.developer_crooot_value_clean)
        },
        icon = if (assessment.rooted) Icons.Outlined.Report else Icons.Outlined.CheckCircle,
        severity = if (assessment.rooted) DeviceSecuritySeverity.Critical else DeviceSecuritySeverity.Ok,
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_suspicious_indicators),
        value = if (assessment.suspicious) {
            stringResource(R.string.developer_crooot_value_found)
        } else {
            stringResource(R.string.developer_crooot_value_none)
        },
        icon = if (assessment.suspicious) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
        severity = if (assessment.suspicious) DeviceSecuritySeverity.Warning else DeviceSecuritySeverity.Ok,
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_hardware_integrity),
        value = when (assessment.hardwareIntegrityOk) {
            true -> stringResource(R.string.developer_crooot_value_ok)
            false -> stringResource(R.string.developer_crooot_value_compromised)
            null -> stringResource(R.string.developer_crooot_value_not_checked)
        },
        icon = when (assessment.hardwareIntegrityOk) {
            true -> Icons.Outlined.CheckCircle
            false -> Icons.Outlined.Report
            null -> Icons.Outlined.WarningAmber
        },
        severity = when (assessment.hardwareIntegrityOk) {
            true -> DeviceSecuritySeverity.Ok
            false -> DeviceSecuritySeverity.Critical
            null -> DeviceSecuritySeverity.Unknown
        },
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_selinux),
        value = when (assessment.selinuxEnforcing) {
            true -> stringResource(R.string.developer_crooot_value_enforcing)
            false -> stringResource(R.string.developer_crooot_value_permissive)
            null -> stringResource(R.string.developer_crooot_value_unknown)
        },
        icon = when (assessment.selinuxEnforcing) {
            true -> Icons.Outlined.CheckCircle
            false -> Icons.Outlined.WarningAmber
            null -> Icons.Outlined.WarningAmber
        },
        severity = when (assessment.selinuxEnforcing) {
            true -> DeviceSecuritySeverity.Ok
            false -> DeviceSecuritySeverity.Warning
            null -> DeviceSecuritySeverity.Unknown
        },
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_tee_attestation),
        value = when (assessment.teeAttestationOk) {
            true -> stringResource(R.string.developer_crooot_value_verified)
            false -> stringResource(R.string.developer_crooot_value_failed)
            null -> stringResource(R.string.developer_crooot_value_not_checked)
        },
        icon = when (assessment.teeAttestationOk) {
            true -> Icons.Outlined.CheckCircle
            false -> Icons.Outlined.Report
            null -> Icons.Outlined.WarningAmber
        },
        severity = when (assessment.teeAttestationOk) {
            true -> DeviceSecuritySeverity.Ok
            false -> DeviceSecuritySeverity.Critical
            null -> DeviceSecuritySeverity.Unknown
        },
    )
    val compromised = assessment.rooted ||
        assessment.hardwareIntegrityOk == false ||
        assessment.teeAttestationOk == false
    if (compromised) {
        DeviceSecurityNotice(
            icon = Icons.Outlined.Report,
            text = assessment.summary,
            severity = DeviceSecuritySeverity.Critical,
        )
    } else {
        Text(
            text = assessment.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (assessment.rawResult != null) {
        CroootDetailedReport(assessment.rawResult)
    }
}

@Composable
private fun CroootDetailedReport(result: com.chloemlla.crooot.CRoootScanResult) {
    var expanded by remember { mutableStateOf(false) }
    TextButton(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
        )
        Text(
            text = stringResource(
                if (expanded) {
                    R.string.developer_crooot_hide_report
                } else {
                    R.string.developer_crooot_show_report
                },
            ),
        )
    }
    if (expanded) {
        val report = remember(result) { CroootReportFormatter.format(result) }
        SelectionContainer {
            Text(
                text = report,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(LumenPreferenceShape)
                    .background(lumenNestedContainerColor)
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
        }
    }
}

/** How a single scan finding should read: benign, unverified, worth attention, or a failure. */
private enum class DeviceSecuritySeverity {
    Ok,
    Unknown,
    Warning,
    Critical,
}

@Composable
private fun deviceSecurityAccentColor(severity: DeviceSecuritySeverity): Color = when (severity) {
    DeviceSecuritySeverity.Ok -> MaterialTheme.colorScheme.primary
    DeviceSecuritySeverity.Unknown -> MaterialTheme.colorScheme.tertiary
    DeviceSecuritySeverity.Warning -> MaterialTheme.colorScheme.secondary
    DeviceSecuritySeverity.Critical -> MaterialTheme.colorScheme.error
}

@Composable
private fun deviceSecurityContainerColor(severity: DeviceSecuritySeverity): Color = when (severity) {
    DeviceSecuritySeverity.Ok, DeviceSecuritySeverity.Unknown -> lumenNestedContainerColor
    DeviceSecuritySeverity.Warning -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    DeviceSecuritySeverity.Critical -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
}

@Composable
private fun DeviceSecurityNotice(
    icon: ImageVector,
    text: String,
    severity: DeviceSecuritySeverity,
) {
    val containerColor = when (severity) {
        DeviceSecuritySeverity.Critical -> MaterialTheme.colorScheme.errorContainer
        DeviceSecuritySeverity.Warning -> MaterialTheme.colorScheme.secondaryContainer
        else -> lumenNestedContainerColor
    }
    val contentColor = when (severity) {
        DeviceSecuritySeverity.Critical -> MaterialTheme.colorScheme.onErrorContainer
        DeviceSecuritySeverity.Warning -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun SecurityLine(
    label: String,
    value: String,
    icon: ImageVector,
    severity: DeviceSecuritySeverity,
) {
    val accentColor = deviceSecurityAccentColor(severity)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenPreferenceShape)
            .background(deviceSecurityContainerColor(severity))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accentColor,
        )
    }
}

/** State for the [DeviceSecurityScanCard]. */
sealed interface DeviceSecurityScanState {
    data object Idle : DeviceSecurityScanState
    data object Running : DeviceSecurityScanState
    data class Complete(val assessment: DeviceSecurityScanner.SecurityAssessment) : DeviceSecurityScanState
    data class Failed(val errorMessage: String) : DeviceSecurityScanState
}