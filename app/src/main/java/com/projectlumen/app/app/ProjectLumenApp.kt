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
import kotlinx.coroutines.withContext

private const val PROJECT_LUMEN_REPO_URL = "https://github.com/Chloemlla/Project-Lumen"
private const val PROJECT_LUMEN_RELEASES_URL = "https://github.com/Chloemlla/Project-Lumen/releases/latest"
private const val PROJECT_LUMEN_RELEASE_API = "https://api.github.com/repos/Chloemlla/Project-Lumen/releases/latest"
private const val PROJECT_LUMEN_RELEASES_BASE_URL = "https://github.com/Chloemlla/Project-Lumen/releases"
private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
private const val MINI_CHROME_VERSION = 107
private const val GKD_DOC_HOST = "gkd.li"
private const val GKD_DOC_CONFIG_URL =
    "https://registry.npmmirror.com/@gkd-kit/docs/latest/files/_config.json"
private const val GKD_DEBUG_JS_TEXT = """
<script src="https://registry.npmmirror.com/eruda/latest/files"></script>
<script>eruda.init();</script>
"""
private const val COUNTDOWN_STYLE_CIRCLE = "circle"
private const val COUNTDOWN_STYLE_BAR = "bar"
private const val COUNTDOWN_STYLE_NUMBER = "number"
private const val DEFAULT_TEMPLATE_TITLE = "Time to rest"
private const val DEFAULT_TEMPLATE_SUBTITLE = "Look away from the screen and relax your eyes."
private val crashDetailsTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
private val updateDialogTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val chromeVersion by lazy {
    WebView.getCurrentWebViewPackage()?.versionName?.run {
        splitToSequence('.').first().toIntOrNull()
    } ?: 0
}

private sealed interface UpdateDialogState {
    data object Hidden : UpdateDialogState
    data object Checking : UpdateDialogState
    data class UpdateAvailable(val candidate: UpdateCandidate) : UpdateDialogState
    data object NoUpdate : UpdateDialogState
    data class Downloading(val candidate: UpdateCandidate, val asset: ReleaseAsset) : UpdateDialogState
    data class InstallAuthorization(val candidate: UpdateCandidate, val file: File) : UpdateDialogState
    data class Error(val message: String) : UpdateDialogState
}

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
    WEB("web", R.string.about_external_link_prompt_title, Icons.AutoMirrored.Outlined.OpenInNew, false),
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
private fun lumenCardElevation() = CardDefaults.cardElevation(
    defaultElevation = 1.dp,
    pressedElevation = 0.dp,
    focusedElevation = 1.dp,
    hoveredElevation = 2.dp,
    draggedElevation = 3.dp,
    disabledElevation = 0.dp,
)

@Composable
private fun lumenCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
fun ProjectLumenApp(
    viewModel: ProjectLumenViewModel,
    crashReport: CrashReport?,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuredThemeMode = runCatching { AppThemeMode.valueOf(uiState.settings.themeMode) }
        .getOrDefault(AppThemeMode.SYSTEM)
    val themeMode = if (
        uiState.settings.useAutoDarkWindow &&
        isAutoDarkActive(
            nowMillis = uiState.nowMillis,
            startMinute = uiState.settings.autoDarkStartMinute,
            endMinute = uiState.settings.autoDarkEndMinute,
        )
    ) {
        AppThemeMode.DARK
    } else {
        configuredThemeMode
    }
    val baseContext = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val updateChecker = remember(baseContext) { UpdateChecker(baseContext, PROJECT_LUMEN_RELEASE_API) }
    val updateInstaller = remember { UpdateInstaller(baseContext) }
    var pendingWebUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var updateDialogState by remember { mutableStateOf<UpdateDialogState>(UpdateDialogState.Hidden) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgressBytes by remember { mutableLongStateOf(0L) }
    var downloadProgressTotalBytes by remember { mutableStateOf<Long?>(null) }
    var autoCheckStarted by rememberSaveable { mutableStateOf(false) }
    val checkingUpdate = updateDialogState is UpdateDialogState.Checking

    fun triggerUpdateCheck(manual: Boolean) {
        coroutineScope.launch {
            if (manual) {
                updateDialogState = UpdateDialogState.Checking
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { updateChecker.checkForUpdate() }
            }
            result.onSuccess { candidate ->
                updateDialogState = when {
                    candidate != null -> UpdateDialogState.UpdateAvailable(candidate)
                    manual -> UpdateDialogState.NoUpdate
                    else -> UpdateDialogState.Hidden
                }
                if (!manual) autoCheckStarted = true
            }.onFailure { throwable ->
                if (manual) {
                    updateDialogState = UpdateDialogState.Error(throwable.message ?: "Update check failed.")
                }
                if (!manual) autoCheckStarted = true
            }
        }
    }

    fun startInstallIfAllowed(candidate: UpdateCandidate, file: File) {
        if (updateInstaller.canInstallPackages()) {
            runCatching { updateInstaller.installApk(file) }
                .onSuccess { updateDialogState = UpdateDialogState.Hidden }
                .onFailure { updateDialogState = UpdateDialogState.Error(it.message ?: "Unable to open installer.") }
            return
        }
        updateDialogState = UpdateDialogState.InstallAuthorization(candidate, file)
    }

    fun triggerUpdateDownload(candidate: UpdateCandidate, asset: ReleaseAsset) {
        coroutineScope.launch {
            downloadingUpdate = true
            updateDialogState = UpdateDialogState.Downloading(candidate, asset)
            downloadProgressBytes = 0L
            downloadProgressTotalBytes = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    updateInstaller.downloadApk(asset) { downloadedBytes, totalBytes ->
                        downloadProgressBytes = downloadedBytes
                        downloadProgressTotalBytes = totalBytes
                    }
                }
            }
            downloadingUpdate = false
            downloadProgressBytes = 0L
            downloadProgressTotalBytes = null
            result.onSuccess { file ->
                startInstallIfAllowed(candidate, file)
            }.onFailure { throwable ->
                updateDialogState = UpdateDialogState.Error(throwable.message ?: "Update download failed.")
            }
        }
    }

    LaunchedEffect(uiState.settings.languageCode) {
        LocaleController.apply(uiState.settings.languageCode)
    }
    LaunchedEffect(viewModel) {
        viewModel.webPageRequests.collect { url ->
            pendingWebUrl = url
        }
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
            pendingWebUrl?.let { url ->
                WebViewScreen(
                    url = url,
                    onDismiss = { pendingWebUrl = null },
                )
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
                            checkingUpdate = updateDialogState is UpdateDialogState.Checking,
                            onManualUpdateCheck = { triggerUpdateCheck(manual = true) },
                            openTemplates = { navController.navigate(Destination.TEMPLATES.route) },
                            openAbout = { navController.navigate(Destination.ABOUT.route) },
                        )
                    }
                    composable(Destination.TEMPLATES.route) { TemplatesScreen(uiState, viewModel) }
                    composable(Destination.ABOUT.route) { AboutScreen(viewModel) }
                }
            }
                    if (updateDialogState !is UpdateDialogState.Hidden) {
                UpdateDialog(
                    viewModel = viewModel,
                    state = updateDialogState,
                    downloadingUpdate = downloadingUpdate,
                    downloadProgressBytes = downloadProgressBytes,
                    downloadProgressTotalBytes = downloadProgressTotalBytes,
                    onDismiss = { updateDialogState = UpdateDialogState.Hidden },
                    onDownloadUpdate = { candidate, asset -> triggerUpdateDownload(candidate, asset) },
                    onInstallDownloadedApk = { candidate, file -> startInstallIfAllowed(candidate, file) },
                    onError = { message -> updateDialogState = UpdateDialogState.Error(message) },
                    updateInstaller = updateInstaller,
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
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
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_report_share_subject))
                            putExtra(Intent.EXTRA_TEXT, report.toClipboardText())
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(
                            Intent.createChooser(intent, context.getString(R.string.crash_report_share))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                ) {
                    Text(stringResource(R.string.crash_report_share))
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
    val canStartReminder = uiState.settings.reminderEnabled && runtime.activeEngine == ActiveEngine.IDLE.name
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
        GoalProgressCard(uiState)
        ActionCard {
            SectionHeader(Icons.Outlined.Schedule, R.string.quick_actions)
            when {
                !uiState.settings.reminderEnabled -> EmptyStateMessage(R.string.reminder_disabled_hint)
                !reminderActive && runtime.activeEngine != ActiveEngine.IDLE.name -> EmptyStateMessage(R.string.other_timer_running_hint)
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
                }
                canResumeReminder -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { runReminderAction(viewModel::resumeReminder) },
                    ) {
                        ButtonLabel(Icons.Outlined.Refresh, R.string.resume_now)
                    }
                }
                else -> EmptyStateMessage(R.string.reminder_idle_hint)
            }
        }
    }
}

