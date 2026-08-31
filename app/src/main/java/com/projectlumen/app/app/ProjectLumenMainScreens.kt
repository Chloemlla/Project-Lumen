package com.projectlumen.app.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase

private val BreakStartablePhases = setOf(
    ReminderPhase.WORKING.name,
    ReminderPhase.PRE_ALERT.name,
    ReminderPhase.AWAITING_ACTION.name,
)

private val BreakSkippablePhases = setOf(
    ReminderPhase.PRE_ALERT.name,
    ReminderPhase.AWAITING_ACTION.name,
    ReminderPhase.RESTING.name,
)

@Composable
internal fun HomeScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    openTranslation: () -> Unit,
) {
    val runtime = uiState.runtime
    val reminderActive = runtime.activeEngine == ActiveEngine.REMINDER.name &&
        runtime.reminderPhase != ReminderPhase.IDLE.name
    val reminderPaused = reminderActive && runtime.reminderPhase == ReminderPhase.PAUSED.name
    val timerActive = runtime.activeEngine != ActiveEngine.IDLE.name
    val canStartReminder = uiState.settings.reminderEnabled && runtime.activeEngine == ActiveEngine.IDLE.name
    val canPauseReminder = reminderActive && !reminderPaused
    val canResumeReminder = uiState.settings.reminderEnabled && reminderPaused
    val permissionRequirements = rememberPermissionRequirements()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    val runWithCameraPermission = rememberCameraPermissionGate()
    fun runReminderAction(action: () -> Unit) {
        if (uiState.settings.notificationEnabled) runWithNotificationPermission(action) else action()
    }
    fun resolveNextEyeCarePermission() {
        when {
            permissionRequirements.notification -> runWithNotificationPermission {
                viewModel.setNotificationsEnabled(true)
            }
            permissionRequirements.exactAlarm -> openExactAlarmSettings(context)
            permissionRequirements.fullScreenIntent -> openFullScreenIntentSettings(context)
            permissionRequirements.camera &&
                (uiState.settings.proximityMonitoringEnabled || uiState.settings.blinkMonitoringEnabled) -> {
                runWithCameraPermission {
                    viewModel.setProximityMonitoringEnabled(true)
                    viewModel.setBlinkMonitoringEnabled(true)
                    viewModel.calibrateProximity()
                }
            }
            permissionRequirements.overlay && uiState.settings.globalOverlayEnabled -> openOverlaySettings(context)
            permissionRequirements.writeSettings && uiState.settings.autoBrightnessEnabled -> openWriteSettings(context)
            uiState.settings.shizukuAdvancedModeEnabled && !shizukuState.ready -> {
                viewModel.refreshShizukuState()
                viewModel.requestShizukuAuthorization()
            }
            else -> applyRecommendedEyeCareSettings(viewModel)
        }
    }
    fun calibrateEyeCareDistance() {
        runWithCameraPermission {
            viewModel.setProximityMonitoringEnabled(true)
            viewModel.setBlinkMonitoringEnabled(true)
            viewModel.calibrateProximity()
        }
    }

    LumenPage {
        // No page intro: the top bar already names this screen, and the live runtime state is
        // what the user opened Home for — an intro banner would push it below the fold.
        StateCard(uiState.runtime, uiState.nowMillis)
        TodayStatsCard(uiState.eyeStats.firstOrNull())
        GoalProgressCard(uiState)
        HomeConvenienceCard(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuState.ready,
            onApplyRecommended = { applyRecommendedEyeCareSettings(viewModel) },
            onStartBreak = viewModel::startBreak,
            onStartPomodoro = { runReminderAction(viewModel::startPomodoro) },
            onPauseOneHour = viewModel::pauseForOneHour,
        )
        EyeCareGuidedSetupCard(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuState.ready,
            canStartReminder = canStartReminder,
            onApplyRecommended = { applyRecommendedEyeCareSettings(viewModel) },
            onResolveNextPermission = ::resolveNextEyeCarePermission,
            onCalibrateDistance = ::calibrateEyeCareDistance,
            onStartReminder = { runReminderAction(viewModel::startReminder) },
            onExportReport = viewModel::shareMonthlyReportPdf,
        )
        EyeCareInsightsHomeCard(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuState.ready,
            onApplyRecommended = { applyRecommendedEyeCareSettings(viewModel) },
        )
        AnimatedVisibility(
            visible = uiState.settings.translationEntryEnabled,
            enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
        ) {
            ActionCard {
                SectionHeader(Icons.Outlined.Translate, R.string.nav_translation)
                Text(
                    stringResource(R.string.translation_home_entry_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = openTranslation,
                ) {
                    ButtonLabel(Icons.Outlined.Translate, R.string.translation_open)
                }
            }
        }
        ActionCard {
            SectionHeader(Icons.Outlined.Schedule, R.string.quick_actions)
            when {
                !uiState.settings.reminderEnabled && !timerActive -> EmptyStateMessage(
                    messageRes = R.string.reminder_disabled_hint,
                    illustration = EmptyStateIllustration.VideoStreaming,
                )
                !reminderActive && timerActive -> {
                    EmptyStateMessage(R.string.other_timer_running_hint)
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                canStartReminder -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { runReminderAction(viewModel::startReminder) },
                    ) {
                        ButtonLabel(Icons.Outlined.PlayArrow, R.string.start_reminder)
                    }
                }
                canPauseReminder -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::pauseReminder,
                        ) {
                            ButtonLabel(Icons.Outlined.Pause, R.string.pause)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::pauseForOneHour,
                        ) {
                            ButtonLabel(Icons.Outlined.Schedule, R.string.silent_until)
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                canResumeReminder -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { runReminderAction(viewModel::resumeReminder) },
                    ) {
                        ButtonLabel(Icons.Outlined.Refresh, R.string.resume_now)
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                timerActive -> {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                else -> EmptyStateMessage(R.string.reminder_idle_hint)
            }
        }
    }
}

@Composable
internal fun BreakScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val template = remember(uiState.templates, uiState.settings.activeTipTemplateId) {
        activeTemplate(uiState)
    }
    val countdownStyle = remember(template?.layoutJson) { templateCountdownStyle(template) }
    val reminderActive = runtime.activeEngine == ActiveEngine.REMINDER.name &&
        runtime.reminderPhase != ReminderPhase.IDLE.name
    val timerActive = runtime.activeEngine != ActiveEngine.IDLE.name
    val isResting = runtime.reminderPhase == ReminderPhase.RESTING.name
    val canStartBreak = uiState.settings.reminderEnabled &&
        reminderActive &&
        runtime.reminderPhase in BreakStartablePhases
    val canSkip = !uiState.settings.disableSkip &&
        template?.showSkipButton != false &&
        reminderActive &&
        runtime.reminderPhase in BreakSkippablePhases
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    fun runReminderAction(action: () -> Unit) {
        if (uiState.settings.notificationEnabled) runWithNotificationPermission(action) else action()
    }
    LumenPage(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        TemplatePreviewCard(template)
        PageIntroText(
            icon = Icons.Outlined.Spa,
            title = templateBreakTitle(template),
            message = if (isResting) templateBreakSubtitle(template) else stringResource(R.string.break_waiting_message),
        )
        TimerCard(
            label = if (timerActive) statusLabel(runtime) else stringResource(R.string.current_state),
            seconds = activeTimerRemainingSeconds(runtime, uiState.nowMillis),
            progress = activeTimerProgress(runtime, uiState.nowMillis),
            fallbackText = statusLabel(runtime),
            countdownStyle = countdownStyle,
        )
        ActionCard {
            SectionHeader(Icons.Outlined.Spa, R.string.quick_actions)
            when {
                !uiState.settings.reminderEnabled && !timerActive -> EmptyStateMessage(
                    messageRes = R.string.reminder_disabled_hint,
                    illustration = EmptyStateIllustration.VideoStreaming,
                )
                canStartBreak && canSkip -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::startBreak,
                        ) {
                            ButtonLabel(Icons.Outlined.Spa, R.string.start_break)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = viewModel::skipBreak,
                        ) {
                            ButtonLabel(Icons.Outlined.SkipNext, R.string.skip_break)
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                canStartBreak -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::startBreak,
                    ) {
                        ButtonLabel(Icons.Outlined.Spa, R.string.start_break)
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                canSkip -> {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::skipBreak,
                    ) {
                        ButtonLabel(Icons.Outlined.SkipNext, R.string.skip_break)
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                !reminderActive && runtime.activeEngine == ActiveEngine.IDLE.name -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { runReminderAction(viewModel::startReminder) },
                    ) {
                        ButtonLabel(Icons.Outlined.PlayArrow, R.string.start_reminder)
                    }
                }
                timerActive -> {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                else -> EmptyStateMessage(R.string.break_action_unavailable)
            }
        }
    }
}

