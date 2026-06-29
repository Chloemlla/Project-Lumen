package com.projectlumen.app.app

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.update.BuildMetadata
import com.projectlumen.app.core.update.ReleaseAsset
import com.projectlumen.app.core.update.ReleaseInfo
import com.projectlumen.app.core.update.UpdateCandidate
import com.projectlumen.app.core.update.UpdateChecker
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PROJECT_LUMEN_REPO_URL = "https://github.com/Chloemlla/Project-Lumen"
private const val PROJECT_LUMEN_RELEASES_URL = "https://github.com/Chloemlla/Project-Lumen/releases/latest"
private const val PROJECT_LUMEN_RELEASE_API = "https://api.github.com/repos/Chloemlla/Project-Lumen/releases/latest"
private const val PROJECT_LUMEN_RELEASES_BASE_URL = "https://github.com/Chloemlla/Project-Lumen/releases"
private val crashDetailsTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private val updateDialogTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val showInBottomNav: Boolean = true,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    BREAK("break", R.string.nav_break, Icons.Outlined.Spa),
    POMODORO("pomodoro", R.string.nav_pomodoro, Icons.Outlined.LocalCafe),
    STATS("stats", R.string.nav_stats, Icons.Outlined.BarChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
    TEMPLATES("templates", R.string.nav_templates, Icons.Outlined.Style, false),
    ABOUT("about", R.string.nav_about, Icons.Outlined.Info, false),
}

private enum class SystemBackgroundColor(
    val key: String,
    @StringRes val labelRes: Int,
    val primaryKey: String,
) {
    PRIMARY_CONTAINER("primaryContainer", R.string.system_color_primary, "primary"),
    SECONDARY_CONTAINER("secondaryContainer", R.string.system_color_secondary, "secondary"),
    TERTIARY_CONTAINER("tertiaryContainer", R.string.system_color_tertiary, "tertiary"),
    SURFACE_CONTAINER("surfaceContainer", R.string.system_color_surface, "primary"),
}

private val LumenCardShape = RoundedCornerShape(8.dp)

