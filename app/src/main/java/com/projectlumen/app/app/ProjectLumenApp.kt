package com.projectlumen.app.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import kotlin.math.max
import kotlin.math.roundToInt

private enum class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    BREAK("break", R.string.nav_break, Icons.Outlined.Spa),
    POMODORO("pomodoro", R.string.nav_pomodoro, Icons.Outlined.LocalCafe),
    STATS("stats", R.string.nav_stats, Icons.Outlined.BarChart),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

@Composable
fun ProjectLumenApp(viewModel: ProjectLumenViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode = remember(uiState.settings.themeMode) {
        runCatching { AppThemeMode.valueOf(uiState.settings.themeMode) }.getOrDefault(AppThemeMode.SYSTEM)
    }
    val baseContext = LocalContext.current
    LaunchedEffect(uiState.settings.languageCode) {
        LocaleController.apply(baseContext, uiState.settings.languageCode)
    }
    val localizedContext = remember(baseContext, uiState.settings.languageCode) {
        LocaleController.wrap(baseContext, uiState.settings.languageCode)
    }

    CompositionLocalProvider(LocalContext provides localizedContext) {
        ProjectLumenTheme(themeMode = themeMode) {
            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            Scaffold(
                topBar = {
                    val current = Destination.entries.firstOrNull {
                        it.route == backStackEntry?.destination?.route
                    } ?: Destination.HOME
                    LumenTopBar(title = stringResource(current.labelRes))
                },
                bottomBar = {
                    NavigationBar {
                        Destination.entries.forEach { destination ->
                            val selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == destination.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.labelRes)) },
                            )
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = Destination.HOME.route,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Destination.HOME.route) {
                        HomeScreen(uiState = uiState, viewModel = viewModel)
                    }
                    composable(Destination.BREAK.route) {
                        BreakScreen(uiState = uiState, viewModel = viewModel)
                    }
                    composable(Destination.POMODORO.route) {
                        PomodoroScreen(uiState = uiState, viewModel = viewModel)
                    }
                    composable(Destination.STATS.route) {
                        StatisticsScreen(uiState = uiState)
                    }
                    composable(Destination.SETTINGS.route) {
                        SettingsScreen(uiState = uiState, viewModel = viewModel)
                    }
                }
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
private fun HomeScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    LumenPage {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StateCard(uiState.runtime, uiState.nowMillis)
        TodayStatsCard(uiState.eyeStats.firstOrNull())
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::startReminder,
                ) {
                    Text(stringResource(R.string.start_reminder))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = viewModel::pauseReminder,
                ) {
                    Text(stringResource(R.string.pause))
                }
            }
        }
    }
}

@Composable
private fun BreakScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val phase = uiState.runtime.reminderPhase
    val isResting = phase == ReminderPhase.RESTING.name
    val remaining = remainingSeconds(uiState.runtime.breakEndAt, uiState.nowMillis)
    LumenPage(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.break_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(if (isResting) R.string.break_message else R.string.break_waiting_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TimerCard(
            label = stringResource(if (isResting) R.string.remaining else R.string.current_state),
            seconds = if (isResting) remaining else 0,
            progress = if (isResting) progress(uiState.runtime.breakStartedAt, uiState.runtime.breakEndAt, uiState.nowMillis) else 0f,
            fallbackText = statusLabel(uiState.runtime),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::startBreak) {
                Text(stringResource(R.string.start_break))
            }
            if (!uiState.settings.disableSkip) {
                OutlinedButton(onClick = viewModel::skipBreak) {
                    Text(stringResource(R.string.skip_break))
                }
            }
        }
    }
}

@Composable
private fun PomodoroScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val phase = runtime.pomodoroPhase
    val running = runtime.activeEngine == ActiveEngine.POMODORO.name && phase != PomodoroPhase.IDLE.name
    LumenPage(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.pomodoro_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.pomodoro_cycle, runtime.pomodoroCycleIndex.coerceIn(1, 4)),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TimerCard(
            label = statusLabel(runtime),
            seconds = if (running) remainingSeconds(runtime.pomodoroPhaseEndAt, uiState.nowMillis) else 0,
            progress = if (running) progress(runtime.pomodoroPhaseStartedAt, runtime.pomodoroPhaseEndAt, uiState.nowMillis) else 0f,
            fallbackText = stringResource(R.string.status_ready),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = uiState.settings.pomodoroEnabled,
                onClick = viewModel::startPomodoro,
            ) {
                Text(stringResource(R.string.start_pomodoro))
            }
            OutlinedButton(onClick = viewModel::stopPomodoro) {
                Text(stringResource(R.string.stop_pomodoro))
            }
        }
    }
}

@Composable
private fun StatisticsScreen(uiState: ProjectLumenUiState) {
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    LumenPage {
        Text(
            text = stringResource(R.string.statistics_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        TodayStatsCard(eye)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricRow(R.string.completed_tomatoes, (pomodoro?.completedTomatoCount ?: 0).toString())
                MetricRow(R.string.focus_sessions, (pomodoro?.completedFocusSessions ?: 0).toString())
                MetricRow(R.string.rest_time, minutesLabel(((pomodoro?.totalBreakSeconds ?: 0L) / 60L).toInt()))
            }
        }
    }
}

