package com.projectlumen.app.app

import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.network.ClashPartnerCompat
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.i18n.LocaleController
import kotlinx.coroutines.launch

private enum class GrowthConfigTarget {
    REPORTS,
    CLOUD,
    FAMILY,
    GUIDANCE,
}

private val GeneralGrowthAnchors = listOf(
    GrowthConfigTarget.REPORTS,
    GrowthConfigTarget.GUIDANCE,
)

private val NotificationPermissionAnchors = listOf(
    PermissionSetupTarget.NOTIFICATIONS,
    PermissionSetupTarget.EXACT_ALARM,
    PermissionSetupTarget.FULL_SCREEN,
)

private val ShizukuPermissionAnchors = listOf(PermissionSetupTarget.SHIZUKU)

private val DiagnosticsAndShizukuPermissionAnchors = listOf(
    PermissionSetupTarget.DIAGNOSTICS,
    PermissionSetupTarget.SHIZUKU,
)

private val EyeProtectionPermissionAnchors = listOf(
    PermissionSetupTarget.BLINK_CAMERA,
    PermissionSetupTarget.AMBIENT_LIGHT,
    PermissionSetupTarget.BRIGHTNESS,
    PermissionSetupTarget.OVERLAY,
)

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
    val template = remember(uiState.templates, settings.activeTipTemplateId) { activeTemplate(uiState) }
    val context = LocalContext.current
    val appContext = context.applicationContext as ProjectLumenApplication
    var remoteAnalysisConsentGrantedAt by remember {
        mutableStateOf(appContext.secureCredentials.remoteFrameUploadConsentGrantedAt())
    }
    var showRemoteAnalysisConsentDialog by rememberSaveable { mutableStateOf(false) }
    val runWithNotificationPermission = rememberNotificationPermissionGate()
    val runWithCameraPermission = rememberCameraPermissionGate()
    val permissionRequirements = rememberPermissionRequirements()
    val notificationPermissionNeeded = permissionRequirements.notification
    val cameraPermissionNeeded = permissionRequirements.camera
    val exactAlarmSettingsNeeded = permissionRequirements.exactAlarm
    val fullScreenIntentSettingsNeeded = permissionRequirements.fullScreenIntent
    val overlayPermissionNeeded = permissionRequirements.overlay
    val writeSettingsPermissionNeeded = permissionRequirements.writeSettings
    val shizukuNativeBrightnessEnabled = usesShizukuNativeBrightness(settings)
    val cloudSyncAllowed = planTier(settings) >= PlanTier.PLUS
    val backupImportPreview by viewModel.backupImportPreview.collectAsStateWithLifecycle()
    val backupImportError by viewModel.backupImportError.collectAsStateWithLifecycle()
    val remoteState by viewModel.remoteState.collectAsStateWithLifecycle()
    val backendConnectivityState by viewModel.backendConnectivityState.collectAsStateWithLifecycle()
    val backendFeaturesVisible = mainBackendUiDecision(backendConnectivityState, uiState.nowMillis).visible
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val settingsScrollState = LocalLumenPageScrollState.current
    val growthAnchorPositions = remember { mutableStateMapOf<GrowthConfigTarget, Int>() }
    val permissionAnchorPositions = remember { mutableStateMapOf<PermissionSetupTarget, Int>() }
    var activeGrowthConfigTarget by rememberSaveable { mutableStateOf<GrowthConfigTarget?>(null) }
    var growthReturnScrollPosition by rememberSaveable { mutableIntStateOf(0) }
    var showGrowthConfiguredDialog by rememberSaveable { mutableStateOf(false) }
    var activePermissionSetupTarget by rememberSaveable { mutableStateOf<PermissionSetupTarget?>(null) }
    var permissionReturnScrollPosition by rememberSaveable { mutableIntStateOf(0) }
    var pendingBackupImportUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var showProximityCalibrationDialog by rememberSaveable { mutableStateOf(false) }
    val proximityCalibrated = settings.proximityBaselineEyeDistancePx > 0f ||
        settings.proximityBaselineFaceWidthPercent > 0
    val proximityCaptureSeconds = settings.proximityCaptureSeconds.coerceIn(1, 2)
    fun requestReminderTimingPermissions() {
        when {
            needsExactAlarmSettings(context) -> openExactAlarmSettings(context)
            needsFullScreenIntentSettings(context) -> openFullScreenIntentSettings(context)
        }
    }
    fun scrollToPermissionTarget(target: PermissionSetupTarget, returnAfterCompletion: Boolean) {
        val scrollState = settingsScrollState ?: return
        activePermissionSetupTarget = if (returnAfterCompletion) target else null
        permissionReturnScrollPosition = scrollState.value
        permissionAnchorPositions[target]?.let { position ->
            coroutineScope.launch { scrollState.animateScrollTo(position) }
        }
    }
    fun isPermissionTargetConfigured(target: PermissionSetupTarget): Boolean {
        return when (target) {
            PermissionSetupTarget.STATISTICS -> settings.statsEnabled
            PermissionSetupTarget.USAGE_ACCESS -> !permissionRequirements.usageAccess
            PermissionSetupTarget.DIAGNOSTICS -> !backendFeaturesVisible || settings.diagnosticTelemetryUploadEnabled
            PermissionSetupTarget.NOTIFICATIONS -> settings.notificationEnabled && !notificationPermissionNeeded
            PermissionSetupTarget.EXACT_ALARM -> settings.notificationEnabled && !exactAlarmSettingsNeeded
            PermissionSetupTarget.FULL_SCREEN -> settings.notificationEnabled && !fullScreenIntentSettingsNeeded
            PermissionSetupTarget.KEEP_ALIVE -> settings.keepAliveEnabled
            PermissionSetupTarget.DISTANCE_CAMERA -> settings.proximityMonitoringEnabled && !cameraPermissionNeeded
            PermissionSetupTarget.BLINK_CAMERA -> settings.blinkMonitoringEnabled && !cameraPermissionNeeded
            PermissionSetupTarget.AMBIENT_LIGHT -> settings.ambientLightMonitoringEnabled
            PermissionSetupTarget.BRIGHTNESS -> settings.autoBrightnessEnabled &&
                (!writeSettingsPermissionNeeded || shizukuNativeBrightnessEnabled)
            PermissionSetupTarget.OVERLAY -> settings.globalOverlayEnabled && !overlayPermissionNeeded
            PermissionSetupTarget.SHIZUKU -> settings.shizukuAdvancedModeEnabled && shizukuState.ready
        }
    }
    fun startPermissionSetup(target: PermissionSetupTarget) {
        if (target == PermissionSetupTarget.DIAGNOSTICS && !backendFeaturesVisible) return
        if (isPermissionTargetConfigured(target)) {
            scrollToPermissionTarget(target, returnAfterCompletion = false)
            return
        }
        scrollToPermissionTarget(target, returnAfterCompletion = true)
        when (target) {
            PermissionSetupTarget.STATISTICS -> {
                viewModel.updateSettings { current -> current.copy(statsEnabled = true) }
            }
            PermissionSetupTarget.USAGE_ACCESS -> {
                openUsageAccessSettings(context)
            }
            PermissionSetupTarget.DIAGNOSTICS -> {
                viewModel.updateSettings { current ->
                    current.copy(
                        diagnosticTelemetryUploadEnabled = true,
                        shizukuAdvancedModeEnabled = true,
                    )
                }
                viewModel.requestShizukuAuthorization()
            }
            PermissionSetupTarget.NOTIFICATIONS -> {
                runWithNotificationPermission {
                    viewModel.setNotificationsEnabled(true)
                }
            }
            PermissionSetupTarget.EXACT_ALARM -> {
                runWithNotificationPermission {
                    viewModel.setNotificationsEnabled(true)
                    openExactAlarmSettings(context)
                }
            }
            PermissionSetupTarget.FULL_SCREEN -> {
                runWithNotificationPermission {
                    viewModel.setNotificationsEnabled(true)
                    openFullScreenIntentSettings(context)
                }
            }
            PermissionSetupTarget.KEEP_ALIVE -> {
                viewModel.setKeepAliveEnabled(true)
            }
            PermissionSetupTarget.DISTANCE_CAMERA -> {
                runWithCameraPermission {
                    viewModel.setProximityMonitoringEnabled(true)
                    if (!proximityCalibrated) showProximityCalibrationDialog = true
                }
            }
            PermissionSetupTarget.BLINK_CAMERA -> {
                runWithCameraPermission { viewModel.setBlinkMonitoringEnabled(true) }
            }
            PermissionSetupTarget.AMBIENT_LIGHT -> {
                viewModel.setAmbientLightMonitoringEnabled(true)
            }
            PermissionSetupTarget.BRIGHTNESS -> {
                viewModel.setAutoBrightnessEnabled(true)
                if (needsWriteSettingsPermission(context) && !shizukuNativeBrightnessEnabled) {
                    openWriteSettings(context)
                }
            }
            PermissionSetupTarget.OVERLAY -> {
                viewModel.updateSettings { current -> current.copy(globalOverlayEnabled = true) }
                if (needsOverlayPermission(context)) {
                    openOverlaySettings(context)
                }
            }
            PermissionSetupTarget.SHIZUKU -> {
                viewModel.updateSettings { current -> current.copy(shizukuAdvancedModeEnabled = true) }
                viewModel.requestShizukuAuthorization()
            }
        }
    }
    fun setPermissionTargetEnabled(target: PermissionSetupTarget, enabled: Boolean) {
        if (enabled) {
            startPermissionSetup(target)
            return
        }
        activePermissionSetupTarget = null
        when (target) {
            PermissionSetupTarget.STATISTICS -> {
                viewModel.updateSettings { current -> current.copy(statsEnabled = false) }
            }
            PermissionSetupTarget.DIAGNOSTICS -> {
                viewModel.updateSettings { current ->
                    current.copy(
                        diagnosticTelemetryUploadEnabled = false,
                        diagnosticCrashReportUploadEnabled = false,
                        diagnosticFaceAnalysisUploadEnabled = false,
                        shizukuAppInventoryUploadEnabled = false,
                    )
                }
            }
            PermissionSetupTarget.NOTIFICATIONS -> {
                viewModel.setNotificationsEnabled(false)
            }
            PermissionSetupTarget.KEEP_ALIVE -> {
                viewModel.setKeepAliveEnabled(false)
            }
            PermissionSetupTarget.DISTANCE_CAMERA -> {
                viewModel.setProximityMonitoringEnabled(false)
            }
            PermissionSetupTarget.BLINK_CAMERA -> {
                viewModel.setBlinkMonitoringEnabled(false)
            }
            PermissionSetupTarget.AMBIENT_LIGHT -> {
                viewModel.setAmbientLightMonitoringEnabled(false)
            }
            PermissionSetupTarget.BRIGHTNESS -> {
                viewModel.setAutoBrightnessEnabled(false)
            }
            PermissionSetupTarget.OVERLAY -> {
                viewModel.updateSettings { current -> current.copy(globalOverlayEnabled = false) }
            }
            PermissionSetupTarget.SHIZUKU -> {
                viewModel.updateSettings { current -> current.copy(shizukuAdvancedModeEnabled = false) }
            }
            PermissionSetupTarget.EXACT_ALARM,
            PermissionSetupTarget.FULL_SCREEN,
            PermissionSetupTarget.USAGE_ACCESS -> Unit
        }
    }
    fun scrollToGrowthTarget(target: GrowthConfigTarget) {
        if (target == GrowthConfigTarget.CLOUD && !backendFeaturesVisible) return
        val scrollState = settingsScrollState ?: return
        activeGrowthConfigTarget = target
        showGrowthConfiguredDialog = false
        growthReturnScrollPosition = scrollState.value
        growthAnchorPositions[target]?.let { position ->
            coroutineScope.launch { scrollState.animateScrollTo(position) }
        }
    }
    fun markGrowthApplyStarted(target: GrowthConfigTarget) {
        activeGrowthConfigTarget = target
        showGrowthConfiguredDialog = false
        growthReturnScrollPosition = settingsScrollState?.value ?: 0
    }
    fun isGrowthTargetConfigured(target: GrowthConfigTarget): Boolean {
        return when (target) {
            GrowthConfigTarget.REPORTS -> settings.statsEnabled
            GrowthConfigTarget.CLOUD -> remoteState.signedIn
            GrowthConfigTarget.FAMILY -> isFamilyEyeCareModeActive(uiState)
            GrowthConfigTarget.GUIDANCE -> settings.statsEnabled && settings.reminderEnabled
        }
    }
    LaunchedEffect(backendFeaturesVisible) {
        if (!backendFeaturesVisible) {
            if (activeGrowthConfigTarget == GrowthConfigTarget.CLOUD) {
                activeGrowthConfigTarget = null
                showGrowthConfiguredDialog = false
            }
            if (activePermissionSetupTarget == PermissionSetupTarget.DIAGNOSTICS) {
                activePermissionSetupTarget = null
            }
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
                if (targetUri != null) {
                    viewModel.importBackup(targetUri)
                    pendingBackupImportUri = null
                    viewModel.clearBackupImportPreview()
                }
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
    backupImportError?.let { message ->
        StatusLine(
            icon = Icons.Outlined.WarningAmber,
            text = stringResource(R.string.backup_import_failed, message),
        )
        OutlinedButton(
            onClick = { viewModel.clearBackupImportError() },
        ) {
            ButtonLabel(Icons.Outlined.Close, R.string.backup_import_dismiss)
        }
    }
    if (showGrowthConfiguredDialog) {
        AlertDialog(
            onDismissRequest = { showGrowthConfiguredDialog = false },
            title = { Text(stringResource(R.string.eye_care_guide_done)) },
            text = { Text(stringResource(R.string.eye_care_capability_active)) },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        showGrowthConfiguredDialog = false
                        activeGrowthConfigTarget = null
                        settingsScrollState?.let { scrollState ->
                            coroutineScope.launch {
                                scrollState.animateScrollTo(growthReturnScrollPosition)
                            }
                        }
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
    if (showRemoteAnalysisConsentDialog) {
        AlertDialog(
            onDismissRequest = { showRemoteAnalysisConsentDialog = false },
            title = { Text(stringResource(R.string.remote_analysis_consent_dialog_title)) },
            text = { Text(stringResource(R.string.remote_analysis_consent_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        appContext.secureCredentials.setRemoteFrameUploadConsent(granted = true)
                        remoteAnalysisConsentGrantedAt = System.currentTimeMillis()
                        showRemoteAnalysisConsentDialog = false
                    },
                ) {
                    Text(stringResource(R.string.remote_analysis_consent_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoteAnalysisConsentDialog = false }) {
                    Text(stringResource(R.string.generic_cancel))
                }
            },
        )
    }
    LaunchedEffect(
        activeGrowthConfigTarget,
        settings.statsEnabled,
        settings.reminderEnabled,
        settings.warnIntervalMinutes,
        settings.restDurationSeconds,
        settings.disableSkip,
        settings.timeoutAutoBreak,
        settings.proximityMonitoringEnabled,
        settings.blinkMonitoringEnabled,
        settings.ambientLightMonitoringEnabled,
        settings.globalOverlayEnabled,
        uiState.dailyGoal.maxContinuousWorkMinutes,
        remoteState.signedIn,
    ) {
        val target = activeGrowthConfigTarget ?: return@LaunchedEffect
        if (isGrowthTargetConfigured(target)) {
            showGrowthConfiguredDialog = true
        }
    }
    LaunchedEffect(
        activePermissionSetupTarget,
        settings.statsEnabled,
        settings.diagnosticTelemetryUploadEnabled,
        settings.notificationEnabled,
        settings.keepAliveEnabled,
        settings.proximityMonitoringEnabled,
        settings.blinkMonitoringEnabled,
        settings.ambientLightMonitoringEnabled,
        settings.autoBrightnessEnabled,
        settings.globalOverlayEnabled,
        settings.shizukuAdvancedModeEnabled,
        settings.shizukuNativeEyeProtectionEnabled,
        notificationPermissionNeeded,
        exactAlarmSettingsNeeded,
        fullScreenIntentSettingsNeeded,
        cameraPermissionNeeded,
        writeSettingsPermissionNeeded,
        overlayPermissionNeeded,
        permissionRequirements.usageAccess,
        shizukuState.ready,
    ) {
        val target = activePermissionSetupTarget ?: return@LaunchedEffect
        if (isPermissionTargetConfigured(target)) {
            activePermissionSetupTarget = null
            settingsScrollState?.animateScrollTo(permissionReturnScrollPosition)
        }
    }
    LaunchedEffect(settings.shizukuAdvancedModeEnabled) {
        if (settings.shizukuAdvancedModeEnabled) {
            viewModel.refreshShizukuState()
        }
    }
    val permissionSetupLifecycleOwner = LocalLifecycleOwner.current
    var permissionSetupResumeToken by remember { mutableIntStateOf(0) }
    DisposableEffect(permissionSetupLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionSetupResumeToken += 1
        }
        permissionSetupLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { permissionSetupLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(permissionSetupResumeToken) {
        val target = activePermissionSetupTarget ?: return@LaunchedEffect
        // Coming back from the system page without satisfying the target means the user gave up;
        // without this the row stays in its "returns automatically" guided state forever.
        if (permissionSetupResumeToken > 0 && !isPermissionTargetConfigured(target)) {
            activePermissionSetupTarget = null
        }
    }
    val templateAppearanceEnabled = remember(
        settings.useDynamicColors,
        uiState.templates,
        settings.activeTipTemplateId,
    ) {
        templateAppearanceLocksThemeMode(uiState)
    }
    val autoDarkWindowEnabled = settings.useAutoDarkWindow && !templateAppearanceEnabled
    val sectionGroupController = rememberSettingsSectionGroupController()
    CompositionLocalProvider(LocalSettingsSectionGroup provides sectionGroupController) {
    LumenPage {
        SettingsSectionToolbar(controller = sectionGroupController)
        SettingsScrollAnchor(
            target = PermissionSetupTarget.USAGE_ACCESS,
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
            SettingsPrivacyPermissionCenter(
                uiState = uiState,
                permissionRequirements = permissionRequirements,
                shizukuReady = shizukuState.ready,
                backendFeaturesVisible = backendFeaturesVisible,
                activeTarget = activePermissionSetupTarget,
                onConfigureTarget = ::startPermissionSetup,
                onTargetCheckedChange = ::setPermissionTargetEnabled,
            )
        }
        SettingsRemoteAnalysisConsentCard(
            grantedAt = remoteAnalysisConsentGrantedAt,
            onReviewConsent = { showRemoteAnalysisConsentDialog = true },
            onRevokeConsent = {
                appContext.secureCredentials.setRemoteFrameUploadConsent(granted = false)
                remoteAnalysisConsentGrantedAt = 0L
            },
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
        EyeCareGrowthCapabilityCard(
            uiState = uiState,
            remoteState = remoteState,
            cloudCapabilityVisible = backendFeaturesVisible,
            onOpenTemplates = openTemplates,
            onConfigureReports = { scrollToGrowthTarget(GrowthConfigTarget.REPORTS) },
            onConfigureCloud = { scrollToGrowthTarget(GrowthConfigTarget.CLOUD) },
            onConfigureFamilyMode = { scrollToGrowthTarget(GrowthConfigTarget.FAMILY) },
            onConfigureGuidance = { scrollToGrowthTarget(GrowthConfigTarget.GUIDANCE) },
            onSyncCloud = viewModel::syncRemoteNow,
            onApplyFamilyMode = {
                markGrowthApplyStarted(GrowthConfigTarget.FAMILY)
                applyFamilyEyeCareMode(viewModel)
            },
            onApplyGuidance = {
                markGrowthApplyStarted(GrowthConfigTarget.GUIDANCE)
                applyPersonalizedEyeCareGuidance(viewModel, uiState)
            },
            onExportReport = viewModel::shareMonthlyReportPdf,
        )
        SettingsScrollAnchors(
            targets = GeneralGrowthAnchors,
            scrollState = settingsScrollState,
            anchorPositions = growthAnchorPositions,
        ) {
        SettingsScrollAnchor(
            target = PermissionSetupTarget.STATISTICS,
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
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
                ThemeChip(R.string.theme_light, AppThemeMode.LIGHT, settings, viewModel, enabled = !templateAppearanceEnabled)
                ThemeChip(R.string.theme_dark, AppThemeMode.DARK, settings, viewModel, enabled = !templateAppearanceEnabled)
            }
            if (templateAppearanceEnabled) {
                Text(
                    stringResource(R.string.template_appearance_locks_theme_mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                settings.useAutoDarkWindow,
                enabled = !templateAppearanceEnabled,
            ) {
                viewModel.updateSettings { current -> current.copy(useAutoDarkWindow = it) }
            }
            AnimatedVisibility(
                visible = autoDarkWindowEnabled,
                enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 4 },
                exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 4 },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
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
            val clashContext = LocalContext.current
            var clashAutoAdapt by remember {
                mutableStateOf(ClashPartnerCompat.isAutoAdaptEnabled(clashContext))
            }
            var clashStatusLabel by remember {
                mutableStateOf(ClashPartnerCompat.statusLabel(clashContext))
            }
            DisposableEffect(clashContext) {
                val listener: (ClashPartnerCompat.Status) -> Unit = {
                    clashAutoAdapt = ClashPartnerCompat.isAutoAdaptEnabled(clashContext)
                    clashStatusLabel = ClashPartnerCompat.statusLabel(clashContext)
                }
                ClashPartnerCompat.addListener(listener)
                ClashPartnerCompat.refresh(clashContext)
                onDispose { ClashPartnerCompat.removeListener(listener) }
            }
            SwitchRow(
                R.string.clash_vpn_auto_adapt,
                Icons.Outlined.Lock,
                clashAutoAdapt,
            ) {
                clashAutoAdapt = it
                ClashPartnerCompat.setAutoAdaptEnabled(clashContext, it)
                clashStatusLabel = ClashPartnerCompat.statusLabel(clashContext)
            }
            Text(
                text = clashStatusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        }
        }
        SettingsReminderSection(settings = settings, viewModel = viewModel)
        SettingsPreAlertSection(settings = settings, viewModel = viewModel)
        SettingsPomodoroSection(settings = settings, viewModel = viewModel)
        SettingsQuietHoursSection(settings = settings, viewModel = viewModel)
        SettingsGoalsSection(dailyGoal = uiState.dailyGoal, viewModel = viewModel)
        SettingsScrollAnchors(
            targets = NotificationPermissionAnchors,
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
        SettingsSection(
            R.string.section_notifications,
            Icons.Outlined.NotificationsActive,
            forceExpanded = activePermissionSetupTarget == PermissionSetupTarget.NOTIFICATIONS ||
                activePermissionSetupTarget == PermissionSetupTarget.EXACT_ALARM ||
                activePermissionSetupTarget == PermissionSetupTarget.FULL_SCREEN,
        ) {
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
            // Always reachable: an entry point once granted, and the only way out once
            // the permission is permanently denied.
            OutlinedButton(onClick = {
                openAppNotificationSettings(context)
            }) {
                ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.notification_system_settings)
            }
        }
        }
        SettingsScrollAnchor(
            target = GrowthConfigTarget.FAMILY,
            scrollState = settingsScrollState,
            anchorPositions = growthAnchorPositions,
        ) {
        SettingsScrollAnchor(
            target = PermissionSetupTarget.DISTANCE_CAMERA,
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
        SettingsSection(
            R.string.section_proximity,
            Icons.Outlined.PhotoCamera,
            initiallyExpanded = false,
            forceExpanded = activePermissionSetupTarget == PermissionSetupTarget.DISTANCE_CAMERA,
        ) {
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
                Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
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
                    OutlinedButton(onClick = { openAppDetailsSettings(context) }) {
                        ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.open_system_settings)
                    }
                }
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
        }
        }
        SettingsScrollAnchors(
            targets = EyeProtectionPermissionAnchors,
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
        SettingsSection(
            R.string.section_eye_protection,
            Icons.Outlined.PhotoCamera,
            initiallyExpanded = false,
            forceExpanded = activePermissionSetupTarget == PermissionSetupTarget.BLINK_CAMERA ||
                activePermissionSetupTarget == PermissionSetupTarget.AMBIENT_LIGHT ||
                activePermissionSetupTarget == PermissionSetupTarget.BRIGHTNESS ||
                activePermissionSetupTarget == PermissionSetupTarget.OVERLAY,
        ) {
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
                Column(verticalArrangement = Arrangement.spacedBy(SettingsPreferenceItemGap)) {
                    NotificationRequirementCard(
                        titleRes = R.string.camera_permission_needed,
                        messageRes = R.string.camera_permission_needed_message,
                        actionLabelRes = R.string.allow_camera,
                        icon = Icons.Outlined.PhotoCamera,
                        onClick = { runWithCameraPermission { viewModel.setBlinkMonitoringEnabled(true) } },
                    )
                    OutlinedButton(onClick = { openAppDetailsSettings(context) }) {
                        ButtonLabel(Icons.AutoMirrored.Outlined.OpenInNew, R.string.open_system_settings)
                    }
                }
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
            NumberSlider(R.string.overlay_rest_duration, Icons.Outlined.Spa, settings.overlayRestDurationSeconds, 5f..120f, 22, stringResource(R.string.seconds_value, settings.overlayRestDurationSeconds)) {
                viewModel.updateSettings { current -> current.copy(overlayRestDurationSeconds = it) }
            }
            NumberSlider(R.string.overlay_strict_distance, Icons.Outlined.PhotoCamera, settings.overlayStrictDistancePercent, 120f..250f, 25, stringResource(R.string.percent_value, settings.overlayStrictDistancePercent)) {
                viewModel.updateSettings { current -> current.copy(overlayStrictDistancePercent = it) }
            }
        }
        }
        SettingsSoundSection(settings = settings, viewModel = viewModel)
        SettingsAppearanceSection(
            settings = settings,
            activeTemplate = template,
            templates = uiState.templates,
            viewModel = viewModel,
        )
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
        if (backendFeaturesVisible) {
            SettingsScrollAnchor(
                target = GrowthConfigTarget.CLOUD,
                scrollState = settingsScrollState,
                anchorPositions = growthAnchorPositions,
            ) {
                RemoteCloudAccountCard(
                    state = remoteState,
                    cloudSyncAllowed = cloudSyncAllowed,
                    onCheckHealth = viewModel::checkRemoteHealth,
                    onStartEmailLogin = viewModel::startRemoteEmailLogin,
                    onVerifyEmailLogin = viewModel::verifyRemoteEmailLogin,
                    onRefreshAccount = viewModel::refreshRemoteAccount,
                    onSyncNow = viewModel::syncRemoteNow,
                    onUploadBackup = viewModel::uploadCloudBackup,
                    onRestoreBackup = viewModel::restoreLatestCloudBackup,
                    onSignOut = viewModel::signOutRemote,
                )
            }
        }
        SettingsSection(R.string.about_update_status, Icons.Outlined.Sync, initiallyExpanded = false) {
            if (checkingUpdate) {
                StatusLine(Icons.Outlined.Sync, stringResource(R.string.about_update_checking))
            } else {
                OutlinedButton(onClick = onManualUpdateCheck) {
                    ButtonLabel(Icons.Outlined.Sync, R.string.about_check_updates)
                }
            }
        }
        SettingsScrollAnchor(
            target = PermissionSetupTarget.KEEP_ALIVE,
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
        SettingsSection(
            R.string.section_keep_alive,
            Icons.Outlined.Schedule,
            initiallyExpanded = false,
            forceExpanded = activePermissionSetupTarget == PermissionSetupTarget.KEEP_ALIVE,
        ) {
            SwitchRow(R.string.enable_keep_alive, Icons.Outlined.Schedule, settings.keepAliveEnabled) {
                viewModel.setKeepAliveEnabled(it)
            }
        }
        }
        SettingsScrollAnchors(
            targets = if (backendFeaturesVisible) {
                DiagnosticsAndShizukuPermissionAnchors
            } else {
                ShizukuPermissionAnchors
            },
            scrollState = settingsScrollState,
            anchorPositions = permissionAnchorPositions,
        ) {
        ShizukuAdvancedSettingsSection(
            settings = settings,
            state = shizukuState,
            viewModel = viewModel,
            backendFeaturesVisible = backendFeaturesVisible,
            forceExpanded = activePermissionSetupTarget == PermissionSetupTarget.SHIZUKU ||
                (backendFeaturesVisible && activePermissionSetupTarget == PermissionSetupTarget.DIAGNOSTICS),
        )
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
}

@Composable
private fun SettingsRemoteAnalysisConsentCard(
    grantedAt: Long,
    onReviewConsent: () -> Unit,
    onRevokeConsent: () -> Unit,
) {
    val granted = grantedAt > 0L
    val grantedTimeText = remember(grantedAt) { formatRemoteConsentTime(grantedAt) }
    SettingsSection(
        titleRes = R.string.remote_analysis_consent_title,
        icon = Icons.Outlined.PhotoCamera,
        initiallyExpanded = true,
    ) {
        Text(
            stringResource(R.string.remote_analysis_consent_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusLine(
            icon = if (granted) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
            text = if (granted) {
                stringResource(R.string.remote_analysis_consent_granted_at, grantedTimeText)
            } else {
                stringResource(R.string.remote_analysis_consent_not_granted)
            },
        )
        if (granted) {
            OutlinedButton(
                onClick = onRevokeConsent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonLabel(Icons.Outlined.Close, R.string.remote_analysis_consent_revoke)
            }
        } else {
            Button(
                onClick = onReviewConsent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ButtonLabel(Icons.Outlined.Lock, R.string.remote_analysis_consent_review)
            }
        }
    }
}

private fun formatRemoteConsentTime(epochMillis: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrNull().orEmpty()