@Composable
fun ProjectLumenApp(
    viewModel: ProjectLumenViewModel,
    crashReport: CrashReport?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode = runCatching { AppThemeMode.valueOf(uiState.settings.themeMode) }
        .getOrDefault(AppThemeMode.SYSTEM)
    val baseContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateChecker = remember { UpdateChecker(PROJECT_LUMEN_RELEASE_API) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var manualUpdateError by remember { mutableStateOf<String?>(null) }
    var updateCandidate by remember { mutableStateOf<UpdateCandidate?>(null) }
    var updateDialogVisible by remember { mutableStateOf(false) }
    var autoCheckStarted by rememberSaveable { mutableStateOf(false) }

    fun triggerUpdateCheck(manual: Boolean) {
        coroutineScope.launch {
            checkingUpdate = true
            if (manual) manualUpdateError = null
            val result = withContext(Dispatchers.IO) {
                runCatching { updateChecker.checkForUpdate() }
            }
            checkingUpdate = false
            result.onSuccess { candidate ->
                updateCandidate = candidate
                updateDialogVisible = candidate != null
                if (!manual) autoCheckStarted = true
            }.onFailure { throwable ->
                if (manual) {
                    manualUpdateError = throwable.message ?: "Update check failed."
                }
                if (!manual) autoCheckStarted = true
            }
        }
    }

    LaunchedEffect(uiState.settings.languageCode) {
        LocaleController.apply(uiState.settings.languageCode)
    }
    LaunchedEffect(uiState.isReady, uiState.settings.autoUpdateCheckEnabled, autoCheckStarted) {
        if (uiState.isReady && uiState.settings.autoUpdateCheckEnabled && !autoCheckStarted && !checkingUpdate) {
            triggerUpdateCheck(manual = false)
        }
    }
    val localizedContext = baseContext

    CompositionLocalProvider(LocalContext provides localizedContext) {
        ProjectLumenTheme(themeMode = themeMode) {
            if (crashReport != null) {
                CrashReportScreen(report = crashReport)
                return@ProjectLumenTheme
            }
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    val current = Destination.entries.firstOrNull {
                        it.route == backStackEntry?.destination?.route
                    } ?: Destination.HOME
                    LumenTopBar(title = stringResource(current.labelRes))
                },
                bottomBar = {
                    NavigationBar {
                        Destination.entries.filter { it.showInBottomNav }.forEach { destination ->
                            val selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == destination.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    val scale by animateFloatAsState(
                                        targetValue = if (selected) 1.12f else 1f,
                                        animationSpec = spring(stiffness = 600f, dampingRatio = 0.72f),
                                        label = "bottomNavIconScale",
                                    )
                                    Icon(
                                        destination.icon,
                                        contentDescription = null,
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        },
                                    )
                                },
                                label = {
                                    AnimatedContent(
                                        targetState = selected,
                                        transitionSpec = {
                                            fadeIn(tween(120)) togetherWith fadeOut(tween(90))
                                        },
                                        label = "bottomNavLabel",
                                    ) { isSelected ->
                                        Text(
                                            text = stringResource(destination.labelRes),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                },
                            )
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Destination.HOME.route,
                    modifier = Modifier.padding(padding),
                    enterTransition = {
                        fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 10 }
                    },
                    exitTransition = {
                        fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { -it / 12 }
                    },
                    popEnterTransition = {
                        fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 10 }
                    },
                    popExitTransition = {
                        fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { it / 12 }
                    },
                ) {
                    composable(Destination.HOME.route) { HomeScreen(uiState, viewModel) }
                    composable(Destination.BREAK.route) { BreakScreen(uiState, viewModel) }
                    composable(Destination.POMODORO.route) { PomodoroScreen(uiState, viewModel) }
                    composable(Destination.STATS.route) { StatisticsScreen(uiState, viewModel) }
                    composable(Destination.SETTINGS.route) {
                        SettingsScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            checkingUpdate = checkingUpdate,
                            updateError = manualUpdateError,
                            onManualUpdateCheck = { triggerUpdateCheck(manual = true) },
                            openTemplates = { navController.navigate(Destination.TEMPLATES.route) },
                            openAbout = { navController.navigate(Destination.ABOUT.route) },
                        )
                    }
                    composable(Destination.TEMPLATES.route) { TemplatesScreen(uiState, viewModel) }
                    composable(Destination.ABOUT.route) { AboutScreen() }
                }
            }
            if (updateDialogVisible && updateCandidate != null) {
                UpdateDialog(
                    candidate = updateCandidate!!,
                    onDismiss = { updateDialogVisible = false },
                )
            }
            if (manualUpdateError != null) {
                AlertDialog(
                    onDismissRequest = { manualUpdateError = null },
                    title = { Text(stringResource(R.string.about_update_failed_title)) },
                    text = { Text(manualUpdateError.orEmpty()) },
                    confirmButton = {
                        OutlinedButton(onClick = { manualUpdateError = null }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LumenTopBar(title: String) {
    TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) })
}

@Composable
private fun CrashReportScreen(report: CrashReport) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val formattedTime = Instant.ofEpochMilli(report.crashedAtMillis)
        .atZone(ZoneId.systemDefault())
        .format(crashDetailsTimeFormatter)
    LumenPage {
        PageIntro(
            icon = Icons.Outlined.Code,
            titleRes = R.string.app_name,
            message = "应用检测到崩溃，报告已保留。",
        )
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = LumenCardShape,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricRow("时间", formattedTime)
                MetricRow("根因", report.rootCause)
                MetricRow("异常类型", report.exceptionType)
                Text("系统信息", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(report.systemInfo, style = MaterialTheme.typography.bodyMedium)
                Text("完整堆栈", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(report.stackTrace, style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(report.toClipboardText()))
                        (context.applicationContext as? ProjectLumenApplication)?.crashReports?.clear()
                    },
                ) {
                    Text("复制报告")
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val reminderActive = runtime.activeEngine == ActiveEngine.REMINDER.name &&
        runtime.reminderPhase != ReminderPhase.IDLE.name
    val reminderPaused = reminderActive && runtime.reminderPhase == ReminderPhase.PAUSED.name
    val canStartReminder = uiState.settings.reminderEnabled && !reminderActive
    val canPauseReminder = reminderActive && !reminderPaused
    val canResumeReminder = uiState.settings.reminderEnabled && reminderPaused
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    fun runReminderAction(action: () -> Unit) {
        if (uiState.settings.notificationEnabled) runWithNotificationPermission(action) else action()
    }

    LumenPage {
        PageIntro(
            icon = Icons.Outlined.Home,
            titleRes = R.string.home_title,
            message = stringResource(R.string.home_subtitle),
        )
        StateCard(uiState.runtime, uiState.nowMillis)
        TodayStatsCard(uiState.eyeStats.firstOrNull())
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
        ) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = canStartReminder,
                    onClick = { runReminderAction(viewModel::startReminder) },
                ) {
                    ButtonLabel(Icons.Outlined.PlayArrow, R.string.start_reminder)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = canPauseReminder,
                    onClick = viewModel::pauseReminder,
                ) {
                    ButtonLabel(Icons.Outlined.Pause, R.string.pause)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = canPauseReminder,
                onClick = viewModel::pauseForOneHour,
            ) {
                ButtonLabel(Icons.Outlined.Schedule, R.string.silent_until)
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = canResumeReminder,
                onClick = { runReminderAction(viewModel::resumeReminder) },
            ) {
                ButtonLabel(Icons.Outlined.Refresh, R.string.resume_now)
            }
        }
    }
}