@Composable
internal fun PomodoroScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val running = runtime.activeEngine == ActiveEngine.POMODORO.name && runtime.pomodoroPhase != PomodoroPhase.IDLE.name
    val canStartPomodoro = uiState.settings.pomodoroEnabled && runtime.activeEngine == ActiveEngine.IDLE.name
    val timerActive = runtime.activeEngine != ActiveEngine.IDLE.name
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    fun runPomodoroAction(action: () -> Unit) {
        if (uiState.settings.notificationEnabled) runWithNotificationPermission(action) else action()
    }
    LumenPage(horizontalAlignment = Alignment.CenterHorizontally) {
        InlineHeader(
            icon = Icons.Outlined.LocalCafe,
            text = stringResource(R.string.pomodoro_cycle, runtime.pomodoroCycleIndex.coerceIn(1, 4)),
        )
        TimerCard(
            label = statusLabel(runtime),
            seconds = activeTimerRemainingSeconds(runtime, uiState.nowMillis),
            progress = activeTimerProgress(runtime, uiState.nowMillis),
            fallbackText = statusLabel(runtime),
        )
        ActionCard {
            SectionHeader(Icons.Outlined.LocalCafe, R.string.quick_actions)
            when {
                !uiState.settings.pomodoroEnabled && !timerActive -> EmptyStateMessage(
                    messageRes = R.string.pomodoro_disabled_hint,
                    illustration = EmptyStateIllustration.VideoStreaming,
                )
                running -> {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopPomodoro,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.stop_pomodoro)
                    }
                }
                canStartPomodoro -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { runPomodoroAction(viewModel::startPomodoro) },
                    ) {
                        ButtonLabel(Icons.Outlined.PlayArrow, R.string.start_pomodoro)
                    }
                }
                timerActive -> {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::stopAll,
                    ) {
                        ButtonLabel(Icons.Outlined.Stop, R.string.notification_action_stop)
                    }
                }
                else -> EmptyStateMessage(R.string.other_timer_running_hint)
            }
        }
    }
}