@Composable
private fun BreakScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val template = activeTemplate(uiState)
    val reminderActive = runtime.activeEngine == ActiveEngine.REMINDER.name &&
        runtime.reminderPhase != ReminderPhase.IDLE.name
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
            label = stringResource(if (isResting) R.string.remaining else R.string.current_state),
            seconds = if (isResting) remainingSeconds(runtime.breakEndAt, uiState.nowMillis) else 0,
            progress = if (isResting) progress(runtime.breakStartedAt, runtime.breakEndAt, uiState.nowMillis) else 0f,
            fallbackText = statusLabel(runtime),
            countdownStyle = templateCountdownStyle(template),
        )
        ActionCard {
            SectionHeader(Icons.Outlined.Spa, R.string.quick_actions)
            when {
                !uiState.settings.reminderEnabled -> EmptyStateMessage(R.string.reminder_disabled_hint)
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
                }
                canStartBreak -> {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::startBreak,
                    ) {
                        ButtonLabel(Icons.Outlined.Spa, R.string.start_break)
                    }
                }
                canSkip -> {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = viewModel::skipBreak,
                    ) {
                        ButtonLabel(Icons.Outlined.SkipNext, R.string.skip_break)
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
                else -> EmptyStateMessage(R.string.break_action_unavailable)
            }
        }
    }
}

@Composable
private fun PomodoroScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val runtime = uiState.runtime
    val running = runtime.activeEngine == ActiveEngine.POMODORO.name && runtime.pomodoroPhase != PomodoroPhase.IDLE.name
    val canStartPomodoro = uiState.settings.pomodoroEnabled && runtime.activeEngine == ActiveEngine.IDLE.name
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
        ActionCard {
            SectionHeader(Icons.Outlined.LocalCafe, R.string.quick_actions)
            when {
                !uiState.settings.pomodoroEnabled -> EmptyStateMessage(R.string.pomodoro_disabled_hint)
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
                else -> EmptyStateMessage(R.string.other_timer_running_hint)
            }
        }
    }
}

@Composable
private fun StatisticsScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    val statsEnabled = uiState.settings.statsEnabled
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
        TodayStatsCard(eye)
        LumenFlowRow {
            FilterChip(selected = statsWindow == 7, onClick = { statsWindow = 7 }, label = { Text(stringResource(R.string.stats_range_7_days)) })
            FilterChip(selected = statsWindow == 30, onClick = { statsWindow = 30 }, label = { Text(stringResource(R.string.stats_range_30_days)) })
            FilterChip(selected = statsWindow == 31, onClick = { statsWindow = 31 }, label = { Text(stringResource(R.string.stats_range_month)) })
        }
        AdvancedStatsCard(windowEyeStats, windowPomodoroStats)
        HabitSuggestionCard(uiState)
        TrendCard(uiState)
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