@Composable
private fun BreakScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val template = activeTemplate(uiState)
    val isResting = runtime.reminderPhase == ReminderPhase.RESTING.name
    val canSkip = runtime.activeEngine == ActiveEngine.REMINDER.name &&
        runtime.reminderPhase in setOf(
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
            ReminderPhase.RESTING.name,
        )
    LumenPage(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        TemplatePreviewCard(template)
        PageIntro(
            icon = Icons.Outlined.Spa,
            titleRes = R.string.break_title,
            message = stringResource(if (isResting) R.string.break_message else R.string.break_waiting_message),
        )
        TimerCard(
            label = stringResource(if (isResting) R.string.remaining else R.string.current_state),
            seconds = if (isResting) remainingSeconds(runtime.breakEndAt, uiState.nowMillis) else 0,
            progress = if (isResting) progress(runtime.breakStartedAt, runtime.breakEndAt, uiState.nowMillis) else 0f,
            fallbackText = statusLabel(runtime),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = uiState.settings.reminderEnabled && !isResting, onClick = viewModel::startBreak) {
                ButtonLabel(Icons.Outlined.Spa, R.string.start_break)
            }
            if (!uiState.settings.disableSkip) {
                OutlinedButton(enabled = canSkip, onClick = viewModel::skipBreak) {
                    ButtonLabel(Icons.Outlined.SkipNext, R.string.skip_break)
                }
            }
        }
    }
}

@Composable
private fun PomodoroScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val running = runtime.activeEngine == ActiveEngine.POMODORO.name && runtime.pomodoroPhase != PomodoroPhase.IDLE.name
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
            seconds = if (running) remainingSeconds(runtime.pomodoroPhaseEndAt, uiState.nowMillis) else 0,
            progress = if (running) progress(runtime.pomodoroPhaseStartedAt, runtime.pomodoroPhaseEndAt, uiState.nowMillis) else 0f,
            fallbackText = stringResource(R.string.status_ready),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = uiState.settings.pomodoroEnabled && !running,
                onClick = { runPomodoroAction(viewModel::startPomodoro) },
            ) {
                ButtonLabel(Icons.Outlined.PlayArrow, R.string.start_pomodoro)
            }
            OutlinedButton(enabled = running, onClick = viewModel::stopPomodoro) {
                ButtonLabel(Icons.Outlined.Stop, R.string.stop_pomodoro)
            }
        }
    }
}

@Composable
private fun StatisticsScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    val hasExportableStats = uiState.eyeStats.any {
        it.workingSeconds > 0L || it.restSeconds > 0L || it.skipCount > 0 || it.completedBreakCount > 0
    } || uiState.pomodoroStats.any {
        it.completedTomatoCount > 0 || it.completedFocusSessions > 0 || it.totalBreakSeconds > 0L || it.totalFocusSeconds > 0L
    }
    LumenPage {
        TodayStatsCard(eye)
        TrendCard(uiState)
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeader(Icons.Outlined.LocalCafe, R.string.section_pomodoro)
                MetricRow(R.string.completed_tomatoes, (pomodoro?.completedTomatoCount ?: 0).toString())
                MetricRow(R.string.focus_sessions, (pomodoro?.completedFocusSessions ?: 0).toString())
                MetricRow(R.string.rest_time, minutesLabel(((pomodoro?.totalBreakSeconds ?: 0L) / 60L).toInt()))
            }
        }
        Button(enabled = hasExportableStats, onClick = viewModel::shareStatistics) {
            ButtonLabel(Icons.Outlined.FileDownload, R.string.export_csv)
        }
    }
}

