package com.projectlumen.app.app

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.security.CroootReportFormatter
import com.projectlumen.app.core.security.DeviceSecurityScanner
import com.projectlumen.app.ui.theme.lumenGlass

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
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InlineHeader(Icons.Outlined.Security, "Device Security Scan")
                if (scanState is DeviceSecurityScanState.Running) {
                    CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
                }
            }

            when (scanState) {
                is DeviceSecurityScanState.Idle -> {
                    Text(
                        text = "Tap \"Scan Now\" to check device security status including root detection, hardware integrity, and TEE attestation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DeviceSecurityScanState.Running -> {
                    Text(
                        text = "Scanning device security… This may take up to 60 seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DeviceSecurityScanState.Complete -> {
                    SecurityResultSummary(scanState.assessment)
                }
                is DeviceSecurityScanState.Failed -> {
                    Text(
                        text = "Scan failed: ${scanState.errorMessage}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Button(
                onClick = onStartScan,
                enabled = scanState !is DeviceSecurityScanState.Running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (scanState is DeviceSecurityScanState.Complete) "Scan Again" else "Scan Now",
                )
            }
        }
    }
}

@Composable
private fun SecurityResultSummary(assessment: DeviceSecurityScanner.SecurityAssessment) {
    SecurityLine(
        label = stringResource(R.string.developer_crooot_root_status),
        value = if (assessment.rooted) "ROOTED" else "Clean",
        icon = if (assessment.rooted) Icons.Outlined.Report else Icons.Outlined.CheckCircle,
        valueColor = if (assessment.rooted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_suspicious_indicators),
        value = if (assessment.suspicious) "Found" else "None",
        icon = if (assessment.suspicious) Icons.Outlined.WarningAmber else Icons.Outlined.CheckCircle,
        valueColor = if (assessment.suspicious) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_hardware_integrity),
        value = when (assessment.hardwareIntegrityOk) {
            true -> "OK"
            false -> "Compromised"
            null -> "Not checked"
        },
        icon = when (assessment.hardwareIntegrityOk) {
            true -> Icons.Outlined.CheckCircle
            false -> Icons.Outlined.Report
            null -> Icons.Outlined.WarningAmber
        },
        valueColor = when (assessment.hardwareIntegrityOk) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.tertiary
        },
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_selinux),
        value = when (assessment.selinuxEnforcing) {
            true -> "Enforcing ✓"
            false -> "Permissive ⚠"
            null -> "Unknown"
        },
        icon = when (assessment.selinuxEnforcing) {
            true -> Icons.Outlined.CheckCircle
            false -> Icons.Outlined.WarningAmber
            null -> Icons.Outlined.WarningAmber
        },
        valueColor = when (assessment.selinuxEnforcing) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.tertiary
        },
    )
    SecurityLine(
        label = stringResource(R.string.developer_crooot_tee_attestation),
        value = when (assessment.teeAttestationOk) {
            true -> "Verified"
            false -> "Failed"
            null -> "Not checked"
        },
        icon = when (assessment.teeAttestationOk) {
            true -> Icons.Outlined.CheckCircle
            false -> Icons.Outlined.Report
            null -> Icons.Outlined.WarningAmber
        },
        valueColor = when (assessment.teeAttestationOk) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.tertiary
        },
    )
    Text(
        text = assessment.summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                softWrap = true,
            )
        }
    }
}

@Composable
private fun SecurityLine(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.padding(end = 4.dp))
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
            color = valueColor,
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