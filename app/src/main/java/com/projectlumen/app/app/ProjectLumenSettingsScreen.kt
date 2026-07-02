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
internal fun SettingsScreen(
    uiState: ProjectLumenUiState,
    viewModel: ProjectLumenViewModel,
    checkingUpdate: Boolean,
    onManualUpdateCheck: () -> Unit,
    openTemplates: () -> Unit,
    openAbout: () -> Unit,
    openDeveloperOptions: () -> Unit,
) {
    val settings = uiState.settings
    val template = activeTemplate(uiState)
    val context = LocalContext.current
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    val runWithCameraPermission = rememberCameraPermissionGate()
    val permissionRequirements = rememberPermissionRequirements()
    val notificationPermissionNeeded = permissionRequirements.notification
    val cameraPermissionNeeded = permissionRequirements.camera
    val exactAlarmSettingsNeeded = permissionRequirements.exactAlarm
    val fullScreenIntentSettingsNeeded = permissionRequirements.fullScreenIntent
    val overlayPermissionNeeded = permissionRequirements.overlay
    val writeSettingsPermissionNeeded = permissionRequirements.writeSettings
    val shizukuNativeBrightnessEnabled = settings.shizukuAdvancedModeEnabled && settings.shizukuNativeEyeProtectionEnabled
    val backupImportPreview by viewModel.backupImportPreview.collectAsStateWithLifecycle()
    val remoteState by viewModel.remoteState.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    var pendingBackupImportUri by remember { mutableStateOf<Uri?>(null) }
    var showProximityCalibrationDialog by rememberSaveable { mutableStateOf(false) }
    val proximityCalibrated = settings.proximityBaselineEyeDistancePx > 0f ||
        settings.proximityBaselineFaceWidthPercent > 0
    val proximityCaptureSeconds = settings.proximityCaptureSeconds.coerceIn(1, 2)
    fun persistUri(uri: Uri): String {
        persistReadableUri(context, uri)
        return uri.toString()
    }
    fun requestReminderTimingPermissions() {
        when {
            needsExactAlarmSettings(context) -> openExactAlarmSettings(context)
            needsFullScreenIntentSettings(context) -> openFullScreenIntentSettings(context)
        }
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
    LaunchedEffect(settings.shizukuAdvancedModeEnabled) {
        if (settings.shizukuAdvancedModeEnabled) {
            viewModel.refreshShizukuState()
        }
    }
    val templateAppearanceEnabled = !settings.useDynamicColors
    val autoDarkWindowEnabled = settings.useAutoDarkWindow && !templateAppearanceEnabled
    LumenPage {
        PageIntro(
            icon = Icons.Outlined.Settings,
            titleRes = R.string.nav_settings,
            message = stringResource(R.string.settings_subtitle),
        )
        EyeCareSetupAndPrivacyCard(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuState.ready,
        )
        EyeCareActionPlanCard(
            uiState = uiState,
            permissionRequirements = permissionRequirements,
            shizukuReady = shizukuState.ready,
            onApplyRecommended = { applyRecommendedEyeCareSettings(viewModel) },
            onExportReport = viewModel::shareMonthlyReportPdf,
        )
        SettingsSection(R.string.section_general, Icons.Outlined.Settings) {
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleSmall)
            LumenFlowRow {
                LanguageChip(R.string.language_system, LocaleController.SYSTEM, settings, viewModel)
                LanguageChip(R.string.language_zh, LocaleController.CHINESE, settings, viewModel)
                LanguageChip(R.string.language_en, LocaleController.ENGLISH, settings, viewModel)
            }
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall)
            LumenFlowRow {
                ThemeChip(R.string.theme_system, AppThemeMode.SYSTEM, settings, viewModel, enabled = !templateAppearanceEnabled)
                ThemeChip(R.string.theme_light, AppThemeMode.LIGHT, settings, viewModel)
                ThemeChip(R.string.theme_dark, AppThemeMode.DARK, settings, viewModel, enabled = !templateAppearanceEnabled)
            }
            SwitchRow(R.string.enable_statistics, Icons.Outlined.BarChart, settings.statsEnabled) {
                viewModel.updateSettings { current -> current.copy(statsEnabled = it) }
            }
            SwitchRow(R.string.translation_entry_enabled, Icons.Outlined.Translate, settings.translationEntryEnabled) {
                viewModel.updateSettings { current -> current.copy(translationEntryEnabled = it) }
            }
            LumenFlowRow {
                OutlinedButton(onClick = openTemplates) { ButtonLabel(Icons.Outlined.Style, R.string.nav_templates) }
                OutlinedButton(onClick = openAbout) { ButtonLabel(Icons.Outlined.Info, R.string.nav_about) }
            }
            SwitchRow(
                R.string.auto_dark_window,
                Icons.Outlined.Style,
                autoDarkWindowEnabled,
                enabled = !templateAppearanceEnabled,
            ) {
                viewModel.updateSettings { current -> current.copy(useAutoDarkWindow = it) }
            }
            AnimatedVisibility(
                visible = autoDarkWindowEnabled,
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
        SettingsSection(R.string.section_goals, Icons.Outlined.CheckCircle, initiallyExpanded = false) {
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
        RemoteCloudAccountCard(
            state = remoteState,
            onCheckHealth = viewModel::checkRemoteHealth,
            onStartEmailLogin = viewModel::startRemoteEmailLogin,
            onVerifyEmailLogin = viewModel::verifyRemoteEmailLogin,
            onRefreshAccount = viewModel::refreshRemoteAccount,
            onSyncNow = viewModel::syncRemoteNow,
            onUploadBackup = viewModel::uploadCloudBackup,
            onRestoreBackup = viewModel::restoreLatestCloudBackup,
            onSignOut = viewModel::signOutRemote,
        )
        EyeCareGrowthCapabilityCard(uiState)
        SettingsSection(R.string.about_update_status, Icons.Outlined.Sync, initiallyExpanded = false) {
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
                    runWithNotificationPermission {
                        viewModel.setNotificationsEnabled(true)
                        requestReminderTimingPermissions()
                    }
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
                    onClick = {
                        runWithNotificationPermission {
                            viewModel.setNotificationsEnabled(true)
                            requestReminderTimingPermissions()
                        }
                    },
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
        SettingsSection(R.string.section_keep_alive, Icons.Outlined.Schedule, initiallyExpanded = false) {
            SwitchRow(R.string.enable_keep_alive, Icons.Outlined.Schedule, settings.keepAliveEnabled) {
                viewModel.setKeepAliveEnabled(it)
            }
        }
        ShizukuAdvancedSettingsSection(
            settings = settings,
            state = shizukuState,
            viewModel = viewModel,
        )
        SettingsSection(R.string.section_proximity, Icons.Outlined.PhotoCamera, initiallyExpanded = false) {
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
        SettingsSection(R.string.section_eye_protection, Icons.Outlined.PhotoCamera, initiallyExpanded = false) {
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
                if (it && needsWriteSettingsPermission(context) && !shizukuNativeBrightnessEnabled) {
                    openWriteSettings(context)
                }
            }
            AnimatedVisibility(
                visible = settings.autoBrightnessEnabled && writeSettingsPermissionNeeded && !shizukuNativeBrightnessEnabled,
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
            NumberSlider(R.string.auto_brightness_min, Icons.Outlined.Style, settings.autoBrightnessMinPercent, 1f..100f, 99, stringResource(R.string.percent_value, settings.autoBrightnessMinPercent)) {
                viewModel.updateSettings { current ->
                    current.copy(autoBrightnessMinPercent = it.coerceAtMost(current.autoBrightnessMaxPercent))
                }
            }
            NumberSlider(R.string.auto_brightness_max, Icons.Outlined.Style, settings.autoBrightnessMaxPercent, 1f..100f, 99, stringResource(R.string.percent_value, settings.autoBrightnessMaxPercent)) {
                viewModel.updateSettings { current ->
                    current.copy(autoBrightnessMaxPercent = it.coerceAtLeast(current.autoBrightnessMinPercent))
                }
            }
            SwitchRow(R.string.enable_global_overlay, Icons.Outlined.NotificationsActive, settings.globalOverlayEnabled) {
                viewModel.updateSettings { current -> current.copy(globalOverlayEnabled = it) }
                if (it && needsOverlayPermission(context)) {
                    openOverlaySettings(context)
                }
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
        SettingsSection(R.string.section_sound, Icons.AutoMirrored.Outlined.VolumeUp, initiallyExpanded = false) {
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
            SwitchRow(R.string.use_wallpaper_colors, Icons.Outlined.Style, settings.useDynamicColors) {
                viewModel.updateSettings { current ->
                    if (it) {
                        current.copy(useDynamicColors = true)
                    } else {
                        current.copy(
                            useDynamicColors = false,
                            themeMode = AppThemeMode.LIGHT.name,
                            useAutoDarkWindow = false,
                        )
                    }
                }
            }
            Text(
                stringResource(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        R.string.wallpaper_colors_message
                    } else {
                        R.string.wallpaper_colors_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = !settings.useDynamicColors,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TemplatePreviewCard(template)
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
        SettingsSection(R.string.section_quiet_hours, Icons.Outlined.Schedule, initiallyExpanded = false) {
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
        SettingsSection(R.string.section_pre_alert, Icons.Outlined.Schedule, initiallyExpanded = false) {
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
        if (settings.developerModeEnabled) {
            SettingsSection(R.string.nav_developer, Icons.Outlined.Code, initiallyExpanded = false) {
                OutlinedButton(onClick = openDeveloperOptions) {
                    ButtonLabel(Icons.Outlined.Code, R.string.nav_developer)
                }
            }
        }
    }
}


