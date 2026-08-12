package com.projectlumen.app.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.projectlumen.app.R
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.ui.theme.lumenGlass
import kotlinx.coroutines.launch

@Composable
internal fun HomeConvenienceCard(
    uiState: ProjectLumenUiState,
    permissionRequirements: PermissionRequirements,
    shizukuReady: Boolean,
    onApplyRecommended: () -> Unit,
    onStartBreak: () -> Unit,
    onStartPomodoro: () -> Unit,
    onPauseOneHour: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val summaryText = homeConvenienceSummary(uiState)
    val recommendedFeedback = rememberRecommendedEyeCareApplyFeedback(onApplyRecommended)
    val copiedMessage = stringResource(R.string.home_convenience_summary_copied)
    val reminderActive = uiState.runtime.activeEngine == ActiveEngine.REMINDER.name &&
        uiState.runtime.reminderPhase != ReminderPhase.IDLE.name
    val canStartBreak = uiState.settings.reminderEnabled &&
        uiState.runtime.activeEngine != ActiveEngine.POMODORO.name
    val canStartPomodoro = uiState.settings.pomodoroEnabled &&
        uiState.runtime.activeEngine == ActiveEngine.IDLE.name
    val canPauseOneHour = reminderActive &&
        uiState.runtime.reminderPhase != ReminderPhase.PAUSED.name

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .lumenGlass(
                shape = LumenCardShape,
                blurRadius = 18f,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
            ),
        shape = LumenCardShape,
        colors = lumenCardColors().copy(containerColor = Color.Transparent),
        elevation = lumenCardElevation(),
        border = lumenCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(Icons.Outlined.CheckCircle, R.string.home_convenience_title)
            Text(
                text = stringResource(R.string.home_convenience_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = recommendedFeedback.onApply,
                ) {
                    ButtonLabel(Icons.Outlined.CheckCircle, R.string.home_convenience_apply_recommended)
                }
                RecommendedEyeCareSetupFeedback(
                    uiState = uiState,
                    permissionRequirements = permissionRequirements,
                    shizukuReady = shizukuReady,
                    applyFeedbackCount = recommendedFeedback.applyCount,
                )
                LumenFlowRow {
                    OutlinedButton(
                        enabled = canStartBreak,
                        onClick = onStartBreak,
                    ) {
                        ButtonLabel(Icons.Outlined.Spa, R.string.home_convenience_rest_now)
                    }
                    OutlinedButton(
                        enabled = canStartPomodoro,
                        onClick = onStartPomodoro,
                    ) {
                        ButtonLabel(Icons.Outlined.LocalCafe, R.string.home_convenience_focus_now)
                    }
                    OutlinedButton(
                        enabled = canPauseOneHour,
                        onClick = onPauseOneHour,
                    ) {
                        ButtonLabel(Icons.Outlined.Schedule, R.string.home_convenience_quiet_hour)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText("home summary", summaryText),
                                    ),
                                )
                                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        ButtonLabel(Icons.Outlined.ContentCopy, R.string.home_convenience_copy_summary)
                    }
                    OutlinedButton(
                        onClick = { shareHomeConvenienceSummary(context, summaryText) },
                    ) {
                        ButtonLabel(Icons.Outlined.Share, R.string.home_convenience_share_summary)
                    }
                }
            }
        }
    }
}

@Composable
private fun homeConvenienceSummary(uiState: ProjectLumenUiState): String {
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    return stringResource(
        R.string.home_convenience_summary_text,
        statusLabel(uiState.runtime),
        ((eye?.workingSeconds ?: 0L) / 60L).toInt(),
        ((eye?.restSeconds ?: 0L) / 60L).toInt(),
        eye?.completedBreakCount ?: 0,
        eye?.skipCount ?: 0,
        pomodoro?.completedFocusSessions ?: 0,
    )
}

private fun shareHomeConvenienceSummary(context: Context, summaryText: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.home_convenience_share_title))
        putExtra(Intent.EXTRA_TEXT, summaryText)
    }
    val chooser = Intent.createChooser(
        intent,
        context.getString(R.string.home_convenience_share_title),
    )
    runCatching { context.startActivity(chooser) }.onFailure {
        Toast.makeText(context, R.string.share_action_failed, Toast.LENGTH_SHORT).show()
    }
}