@Composable
private fun SettingsScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val settings = uiState.settings
    LumenPage {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        SettingsSection(titleRes = R.string.section_general) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip(R.string.language_system, LocaleController.SYSTEM, settings, viewModel)
                LanguageChip(R.string.language_zh, LocaleController.CHINESE, settings, viewModel)
                LanguageChip(R.string.language_en, LocaleController.ENGLISH, settings, viewModel)
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip(R.string.theme_system, AppThemeMode.SYSTEM, settings, viewModel)
                ThemeChip(R.string.theme_light, AppThemeMode.LIGHT, settings, viewModel)
                ThemeChip(R.string.theme_dark, AppThemeMode.DARK, settings, viewModel)
            }
        }
        SettingsSection(titleRes = R.string.section_reminder) {
            SwitchRow(R.string.enable_reminder, settings.reminderEnabled) {
                viewModel.updateSettings { current -> current.copy(reminderEnabled = it) }
            }
            NumberSlider(
                labelRes = R.string.warn_interval,
                value = settings.warnIntervalMinutes,
                range = 5f..120f,
                steps = 22,
                valueLabel = stringResource(R.string.minutes_value, settings.warnIntervalMinutes),
            ) {
                viewModel.updateSettings { current -> current.copy(warnIntervalMinutes = it) }
            }
            NumberSlider(
                labelRes = R.string.rest_duration,
                value = settings.restDurationSeconds,
                range = 10f..300f,
                steps = 28,
                valueLabel = stringResource(R.string.seconds_value, settings.restDurationSeconds),
            ) {
                viewModel.updateSettings { current -> current.copy(restDurationSeconds = it) }
            }
            SwitchRow(R.string.ask_before_break, settings.askBeforeBreak) {
                viewModel.updateSettings { current -> current.copy(askBeforeBreak = it) }
            }
            SwitchRow(R.string.disable_skip, settings.disableSkip) {
                viewModel.updateSettings { current -> current.copy(disableSkip = it) }
            }
        }
        SettingsSection(titleRes = R.string.section_pre_alert) {
            SwitchRow(R.string.enable_pre_alert, settings.preAlertEnabled) {
                viewModel.updateSettings { current -> current.copy(preAlertEnabled = it) }
            }
            NumberSlider(
                labelRes = R.string.pre_alert_seconds,
                value = settings.preAlertSeconds,
                range = 10f..300f,
                steps = 28,
                valueLabel = stringResource(R.string.seconds_value, settings.preAlertSeconds),
            ) {
                viewModel.updateSettings { current -> current.copy(preAlertSeconds = it) }
            }
        }
        SettingsSection(titleRes = R.string.section_pomodoro) {
            SwitchRow(R.string.enable_pomodoro, settings.pomodoroEnabled) {
                viewModel.updateSettings { current -> current.copy(pomodoroEnabled = it) }
            }
            NumberSlider(R.string.pomodoro_work, settings.pomodoroWorkMinutes, 5f..60f, 10, minutesLabel(settings.pomodoroWorkMinutes)) {
                viewModel.updateSettings { current -> current.copy(pomodoroWorkMinutes = it) }
            }
            NumberSlider(R.string.pomodoro_short_break, settings.pomodoroShortBreakMinutes, 3f..20f, 16, minutesLabel(settings.pomodoroShortBreakMinutes)) {
                viewModel.updateSettings { current -> current.copy(pomodoroShortBreakMinutes = it) }
            }
            NumberSlider(R.string.pomodoro_long_break, settings.pomodoroLongBreakMinutes, 5f..45f, 39, minutesLabel(settings.pomodoroLongBreakMinutes)) {
                viewModel.updateSettings { current -> current.copy(pomodoroLongBreakMinutes = it) }
            }
        }
    }
}

@Composable
private fun StateCard(runtime: RuntimeStateEntity, nowMillis: Long) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricRow(R.string.current_state, statusLabel(runtime))
            MetricRow(
                R.string.next_reminder,
                if (runtime.nextReminderAt > 0) compactTime(remainingSeconds(runtime.nextReminderAt, nowMillis)) else stringResource(R.string.not_set),
            )
        }
    }
}

@Composable
private fun TodayStatsCard(stat: DailyEyeStatsEntity?) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.today_summary), style = MaterialTheme.typography.titleMedium)
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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (seconds > 0) compactTime(seconds) else fallbackText,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SettingsSection(@StringRes titleRes: Int, content: @Composable Column.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SwitchRow(@StringRes labelRes: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberSlider(
    @StringRes labelRes: Int,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
            Text(valueLabel, color = MaterialTheme.colorScheme.primary)
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
private fun LanguageChip(
    @StringRes labelRes: Int,
    code: String,
    settings: AppSettingsEntity,
    viewModel: ProjectLumenViewModel,
) {
    FilterChip(
        selected = settings.languageCode == code,
        onClick = { viewModel.updateSettings { it.copy(languageCode = code) } },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun ThemeChip(
    @StringRes labelRes: Int,
    mode: AppThemeMode,
    settings: AppSettingsEntity,
    viewModel: ProjectLumenViewModel,
) {
    FilterChip(
        selected = settings.themeMode == mode.name,
        onClick = { viewModel.setThemeMode(mode) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SmallMetric(@StringRes labelRes: Int, value: String) {
    ElevatedCard(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MetricRow(@StringRes labelRes: Int, value: String) {
    MetricRow(stringResource(labelRes), value)
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LumenPage(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable Column.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
    return stringResource(
        R.string.minutes_compact,
        (safeSeconds / 60L).toInt(),
        (safeSeconds % 60L).toInt(),
    )
}

@Composable
private fun minutesLabel(minutes: Int): String = stringResource(R.string.minutes_short, minutes)

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