@Composable
private fun SettingsScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    checkingUpdate: Boolean,
    onManualUpdateCheck: () -> Unit,
    openTemplates: () -> Unit,
    openAbout: () -> Unit,
) {
    val settings = uiState.settings
    val template = activeTemplate(uiState)
    val context = LocalContext.current
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    val runWithCameraPermission = rememberCameraPermissionGate()
    val notificationPermissionNeeded = needsNotificationPermission(context)
    val cameraPermissionNeeded = needsCameraPermission(context)
    val exactAlarmSettingsNeeded = needsExactAlarmSettings(context)
    val fullScreenIntentSettingsNeeded = needsFullScreenIntentSettings(context)
    val overlayPermissionNeeded = needsOverlayPermission(context)
    val writeSettingsPermissionNeeded = needsWriteSettingsPermission(context)
    val backupImportPreview by viewModel.backupImportPreview.collectAsStateWithLifecycle()
    var pendingBackupImportUri by remember { mutableStateOf<Uri?>(null) }
    var showProximityCalibrationDialog by rememberSaveable { mutableStateOf(false) }
    val proximityCalibrated = settings.proximityBaselineEyeDistancePx > 0f ||
        settings.proximityBaselineFaceWidthPercent > 0
    val proximityCaptureSeconds = settings.proximityCaptureSeconds.coerceIn(1, 2)
    fun persistUri(uri: Uri): String {
        persistReadableUri(context, uri)
        return uri.toString()
    }
    val restSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val path = persistUri(it)
            viewModel.updateSettings { current -> current.copy(restSoundPath = path) }
        }
    }
    val restStartSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val path = persistUri(it)
            viewModel.updateSettings { current -> current.copy(restStartSoundPath = path) }
        }
    }
    val workStartSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val path = persistUri(it)
            viewModel.updateSettings { current -> current.copy(pomodoroWorkStartSoundPath = path) }
        }
    }
    val workEndSoundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val path = persistUri(it)
            viewModel.updateSettings { current -> current.copy(pomodoroWorkEndSoundPath = path) }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingBackupImportUri = uri
            viewModel.previewBackupImport(uri)
        }
    }
    backupImportPreview?.let { summary ->
        val targetUri = pendingBackupImportUri
        BackupImportDialog(
            summary = summary,
            onDismiss = {
                pendingBackupImportUri = null
                viewModel.clearBackupImportPreview()
            },
            onConfirm = {
                if (targetUri != null) viewModel.importBackup(targetUri)
            },
        )
    }
    if (showProximityCalibrationDialog) {
        ProximityCalibrationDialog(
            onDismiss = { showProximityCalibrationDialog = false },
            onConfirm = {
                runWithCameraPermission {
                    viewModel.calibrateProximity()
                    showProximityCalibrationDialog = false
                }
            },
        )
    }
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
            SwitchRow(R.string.enable_statistics, Icons.Outlined.BarChart, settings.statsEnabled) {
                viewModel.updateSettings { current -> current.copy(statsEnabled = it) }
            }
            LumenFlowRow {
                OutlinedButton(onClick = openTemplates) { ButtonLabel(Icons.Outlined.Style, R.string.nav_templates) }
                OutlinedButton(onClick = openAbout) { ButtonLabel(Icons.Outlined.Info, R.string.nav_about) }
            }
            SwitchRow(R.string.auto_dark_window, Icons.Outlined.Style, settings.useAutoDarkWindow) {
                viewModel.updateSettings { current -> current.copy(useAutoDarkWindow = it) }
            }
            AnimatedVisibility(
                visible = settings.useAutoDarkWindow,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberSlider(
                        R.string.auto_dark_start,
                        Icons.Outlined.Schedule,
                        settings.autoDarkStartMinute,
                        0f..1435f,
                        0,
                        timeOfDayLabel(settings.autoDarkStartMinute),
                    ) {
                        viewModel.updateSettings { current -> current.copy(autoDarkStartMinute = snapTimeMinute(it)) }
                    }
                    NumberSlider(
                        R.string.auto_dark_end,
                        Icons.Outlined.Schedule,
                        settings.autoDarkEndMinute,
                        0f..1435f,
                        0,
                        timeOfDayLabel(settings.autoDarkEndMinute),
                    ) {
                        viewModel.updateSettings { current -> current.copy(autoDarkEndMinute = snapTimeMinute(it)) }
                    }
                }
            }
            SwitchRow(R.string.auto_update_check, Icons.Outlined.Sync, settings.autoUpdateCheckEnabled) {
                viewModel.setAutoUpdateCheckEnabled(it)
            }
        }
        SettingsSection(R.string.section_goals, Icons.Outlined.CheckCircle) {
            NumberSlider(R.string.daily_rest_goal, Icons.Outlined.Spa, uiState.dailyGoal.restBreakGoal, 1f..20f, 18, "${uiState.dailyGoal.restBreakGoal}") {
                viewModel.updateDailyGoal { current -> current.copy(restBreakGoal = it) }
            }
            NumberSlider(R.string.max_continuous_work_goal, Icons.Outlined.Schedule, uiState.dailyGoal.maxContinuousWorkMinutes, 15f..120f, 20, stringResource(R.string.minutes_value, uiState.dailyGoal.maxContinuousWorkMinutes)) {
                viewModel.updateDailyGoal { current -> current.copy(maxContinuousWorkMinutes = it) }
            }
            NumberSlider(R.string.daily_pomodoro_goal, Icons.Outlined.LocalCafe, uiState.dailyGoal.pomodoroGoal, 1f..16f, 15, "${uiState.dailyGoal.pomodoroGoal}") {
                viewModel.updateDailyGoal { current -> current.copy(pomodoroGoal = it) }
            }
            NumberSlider(R.string.weekly_active_days_goal, Icons.Outlined.CheckCircle, uiState.dailyGoal.weeklyActiveDaysGoal, 1f..7f, 5, "${uiState.dailyGoal.weeklyActiveDaysGoal}") {
                viewModel.updateDailyGoal { current -> current.copy(weeklyActiveDaysGoal = it) }
            }
        }
        SettingsSection(R.string.section_data, Icons.Outlined.FileDownload) {
            LumenFlowRow {
                Button(onClick = viewModel::shareBackup) {
                    ButtonLabel(Icons.Outlined.FileDownload, R.string.backup_export)
                }
                OutlinedButton(onClick = { backupImportLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }) {
                    ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.backup_import)
                }
            }
        }
        SettingsSection(R.string.section_pro, Icons.Outlined.CheckCircle) {
            MetricRow(R.string.plan_tier, settings.planTier.lowercase())
            MetricRow(R.string.pro_templates, uiState.templates.count { it.isPremium }.toString())
        }
        SettingsSection(R.string.about_update_status, Icons.Outlined.Sync) {
            if (checkingUpdate) {
                StatusLine(Icons.Outlined.Sync, stringResource(R.string.about_update_checking))
            } else {
                OutlinedButton(onClick = onManualUpdateCheck) {
                    ButtonLabel(Icons.Outlined.Sync, R.string.about_check_updates)
                }
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
            AnimatedVisibility(
                visible = settings.notificationEnabled && !notificationPermissionNeeded && fullScreenIntentSettingsNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.full_screen_intent_permission_needed,
                    messageRes = R.string.full_screen_intent_permission_needed_message,
                    actionLabelRes = R.string.open_system_settings,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onClick = { openFullScreenIntentSettings(context) },
                )
            }
            if (!notificationPermissionNeeded) {
                OutlinedButton(onClick = {
                    openAppNotificationSettings(context)
                }) {
                    ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.notification_system_settings)
                }
            }
        }
        SettingsSection(R.string.section_keep_alive, Icons.Outlined.Schedule) {
            SwitchRow(R.string.enable_keep_alive, Icons.Outlined.Schedule, settings.keepAliveEnabled) {
                viewModel.setKeepAliveEnabled(it)
            }
        }
        SettingsSection(R.string.section_proximity, Icons.Outlined.PhotoCamera) {
            SwitchRow(R.string.enable_proximity_monitoring, Icons.Outlined.PhotoCamera, settings.proximityMonitoringEnabled) { enabled ->
                if (enabled) {
                    runWithCameraPermission {
                        viewModel.setProximityMonitoringEnabled(true)
                        if (!proximityCalibrated) showProximityCalibrationDialog = true
                    }
                } else {
                    viewModel.setProximityMonitoringEnabled(false)
                }
            }
            AnimatedVisibility(
                visible = settings.proximityMonitoringEnabled && cameraPermissionNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.camera_permission_needed,
                    messageRes = R.string.camera_permission_needed_message,
                    actionLabelRes = R.string.allow_camera,
                    icon = Icons.Outlined.PhotoCamera,
                    onClick = {
                        runWithCameraPermission {
                            viewModel.setProximityMonitoringEnabled(true)
                            if (!proximityCalibrated) showProximityCalibrationDialog = true
                        }
                    },
                )
            }
            Text(
                if (settings.proximityBaselineEyeDistancePx > 0f) {
                    stringResource(R.string.proximity_calibrated, settings.proximityBaselineEyeDistancePx)
                } else if (settings.proximityBaselineFaceWidthPercent > 0) {
                    stringResource(R.string.proximity_calibrated_face, settings.proximityBaselineFaceWidthPercent)
                } else {
                    stringResource(R.string.proximity_not_calibrated)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.proximity_ratio, uiState.runtime.proximityLastRatioPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.eye_open_probability, uiState.runtime.blinkLastEyeOpenProbabilityPercent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showProximityCalibrationDialog = true },
            ) {
                ButtonLabel(Icons.Outlined.Refresh, R.string.calibrate_proximity)
            }
            NumberSlider(
                R.string.proximity_check_interval,
                Icons.Outlined.Schedule,
                settings.proximityCheckIntervalMinutes,
                1f..60f,
                58,
                stringResource(R.string.minutes_value, settings.proximityCheckIntervalMinutes),
            ) {
                viewModel.updateSettings { current -> current.copy(proximityCheckIntervalMinutes = it) }
            }
            NumberSlider(
                R.string.proximity_distance_multiplier,
                Icons.Outlined.PhotoCamera,
                settings.proximityDistanceMultiplierPercent,
                105f..200f,
                18,
                stringResource(R.string.percent_value, settings.proximityDistanceMultiplierPercent),
            ) {
                viewModel.updateSettings { current -> current.copy(proximityDistanceMultiplierPercent = it) }
            }
            NumberSlider(
                R.string.proximity_face_threshold,
                Icons.Outlined.PhotoCamera,
                settings.proximityFaceThresholdPercent,
                20f..70f,
                9,
                stringResource(R.string.percent_value, settings.proximityFaceThresholdPercent),
            ) {
                viewModel.updateSettings { current -> current.copy(proximityFaceThresholdPercent = it) }
            }
            NumberSlider(
                R.string.proximity_capture_seconds,
                Icons.Outlined.Schedule,
                proximityCaptureSeconds,
                1f..2f,
                0,
                stringResource(R.string.seconds_value, proximityCaptureSeconds),
            ) {
                viewModel.updateSettings { current -> current.copy(proximityCaptureSeconds = it) }
            }
            NumberSlider(
                R.string.proximity_alert_cooldown,
                Icons.Outlined.NotificationsActive,
                settings.proximityAlertCooldownSeconds,
                30f..600f,
                18,
                stringResource(R.string.seconds_value, settings.proximityAlertCooldownSeconds),
            ) {
                viewModel.updateSettings { current -> current.copy(proximityAlertCooldownSeconds = it) }
            }
        }
        SettingsSection(R.string.section_eye_protection, Icons.Outlined.PhotoCamera) {
            SwitchRow(R.string.enable_blink_monitoring, Icons.Outlined.PhotoCamera, settings.blinkMonitoringEnabled) { enabled ->
                if (enabled) {
                    runWithCameraPermission { viewModel.setBlinkMonitoringEnabled(true) }
                } else {
                    viewModel.setBlinkMonitoringEnabled(false)
                }
            }
            AnimatedVisibility(
                visible = settings.blinkMonitoringEnabled && cameraPermissionNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.camera_permission_needed,
                    messageRes = R.string.camera_permission_needed_message,
                    actionLabelRes = R.string.allow_camera,
                    icon = Icons.Outlined.PhotoCamera,
                    onClick = { runWithCameraPermission { viewModel.setBlinkMonitoringEnabled(true) } },
                )
            }
            NumberSlider(R.string.blink_no_blink_threshold, Icons.Outlined.Schedule, settings.blinkNoBlinkThresholdSeconds, 5f..60f, 10, stringResource(R.string.seconds_value, settings.blinkNoBlinkThresholdSeconds)) {
                viewModel.updateSettings { current -> current.copy(blinkNoBlinkThresholdSeconds = it) }
            }
            NumberSlider(R.string.blink_alert_cooldown, Icons.Outlined.NotificationsActive, settings.blinkAlertCooldownSeconds, 30f..600f, 18, stringResource(R.string.seconds_value, settings.blinkAlertCooldownSeconds)) {
                viewModel.updateSettings { current -> current.copy(blinkAlertCooldownSeconds = it) }
            }
            SwitchRow(R.string.enable_ambient_light_monitoring, Icons.Outlined.NotificationsActive, settings.ambientLightMonitoringEnabled) {
                viewModel.setAmbientLightMonitoringEnabled(it)
            }
            Text(
                stringResource(R.string.ambient_current_lux, uiState.runtime.ambientLastLux),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberSlider(R.string.ambient_low_lux_threshold, Icons.Outlined.NotificationsActive, settings.ambientLightLowLuxThreshold, 1f..100f, 99, "${settings.ambientLightLowLuxThreshold} lux") {
                viewModel.updateSettings { current -> current.copy(ambientLightLowLuxThreshold = it) }
            }
            SwitchRow(R.string.enable_auto_brightness, Icons.Outlined.Style, settings.autoBrightnessEnabled) {
                viewModel.setAutoBrightnessEnabled(it)
            }
            AnimatedVisibility(
                visible = settings.autoBrightnessEnabled && writeSettingsPermissionNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.brightness_permission_needed,
                    messageRes = R.string.brightness_permission_needed_message,
                    actionLabelRes = R.string.open_system_settings,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onClick = { openWriteSettings(context) },
                )
            }
            NumberSlider(R.string.auto_brightness_min, Icons.Outlined.Style, settings.autoBrightnessMinPercent, 5f..100f, 19, stringResource(R.string.percent_value, settings.autoBrightnessMinPercent)) {
                viewModel.updateSettings { current -> current.copy(autoBrightnessMinPercent = it) }
            }
            NumberSlider(R.string.auto_brightness_max, Icons.Outlined.Style, settings.autoBrightnessMaxPercent, 5f..100f, 19, stringResource(R.string.percent_value, settings.autoBrightnessMaxPercent)) {
                viewModel.updateSettings { current -> current.copy(autoBrightnessMaxPercent = it.coerceAtLeast(settings.autoBrightnessMinPercent)) }
            }
            SwitchRow(R.string.enable_global_overlay, Icons.Outlined.NotificationsActive, settings.globalOverlayEnabled) {
                viewModel.updateSettings { current -> current.copy(globalOverlayEnabled = it) }
            }
            AnimatedVisibility(
                visible = settings.globalOverlayEnabled && overlayPermissionNeeded,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                NotificationRequirementCard(
                    titleRes = R.string.overlay_permission_needed,
                    messageRes = R.string.overlay_permission_needed_message,
                    actionLabelRes = R.string.open_system_settings,
                    icon = Icons.AutoMirrored.Outlined.OpenInNew,
                    onClick = { openOverlaySettings(context) },
                )
            }
            NumberSlider(R.string.overlay_rest_duration, Icons.Outlined.Spa, settings.overlayRestDurationSeconds, 5f..120f, 23, stringResource(R.string.seconds_value, settings.overlayRestDurationSeconds)) {
                viewModel.updateSettings { current -> current.copy(overlayRestDurationSeconds = it) }
            }
            NumberSlider(R.string.overlay_strict_distance, Icons.Outlined.PhotoCamera, settings.overlayStrictDistancePercent, 120f..250f, 26, stringResource(R.string.percent_value, settings.overlayStrictDistancePercent)) {
                viewModel.updateSettings { current -> current.copy(overlayStrictDistancePercent = it) }
            }
        }
        SettingsSection(R.string.section_sound, Icons.AutoMirrored.Outlined.VolumeUp) {
            SwitchRow(R.string.enable_sound, Icons.AutoMirrored.Outlined.VolumeUp, settings.soundEnabled) {
                viewModel.updateSettings { current -> current.copy(soundEnabled = it) }
            }
            SwitchRow(R.string.enable_vibration, Icons.Outlined.NotificationsActive, settings.vibrationEnabled) {
                viewModel.updateSettings { current -> current.copy(vibrationEnabled = it) }
            }
            SwitchRow(R.string.pre_alert_sound, Icons.Outlined.Schedule, settings.preAlertSoundEnabled) {
                viewModel.updateSettings { current -> current.copy(preAlertSoundEnabled = it) }
            }
            SwitchRow(R.string.rest_start_sound, Icons.Outlined.Spa, settings.restStartSoundEnabled) {
                viewModel.updateSettings { current -> current.copy(restStartSoundEnabled = it) }
            }
            SwitchRow(R.string.pomodoro_work_start_sound, Icons.Outlined.PlayArrow, settings.pomodoroWorkStartSoundEnabled) {
                viewModel.updateSettings { current -> current.copy(pomodoroWorkStartSoundEnabled = it) }
            }
            SwitchRow(R.string.pomodoro_work_end_sound, Icons.Outlined.Stop, settings.pomodoroWorkEndSoundEnabled) {
                viewModel.updateSettings { current -> current.copy(pomodoroWorkEndSoundEnabled = it) }
            }
            NumberSlider(R.string.pre_alert_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.preAlertVolumePercent, 0f..100f, 20, stringResource(R.string.percent_value, settings.preAlertVolumePercent)) {
                viewModel.updateSettings { current -> current.copy(preAlertVolumePercent = it) }
            }
            NumberSlider(R.string.rest_start_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.restStartVolumePercent, 0f..100f, 20, stringResource(R.string.percent_value, settings.restStartVolumePercent)) {
                viewModel.updateSettings { current -> current.copy(restStartVolumePercent = it) }
            }
            NumberSlider(R.string.rest_end_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.restEndVolumePercent, 0f..100f, 20, stringResource(R.string.percent_value, settings.restEndVolumePercent)) {
                viewModel.updateSettings { current -> current.copy(restEndVolumePercent = it) }
            }
            NumberSlider(R.string.pomodoro_start_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.pomodoroWorkStartVolumePercent, 0f..100f, 20, stringResource(R.string.percent_value, settings.pomodoroWorkStartVolumePercent)) {
                viewModel.updateSettings { current -> current.copy(pomodoroWorkStartVolumePercent = it) }
            }
            NumberSlider(R.string.pomodoro_end_volume, Icons.AutoMirrored.Outlined.VolumeUp, settings.pomodoroWorkEndVolumePercent, 0f..100f, 20, stringResource(R.string.percent_value, settings.pomodoroWorkEndVolumePercent)) {
                viewModel.updateSettings { current -> current.copy(pomodoroWorkEndVolumePercent = it) }
            }
            FileSettingRow(
                labelRes = R.string.rest_sound_file,
                path = settings.restSoundPath,
                onChoose = { restSoundLauncher.launch(arrayOf("audio/*")) },
                onClear = { viewModel.updateSettings { current -> current.copy(restSoundPath = "") } },
            )
            FileSettingRow(
                labelRes = R.string.rest_start_sound_file,
                path = settings.restStartSoundPath,
                onChoose = { restStartSoundLauncher.launch(arrayOf("audio/*")) },
                onClear = { viewModel.updateSettings { current -> current.copy(restStartSoundPath = "") } },
            )
            FileSettingRow(
                labelRes = R.string.pomodoro_work_start_sound_file,
                path = settings.pomodoroWorkStartSoundPath,
                onChoose = { workStartSoundLauncher.launch(arrayOf("audio/*")) },
                onClear = { viewModel.updateSettings { current -> current.copy(pomodoroWorkStartSoundPath = "") } },
            )
            FileSettingRow(
                labelRes = R.string.pomodoro_work_end_sound_file,
                path = settings.pomodoroWorkEndSoundPath,
                onChoose = { workEndSoundLauncher.launch(arrayOf("audio/*")) },
                onClear = { viewModel.updateSettings { current -> current.copy(pomodoroWorkEndSoundPath = "") } },
            )
        }
        SettingsSection(R.string.section_appearance, Icons.Outlined.Style) {
            val proEnabled = planTier(settings) >= PlanTier.PRO
            LumenFlowRow {
                uiState.templates.filter { !it.isPremium || proEnabled }.forEach { template ->
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
        SettingsSection(R.string.section_quiet_hours, Icons.Outlined.Schedule) {
            SwitchRow(R.string.quiet_hours, Icons.Outlined.Schedule, settings.quietHoursEnabled) {
                viewModel.updateSettings { current -> current.copy(quietHoursEnabled = it) }
            }
            AnimatedVisibility(
                visible = settings.quietHoursEnabled,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberSlider(R.string.quiet_start, Icons.Outlined.Schedule, settings.quietStartMinute, 0f..1435f, 0, timeOfDayLabel(settings.quietStartMinute)) {
                        viewModel.updateSettings { current -> current.copy(quietStartMinute = snapTimeMinute(it)) }
                    }
                    NumberSlider(R.string.quiet_end, Icons.Outlined.Schedule, settings.quietEndMinute, 0f..1435f, 0, timeOfDayLabel(settings.quietEndMinute)) {
                        viewModel.updateSettings { current -> current.copy(quietEndMinute = snapTimeMinute(it)) }
                    }
                    Text(stringResource(R.string.quiet_mode), style = MaterialTheme.typography.titleSmall)
                    LumenFlowRow {
                        QuietModeChip(R.string.quiet_mode_pause_timer, QuietMode.PAUSE_TIMER, settings, viewModel)
                        QuietModeChip(R.string.quiet_mode_silent_notifications, QuietMode.SILENT_NOTIFICATIONS, settings, viewModel)
                        QuietModeChip(R.string.quiet_mode_record_only, QuietMode.RECORD_ONLY, settings, viewModel)
                    }
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.weekly_trend)
            if (recent.isEmpty()) {
                EmptyStateMessage(R.string.statistics_no_trend_data)
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
private fun HabitSuggestionCard(uiState: ProjectLumenUiState) {
    val recentEyeStats = uiState.eyeStats.take(14)
    val completedBreaks = recentEyeStats.sumOf { it.completedBreakCount }
    val skips = recentEyeStats.sumOf { it.skipCount }
    val totalBreakDecisions = completedBreaks + skips
    val skipRate = if (totalBreakDecisions > 0) (skips * 100) / totalBreakDecisions else 0
    val completionRate = if (totalBreakDecisions > 0) (completedBreaks * 100) / totalBreakDecisions else 100
    val maxContinuousMinutes = (recentEyeStats.maxOfOrNull { it.maxContinuousWorkSeconds } ?: 0L) / 60L
    val lowLightWarnings = recentEyeStats.sumOf { it.lowLightWarningCount }
    val eyeDryWarnings = recentEyeStats.sumOf { it.eyeDryWarningCount }
    val suggestionIds = buildList {
        if (skipRate > 50 && completionRate < 40) add(R.string.habit_suggestion_shorter_break)
        if (maxContinuousMinutes > uiState.dailyGoal.maxContinuousWorkMinutes.toLong()) add(R.string.habit_suggestion_strict_overlay)
        if (lowLightWarnings >= 3) add(R.string.habit_suggestion_room_light)
        if (eyeDryWarnings >= 3) add(R.string.habit_suggestion_blink_pause)
        if (isEmpty()) add(R.string.habit_suggestion_keep_rhythm)
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.WarningAmber, R.string.habit_suggestions)
            suggestionIds.distinct().forEach { suggestionRes ->
                StatusLine(Icons.Outlined.CheckCircle, stringResource(suggestionRes))
            }
        }
    }
}

@Composable
private fun AdvancedStatsCard(
    eyeStats: List<DailyEyeStatsEntity>,
    pomodoroStats: List<com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity>,
) {
    val workSeconds = eyeStats.sumOf { it.workingSeconds }
    val restSeconds = eyeStats.sumOf { it.restSeconds }
    val completedBreaks = eyeStats.sumOf { it.completedBreakCount }
    val skips = eyeStats.sumOf { it.skipCount }
    val totalBreakDecisions = (completedBreaks + skips).coerceAtLeast(1)
    val averageContinuousMinutes = eyeStats
        .filter { it.maxContinuousWorkSeconds > 0L }
        .map { it.maxContinuousWorkSeconds / 60L }
        .average()
        .takeIf { !it.isNaN() }
        ?: 0.0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.advanced_statistics)
            MetricRow(R.string.working_time, stringResource(R.string.hours_short, workSeconds / 3600.0))
            MetricRow(R.string.rest_time, minutesLabel((restSeconds / 60L).toInt()))
            MetricRow(R.string.rest_completion_rate, stringResource(R.string.percent_value, ((completedBreaks * 100) / totalBreakDecisions).coerceIn(0, 100)))
            MetricRow(R.string.skip_rate, stringResource(R.string.percent_value, ((skips * 100) / totalBreakDecisions).coerceIn(0, 100)))
            MetricRow(R.string.average_continuous_work, stringResource(R.string.minutes_value, averageContinuousMinutes.roundToInt()))
            MetricRow(R.string.completed_tomatoes, pomodoroStats.sumOf { it.completedTomatoCount }.toString())
        }
    }
}