private fun activeTimerRemainingSeconds(runtime: RuntimeStateEntity, nowMillis: Long): Long {
    return when (runtime.activeEngine) {
        ActiveEngine.REMINDER.name -> when (runtime.reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name -> remainingSeconds(runtime.nextReminderAt, nowMillis)
            ReminderPhase.RESTING.name -> remainingSeconds(runtime.breakEndAt, nowMillis)
            else -> 0L
        }
        ActiveEngine.POMODORO.name -> when (runtime.pomodoroPhase) {
            PomodoroPhase.FOCUS.name,
            PomodoroPhase.SHORT_BREAK.name,
            PomodoroPhase.LONG_BREAK.name -> remainingSeconds(runtime.pomodoroPhaseEndAt, nowMillis)
            else -> 0L
        }
        else -> 0L
    }
}

private fun activeTimerProgress(runtime: RuntimeStateEntity, nowMillis: Long): Float {
    return when (runtime.activeEngine) {
        ActiveEngine.REMINDER.name -> when (runtime.reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> progress(runtime.reminderStartedAt, runtime.nextReminderAt, nowMillis)
            ReminderPhase.RESTING.name -> progress(runtime.breakStartedAt, runtime.breakEndAt, nowMillis)
            else -> 0f
        }
        ActiveEngine.POMODORO.name -> when (runtime.pomodoroPhase) {
            PomodoroPhase.FOCUS.name,
            PomodoroPhase.SHORT_BREAK.name,
            PomodoroPhase.LONG_BREAK.name -> progress(runtime.pomodoroPhaseStartedAt, runtime.pomodoroPhaseEndAt, nowMillis)
            else -> 0f
        }
        else -> 0f
    }
}

