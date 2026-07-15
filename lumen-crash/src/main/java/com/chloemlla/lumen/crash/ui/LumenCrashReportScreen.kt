package com.chloemlla.lumen.crash.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.chloemlla.lumen.crash.AuthorIntegrity
import com.chloemlla.lumen.crash.CrashAuthorAttribution
import com.chloemlla.lumen.crash.CrashReport
import com.chloemlla.lumen.crash.LumenCrash
import com.chloemlla.lumen.crash.R
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashReportScreenTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private const val CRASH_STACK_COLLAPSED_LINES = 18
private const val CRASH_EVENT_VISIBLE_COUNT = 12

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalLayoutApi::class)
@Composable
fun LumenCrashReportScreen(
    report: CrashReport,
    onContinue: (() -> Unit)? = null,
    clearStoredReportOnContinue: Boolean = true,
    onClearStoredReport: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val integrityOk = remember(report.reportId) {
        runCatching {
            AuthorIntegrity.verifyOrThrow("ui")
            true
        }.getOrDefault(false)
    }
    if (!integrityOk) {
        CrashIntegrityBlockedScreen()
        return
    }

    val activity = context.findActivity()
    val windowSizeClass = if (activity != null) calculateWindowSizeClass(activity) else null
    val config = LumenCrash.configOrNull()
    val title = config?.reportTitle?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.lumen_crash_report_title)
    val message = config?.reportMessage?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.lumen_crash_report_message)

    var stackExpanded by rememberSaveable(report.crashedAtMillis) { mutableStateOf(false) }
    var shareOptionsVisible by rememberSaveable(report.crashedAtMillis) { mutableStateOf(false) }
    val formattedTime = remember(report.crashedAtMillis) {
        Instant.ofEpochMilli(report.crashedAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(crashReportScreenTimeFormatter)
    }
    val stackLineCount = remember(report.stackTrace) { report.stackTrace.lineSequence().count() }
    val stackPreview = remember(report.stackTrace, stackExpanded) {
        if (stackExpanded) {
            report.stackTrace
        } else {
            report.stackTrace.lineSequence().take(CRASH_STACK_COLLAPSED_LINES).joinToString("\n")
        }
    }
    val systemInfo = remember(report.systemInfo) {
        report.systemInfo.lines().mapNotNull { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        val layout = rememberCrashLayoutTokens(windowSizeClass, maxWidth, maxHeight)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = layout.maxContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding),
            verticalArrangement = Arrangement.spacedBy(layout.sectionSpacing),
        ) {
            CrashReportHero(title = title, message = message)

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.Info, stringResource(R.string.lumen_crash_report_summary))
                CrashReportInfoTile(stringResource(R.string.lumen_crash_report_id), report.reportId)
                CrashReportInfoTile(stringResource(R.string.lumen_crash_report_time), formattedTime)
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_root_cause),
                    value = report.rootCause,
                    emphasis = true,
                )
                CrashReportInfoTile(stringResource(R.string.lumen_crash_report_exception_type), report.exceptionType)
                CrashReportInfoTile(stringResource(R.string.lumen_crash_report_thread), report.threadName)
                CrashReportInfoTile(stringResource(R.string.lumen_crash_report_process), report.processName)
            }

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.Devices, stringResource(R.string.lumen_crash_report_system_info))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    systemInfo.forEach { (label, value) ->
                        CrashReportMetadataPill(label = label, value = value, maxWidth = layout.pillMaxWidth)
                    }
                }
            }

            if (report.recentEvents.isNotEmpty()) {
                CrashReportCard {
                    CrashReportSectionHeader(
                        Icons.Outlined.Info,
                        stringResource(R.string.lumen_crash_report_recent_events),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.recentEvents.takeLast(CRASH_EVENT_VISIBLE_COUNT).forEach { event ->
                            CrashReportEventRow(event)
                        }
                    }
                }
            }

            CrashReportCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CrashReportSectionHeader(
                        icon = Icons.Outlined.BugReport,
                        title = stringResource(R.string.lumen_crash_report_stack_trace),
                    )
                    TextButton(onClick = { stackExpanded = !stackExpanded }) {
                        Icon(
                            imageVector = if (stackExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(
                                if (stackExpanded) {
                                    R.string.lumen_crash_report_collapse_stack
                                } else {
                                    R.string.lumen_crash_report_show_full_stack
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = pluralStringResource(
                        id = R.plurals.lumen_crash_report_stack_hint,
                        count = stackLineCount,
                        stackLineCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .heightIn(
                            max = if (stackExpanded) {
                                layout.stackExpandedMaxHeight
                            } else {
                                layout.stackCollapsedMaxHeight
                            },
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                ) {
                    Text(
                        text = stackPreview,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.lumen_crash_report_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            CrashAuthorFooterCard(report = report)

            CrashReportActionPanel(
                horizontalActions = layout.horizontalActions,
                onCopyId = {
                    runCatching { AuthorIntegrity.verifyOrThrow("copy-id") }
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.lumen_crash_report_id),
                                report.reportId,
                            ),
                        )
                    Toast.makeText(
                        context,
                        context.getString(R.string.lumen_crash_report_id_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onCopy = {
                    runCatching {
                        AuthorIntegrity.verifyOrThrow("copy-report")
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(
                                ClipData.newPlainText(
                                    context.getString(R.string.lumen_crash_report_title),
                                    report.toClipboardText(),
                                ),
                            )
                        Toast.makeText(
                            context,
                            context.getString(R.string.lumen_crash_report_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }.onFailure {
                        Toast.makeText(
                            context,
                            context.getString(R.string.lumen_crash_report_integrity_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onShare = { shareOptionsVisible = true },
                onClear = {
                    if (clearStoredReportOnContinue) {
                        onClearStoredReport?.invoke() ?: LumenCrash.clearPendingReport()
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.lumen_crash_report_cleared),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onContinue?.invoke()
                },
            )
        }
    }

    if (shareOptionsVisible) {
        CrashReportShareOptionsDialog(
            fileShareEnabled = !config?.fileProviderAuthority.isNullOrBlank(),
            onDismiss = { shareOptionsVisible = false },
            onShareText = {
                shareOptionsVisible = false
                shareCrashReportText(context, report, config?.shareSubject)
            },
            onShareFile = {
                shareOptionsVisible = false
                val authority = config?.fileProviderAuthority
                if (authority.isNullOrBlank()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.lumen_crash_report_file_share_unavailable),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    shareCrashReportFile(context, report, authority, config?.shareSubject)
                }
            },
        )
    }
}

private data class CrashLayoutTokens(
    val maxContentWidth: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val stackCollapsedMaxHeight: Dp,
    val stackExpandedMaxHeight: Dp,
    val pillMaxWidth: Dp,
    val horizontalActions: Boolean,
)

@Composable
private fun rememberCrashLayoutTokens(
    windowSizeClass: WindowSizeClass?,
    maxWidth: Dp,
    maxHeight: Dp,
): CrashLayoutTokens {
    val widthClass = windowSizeClass?.widthSizeClass
    val heightClass = windowSizeClass?.heightSizeClass
    val compactWidth = widthClass == WindowWidthSizeClass.Compact || maxWidth < 600.dp
    val expandedWidth = widthClass == WindowWidthSizeClass.Expanded || maxWidth >= 840.dp
    val compactHeight = heightClass == WindowHeightSizeClass.Compact || maxHeight < 560.dp
    val maxContentWidth = when {
        expandedWidth -> 960.dp
        compactWidth -> maxWidth
        else -> 720.dp
    }
    val stackCollapsed = when {
        compactHeight -> 140.dp
        expandedWidth -> 280.dp
        else -> 220.dp
    }
    val stackExpanded = when {
        compactHeight -> 240.dp
        expandedWidth -> 520.dp
        else -> 420.dp
    }
    return CrashLayoutTokens(
        maxContentWidth = maxContentWidth,
        horizontalPadding = if (compactWidth) 16.dp else 20.dp,
        verticalPadding = if (compactHeight) 16.dp else 24.dp,
        sectionSpacing = if (compactHeight) 12.dp else 16.dp,
        stackCollapsedMaxHeight = stackCollapsed,
        stackExpandedMaxHeight = stackExpanded,
        pillMaxWidth = if (expandedWidth) 360.dp else 320.dp,
        horizontalActions = expandedWidth && !compactHeight,
    )
}

@Composable
private fun CrashIntegrityBlockedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 480.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.lumen_crash_report_integrity_failed),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = CrashAuthorAttribution.FOOTER_LABEL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun CrashAuthorFooterCard(report: CrashReport) {
    CrashReportCard {
        CrashReportSectionHeader(
            icon = Icons.Outlined.Person,
            title = stringResource(R.string.lumen_crash_report_author_label),
        )
        CrashReportInfoTile(
            label = CrashAuthorAttribution.AUTHOR_NAME,
            value = CrashAuthorAttribution.AUTHOR_URL,
            emphasis = true,
        )
        Text(
            text = CrashAuthorAttribution.FOOTER_LABEL,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = report.authorFingerprint,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrashReportHero(title: String, message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.BugReport,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CrashReportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun CrashReportSectionHeader(icon: ImageVector, title: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CrashReportInfoTile(
    label: String,
    value: String,
    emphasis: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (emphasis) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (emphasis) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrashReportMetadataPill(label: String, value: String, maxWidth: Dp) {
    Surface(
        modifier = Modifier.widthIn(max = maxWidth),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CrashReportEventRow(event: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = event,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CrashReportActionPanel(
    horizontalActions: Boolean,
    onCopyId: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (horizontalActions) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onCopyId) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.lumen_crash_report_copy_id),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onCopy) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.lumen_crash_report_copy),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = onShare) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = stringResource(R.string.lumen_crash_report_share),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onClear) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.lumen_crash_report_clear_and_continue))
                }
            } else {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCopyId) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.lumen_crash_report_copy_id))
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onCopy) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.lumen_crash_report_copy))
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onShare) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.lumen_crash_report_share))
                }
                Button(modifier = Modifier.fillMaxWidth(), onClick = onClear) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.lumen_crash_report_clear_and_continue))
                }
            }
        }
    }
}

