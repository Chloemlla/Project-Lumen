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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalConfiguration
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
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
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
            CrashReportHero(
                title = title,
                message = message,
                compact = layout.compactWidth,
            )

            CrashReportCard(contentPadding = layout.cardPadding) {
                CrashReportSectionHeader(Icons.Outlined.Info, stringResource(R.string.lumen_crash_report_summary))
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_id),
                    value = report.reportId,
                    dense = layout.denseControls,
                    contentPadding = layout.controlPadding,
                )
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_time),
                    value = formattedTime,
                    dense = layout.denseControls,
                    contentPadding = layout.controlPadding,
                )
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_root_cause),
                    value = report.rootCause,
                    emphasis = true,
                    dense = layout.denseControls,
                    contentPadding = layout.controlPadding,
                )
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_exception_type),
                    value = report.exceptionType,
                    dense = layout.denseControls,
                    contentPadding = layout.controlPadding,
                )
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_thread),
                    value = report.threadName,
                    dense = layout.denseControls,
                    contentPadding = layout.controlPadding,
                )
                CrashReportInfoTile(
                    label = stringResource(R.string.lumen_crash_report_process),
                    value = report.processName,
                    dense = layout.denseControls,
                    contentPadding = layout.controlPadding,
                )
            }

            CrashReportCard(contentPadding = layout.cardPadding) {
                CrashReportSectionHeader(Icons.Outlined.Devices, stringResource(R.string.lumen_crash_report_system_info))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    systemInfo.forEach { (label, value) ->
                        CrashReportMetadataPill(
                            label = label,
                            value = value,
                            maxWidth = layout.pillMaxWidth,
                            dense = layout.denseControls,
                        )
                    }
                }
            }

            if (report.recentEvents.isNotEmpty()) {
                CrashReportCard(contentPadding = layout.cardPadding) {
                    CrashReportSectionHeader(
                        Icons.Outlined.Info,
                        stringResource(R.string.lumen_crash_report_recent_events),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        report.recentEvents.takeLast(CRASH_EVENT_VISIBLE_COUNT).forEach { event ->
                            CrashReportEventRow(event = event, dense = layout.denseControls)
                        }
                    }
                }
            }

            CrashReportCard(contentPadding = layout.cardPadding) {
                if (layout.compactWidth) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CrashReportSectionHeader(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.lumen_crash_report_stack_trace),
                        )
                        TextButton(
                            onClick = { stackExpanded = !stackExpanded },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (stackExpanded) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CrashReportSectionHeader(
                            icon = Icons.Outlined.BugReport,
                            title = stringResource(R.string.lumen_crash_report_stack_trace),
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        TextButton(onClick = { stackExpanded = !stackExpanded }) {
                            Icon(
                                imageVector = if (stackExpanded) {
                                    Icons.Outlined.ExpandLess
                                } else {
                                    Icons.Outlined.ExpandMore
                                },
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
                twoColumnActions = layout.twoColumnActions,
                compact = layout.compactWidth,
                dense = layout.denseControls,
                buttonMinHeight = layout.buttonMinHeight,
                buttonContentPadding = layout.buttonContentPadding,
                iconButtonSpacing = layout.iconButtonSpacing,
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
    val cardPadding: Dp,
    val controlPadding: Dp,
    val controlInnerSpacing: Dp,
    val buttonMinHeight: Dp,
    val buttonContentPadding: PaddingValues,
    val iconButtonSpacing: Dp,
    val stackCollapsedMaxHeight: Dp,
    val stackExpandedMaxHeight: Dp,
    val pillMaxWidth: Dp,
    val compactWidth: Boolean,
    val denseControls: Boolean,
    val horizontalActions: Boolean,
    val twoColumnActions: Boolean,
)

@Composable
private fun rememberCrashLayoutTokens(
    windowSizeClass: WindowSizeClass?,
    maxWidth: Dp,
    maxHeight: Dp,
): CrashLayoutTokens {
    val configuration = LocalConfiguration.current
    val widthClass = windowSizeClass?.widthSizeClass
    val heightClass = windowSizeClass?.heightSizeClass
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val effectiveWidth = minOf(maxWidth, screenWidth)
    val effectiveHeight = minOf(maxHeight, screenHeight)
    val compactWidth =
        widthClass == WindowWidthSizeClass.Compact ||
            effectiveWidth < 600.dp ||
            screenWidth < 600.dp
    val mediumWidth =
        !compactWidth && (
            widthClass == WindowWidthSizeClass.Medium ||
                (effectiveWidth >= 600.dp && effectiveWidth < 840.dp)
            )
    val expandedWidth =
        widthClass == WindowWidthSizeClass.Expanded ||
            effectiveWidth >= 840.dp ||
            screenWidth >= 840.dp
    val compactHeight =
        heightClass == WindowHeightSizeClass.Compact ||
            effectiveHeight < 560.dp ||
            screenHeight < 560.dp
    val phoneLike = compactWidth || (screenWidth < 600.dp)
    val maxContentWidth = when {
        expandedWidth -> 960.dp
        compactWidth -> effectiveWidth
        mediumWidth -> 720.dp
        else -> 720.dp
    }
    val stackCollapsed = when {
        phoneLike && compactHeight -> 120.dp
        compactHeight -> 140.dp
        expandedWidth -> 280.dp
        phoneLike -> 180.dp
        else -> 220.dp
    }
    val stackExpanded = when {
        phoneLike && compactHeight -> 220.dp
        compactHeight -> 240.dp
        expandedWidth -> 520.dp
        phoneLike -> 320.dp
        else -> 420.dp
    }
    val twoColumnActions = mediumWidth && !compactHeight
    val horizontalActions = expandedWidth && !compactHeight
    val denseControls = phoneLike || compactHeight
    val veryNarrowPhone = phoneLike && effectiveWidth < 360.dp
    val controlPadding = when {
        veryNarrowPhone -> 10.dp
        denseControls -> 12.dp
        else -> 14.dp
    }
    val buttonMinHeight = when {
        veryNarrowPhone -> 44.dp
        denseControls -> 48.dp
        else -> 52.dp
    }
    val buttonHorizontalPadding = when {
        veryNarrowPhone -> 12.dp
        denseControls -> 14.dp
        else -> 16.dp
    }
    val buttonVerticalPadding = when {
        veryNarrowPhone -> 8.dp
        denseControls -> 10.dp
        else -> 12.dp
    }
    return CrashLayoutTokens(
        maxContentWidth = maxContentWidth,
        horizontalPadding = when {
            veryNarrowPhone -> 12.dp
            compactWidth -> 16.dp
            else -> 20.dp
        },
        verticalPadding = when {
            compactHeight -> 12.dp
            phoneLike -> 16.dp
            else -> 24.dp
        },
        sectionSpacing = when {
            compactHeight -> 10.dp
            phoneLike -> 12.dp
            else -> 16.dp
        },
        cardPadding = if (denseControls) 12.dp else 16.dp,
        controlPadding = controlPadding,
        controlInnerSpacing = if (denseControls) 8.dp else 12.dp,
        buttonMinHeight = buttonMinHeight,
        buttonContentPadding = PaddingValues(
            horizontal = buttonHorizontalPadding,
            vertical = buttonVerticalPadding,
        ),
        iconButtonSpacing = if (veryNarrowPhone) 6.dp else if (denseControls) 8.dp else 10.dp,
        stackCollapsedMaxHeight = stackCollapsed,
        stackExpandedMaxHeight = stackExpanded,
        pillMaxWidth = when {
            phoneLike && effectiveWidth < 400.dp -> effectiveWidth
            phoneLike -> (effectiveWidth - 24.dp).coerceAtLeast(160.dp)
            expandedWidth -> 360.dp
            else -> 320.dp
        },
        compactWidth = compactWidth,
        denseControls = denseControls,
        horizontalActions = horizontalActions,
        twoColumnActions = twoColumnActions,
    )
}

@Composable
private fun CrashIntegrityBlockedScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
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
            dense = true,
            contentPadding = 12.dp,
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
private fun CrashReportHero(
    title: String,
    message: String,
    compact: Boolean,
) {
    val iconSize = if (compact) 44.dp else 52.dp
    val titleStyle = if (compact) {
        MaterialTheme.typography.titleLarge
    } else {
        MaterialTheme.typography.headlineSmall
    }
    val messageStyle = if (compact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
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
                style = titleStyle,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message,
                style = messageStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CrashReportCard(
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(if (contentPadding < 16.dp) 10.dp else 12.dp),
            content = content,
        )
    }
}

@Composable
private fun CrashReportSectionHeader(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrashReportInfoTile(
    label: String,
    value: String,
    emphasis: Boolean = false,
    dense: Boolean = false,
    contentPadding: Dp = 12.dp,
) {
    val labelStyle = if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val valueStyle = when {
        emphasis && dense -> MaterialTheme.typography.titleSmall
        emphasis -> MaterialTheme.typography.titleMedium
        dense -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyMedium
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 4.dp),
    ) {
        Text(
            text = label,
            style = labelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = valueStyle,
            fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (emphasis) 5 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrashReportMetadataPill(
    label: String,
    value: String,
    maxWidth: Dp,
    dense: Boolean = false,
) {
    val horizontal = if (dense) 10.dp else 12.dp
    val vertical = if (dense) 8.dp else 10.dp
    Surface(
        modifier = if (dense) {
            Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
        } else {
            Modifier.widthIn(max = maxWidth)
        },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = horizontal, vertical = vertical),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = if (dense) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CrashReportEventRow(
    event: String,
    dense: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            text = event,
            modifier = Modifier.padding(
                horizontal = if (dense) 10.dp else 12.dp,
                vertical = if (dense) 8.dp else 10.dp,
            ),
            style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (dense) 4 else 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CrashReportActionPanel(
    horizontalActions: Boolean,
    twoColumnActions: Boolean,
    compact: Boolean,
    dense: Boolean,
    buttonMinHeight: Dp,
    buttonContentPadding: PaddingValues,
    iconButtonSpacing: Dp,
    onCopyId: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClear: () -> Unit,
) {
    val panelPadding = if (dense) 12.dp else 16.dp
    val buttonSpacing = if (dense) 8.dp else 12.dp
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(panelPadding),
            verticalArrangement = Arrangement.spacedBy(buttonSpacing),
        ) {
            when {
                horizontalActions -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_copy_id),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopyId,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                        )
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_copy),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopy,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                        )
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_share),
                            icon = Icons.Outlined.Share,
                            onClick = onShare,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                        )
                    }
                    CrashReportActionButton(
                        text = stringResource(R.string.lumen_crash_report_clear_and_continue),
                        icon = Icons.Outlined.DeleteOutline,
                        onClick = onClear,
                        outlined = false,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = buttonMinHeight,
                        contentPadding = buttonContentPadding,
                        iconSpacing = iconButtonSpacing,
                        maxLines = 1,
                    )
                }
                twoColumnActions -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_copy_id),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopyId,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                        )
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_copy),
                            icon = Icons.Outlined.ContentCopy,
                            onClick = onCopy,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_share),
                            icon = Icons.Outlined.Share,
                            onClick = onShare,
                            outlined = true,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                        )
                        CrashReportActionButton(
                            text = stringResource(R.string.lumen_crash_report_clear_and_continue),
                            icon = Icons.Outlined.DeleteOutline,
                            onClick = onClear,
                            outlined = false,
                            modifier = Modifier.weight(1f),
                            minHeight = buttonMinHeight,
                            contentPadding = buttonContentPadding,
                            iconSpacing = iconButtonSpacing,
                            maxLines = 1,
                        )
                    }
                }
                else -> {
                    CrashReportActionButton(
                        text = stringResource(R.string.lumen_crash_report_copy_id),
                        icon = Icons.Outlined.ContentCopy,
                        onClick = onCopyId,
                        outlined = true,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = buttonMinHeight,
                        contentPadding = buttonContentPadding,
                        iconSpacing = iconButtonSpacing,
                    )
                    CrashReportActionButton(
                        text = stringResource(R.string.lumen_crash_report_copy),
                        icon = Icons.Outlined.ContentCopy,
                        onClick = onCopy,
                        outlined = true,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = buttonMinHeight,
                        contentPadding = buttonContentPadding,
                        iconSpacing = iconButtonSpacing,
                    )
                    CrashReportActionButton(
                        text = stringResource(R.string.lumen_crash_report_share),
                        icon = Icons.Outlined.Share,
                        onClick = onShare,
                        outlined = true,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = buttonMinHeight,
                        contentPadding = buttonContentPadding,
                        iconSpacing = iconButtonSpacing,
                    )
                    CrashReportActionButton(
                        text = stringResource(R.string.lumen_crash_report_clear_and_continue),
                        icon = Icons.Outlined.DeleteOutline,
                        onClick = onClear,
                        outlined = false,
                        modifier = Modifier.fillMaxWidth(),
                        minHeight = buttonMinHeight,
                        contentPadding = buttonContentPadding,
                        iconSpacing = iconButtonSpacing,
                        maxLines = if (compact) 2 else 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashReportActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    minHeight: Dp,
    contentPadding: PaddingValues,
    iconSpacing: Dp,
    maxLines: Int = 1,
) {
    val buttonModifier = modifier.defaultMinSize(minHeight = minHeight)
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = contentPadding,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(iconSpacing))
            Text(text = text, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = contentPadding,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(iconSpacing))
            Text(text = text, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
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
    val dense = LocalConfiguration.current.screenWidthDp < 600
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (dense) 56.dp else 64.dp),
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(if (dense) 12.dp else 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (dense) 18.dp else 20.dp),
        )
        Spacer(modifier = Modifier.size(if (dense) 10.dp else 12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = title,
                style = if (dense) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (dense) 3 else 2,
                overflow = TextOverflow.Ellipsis,
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