@Composable
private fun SettingsScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    checkingUpdate: Boolean,
    updateError: String?,
    onManualUpdateCheck: () -> Unit,
    openTemplates: () -> Unit,
    openAbout: () -> Unit,
) {
    val settings = uiState.settings
    val template = activeTemplate(uiState)
    val context = LocalContext.current
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    val notificationPermissionNeeded = needsNotificationPermission(context)
    val exactAlarmSettingsNeeded = needsExactAlarmSettings(context)
    LumenPage {
        TemplatePreviewCard(template)
        SettingsSection(R.string.section_general, Icons.Outlined.Settings) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            LumenFlowRow {
                LanguageChip(R.string.language_system, LocaleController.SYSTEM, settings, viewModel)
                LanguageChip(R.string.language_zh, LocaleController.CHINESE, settings, viewModel)
                LanguageChip(R.string.language_en, LocaleController.ENGLISH, settings, viewModel)
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            LumenFlowRow {
                ThemeChip(R.string.theme_system, AppThemeMode.SYSTEM, settings, viewModel)
                ThemeChip(R.string.theme_light, AppThemeMode.LIGHT, settings, viewModel)
                ThemeChip(R.string.theme_dark, AppThemeMode.DARK, settings, viewModel)
            }
            LumenFlowRow {
                OutlinedButton(onClick = openTemplates) { ButtonLabel(Icons.Outlined.Style, R.string.nav_templates) }
                OutlinedButton(onClick = openAbout) { ButtonLabel(Icons.Outlined.Info, R.string.nav_about) }
            }
            SwitchRow(R.string.auto_dark_window, Icons.Outlined.Style, settings.useAutoDarkWindow) {
                viewModel.updateSettings { current -> current.copy(useAutoDarkWindow = it) }
            }
            SwitchRow(R.string.auto_update_check, Icons.Outlined.Sync, settings.autoUpdateCheckEnabled) {
                viewModel.setAutoUpdateCheckEnabled(it)
            }
        }
        SettingsSection(R.string.about_update_status, Icons.Outlined.Sync) {
            if (checkingUpdate) {
                Text(stringResource(R.string.about_update_checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onManualUpdateCheck, enabled = !checkingUpdate) {
                ButtonLabel(Icons.Outlined.Sync, R.string.about_check_updates)
            }
        }
        SettingsSection(R.string.section_notifications, Icons.Outlined.NotificationsActive) {
            SwitchRow(R.string.enable_notifications, Icons.Outlined.NotificationsActive, settings.notificationEnabled) { enabled ->
                if (enabled) {
                    runWithNotificationPermission { viewModel.setNotificationsEnabled(true) }
                } else {
                    viewModel.setNotificationsEnabled(false)
                }
            }
            AnimatedVisibility(
                visible = settings.notificationEnabled && notificationPermissionNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.notification_permission_needed,
                    messageRes = R.string.notification_permission_needed_message,
                    actionLabelRes = R.string.allow_notifications,
                    icon = Icons.Outlined.NotificationsActive,
                    onClick = { runWithNotificationPermission { viewModel.setNotificationsEnabled(true) } },
                )
            }
            AnimatedVisibility(
                visible = settings.notificationEnabled && exactAlarmSettingsNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.exact_alarm_permission_needed,
                    messageRes = R.string.exact_alarm_permission_needed_message,
                    actionLabelRes = R.string.open_system_settings,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onClick = { openExactAlarmSettings(context) },
                )
            }
            Button(onClick = {
                if (notificationPermissionNeeded) {
                    runWithNotificationPermission { viewModel.setNotificationsEnabled(true) }
                } else {
                    openAppNotificationSettings(context)
                }
            }) {
                ButtonLabel(
                    if (notificationPermissionNeeded) Icons.Outlined.NotificationsActive else Icons.AutoMirrored.Outlined.OpenInNew,
                    if (notificationPermissionNeeded) R.string.allow_notifications else R.string.notification_system_settings,
                )
            }
        }
        SettingsSection(R.string.section_sound, Icons.AutoMirrored.Outlined.VolumeUp) {
            SwitchRow(R.string.enable_sound, Icons.AutoMirrored.Outlined.VolumeUp, settings.soundEnabled) {
                viewModel.updateSettings { current -> current.copy(soundEnabled = it) }
            }
        }
        SettingsSection(R.string.section_appearance, Icons.Outlined.Style) {
            LumenFlowRow {
                uiState.templates.forEach { template ->
                    val selected = settings.activeTipTemplateId == template.id
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTemplate(template.id) },
                        label = { Text(templateDisplayName(template)) },
                        leadingIcon = {
                            AnimatedVisibility(
                                visible = selected,
                                enter = scaleIn(tween(120)) + fadeIn(tween(120)),
                                exit = scaleOut(tween(90)) + fadeOut(tween(90)),
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
        SettingsSection(R.string.section_reminder, Icons.Outlined.Spa) {
            SwitchRow(R.string.enable_reminder, Icons.Outlined.Spa, settings.reminderEnabled) {
                viewModel.setReminderEnabled(it)
            }
            NumberSlider(R.string.warn_interval, Icons.Outlined.Schedule, settings.warnIntervalMinutes, 5f..120f, 22, stringResource(R.string.minutes_value, settings.warnIntervalMinutes)) {
                viewModel.updateSettings { current -> current.copy(warnIntervalMinutes = it) }
            }
            NumberSlider(R.string.rest_duration, Icons.Outlined.Spa, settings.restDurationSeconds, 10f..300f, 28, stringResource(R.string.seconds_value, settings.restDurationSeconds)) {
                viewModel.updateSettings { current -> current.copy(restDurationSeconds = it) }
            }
            SwitchRow(R.string.ask_before_break, Icons.Outlined.NotificationsActive, settings.askBeforeBreak) {
                viewModel.updateSettings { current -> current.copy(askBeforeBreak = it) }
            }
            SwitchRow(R.string.disable_skip, Icons.Outlined.SkipNext, settings.disableSkip) {
                viewModel.updateSettings { current -> current.copy(disableSkip = it) }
            }
        }
        SettingsSection(R.string.section_pre_alert, Icons.Outlined.Schedule) {
            SwitchRow(R.string.enable_pre_alert, Icons.Outlined.Schedule, settings.preAlertEnabled) {
                viewModel.updateSettings { current -> current.copy(preAlertEnabled = it) }
            }
            NumberSlider(R.string.pre_alert_seconds, Icons.Outlined.Schedule, settings.preAlertSeconds, 10f..300f, 28, stringResource(R.string.seconds_value, settings.preAlertSeconds)) {
                viewModel.updateSettings { current -> current.copy(preAlertSeconds = it) }
            }
        }
        SettingsSection(R.string.section_pomodoro, Icons.Outlined.LocalCafe) {
            SwitchRow(R.string.enable_pomodoro, Icons.Outlined.LocalCafe, settings.pomodoroEnabled) {
                viewModel.setPomodoroEnabled(it)
            }
            NumberSlider(R.string.pomodoro_work, Icons.Outlined.LocalCafe, settings.pomodoroWorkMinutes, 5f..60f, 10, minutesLabel(settings.pomodoroWorkMinutes)) {
                viewModel.updateSettings { current -> current.copy(pomodoroWorkMinutes = it) }
            }
            NumberSlider(R.string.pomodoro_short_break, Icons.Outlined.Spa, settings.pomodoroShortBreakMinutes, 3f..20f, 16, minutesLabel(settings.pomodoroShortBreakMinutes)) {
                viewModel.updateSettings { current -> current.copy(pomodoroShortBreakMinutes = it) }
            }
            NumberSlider(R.string.pomodoro_long_break, Icons.Outlined.Spa, settings.pomodoroLongBreakMinutes, 5f..45f, 39, minutesLabel(settings.pomodoroLongBreakMinutes)) {
                viewModel.updateSettings { current -> current.copy(pomodoroLongBreakMinutes = it) }
            }
        }
    }
}

@Composable
private fun TrendCard(uiState: ProjectLumenUiState) {
    val recent = uiState.eyeStats.take(7).reversed()
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.weekly_trend)
            if (recent.isEmpty()) {
                Text(stringResource(R.string.not_set), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val maxSeconds = recent.maxOf { max(it.workingSeconds, 1L) }
                recent.forEach { stat ->
                    val targetWidth = (stat.workingSeconds.toFloat() / maxSeconds.toFloat()).coerceIn(0.05f, 1f)
                    val animatedWidth by animateFloatAsState(
                        targetValue = targetWidth,
                        animationSpec = tween(durationMillis = 520),
                        label = "trendBarWidth",
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stat.statDate.takeLast(5), style = MaterialTheme.typography.labelMedium)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedWidth)
                                    .height(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                        Text(minutesLabel((stat.workingSeconds / 60L).toInt()), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatesScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val activeTemplate = activeTemplate(uiState)
    LumenPage {
        SectionHeader(Icons.Outlined.Style, R.string.template_preview)
        TemplatePreviewCard(activeTemplate)
        if (activeTemplate != null) {
            SystemBackgroundPicker(activeTemplate, viewModel)
        }
        uiState.templates.forEach { template ->
            val selected = template.id == uiState.settings.activeTipTemplateId
            val borderColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(180),
                label = "templateBorderColor",
            )
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, LumenCardShape)
                    .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
                shape = LumenCardShape,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TemplateColorSwatch(template)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(templateDisplayName(template), style = MaterialTheme.typography.titleMedium)
                            Text(templateSubtitle(template), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTemplate(template.id) },
                        label = { Text(stringResource(R.string.active_template)) },
                        leadingIcon = {
                            AnimatedVisibility(
                                visible = selected,
                                enter = scaleIn(tween(120)) + fadeIn(tween(120)),
                                exit = scaleOut(tween(90)) + fadeOut(tween(90)),
                            ) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemBackgroundPicker(template: TipTemplateEntity, viewModel: ProjectLumenViewModel) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = LumenCardShape) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Style, R.string.system_background_color)
            LumenFlowRow {
                SystemBackgroundColor.entries.forEach { option ->
                    FilterChip(
                        selected = template.backgroundType == TemplateBackgroundType.SYSTEM.name &&
                            template.backgroundValue == option.key,
                        onClick = {
                            viewModel.updateTemplateSystemBackground(
                                template = template,
                                backgroundValue = option.key,
                                primaryColor = option.primaryKey,
                            )
                        },
                        label = { Text(stringResource(option.labelRes)) },
                        leadingIcon = { ColorSwatch(systemThemeColor(option.key), size = 18.dp) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutScreen() {
    val versionLabel = rememberBuildVersionLabel()

    LumenPage {
        AboutHeroCard(versionLabel = versionLabel)
        AboutLinksCard()
    }
}

@Composable
private fun AboutHeroCard(versionLabel: String) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.Info, R.string.app_name)
            Text(stringResource(R.string.about_version), style = MaterialTheme.typography.titleMedium)
            Text(versionLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(R.string.about_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutLinksCard() {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = LumenCardShape) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Code, R.string.about_links)
            LinkButton(Icons.Outlined.Code, R.string.about_source_code, PROJECT_LUMEN_REPO_URL)
            LinkButton(Icons.Outlined.Code, R.string.about_latest_release, PROJECT_LUMEN_RELEASES_URL)
        }
    }
}

@Composable
private fun AboutUpdateCard(
    checkingUpdate: Boolean,
    manualCheckError: String?,
    onManualCheck: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = LumenCardShape) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Sync, R.string.about_update_status)
            if (checkingUpdate) {
                Text(stringResource(R.string.about_update_checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            manualCheckError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(onClick = onManualCheck, enabled = !checkingUpdate) {
                ButtonLabel(Icons.Outlined.Sync, R.string.about_check_updates)
            }
        }
    }
}

@Composable
private fun LinkButton(icon: ImageVector, @StringRes labelRes: Int, url: String) {
    val context = LocalContext.current
    OutlinedButton(onClick = { openUrl(context, url) }) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
}

@Composable
private fun UpdateDialog(candidate: UpdateCandidate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val release = candidate.release
    val targetAsset = candidate.matchedAsset ?: chooseFallbackAsset(release.assets)
    val current = candidate.currentBuild
    val publishTime = Instant.ofEpochMilli(release.publishedAtUtcMillis).atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
    val buildTime = Instant.ofEpochMilli(current.buildTimeUtcMillis).atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
    val primaryActionLabel = if (targetAsset != null) R.string.about_download_update else R.string.about_open_release

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(release.tagName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.about_update_found, release.tagName))
                Text(stringResource(R.string.about_update_current_version, current.versionName, current.shortHash))
                Text(stringResource(R.string.about_update_build_time, buildTime))
                Text(stringResource(R.string.about_update_publish_time, publishTime))
                if (candidate.isTimeFallback) {
                    Text(stringResource(R.string.about_update_time_fallback), color = MaterialTheme.colorScheme.primary)
                }
                if (release.body.isNotBlank()) {
                    Text(release.body)
                }
                Text(stringResource(R.string.about_update_release_notes, release.releaseName))
            }
        },
        confirmButton = {
            OutlinedButton(onClick = {
                if (targetAsset != null) {
                    openUrl(context, targetAsset.downloadUrl)
                } else {
                    openUrl(context, release.htmlUrl.ifBlank { PROJECT_LUMEN_RELEASES_BASE_URL })
                }
            }) {
                ButtonLabel(Icons.Outlined.FileDownload, primaryActionLabel)
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun StateCard(runtime: RuntimeStateEntity, nowMillis: Long) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.NotificationsActive, R.string.current_state)
            AnimatedContent(
                targetState = statusLabel(runtime),
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 })
                },
                label = "runtimeStatus",
            ) { status ->
                Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            MetricRow(
                R.string.next_reminder,
                if (runtime.nextReminderAt > 0) compactTime(remainingSeconds(runtime.nextReminderAt, nowMillis)) else stringResource(R.string.not_set),
            )
        }
    }
}

@Composable
private fun TodayStatsCard(stat: DailyEyeStatsEntity?) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.today_summary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.working_time, stringResource(R.string.hours_short, ((stat?.workingSeconds ?: 0L) / 3600.0)))
                SmallMetric(R.string.rest_time, minutesLabel(((stat?.restSeconds ?: 0L) / 60L).toInt()))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.skip_count, (stat?.skipCount ?: 0).toString())
                SmallMetric(R.string.completed_breaks, (stat?.completedBreakCount ?: 0).toString())
            }
        }
    }
}

