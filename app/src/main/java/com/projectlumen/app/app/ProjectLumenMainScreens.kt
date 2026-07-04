package com.projectlumen.app.app

import android.annotation.SuppressLint
import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.projectlumen.app.BuildConfig
import com.projectlumen.app.R
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.enums.QuietMode
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.services.BackupImportSummary
import com.projectlumen.app.core.update.BuildMetadata
import com.projectlumen.app.core.update.ReleaseAsset
import com.projectlumen.app.core.update.ReleaseInfo
import com.projectlumen.app.core.update.UpdateInstaller
import com.projectlumen.app.core.update.UpdateCandidate
import com.projectlumen.app.core.update.UpdateChecker
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
        PageIntro(
            icon = Icons.Outlined.Home,
            titleRes = R.string.home_title,
            message = stringResource(R.string.home_subtitle),
        )
        StateCard(uiState.runtime, uiState.nowMillis)
        TodayStatsCard(uiState.eyeStats.firstOrNull())
        GoalProgressCard(uiState)
        HomeConvenienceCard(
            uiState = uiState,
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
                !uiState.settings.reminderEnabled && !timerActive -> EmptyStateMessage(R.string.reminder_disabled_hint)
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
    val template = activeTemplate(uiState)
    val reminderActive = runtime.activeEngine == ActiveEngine.REMINDER.name &&
        runtime.reminderPhase != ReminderPhase.IDLE.name
    val timerActive = runtime.activeEngine != ActiveEngine.IDLE.name
    val isResting = runtime.reminderPhase == ReminderPhase.RESTING.name
    val canStartBreak = uiState.settings.reminderEnabled &&
        reminderActive &&
        runtime.reminderPhase in setOf(
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
        )
    val canSkip = !uiState.settings.disableSkip &&
        template?.showSkipButton != false &&
        reminderActive &&
        runtime.reminderPhase in setOf(
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
            ReminderPhase.RESTING.name,
        )
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
            countdownStyle = templateCountdownStyle(template),
        )
        ActionCard {
            SectionHeader(Icons.Outlined.Spa, R.string.quick_actions)
            when {
                !uiState.settings.reminderEnabled && !timerActive -> EmptyStateMessage(R.string.reminder_disabled_hint)
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
                !uiState.settings.pomodoroEnabled && !timerActive -> EmptyStateMessage(R.string.pomodoro_disabled_hint)
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
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    var statsWindow by rememberSaveable { mutableIntStateOf(7) }
    val windowEyeStats = uiState.eyeStats.take(statsWindow)
    val windowPomodoroStats = uiState.pomodoroStats.take(statsWindow)
    val hasExportableStats = statsEnabled && (uiState.eyeStats.any {
        it.workingSeconds > 0L || it.restSeconds > 0L || it.skipCount > 0 || it.completedBreakCount > 0
    } || uiState.pomodoroStats.any {
        it.completedTomatoCount > 0 || it.completedFocusSessions > 0 || it.totalBreakSeconds > 0L || it.totalFocusSeconds > 0L
    })
    LumenPage {
        PageIntro(
            icon = Icons.Outlined.BarChart,
            titleRes = R.string.statistics_title,
            message = stringResource(if (statsEnabled) R.string.statistics_subtitle else R.string.statistics_disabled),
        )
        EyeCareHealthReportCard(uiState, permissionRequirements, shizukuState.ready)
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
            colors = lumenCardColors(),
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
                    if (statsEnabled) R.string.statistics_no_export_data else R.string.statistics_disabled,
                )
            }
        }
    }
}