@Composable
private fun CrashReportShareOptionsDialog(
    fileShareEnabled: Boolean,
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    onShareFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = Icons.Outlined.Share, contentDescription = null) },
        title = { Text(text = stringResource(R.string.lumen_crash_report_share_options_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.lumen_crash_report_share_options_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CrashReportShareChoice(
                    icon = Icons.Outlined.ContentCopy,
                    title = stringResource(R.string.lumen_crash_report_share_as_text),
                    description = stringResource(R.string.lumen_crash_report_share_as_text_description),
                    onClick = onShareText,
                )
                if (fileShareEnabled) {
                    CrashReportShareChoice(
                        icon = Icons.Outlined.Share,
                        title = stringResource(R.string.lumen_crash_report_share_as_file),
                        description = stringResource(R.string.lumen_crash_report_share_as_file_description),
                        onClick = onShareFile,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.lumen_crash_report_share_cancel))
            }
        },
    )
}

@Composable
private fun CrashReportShareChoice(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(14.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.size(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun shareCrashReportText(context: Context, report: CrashReport, shareSubject: String?) {
    runCatching {
        AuthorIntegrity.verifyOrThrow("share-text")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                shareSubject?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.lumen_crash_report_share_subject),
            )
            putExtra(Intent.EXTRA_TEXT, report.toClipboardText())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchCrashReportShare(context, intent)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.lumen_crash_report_share_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun shareCrashReportFile(
    context: Context,
    report: CrashReport,
    authority: String,
    shareSubject: String?,
) {
    runCatching {
        AuthorIntegrity.verifyOrThrow("share-file")
        val shareDir = context.externalCacheDir ?: context.cacheDir
        val file = File(shareDir, "lumen_crash_report_${report.crashedAtMillis}.txt")
        file.writeText(report.toClipboardText(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_SUBJECT,
                shareSubject?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.lumen_crash_report_share_subject),
            )
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchCrashReportShare(context, intent)
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.lumen_crash_report_share_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun launchCrashReportShare(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.lumen_crash_report_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.lumen_crash_report_share_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
