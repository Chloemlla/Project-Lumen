package com.projectlumen.app.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
import com.projectlumen.app.core.crash.CrashReport
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.update.ReleaseAsset
import com.projectlumen.app.core.update.UpdateCandidate
import com.projectlumen.app.core.update.UpdateChecker
import com.projectlumen.app.core.update.UpdateInstaller
import com.projectlumen.app.ui.theme.ProjectLumenTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface UpdateDialogState {
    data object Hidden : UpdateDialogState
    data object Checking : UpdateDialogState
    data class UpdateAvailable(val candidate: UpdateCandidate) : UpdateDialogState
    data object NoUpdate : UpdateDialogState
    data class Downloading(val candidate: UpdateCandidate, val asset: ReleaseAsset) : UpdateDialogState
    data class InstallAuthorization(val candidate: UpdateCandidate, val file: File) : UpdateDialogState
    data class Error(val message: String) : UpdateDialogState
}

internal enum class Destination(
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
    DEVELOPER("developer", R.string.nav_developer, Icons.Outlined.Code, false),
    WEB("web", R.string.about_external_link_prompt_title, Icons.AutoMirrored.Outlined.OpenInNew, false),
}

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
    var activeCrashReport by remember(crashReport) { mutableStateOf(crashReport) }
    var activeCrashReportClearsStore by remember(crashReport) { mutableStateOf(crashReport != null) }

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
                    updateDialogState = UpdateDialogState.Error(
                        throwable.message ?: baseContext.getString(R.string.about_update_failed_title),
                    )
                }
                if (!manual) autoCheckStarted = true
            }
        }
    }

    fun startInstallIfAllowed(candidate: UpdateCandidate, file: File) {
        if (updateInstaller.canInstallPackages()) {
            runCatching { updateInstaller.installApk(file) }
                .onSuccess { updateDialogState = UpdateDialogState.Hidden }
                .onFailure {
                    updateDialogState = UpdateDialogState.Error(
                        it.message ?: baseContext.getString(R.string.about_update_open_installer_failed),
                    )
                }
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
                updateDialogState = UpdateDialogState.Error(
                    throwable.message ?: baseContext.getString(R.string.about_update_download_failed),
                )
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
    val activeThemeTemplate = activeTemplate(uiState)

    CompositionLocalProvider(LocalContext provides localizedContext) {
        ProjectLumenTheme(
            themeMode = themeMode,
            useDynamicColors = uiState.settings.useDynamicColors,
            themePrimaryColor = activeThemeTemplate?.primaryColor,
            themeBackgroundColor = activeThemeTemplate?.backgroundValue,
        ) {
            activeCrashReport?.let { report ->
                CrashReportScreen(
                    report = report,
                    onContinue = { activeCrashReport = null },
                    clearStoredReportOnContinue = activeCrashReportClearsStore,
                )
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
            val currentDestination = Destination.entries.firstOrNull {
                it.route == backStackEntry?.destination?.route
            } ?: Destination.HOME
            fun navigateBackFromSecondaryPage() {
                if (!navController.navigateUp()) {
                    navController.navigate(Destination.SETTINGS.route) {
                        launchSingleTop = true
                    }
                }
            }
            BackHandler(enabled = !currentDestination.showInBottomNav) {
                navigateBackFromSecondaryPage()
            }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    LumenTopBar(
                        title = stringResource(currentDestination.labelRes),
                        onNavigateBack = if (currentDestination.showInBottomNav) null else {
                            { navigateBackFromSecondaryPage() }
                        },
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        Destination.entries.filter { it.showInBottomNav }.forEach { destination ->
                            val selected = backStackEntry?.destination?.hierarchy?.any {
                                it.route == destination.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
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
                                        contentDescription = stringResource(destination.labelRes),
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
                            openDeveloperOptions = { navController.navigate(Destination.DEVELOPER.route) },
                        )
                    }
                    composable(Destination.TEMPLATES.route) { TemplatesScreen(uiState, viewModel) }
                    composable(Destination.ABOUT.route) { AboutScreen(viewModel) }
                    composable(Destination.DEVELOPER.route) {
                        DeveloperDebugScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onPreviewCrashReport = { report ->
                                activeCrashReportClearsStore = false
                                activeCrashReport = report
                            },
                        )
                    }
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