@Composable
private fun TemplatesScreen(uiState: ProjectLumenUiState, viewModel: ProjectLumenViewModel) {
    val activeTemplate = activeTemplate(uiState)
    val context = LocalContext.current
    val proEnabled = planTier(uiState.settings) >= PlanTier.PRO
    var imageTargetTemplateId by remember { mutableStateOf<Long?>(null) }
    val templateImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetTemplate = uiState.templates.firstOrNull { it.id == imageTargetTemplateId }
        imageTargetTemplateId = null
        if (uri != null && targetTemplate != null) {
            persistReadableUri(context, uri)
            viewModel.updateTemplateImage(targetTemplate, uri.toString())
        }
    }
    LumenPage {
        SectionHeader(Icons.Outlined.Style, R.string.template_preview)
        TemplatePreviewCard(activeTemplate)
        if (activeTemplate != null) {
            SystemBackgroundPicker(activeTemplate, viewModel)
        }
        uiState.templates.forEach { template ->
            val selected = template.id == uiState.settings.activeTipTemplateId
            val locked = template.isPremium && !proEnabled
            val borderColor by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                animationSpec = tween(180),
                label = "templateBorderColor",
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, LumenCardShape)
                    .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
                shape = LumenCardShape,
                colors = lumenCardColors(),
                elevation = lumenCardElevation(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TemplateColorSwatch(template)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                if (template.isPremium) {
                                    "${templateDisplayName(template)} · ${stringResource(R.string.premium_template)}"
                                } else {
                                    templateDisplayName(template)
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(templateSubtitle(template), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    when {
                        locked -> StatusPill(Icons.Outlined.Lock, R.string.pro_required)
                        selected -> StatusPill(Icons.Outlined.CheckCircle, R.string.active_template)
                        else -> FilterChip(
                            selected = false,
                            onClick = { viewModel.selectTemplate(template.id) },
                            label = { Text(stringResource(R.string.use_template)) },
                        )
                    }
                    if (!locked) {
                        LumenFlowRow {
                            OutlinedButton(
                                onClick = {
                                    imageTargetTemplateId = template.id
                                    templateImageLauncher.launch(arrayOf("image/*"))
                                },
                            ) {
                                ButtonLabel(Icons.Outlined.FileDownload, R.string.choose_template_image)
                            }
                            if (template.imagePath.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { viewModel.updateTemplateImage(template, "") },
                                ) {
                                    Text(stringResource(R.string.clear_custom_file))
                                }
                            }
                        }
                        if (selected) {
                            TemplateEditor(template, viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateEditor(template: TipTemplateEntity, viewModel: ProjectLumenViewModel) {
    var titleText by remember(template.id, template.updatedAt) { mutableStateOf(template.titleText) }
    var subtitleText by remember(template.id, template.updatedAt) { mutableStateOf(template.subtitleText) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.template_editor), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = titleText,
            onValueChange = {
                titleText = it
                viewModel.updateTemplateContent(template, it, subtitleText, template.showSkipButton)
            },
            label = { Text(stringResource(R.string.template_title_text)) },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = subtitleText,
            onValueChange = {
                subtitleText = it
                viewModel.updateTemplateContent(template, titleText, it, template.showSkipButton)
            },
            label = { Text(stringResource(R.string.template_subtitle_text)) },
        )
        SwitchRow(R.string.template_show_skip_button, Icons.Outlined.SkipNext, template.showSkipButton) {
            viewModel.updateTemplateContent(template, titleText, subtitleText, it)
        }
        Text(stringResource(R.string.template_countdown_style), style = MaterialTheme.typography.titleSmall)
        LumenFlowRow {
            CountdownStyleChip(R.string.template_countdown_circle, COUNTDOWN_STYLE_CIRCLE, template, viewModel)
            CountdownStyleChip(R.string.template_countdown_bar, COUNTDOWN_STYLE_BAR, template, viewModel)
            CountdownStyleChip(R.string.template_countdown_number, COUNTDOWN_STYLE_NUMBER, template, viewModel)
        }
    }
}

@Composable
private fun CountdownStyleChip(
    @StringRes labelRes: Int,
    style: String,
    template: TipTemplateEntity,
    viewModel: ProjectLumenViewModel,
) {
    FilterChip(
        selected = templateCountdownStyle(template) == style,
        onClick = { viewModel.updateTemplateCountdownStyle(template, style) },
        label = { Text(stringResource(labelRes)) },
    )
}

@Composable
private fun SystemBackgroundPicker(template: TipTemplateEntity, viewModel: ProjectLumenViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = LumenCardShape, colors = lumenCardColors(), elevation = lumenCardElevation()) {
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
private fun AboutScreen(viewModel: ProjectLumenViewModel) {
    val versionLabel = rememberBuildVersionLabel()

    LumenPage {
        AboutHeroCard(versionLabel = versionLabel)
        AboutLinksCard(viewModel)
    }
}

@Composable
private fun AboutHeroCard(versionLabel: String) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
        ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(Icons.Outlined.Info, R.string.app_name)
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
private fun AboutLinksCard(viewModel: ProjectLumenViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = LumenCardShape, colors = lumenCardColors(), elevation = lumenCardElevation()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.Code, R.string.about_links)
            ConfirmExternalLinkButton(Icons.Outlined.Code, R.string.about_source_code, PROJECT_LUMEN_REPO_URL, viewModel)
            ConfirmExternalLinkButton(Icons.Outlined.Code, R.string.about_latest_release, PROJECT_LUMEN_RELEASES_URL, viewModel)
        }
    }
}

@Composable
private fun ConfirmExternalLinkButton(icon: ImageVector, @StringRes labelRes: Int, url: String, viewModel: ProjectLumenViewModel) {
    var pendingUrl by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { pendingUrl = url }) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(labelRes))
    }
    pendingUrl?.let { targetUrl ->
        AlertDialog(
            onDismissRequest = { pendingUrl = null },
            title = { Text(stringResource(R.string.about_external_link_prompt_title)) },
            text = { Text(stringResource(R.string.about_external_link_prompt_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    pendingUrl = null
                    viewModel.navigateWebPage(targetUrl)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingUrl = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProximityCalibrationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
        title = { Text(stringResource(R.string.proximity_calibration_title)) },
        text = { Text(stringResource(R.string.proximity_calibration_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.proximity_record_baseline))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.generic_cancel))
            }
        },
    )
}