@Composable
private fun TimerCard(label: String, seconds: Long, progress: Float, fallbackText: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "timerProgress",
    )
    val running = seconds > 0
    val transition = rememberInfiniteTransition(label = "timerPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "timerPulseScale",
    )
    val pulse = if (running) pulseScale else 1f
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer {
                        scaleX = pulse
                        scaleY = pulse
                    },
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                AnimatedContent(
                    targetState = if (seconds > 0) compactTime(seconds) else fallbackText,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 2 },
                            initialContentExit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 2 },
                            sizeTransform = SizeTransform(clip = false),
                        )
                    },
                    label = "timerText",
                ) { text ->
                    Text(text, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                }
            }
            LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun TemplatePreviewCard(template: TipTemplateEntity?) {
    val background = templateBackgroundColor(template)
    val primary = templatePrimaryColor(template)
    val animatedBackground by animateColorAsState(background, tween(220), label = "templateBackground")
    val animatedPrimary by animateColorAsState(primary, tween(220), label = "templatePrimary")
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = CardDefaults.elevatedCardColors(containerColor = animatedBackground),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(animatedBackground)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(templateDisplayName(template), style = MaterialTheme.typography.titleMedium, color = animatedPrimary)
                Text(templateSubtitle(template), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PageIntro(icon: ImageVector, @StringRes titleRes: Int, message: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InlineHeader(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, @StringRes titleRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsSection(@StringRes titleRes: Int, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(icon, titleRes)
            content()
        }
    }
}

@Composable
private fun NotificationRequirementCard(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    @StringRes actionLabelRes: Int,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(messageRes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) {
            Text(stringResource(actionLabelRes))
        }
    }
}

@Composable
private fun ButtonLabel(icon: ImageVector, @StringRes labelRes: Int) {
    Icon(icon, contentDescription = null)
    Spacer(Modifier.width(8.dp))
    Text(stringResource(labelRes))
}

@Composable
private fun rememberNotificationPermissionGate(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }
    return { action ->
        if (needsNotificationPermission(context)) {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
}

@Composable
private fun SwitchRow(@StringRes labelRes: Int, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelWithIcon(icon, labelRes)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberSlider(
    @StringRes labelRes: Int,
    icon: ImageVector,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            LabelWithIcon(icon, labelRes)
            AnimatedContent(
                targetState = valueLabel,
                transitionSpec = {
                    (fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 2 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 2 })
                },
                label = "sliderValue",
            ) { text ->
                Text(text, color = MaterialTheme.colorScheme.primary)
            }
        }
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            valueRange = range,
            steps = steps,
            onValueChange = { onValueChange(it.roundToInt()) },
        )
    }
}

