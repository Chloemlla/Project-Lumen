package com.projectlumen.app.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.crash.CrashReport
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val crashReportScreenTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private const val CRASH_STACK_COLLAPSED_LINES = 18

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CrashReportScreen(
    report: CrashReport,
    onContinue: (() -> Unit)? = null,
    clearStoredReportOnContinue: Boolean = true,
) {
    val context = LocalContext.current
    var stackExpanded by rememberSaveable(report.crashedAtMillis) { mutableStateOf(false) }
    var shareOptionsVisible by rememberSaveable(report.crashedAtMillis) { mutableStateOf(false) }
    val formattedTime = remember(report.crashedAtMillis) {
        Instant.ofEpochMilli(report.crashedAtMillis)
            .atZone(ZoneId.systemDefault())
            .format(crashReportScreenTimeFormatter)
    }
    val stackLineCount = remember(report.stackTrace) {
        report.stackTrace.lineSequence().count()
    }
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CrashReportHero(
                title = stringResource(R.string.crash_report_title),
                message = stringResource(R.string.crash_report_message),
            )

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.Info, stringResource(R.string.crash_report_summary))
                CrashReportInfoTile(stringResource(R.string.crash_report_time), formattedTime)
                CrashReportInfoTile(
                    label = stringResource(R.string.crash_report_root_cause),
                    value = report.rootCause,
                    emphasis = true,
                )
                CrashReportInfoTile(stringResource(R.string.crash_report_exception_type), report.exceptionType)
            }

            CrashReportCard {
                CrashReportSectionHeader(Icons.Outlined.Devices, stringResource(R.string.crash_report_system_info))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    systemInfo.forEach { (label, value) ->
                        CrashReportMetadataPill(label = label, value = value)
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
                        title = stringResource(R.string.crash_report_stack_trace),
                    )
                    TextButton(onClick = { stackExpanded = !stackExpanded }) {
                        Icon(
                            imageVector = if (stackExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(
                                if (stackExpanded) {
                                    R.string.crash_report_collapse_stack
                                } else {
                                    R.string.crash_report_show_full_stack
                                },
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = pluralStringResource(
                        id = R.plurals.crash_report_stack_hint,
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
                        .heightIn(max = if (stackExpanded) 420.dp else 220.dp),
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
            }

            CrashReportActionPanel(
                onCopy = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(
                            ClipData.newPlainText(
                                context.getString(R.string.crash_report_title),
                                report.toClipboardText(),
                            ),
                        )
                    Toast.makeText(context, context.getString(R.string.crash_report_copied), Toast.LENGTH_SHORT).show()
                },
                onShare = {
                    shareOptionsVisible = true
                },
                onClear = {
                    if (clearStoredReportOnContinue) {
                        (context.applicationContext as? ProjectLumenApplication)?.let { application ->
                            application.crashReports.clear()
                            application.clearStartupCrashReport()
                        }
                    }
                    Toast.makeText(context, context.getString(R.string.crash_report_cleared), Toast.LENGTH_SHORT).show()
                    onContinue?.invoke()
                },
            )
        }
    }

    if (shareOptionsVisible) {
        CrashReportShareOptionsDialog(
            onDismiss = { shareOptionsVisible = false },
            onShareText = {
                shareOptionsVisible = false
                shareCrashReportText(context, report)
            },
            onShareFile = {
                shareOptionsVisible = false
                shareCrashReportFile(context, report)
            },
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
private fun CrashReportMetadataPill(label: String, value: String) {
    Surface(
        modifier = Modifier.widthIn(max = 320.dp),
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
private fun CrashReportActionPanel(
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.crash_report_privacy_note),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Button(modifier = Modifier.fillMaxWidth(), onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_copy))
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onShare) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_share))
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onClear) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.crash_report_clear_and_continue))
            }
        }
    }
}

@Composable
private fun CrashReportShareOptionsDialog(
    onDismiss: () -> Unit,
    onShareText: () -> Unit,
    onShareFile: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
        },
        title = {
            Text(text = stringResource(R.string.crash_report_share_options_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.crash_report_share_options_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CrashReportShareChoice(
                    icon = Icons.Outlined.ContentCopy,
                    title = stringResource(R.string.crash_report_share_as_text),
                    description = stringResource(R.string.crash_report_share_as_text_description),
                    onClick = onShareText,
                )
                CrashReportShareChoice(
                    icon = Icons.Outlined.Share,
                    title = stringResource(R.string.crash_report_share_as_file),
                    description = stringResource(R.string.crash_report_share_as_file_description),
                    onClick = onShareFile,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.crash_report_share_cancel))
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

private fun shareCrashReportText(context: Context, report: CrashReport) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_share_subject))
        putExtra(Intent.EXTRA_TEXT, report.toClipboardText())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    launchCrashReportShare(context, intent)
}

private fun shareCrashReportFile(context: Context, report: CrashReport) {
    runCatching {
        val file = File(context.cacheDir, "project_lumen_crash_report_${report.crashedAtMillis}.txt")
        file.writeText(report.toClipboardText(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_share_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchCrashReportShare(context, intent)
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.crash_report_share_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun launchCrashReportShare(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.crash_report_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.crash_report_share_failed), Toast.LENGTH_SHORT).show()
    }
}