@Composable
private fun BackupImportDialog(
    summary: BackupImportSummary,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_import_message))
                MetricRow(R.string.backup_schema_version, summary.schemaVersion.toString())
                MetricRow(R.string.backup_templates_count, summary.templateCount.toString())
                MetricRow(R.string.backup_eye_days_count, summary.eyeStatDays.toString())
                MetricRow(R.string.backup_pomodoro_days_count, summary.pomodoroStatDays.toString())
                MetricRow(R.string.backup_entitlements_count, summary.entitlementCount.toString())
                MetricRow(R.string.backup_plans_count, summary.reminderPlanCount.toString())
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.backup_import_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun UpdateDialog(
    viewModel: ProjectLumenViewModel,
    state: UpdateDialogState,
    downloadingUpdate: Boolean,
    downloadProgressBytes: Long,
    downloadProgressTotalBytes: Long?,
    onDismiss: () -> Unit,
    onDownloadUpdate: (UpdateCandidate, ReleaseAsset) -> Unit,
    onInstallDownloadedApk: (UpdateCandidate, File) -> Unit,
    onError: (String) -> Unit,
    updateInstaller: UpdateInstaller,
) {
    val context = LocalContext.current
    var pendingReleaseUrl by remember { mutableStateOf<String?>(null) }
    val showReleaseInfo: @Composable (ReleaseInfo, BuildMetadata, UpdateCandidate?) -> Unit = { release, current, candidate ->
        val publishTime = Instant.ofEpochMilli(release.publishedAtUtcMillis).atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
        val buildTime = Instant.ofEpochMilli(current.buildTimeUtcMillis).atZone(ZoneOffset.UTC).format(updateDialogTimeFormatter)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.about_update_found, release.tagName))
            Text(stringResource(R.string.about_update_current_version, current.versionName, current.shortHash))
            Text(stringResource(R.string.about_update_build_time, buildTime))
            Text(stringResource(R.string.about_update_publish_time, publishTime))
            if (candidate?.isTimeFallback == true) {
                Text(stringResource(R.string.about_update_time_fallback), color = MaterialTheme.colorScheme.primary)
            }
            if (release.body.isNotBlank()) {
                Text(release.body)
            }
            Text(stringResource(R.string.about_update_release_notes, release.releaseName))
        }
    }

    when (val currentState = state) {
        UpdateDialogState.Hidden -> Unit
        UpdateDialogState.Checking -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.about_update_checking_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.about_update_checking))
                }
            },
            confirmButton = {},
        )
        UpdateDialogState.NoUpdate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.about_update_up_to_date_title)) },
            text = { Text(stringResource(R.string.about_update_up_to_date_message)) },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is UpdateDialogState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.about_update_failed_title)) },
            text = { Text(currentState.message) },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            },
        )
        is UpdateDialogState.UpdateAvailable -> {
            val candidate = currentState.candidate
            val release = candidate.release
            val targetAsset = candidate.matchedAsset ?: chooseFallbackAsset(release.assets)
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(release.tagName) },
                text = { showReleaseInfo(release, candidate.currentBuild, candidate) },
                confirmButton = {
                    OutlinedButton(
                        enabled = !downloadingUpdate,
                        onClick = {
                            if (targetAsset != null) {
                                onDownloadUpdate(candidate, targetAsset)
                            } else {
                                pendingReleaseUrl = release.htmlUrl.ifBlank { PROJECT_LUMEN_RELEASES_BASE_URL }
                            }
                        },
                    ) {
                        ButtonLabel(Icons.Outlined.FileDownload, if (targetAsset != null) R.string.about_download_update else R.string.about_open_release)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        is UpdateDialogState.Downloading -> {
            val candidate = currentState.candidate
            val release = candidate.release
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.about_update_downloading_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        showReleaseInfo(release, candidate.currentBuild, candidate)
                        val progress = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
                            downloadProgressBytes.toFloat() / it.toFloat()
                        }
                        LinearProgressIndicator(
                            progress = { progress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = downloadProgressTotalBytes?.takeIf { it > 0 }?.let {
                                val percent = ((downloadProgressBytes * 100) / it).coerceIn(0, 100)
                                stringResource(R.string.about_update_downloading_progress, percent)
                            } ?: stringResource(R.string.about_update_downloading),
                        )
                    }
                },
                confirmButton = {},
                dismissButton = {},
            )
        }
        is UpdateDialogState.InstallAuthorization -> {
            val candidate = currentState.candidate
            val release = candidate.release
            val permissionGranted = updateInstaller.canInstallPackages()
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.about_install_permission_prompt_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.about_install_permission_prompt_message))
                        showReleaseInfo(release, candidate.currentBuild, candidate)
                    }
                },
                confirmButton = {
                    OutlinedButton(onClick = {
                        if (permissionGranted) {
                            onInstallDownloadedApk(candidate, currentState.file)
                        } else {
                            runCatching { context.startActivity(updateInstaller.createInstallPermissionIntent()) }
                                .onFailure { onError(it.message ?: "Unable to open install settings.") }
                        }
                    }) {
                        ButtonLabel(
                            if (permissionGranted) Icons.Outlined.FileDownload else Icons.AutoMirrored.Outlined.OpenInNew,
                            if (permissionGranted) R.string.about_install_now else R.string.about_update_grant_permission,
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
    }

    pendingReleaseUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { pendingReleaseUrl = null },
            title = { Text(stringResource(R.string.about_external_link_prompt_title)) },
            text = { Text(stringResource(R.string.about_external_link_prompt_message)) },
            confirmButton = {
                OutlinedButton(onClick = {
                    pendingReleaseUrl = null
                    viewModel.navigateWebPage(url)
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingReleaseUrl = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }
}
@Composable
private fun StateCard(runtime: RuntimeStateEntity, nowMillis: Long) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
            shape = LumenCardShape,
            colors = lumenCardColors(),
            elevation = lumenCardElevation(),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebViewScreen(
    url: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageTitle by remember(url) { mutableStateOf("") }
    var currentUrl by rememberSaveable(url) { mutableStateOf(url) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var showCompatibilityDialog by remember { mutableStateOf(false) }
    val currentPageUrl = webView?.url ?: currentUrl

    BackHandler(onBack = onDismiss)
    DisposableEffect(webView) {
        val disposableWebView = webView
        onDispose {
            disposableWebView?.destroy()
        }
    }

    if (showCompatibilityDialog) {
        AlertDialog(
            onDismissRequest = { showCompatibilityDialog = false },
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null) },
            title = { Text("兼容性提示") },
            text = {
                Text(
                    "检测到您的系统内置浏览器版本($chromeVersion)过低, 可能无法正常浏览网页文档\n\n" +
                        "建议自行升级版本后重启 GKD 再查看文档, 或点击右上角后在外部浏览器打开查阅\n\n" +
                        "若能正常浏览文档请忽略此项提示",
                )
            },
            confirmButton = {
                OutlinedButton(onClick = { showCompatibilityDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = pageTitle.ifBlank { webView?.title.orEmpty() },
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    if (chromeVersion in 1 until MINI_CHROME_VERSION) {
                        IconButton(onClick = { showCompatibilityDialog = true }) {
                            Icon(Icons.Outlined.WarningAmber, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            if (!isLoading) {
                                DropdownMenuItem(
                                    text = { Text("刷新页面") },
                                    onClick = {
                                        expanded = false
                                        webView?.reload()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("复制链接") },
                                onClick = {
                                    expanded = false
                                    copyWebPageUrl(context, currentPageUrl)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("外部打开") },
                                onClick = {
                                    expanded = false
                                    openUri(context, currentPageUrl.toUri())
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        key(url) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                factory = {
                    WebView(it).apply {
                        webView = this
                        addJavascriptInterface(ProjectLumenWebViewJsApi(it.applicationContext), "gkd")
                        @SuppressLint("SetJavaScriptEnabled")
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            settings.setAlgorithmicDarkeningAllowed(false)
                        }
                        webViewClient = ProjectLumenWebViewClient(
                            onPageStarted = { startedUrl ->
                                isLoading = true
                                if (!startedUrl.isNullOrBlank()) currentUrl = startedUrl
                            },
                            onPageFinished = { finishedUrl, title ->
                                isLoading = false
                                if (!finishedUrl.isNullOrBlank()) currentUrl = finishedUrl
                                pageTitle = title.orEmpty()
                            },
                        )
                        webChromeClient = object : WebChromeClient() {
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                pageTitle = title.orEmpty()
                            }

                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                isLoading = newProgress < 100
                                view?.url?.takeIf { it.isNotBlank() }?.let { currentUrl = it }
                                view?.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }
                            }
                        }
                        loadUrl(url)
                    }
                },
            )
        }
    }
}

private class ProjectLumenWebViewClient(
    private val onPageStarted: (String?) -> Unit,
    private val onPageFinished: (String?, String?) -> Unit,
) : WebViewClient() {
    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageFinished(url, view.title)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        if (uri.host != GKD_DOC_HOST) {
            openUri(view.context, uri)
            return true
        }
        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        try {
            if (request.isForMainFrame && request.url.host == GKD_DOC_HOST && request.method == "GET") {
                val path = request.url.path?.takeIf { it.isNotEmpty() } ?: "/"
                loadGkdDocResponse(path)?.let { return it }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return super.shouldInterceptRequest(view, request)
    }
}

private class ProjectLumenWebViewJsApi(private val context: Context) {
    @JavascriptInterface
    fun getAppId() = context.packageName

    @JavascriptInterface
    fun getAppName() = context.getString(R.string.app_name)

    @JavascriptInterface
    fun getVersionCode() = appVersionCode(context)

    @JavascriptInterface
    fun getVersionName() = appVersionName(context)

    @JavascriptInterface
    fun getChannel() = "project-lumen"

    @JavascriptInterface
    fun getDebuggable() = BuildConfig.DEBUG
}

private fun loadGkdDocResponse(path: String): WebResourceResponse? {
    val docConfig = JSONObject(httpGetText(GKD_DOC_CONFIG_URL))
    val mirrorBaseUrl = docConfig.getString("mirrorBaseUrl")
    val htmlUrlMap = docConfig.getJSONObject("htmlUrlMap")
    val htmlPath = htmlUrlMap.optString(path).takeIf { it.isNotBlank() } ?: return null
    val htmlText = httpGetText(mirrorBaseUrl + htmlPath).let {
        if (BuildConfig.DEBUG) GKD_DEBUG_JS_TEXT + it else it
    }
    return WebResourceResponse(
        "text/html",
        "UTF-8",
        htmlText.byteInputStream(Charsets.UTF_8),
    )
}

private fun httpGetText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    return try {
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun copyWebPageUrl(context: Context, url: String) {
    context.getSystemService<ClipboardManager>()?.setPrimaryClip(
        ClipData.newPlainText(context.packageName, url),
    )
    Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
}

private fun openUri(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
}

@Suppress("DEPRECATION")
private fun appVersionCode(context: Context): Int {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    } else {
        packageInfo.versionCode
    }
}

@Suppress("DEPRECATION")
private fun appVersionName(context: Context): String {
    return context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}

@Composable
private fun TodayStatsCard(stat: DailyEyeStatsEntity?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.BarChart, R.string.today_summary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.working_time, stringResource(R.string.hours_short, ((stat?.workingSeconds ?: 0L) / 3600.0)))
                SmallMetric(R.string.rest_time, minutesLabel(((stat?.restSeconds ?: 0L) / 60L).toInt()))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.skip_count, (stat?.skipCount ?: 0).toString())
                SmallMetric(R.string.completed_breaks, (stat?.completedBreakCount ?: 0).toString())
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.proximity_warnings, (stat?.proximityWarningCount ?: 0).toString())
                SmallMetric(R.string.proximity_close_time, minutesLabel(((stat?.proximityCloseSeconds ?: 0L) / 60L).toInt()))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallMetric(R.string.eye_dry_warnings, (stat?.eyeDryWarningCount ?: 0).toString())
                SmallMetric(R.string.low_light_warnings, (stat?.lowLightWarningCount ?: 0).toString())
            }
        }
    }
}

@Composable
private fun GoalProgressCard(uiState: ProjectLumenUiState) {
    val goal = uiState.dailyGoal
    val eye = uiState.eyeStats.firstOrNull()
    val pomodoro = uiState.pomodoroStats.firstOrNull()
    val eyeActiveDates = uiState.eyeStats.take(7)
        .filter { it.workingSeconds > 0L || it.restSeconds > 0L }
        .map { it.statDate }
        .toSet()
    val activeDays = eyeActiveDates.size + uiState.pomodoroStats.take(7)
        .filter { it.completedFocusSessions > 0 && it.statDate !in eyeActiveDates }
        .size
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(Icons.Outlined.CheckCircle, R.string.today_goals)
            GoalLine(
                label = stringResource(R.string.daily_rest_goal),
                value = "${eye?.completedBreakCount ?: 0}/${goal.restBreakGoal}",
                progress = (eye?.completedBreakCount ?: 0).toFloat() / goal.restBreakGoal.coerceAtLeast(1).toFloat(),
            )
            val continuousMinutes = ((eye?.maxContinuousWorkSeconds ?: 0L) / 60L).toInt()
            GoalLine(
                label = stringResource(R.string.max_continuous_work_goal),
                value = stringResource(R.string.minutes_value, continuousMinutes),
                progress = 1f - (continuousMinutes.toFloat() / goal.maxContinuousWorkMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
            )
            GoalLine(
                label = stringResource(R.string.daily_pomodoro_goal),
                value = "${pomodoro?.completedFocusSessions ?: 0}/${goal.pomodoroGoal}",
                progress = (pomodoro?.completedFocusSessions ?: 0).toFloat() / goal.pomodoroGoal.coerceAtLeast(1).toFloat(),
            )
            GoalLine(
                label = stringResource(R.string.weekly_active_days_goal),
                value = "$activeDays/${goal.weeklyActiveDaysGoal}",
                progress = activeDays.toFloat() / goal.weeklyActiveDaysGoal.coerceAtLeast(1).toFloat(),
            )
        }
    }
}