@Composable
internal fun StatisticsScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    val statsEnabled = uiState.settings.statsEnabled
    val permissionRequirements = rememberPermissionRequirements()
    val context = LocalContext.current
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    var statsWindow by rememberSaveable { mutableIntStateOf(7) }
    val windowEyeStats = remember(uiState.eyeStats, statsWindow) { uiState.eyeStats.take(statsWindow) }
    val windowPomodoroStats = remember(uiState.pomodoroStats, statsWindow) {
        uiState.pomodoroStats.take(statsWindow)
    }
    val hasStatsData = remember(uiState.eyeStats, uiState.pomodoroStats) { hasStatsHistory(uiState) }
    val hasExportableStats = statsEnabled && hasStatsData
    LaunchedEffect(permissionRequirements.usageAccess) {
        viewModel.refreshDeviceInsights()
    }
    LumenPage {
        // The enabled-state intro only restated the top-bar title; keep the banner for the
        // paused case, where it is the one place that explains why the numbers are empty.
        if (!statsEnabled) {
            PageIntro(
                icon = Icons.Outlined.BarChart,
                titleRes = R.string.statistics_title,
                message = stringResource(R.string.statistics_disabled),
            )
        }
        EyeCareHealthReportCard(uiState, permissionRequirements, shizukuState.ready)
        DeviceUsageAndPowerInsightsCard(
            state = uiState.deviceInsights,
            onRefresh = viewModel::refreshDeviceInsights,
            onOpenUsageAccess = { openUsageAccessSettings(context) },
            onOpenBatteryUsage = { openSystemBatteryUsageSettings(context) },
        )
        TodayStatsCard(eye)
        LumenFlowRow {
            FilterChip(selected = statsWindow == 7, onClick = { statsWindow = 7 }, label = { Text(stringResource(R.string.stats_range_7_days)) })
            FilterChip(selected = statsWindow == 30, onClick = { statsWindow = 30 }, label = { Text(stringResource(R.string.stats_range_30_days)) })
            FilterChip(selected = statsWindow == 31, onClick = { statsWindow = 31 }, label = { Text(stringResource(R.string.stats_range_month)) })
        }
        AdvancedStatsCard(windowEyeStats, windowPomodoroStats)
        HabitSuggestionCard(uiState)
        TrendCard(uiState)
        EyeCareActionPlanCard(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuState.ready,
            onApplyRecommended = { applyRecommendedEyeCareSettings(viewModel) },
            onExportReport = viewModel::shareMonthlyReportPdf,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
            // Footnote for a different feature on an eye-care page: quiet it so the eye-care
            // cards above keep the page's attention.
            colors = lumenCardColors(LumenCardEmphasis.Quiet),
            elevation = lumenCardElevation(),
            border = lumenCardBorder(LumenCardEmphasis.Quiet),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(Icons.Outlined.LocalCafe, R.string.section_pomodoro)
                MetricRow(R.string.completed_tomatoes, (pomodoro?.completedTomatoCount ?: 0).toString())
                MetricRow(R.string.focus_sessions, (pomodoro?.completedFocusSessions ?: 0).toString())
                MetricRow(R.string.rest_time, minutesLabel(((pomodoro?.totalBreakSeconds ?: 0L) / 60L).toInt()))
            }
        }
        ActionCard {
            SectionHeader(Icons.Outlined.FileDownload, R.string.statistics_export)
            if (hasExportableStats) {
                LumenFlowRow {
                    Button(
                        onClick = viewModel::shareStatistics,
                    ) {
                        ButtonLabel(Icons.Outlined.FileDownload, R.string.export_csv)
                    }
                    OutlinedButton(
                        onClick = viewModel::shareStatisticsImage,
                    ) {
                        ButtonLabel(Icons.Outlined.BarChart, R.string.share_stats_image)
                    }
                    OutlinedButton(
                        onClick = viewModel::shareMonthlyReportPdf,
                    ) {
                        ButtonLabel(Icons.Outlined.FileDownload, R.string.export_pdf_monthly)
                    }
                }
            } else {
                EmptyStateMessage(
                    messageRes = if (statsEnabled) {
                        R.string.statistics_no_export_data
                    } else {
                        R.string.statistics_disabled
                    },
                    illustration = EmptyStateIllustration.Download,
                )
            }
        }
    }
}