@Composable
private fun LabelWithIcon(icon: ImageVector, @StringRes labelRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LumenFlowRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun LanguageChip(@StringRes labelRes: Int, code: String, settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    FilterChip(
        selected = LocaleController.normalize(settings.languageCode) == LocaleController.normalize(code),
        onClick = { viewModel.setLanguageCode(code) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun ThemeChip(@StringRes labelRes: Int, mode: AppThemeMode, settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    FilterChip(
        selected = settings.themeMode == mode.name,
        onClick = { viewModel.setThemeMode(mode) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun RowScope.SmallMetric(@StringRes labelRes: Int, value: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f))
            .padding(12.dp),
    ) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (fadeIn(tween(150)) + slideInVertically(tween(150)) { it / 2 }) togetherWith
                        (fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 2 })
                },
                label = "metricValue",
            ) { metricValue ->
                Text(metricValue, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
}

@Composable
private fun MetricRow(@StringRes labelRes: Int, value: String) {
    MetricRow(stringResource(labelRes), value)
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 2 }) togetherWith
                    (fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 2 })
            },
            label = "metricRowValue",
        ) { metricValue ->
            Text(metricValue, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ColorSwatch(color: Color, size: Dp = 44.dp) {
    val animatedColor by animateColorAsState(color, tween(180), label = "colorSwatch")
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(animatedColor),
    )
}

@Composable
private fun TemplateColorSwatch(template: TipTemplateEntity) {
    ColorSwatch(templateBackgroundColor(template))
}

@Composable
private fun LumenPage(horizontalAlignment: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
                .padding(PaddingValues(16.dp)),
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun statusLabel(runtime: RuntimeStateEntity): String {
    return when (runtime.activeEngine) {
        ActiveEngine.REMINDER.name -> when (runtime.reminderPhase) {
            ReminderPhase.WORKING.name -> stringResource(R.string.status_working)
            ReminderPhase.PRE_ALERT.name -> stringResource(R.string.status_pre_alert)
            ReminderPhase.AWAITING_ACTION.name -> stringResource(R.string.status_waiting)
            ReminderPhase.RESTING.name -> stringResource(R.string.status_resting)
            ReminderPhase.PAUSED.name -> stringResource(R.string.status_paused)
            else -> stringResource(R.string.status_ready)
        }
        ActiveEngine.POMODORO.name -> when (runtime.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> stringResource(R.string.status_focus)
            PomodoroPhase.SHORT_BREAK.name -> stringResource(R.string.status_short_break)
            PomodoroPhase.LONG_BREAK.name -> stringResource(R.string.status_long_break)
            else -> stringResource(R.string.status_ready)
        }
        else -> stringResource(R.string.status_ready)
    }
}

@Composable
private fun compactTime(totalSeconds: Long): String {
    val safeSeconds = max(0L, totalSeconds)
    return stringResource(R.string.minutes_compact, (safeSeconds / 60L).toInt(), (safeSeconds % 60L).toInt())
}

@Composable
private fun minutesLabel(minutes: Int): String = stringResource(R.string.minutes_short, minutes)

@Composable
private fun templateDisplayName(template: TipTemplateEntity?): String {
    return when (template?.id) {
        1L -> stringResource(R.string.template_calm_teal)
        2L -> stringResource(R.string.template_soft_sunrise)
        3L -> stringResource(R.string.template_focus_indigo)
        4L -> stringResource(R.string.template_system_colors)
        else -> template?.name ?: stringResource(R.string.template_calm_teal)
    }
}

@Composable
private fun templateSubtitle(template: TipTemplateEntity?): String {
    return when (template?.id) {
        1L -> stringResource(R.string.template_calm_teal_subtitle)
        2L -> stringResource(R.string.template_soft_sunrise_subtitle)
        3L -> stringResource(R.string.template_focus_indigo_subtitle)
        4L -> stringResource(R.string.template_system_colors_subtitle)
        else -> template?.subtitleText ?: stringResource(R.string.break_message)
    }
}

private fun activeTemplate(uiState: ProjectLumenUiState): TipTemplateEntity? {
    return uiState.templates.firstOrNull { it.id == uiState.settings.activeTipTemplateId } ?: uiState.templates.firstOrNull()
}

private fun remainingSeconds(endAt: Long, nowMillis: Long): Long {
    if (endAt <= 0L) return 0L
    return max(0L, (endAt - nowMillis) / 1000L)
}

private fun progress(startAt: Long, endAt: Long, nowMillis: Long): Float {
    if (startAt <= 0L || endAt <= startAt) return 0f
    val elapsed = (nowMillis - startAt).coerceAtLeast(0L).toFloat()
    val duration = (endAt - startAt).toFloat()
    return (elapsed / duration).coerceIn(0f, 1f)
}

@Composable
private fun templateBackgroundColor(template: TipTemplateEntity?): Color {
    return if (template?.backgroundType == TemplateBackgroundType.SYSTEM.name) {
        systemThemeColor(template.backgroundValue)
    } else {
        parseColor(template?.backgroundValue, MaterialTheme.colorScheme.primaryContainer)
    }
}

@Composable
private fun templatePrimaryColor(template: TipTemplateEntity?): Color {
    return if (template?.backgroundType == TemplateBackgroundType.SYSTEM.name) {
        systemThemeColor(template.primaryColor)
    } else {
        parseColor(template?.primaryColor, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun systemThemeColor(key: String?): Color {
    val colors = MaterialTheme.colorScheme
    return when (key) {
        "primary" -> colors.primary
        "secondary" -> colors.secondary
        "tertiary" -> colors.tertiary
        "primaryContainer" -> colors.primaryContainer
        "secondaryContainer" -> colors.secondaryContainer
        "tertiaryContainer" -> colors.tertiaryContainer
        "surfaceContainer" -> colors.surfaceContainer
        "surfaceVariant" -> colors.surfaceVariant
        else -> colors.primaryContainer
    }
}

private fun parseColor(hex: String?, fallback: Color): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

private fun openUrl(context: Context, url: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun rememberBuildVersionLabel(): String {
    val metadata = remember { BuildMetadata.current() }
    return "${metadata.versionName} (${metadata.shortHash})"
}

private fun chooseFallbackAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
    return assets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.contains("all", ignoreCase = true) }
        ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}

private fun needsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
}

private fun needsExactAlarmSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    return !alarmManager.canScheduleExactAlarms()
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}