@Composable
private fun GoalLine(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TimerCard(
    label: String,
    seconds: Long,
    progress: Float,
    fallbackText: String,
    countdownStyle: String = COUNTDOWN_STYLE_CIRCLE,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650),
        label = "timerProgress",
    )
    val timerText = if (seconds > 0) compactTime(seconds) else fallbackText
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
    ) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            when (countdownStyle) {
                COUNTDOWN_STYLE_BAR -> {
                    AnimatedTimerText(timerText, Modifier.padding(vertical = 40.dp))
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                }
                COUNTDOWN_STYLE_NUMBER -> {
                    AnimatedTimerText(
                        timerText = timerText,
                        modifier = Modifier
                            .padding(vertical = 56.dp)
                            .graphicsLayer {
                                scaleX = pulse
                                scaleY = pulse
                            },
                    )
                }
                else -> {
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
                        AnimatedTimerText(timerText)
                    }
                    LinearProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AnimatedTimerText(timerText: String, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = timerText,
        modifier = modifier,
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

@Composable
private fun TemplatePreviewCard(template: TipTemplateEntity?) {
    val background = templateBackgroundColor(template)
    val primary = templatePrimaryColor(template)
    val animatedBackground by animateColorAsState(background, tween(220), label = "templateBackground")
    val animatedPrimary by animateColorAsState(primary, tween(220), label = "templatePrimary")
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = CardDefaults.cardColors(containerColor = animatedBackground),
        elevation = lumenCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (template?.imagePath?.isNotBlank() == true) {
                UriImagePreview(template.imagePath)
            } else {
                ColorSwatch(animatedBackground)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(templateBreakTitle(template), style = MaterialTheme.typography.titleMedium, color = animatedPrimary)
                Text(
                    templateBreakSubtitle(template),
                    style = MaterialTheme.typography.bodyMedium,
                    color = animatedPrimary.copy(alpha = 0.76f),
                )
            }
        }
    }
}

@Composable
private fun PageIntro(icon: ImageVector, @StringRes titleRes: Int, message: String) {
    PageIntroText(icon = icon, title = stringResource(titleRes), message = message)
}

@Composable
private fun PageIntroText(icon: ImageVector, title: String, message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            stringResource(titleRes),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SettingsSection(@StringRes titleRes: Int, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(icon, titleRes)
            content()
        }
    }
}

@Composable
private fun ActionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.82f)),
        shape = LumenCardShape,
        colors = lumenCardColors(),
        elevation = lumenCardElevation(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun EmptyStateMessage(@StringRes messageRes: Int) {
    Text(
        text = stringResource(messageRes),
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun StatusLine(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(LumenCardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusPill(icon: ImageVector, @StringRes labelRes: Int) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FileSettingRow(
    @StringRes labelRes: Int,
    path: String,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(labelRes), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(if (path.isBlank()) R.string.not_set else R.string.custom_file_selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                modifier = Modifier.weight(if (path.isBlank()) 1f else 0.62f),
                onClick = onChoose,
            ) {
                ButtonLabel(Icons.Outlined.FileDownload, R.string.choose_custom_file)
            }
            if (path.isNotBlank()) {
                OutlinedButton(
                    modifier = Modifier.weight(0.38f),
                    onClick = onClear,
                ) {
                    Text(stringResource(R.string.clear_custom_file))
                }
            }
        }
    }
}

@Composable
private fun UriImagePreview(path: String) {
    AndroidView(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(6.dp)),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(path.toUri())
            }
        },
        update = { imageView ->
            imageView.setImageURI(path.toUri())
        },
    )
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
            permissionLauncher.launch(POST_NOTIFICATIONS_PERMISSION)
        } else {
            action()
        }
    }
}

@Composable
private fun rememberCameraPermissionGate(): ((() -> Unit) -> Unit) {
    val context = LocalContext.current
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAction
        pendingAction = null
        if (granted) action?.invoke()
    }
    return { action ->
        if (needsCameraPermission(context)) {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.CAMERA)
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
        LabelWithIcon(icon, labelRes, Modifier.weight(1f))
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
            LabelWithIcon(icon, labelRes, Modifier.weight(1f))
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
private fun LabelWithIcon(icon: ImageVector, @StringRes labelRes: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun QuietModeChip(@StringRes labelRes: Int, mode: QuietMode, settings: AppSettingsEntity, viewModel: ProjectLumenViewModel) {
    FilterChip(
        selected = settings.quietMode == mode.name,
        onClick = { viewModel.updateSettings { current -> current.copy(quietMode = mode.name) } },
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
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .animateContentSize(animationSpec = spring(stiffness = 420f, dampingRatio = 0.86f))
                .padding(PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp)),
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
private fun timeOfDayLabel(totalMinutes: Int): String {
    val safeMinutes = totalMinutes.coerceIn(0, 1439)
    return stringResource(R.string.time_value, safeMinutes / 60, safeMinutes % 60)
}

private fun snapTimeMinute(value: Int): Int {
    return (((value.coerceIn(0, 1435) + 2) / 5) * 5).coerceIn(0, 1435)
}

private fun isAutoDarkActive(nowMillis: Long, startMinute: Int, endMinute: Int): Boolean {
    val currentMinute = Instant.ofEpochMilli(nowMillis)
        .atZone(ZoneId.systemDefault())
        .let { it.hour * 60 + it.minute }
    val start = snapTimeMinute(startMinute)
    val end = snapTimeMinute(endMinute)
    return when {
        start == end -> false
        start < end -> currentMinute in start until end
        else -> currentMinute >= start || currentMinute < end
    }
}

@Composable
private fun templateDisplayName(template: TipTemplateEntity?): String {
    return when (template?.id) {
        1L -> stringResource(R.string.template_calm_teal)
        2L -> stringResource(R.string.template_soft_sunrise)
        3L -> stringResource(R.string.template_focus_indigo)
        4L -> stringResource(R.string.template_system_colors)
        5L -> stringResource(R.string.template_forest_glass)
        6L -> stringResource(R.string.template_clear_sky)
        7L -> stringResource(R.string.template_rose_quartz)
        8L -> stringResource(R.string.template_deep_slate)
        9L -> stringResource(R.string.template_paper_mint)
        10L -> stringResource(R.string.template_warm_desk)
        11L -> stringResource(R.string.template_graphite_focus)
        12L -> stringResource(R.string.template_lotus_pause)
        13L -> stringResource(R.string.template_clinic_calm)
        14L -> stringResource(R.string.template_night_amber)
        15L -> stringResource(R.string.template_reading_green)
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
        5L -> stringResource(R.string.template_forest_glass_subtitle)
        6L -> stringResource(R.string.template_clear_sky_subtitle)
        7L -> stringResource(R.string.template_rose_quartz_subtitle)
        8L -> stringResource(R.string.template_deep_slate_subtitle)
        9L -> stringResource(R.string.template_paper_mint_subtitle)
        10L -> stringResource(R.string.template_warm_desk_subtitle)
        11L -> stringResource(R.string.template_graphite_focus_subtitle)
        12L -> stringResource(R.string.template_lotus_pause_subtitle)
        13L -> stringResource(R.string.template_clinic_calm_subtitle)
        14L -> stringResource(R.string.template_night_amber_subtitle)
        15L -> stringResource(R.string.template_reading_green_subtitle)
        else -> template?.subtitleText ?: stringResource(R.string.break_message)
    }
}

@Composable
private fun templateBreakTitle(template: TipTemplateEntity?): String {
    val title = template?.titleText.orEmpty()
    return if (title.isBlank() || title == DEFAULT_TEMPLATE_TITLE) {
        stringResource(R.string.break_title)
    } else {
        title
    }
}

@Composable
private fun templateBreakSubtitle(template: TipTemplateEntity?): String {
    val subtitle = template?.subtitleText.orEmpty()
    return if (subtitle.isBlank() || subtitle == DEFAULT_TEMPLATE_SUBTITLE) {
        stringResource(R.string.break_message)
    } else {
        subtitle
    }
}

private fun templateCountdownStyle(template: TipTemplateEntity?): String {
    val style = runCatching {
        JSONObject(template?.layoutJson?.takeIf { it.isNotBlank() } ?: "{}")
            .optString("countdownStyle", COUNTDOWN_STYLE_CIRCLE)
    }.getOrDefault(COUNTDOWN_STYLE_CIRCLE)
    return when (style) {
        COUNTDOWN_STYLE_BAR,
        COUNTDOWN_STYLE_NUMBER,
        COUNTDOWN_STYLE_CIRCLE -> style
        else -> COUNTDOWN_STYLE_CIRCLE
    }
}

private fun activeTemplate(uiState: ProjectLumenUiState): TipTemplateEntity? {
    return uiState.templates.firstOrNull { it.id == uiState.settings.activeTipTemplateId } ?: uiState.templates.firstOrNull()
}

private fun planTier(settings: AppSettingsEntity): PlanTier {
    return PlanTier.entries.firstOrNull { it.name == settings.planTier } ?: PlanTier.FREE
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
    return hex
        ?.let { runCatching { Color(it.toColorInt()) }.getOrDefault(fallback) }
        ?: fallback
}

@Composable
private fun rememberBuildVersionLabel(): String {
    val metadata = remember { BuildMetadata.current() }
    return "${metadata.versionName} (${metadata.shortHash})"
}

private fun chooseFallbackAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
    val verifiedApks = assets.filter { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) && !asset.sha256.isNullOrBlank()
    }
    return verifiedApks.firstOrNull { it.name.contains("universal", ignoreCase = true) }
        ?: verifiedApks.firstOrNull { it.name.contains("all", ignoreCase = true) }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.startActivity(intent)
}

private fun persistReadableUri(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun needsNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) !=
        PackageManager.PERMISSION_GRANTED
}

private fun needsCameraPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
        PackageManager.PERMISSION_GRANTED
}

private fun needsExactAlarmSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    return !alarmManager.canScheduleExactAlarms()
}

private fun needsFullScreenIntentSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    return !notificationManager.canUseFullScreenIntent()
}

private fun needsOverlayPermission(context: Context): Boolean {
    return !Settings.canDrawOverlays(context)
}

private fun needsWriteSettingsPermission(context: Context): Boolean {
    return !Settings.System.canWrite(context)
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}

private fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val intent = Intent(
        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}

private fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}

private fun openWriteSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        "package:${context.packageName}".toUri(),
    )
    runCatching { context.startActivity(intent) }
        .onFailure { openAppNotificationSettings(context) }
}
