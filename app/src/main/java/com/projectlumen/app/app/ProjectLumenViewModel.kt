package com.projectlumen.app.app

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectlumen.app.core.api.ProjectLumenApiClient
import com.projectlumen.app.core.api.ProjectLumenApiDiagnostics
import com.chloemlla.lumen.crash.CrashBreadcrumbs
import com.chloemlla.lumen.crash.CrashReport
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppNetworkControlEntity
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.debug.MemoryHealthMonitor
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.DataBackupService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.security.DeviceInstallProfile
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import com.projectlumen.app.core.shizuku.ShizukuNetworkApp
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectLumenViewModel(
    private val database: AppDatabase,
    notifications: NotificationService,
    audio: AudioService,
    export: ExportService,
    backup: DataBackupService,
    apiClient: ProjectLumenApiClient,
    private val secureCredentials: SecureCredentialStore,
    eyeCarePreferences: EyeCarePreferencesDataStore,
    startTimerService: () -> Unit,
    stopTimerService: () -> Unit,
    scheduleProximityMonitoring: () -> Unit,
    cancelProximityMonitoring: () -> Unit,
    calibrateProximityMonitoring: () -> Unit,
    startLightMonitoring: () -> Unit,
    stopLightMonitoring: () -> Unit,
    startDeveloperDebugService: () -> Unit,
    stopDeveloperDebugService: () -> Unit,
    startShizukuResilience: () -> Unit,
    stopShizukuResilience: () -> Unit,
    private val shizuku: ShizukuCapabilityManager,
    private val simulateDeveloperLowMemory: () -> Unit,
    private val uploadTelemetrySnapshot: suspend () -> Unit,
    private val recordCrashReport: (Throwable) -> CrashReport?,
) : ViewModel() {
    private val repositories = ProjectLumenRepositories(database, eyeCarePreferences, secureCredentials)
    private val now = MutableStateFlow(System.currentTimeMillis())
    private var crashStateStore: ProjectLumenStateStore? = null
    private val installProfile = runCatching { secureCredentials.installProfile() }
        .onFailure { Log.e(TAG, "installProfile failed in ViewModel", it) }
        .getOrElse {
            DeviceInstallProfile(
                hadDeviceCredentialBeforeLaunch = false,
                firstSeenAt = System.currentTimeMillis(),
                packageFirstInstallAt = 0L,
                onboardingCompletedAt = 0L,
            )
        }
    private val deviceFingerprint = runCatching { secureCredentials.deviceInstallationId() }
        .onFailure { Log.e(TAG, "deviceInstallationId failed in ViewModel", it) }
        .getOrDefault("unknown-device")
    private val crashReportingHandler = CoroutineExceptionHandler { _, throwable ->
        crashStateStore?.recordCrash(throwable) ?: recordCrashReport(throwable)
    }
    private val reportingScope = CoroutineScope(viewModelScope.coroutineContext + crashReportingHandler)
    private val stateStore = ProjectLumenStateStore(
        repositories = repositories,
        scope = reportingScope,
        now = now,
        recordCrashReport = recordCrashReport,
    ).also { crashStateStore = it }
    private val runtimeEntry = ProjectLumenRuntimeFeatureEntry(
        scope = reportingScope,
        settingsRepository = repositories.settings,
        runtimeRepository = repositories.runtime,
        statisticsRepository = repositories.statistics,
        notifications = notifications,
        audio = audio,
        startTimerService = startTimerService,
        stopTimerService = stopTimerService,
        uploadTelemetrySnapshot = uploadTelemetrySnapshot,
    )
    private val settingsEntry = ProjectLumenSettingsFeatureEntry(
        scope = reportingScope,
        settingsRepository = repositories.settings,
        runtimeRepository = repositories.runtime,
        dailyGoalsRepository = repositories.dailyGoals,
        runtimeEntry = runtimeEntry,
        notifications = notifications,
        stopTimerService = stopTimerService,
        scheduleProximityMonitoring = scheduleProximityMonitoring,
        cancelProximityMonitoring = cancelProximityMonitoring,
        calibrateProximityMonitoring = calibrateProximityMonitoring,
        startLightMonitoring = startLightMonitoring,
        stopLightMonitoring = stopLightMonitoring,
        startDeveloperDebugService = startDeveloperDebugService,
        stopDeveloperDebugService = stopDeveloperDebugService,
        startShizukuResilience = startShizukuResilience,
        stopShizukuResilience = stopShizukuResilience,
        shizuku = shizuku,
    )
    private val appNetworkControlEntry = ProjectLumenAppNetworkControlFeatureEntry(
        scope = reportingScope,
        repository = repositories.appNetworkControls,
        shizuku = shizuku,
    )
    private val templatesEntry = ProjectLumenTemplatesFeatureEntry(
        scope = reportingScope,
        settingsRepository = repositories.settings,
        tipTemplateRepository = repositories.tipTemplates,
    )
    private val sharingEntry = ProjectLumenSharingFeatureEntry(
        export = export,
        stateProvider = { stateStore.uiState.value },
    )
    private val backupEntry = ProjectLumenBackupFeatureEntry(
        scope = reportingScope,
        backup = backup,
        settingsRepository = repositories.settings,
        runtimeEntry = runtimeEntry,
    )
    private val entitlementEntry = ProjectLumenEntitlementFeatureEntry(
        scope = reportingScope,
        settingsRepository = repositories.settings,
        entitlementRepository = repositories.entitlements,
    )
    private val remoteEntry = ProjectLumenRemoteFeatureEntry(
        scope = reportingScope,
        apiClient = apiClient,
        credentials = secureCredentials,
        backup = backup,
        settingsRepository = repositories.settings,
        entitlementRepository = repositories.entitlements,
        featureFlagRepository = repositories.featureFlags,
        tipTemplateRepository = repositories.tipTemplates,
    )
    private val _webPageRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    private val _onboardingState = MutableStateFlow(
        ProjectLumenOnboardingState(deviceFingerprint = deviceFingerprint),
    )

    val webPageRequests = _webPageRequests.asSharedFlow()
    val onboardingState = _onboardingState.asStateFlow()
    val backupImportPreview = backupEntry.importPreview
    internal val remoteState = remoteEntry.state
    val shizukuState = shizuku.state
    val shizukuNetworkApps = appNetworkControlEntry.networkApps
    val appNetworkControlRecords = appNetworkControlEntry.records
    val apiDiagnostics = ProjectLumenApiDiagnostics.traces
    val memoryHealth = MemoryHealthMonitor.snapshot
    val uiState = stateStore.uiState

    init {
        runCatching { CrashBreadcrumbs.record("ProjectLumenViewModel.init") }
        reportingScope.launch {
            runCatching {
                val hadExistingLocalUse = hasExistingLocalUse()
                repositories.settings.ensureDefault()
                repositories.runtime.ensureDefault()
                repositories.dailyGoals.ensureDefault()
                templatesEntry.seedDefaultTemplates()
                runtimeEntry.restoreFromClock()
                val settings = repositories.settings.getOrDefault()
                settingsEntry.applyStartupMonitoring(settings)
                runtimeEntry.refreshActiveNotifications(settings, repositories.runtime.getOrDefault())
                refreshOnboardingState(hadExistingLocalUse)
                runCatching { uploadTelemetrySnapshot() }
            }.onFailure { error ->
                Log.e(TAG, "ViewModel startup work failed", error)
                recordCrashReport(error)
            }
        }
        runCatching { runtimeEntry.startClock(now) }
            .onFailure { Log.e(TAG, "startClock failed", it) }
    }

    fun navigateWebPage(url: String) {
        _webPageRequests.tryEmit(url)
    }

    fun startReminder() = traceAction("startReminder") { runtimeEntry.startReminder() }
    fun pauseReminder() = traceAction("pauseReminder") { runtimeEntry.pauseReminder() }
    fun pauseForOneHour() = traceAction("pauseForOneHour") { runtimeEntry.pauseForOneHour() }
    fun resumeReminder() = traceAction("resumeReminder") { runtimeEntry.resumeReminder() }
    fun stopAll() = traceAction("stopAll") { runtimeEntry.stopAll() }
    fun startBreak() = traceAction("startBreak") { runtimeEntry.startBreak() }
    fun skipBreak() = traceAction("skipBreak") { runtimeEntry.skipBreak() }
    fun startPomodoro() = traceAction("startPomodoro") { runtimeEntry.startPomodoro() }
    fun stopPomodoro() = traceAction("stopPomodoro") { runtimeEntry.stopPomodoro() }

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        CrashBreadcrumbs.record("Action updateSettings")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis, transform)
        settingsEntry.updateSettings(transform, nowMillis)
    }

    fun setReminderEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setReminderEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(reminderEnabled = enabled) }
        settingsEntry.setReminderEnabled(enabled, nowMillis)
    }

    fun setPomodoroEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setPomodoroEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(pomodoroEnabled = enabled) }
        settingsEntry.setPomodoroEnabled(enabled, nowMillis)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setNotificationsEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(notificationEnabled = enabled) }
        settingsEntry.setNotificationsEnabled(enabled, nowMillis)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setKeepAliveEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(keepAliveEnabled = enabled) }
        settingsEntry.setKeepAliveEnabled(enabled, nowMillis)
    }

    fun setProximityMonitoringEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setProximityMonitoringEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(proximityMonitoringEnabled = enabled) }
        settingsEntry.setProximityMonitoringEnabled(enabled, nowMillis)
    }

    fun setBlinkMonitoringEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setBlinkMonitoringEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(blinkMonitoringEnabled = enabled) }
        settingsEntry.setBlinkMonitoringEnabled(enabled, nowMillis)
    }

    fun setAmbientLightMonitoringEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setAmbientLightMonitoringEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(ambientLightMonitoringEnabled = enabled) }
        settingsEntry.setAmbientLightMonitoringEnabled(enabled, nowMillis)
    }

    fun setAutoBrightnessEnabled(enabled: Boolean) {
        CrashBreadcrumbs.record("Action setAutoBrightnessEnabled=$enabled")
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(autoBrightnessEnabled = enabled) }
        settingsEntry.setAutoBrightnessEnabled(enabled, nowMillis)
    }

    fun calibrateProximity() = reportIfThrows {
        settingsEntry.calibrateProximity()
    }

    fun setLanguageCode(languageCode: String) {
        val nowMillis = System.currentTimeMillis()
        val normalized = LocaleController.normalize(languageCode)
        previewSettings(nowMillis) { it.copy(languageCode = normalized) }
        settingsEntry.setLanguageCode(languageCode, nowMillis)
    }

    fun setThemeMode(mode: AppThemeMode) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(themeMode = mode.name) }
        settingsEntry.setThemeMode(mode, nowMillis)
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(autoUpdateCheckEnabled = enabled) }
        settingsEntry.setAutoUpdateCheckEnabled(enabled, nowMillis)
    }

    fun updateDailyGoal(transform: (DailyGoalEntity) -> DailyGoalEntity) = settingsEntry.updateDailyGoal(transform)
    fun simulateLowMemory() = reportIfThrows {
        simulateDeveloperLowMemory()
    }
    fun refreshShizukuState() {
        shizuku.refreshState()
        reportingScope.launch {
            val settings = stateStore.uiState.value.settings
            shizuku.refreshForegroundContext()
            shizuku.refreshSystemContext(settings)
        }
    }
    fun requestShizukuAuthorization() = reportIfThrows {
        shizuku.requestPermission()
    }
    fun refreshShizukuNetworkApps() = appNetworkControlEntry.refreshApps()
    fun restrictAppNetwork(app: ShizukuNetworkApp) = appNetworkControlEntry.restrictApp(app)
    fun restoreAppNetwork(record: AppNetworkControlEntity) = appNetworkControlEntry.restoreApp(record)
    fun uploadDiagnosticsNow() {
        reportingScope.launch {
            runCatching { uploadTelemetrySnapshot() }
                .onFailure(stateStore::recordCrash)
        }
    }

    fun clearCrashReport() {
        stateStore.clearCrashReport()
    }

    fun completeOnboarding(applyRecommendedSetup: Boolean) {
        secureCredentials.markOnboardingCompleted()
        _onboardingState.value = _onboardingState.value.copy(visible = false)
        if (!applyRecommendedSetup) return
        updateSettings { current ->
            recommendedEyeCareSettings(current).copy(pomodoroEnabled = true)
        }
        updateDailyGoal(::recommendedEyeCareDailyGoal)
    }

    fun selectTemplate(templateId: Long) {
        CrashBreadcrumbs.record("Action selectTemplate id=$templateId")
        val state = stateStore.uiState.value
        val template = state.templates.firstOrNull { it.id == templateId } ?: return
        if (template.isPremium && planTier(state.settings) < PlanTier.PRO) return

        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(activeTipTemplateId = templateId) }
        templatesEntry.selectTemplate(templateId, nowMillis)
    }
    fun updateTemplateSystemBackground(template: TipTemplateEntity, backgroundValue: String, primaryColor: String) =
        templatesEntry.updateTemplateSystemBackground(template, backgroundValue, primaryColor)
    fun updateTemplateImage(template: TipTemplateEntity, imagePath: String) = templatesEntry.updateTemplateImage(template, imagePath)
    fun updateTemplateContent(
        template: TipTemplateEntity,
        titleText: String,
        subtitleText: String,
        showSkipButton: Boolean,
    ) = templatesEntry.updateTemplateContent(template, titleText, subtitleText, showSkipButton)
    fun updateTemplateCountdownStyle(template: TipTemplateEntity, countdownStyle: String) =
        templatesEntry.updateTemplateCountdownStyle(template, countdownStyle)

    fun shareStatistics() = reportIfThrows {
        sharingEntry.shareStatistics()
    }
    fun shareStatisticsImage() = reportIfThrows {
        sharingEntry.shareStatisticsImage()
    }
    fun shareMonthlyReportPdf() = reportIfThrows {
        sharingEntry.shareMonthlyReportPdf()
    }
    fun shareBackup() = backupEntry.shareBackup()
    fun previewBackupImport(uri: Uri) = backupEntry.previewBackupImport(uri)
    fun clearBackupImportPreview() = backupEntry.clearBackupImportPreview()
    fun importBackup(uri: Uri) = backupEntry.importBackup(uri)

    fun recordManualProEntitlement(productId: String = "manual_pro") = entitlementEntry.recordManualProEntitlement(productId)
    fun checkRemoteHealth() = remoteEntry.checkHealth()
    fun clearApiDiagnostics() = ProjectLumenApiDiagnostics.clear()
    fun startRemoteEmailLogin(email: String) = remoteEntry.startEmailLogin(email)
    fun verifyRemoteEmailLogin(code: String) = remoteEntry.verifyEmailLogin(code)
    fun refreshRemoteAccount() = remoteEntry.refreshAccount()
    fun syncRemoteNow() = remoteEntry.syncNow()
    fun uploadCloudBackup() = remoteEntry.uploadCloudBackup()
    fun restoreLatestCloudBackup() = remoteEntry.restoreLatestCloudBackup()
    fun verifyGooglePurchase(productId: String, purchaseToken: String) =
        remoteEntry.verifyGooglePurchase(productId, purchaseToken)
    fun signOutRemote() = remoteEntry.signOut()

    private fun previewSettings(
        nowMillis: Long,
        transform: (AppSettingsEntity) -> AppSettingsEntity,
    ) {
        val current = stateStore.uiState.value.settings
        val updated = normalizeTemplateAppearanceSettings(transform(current))
        stateStore.previewSettings(updated.copy(id = 1, updatedAt = nowMillis))
    }

    private fun normalizeTemplateAppearanceSettings(settings: AppSettingsEntity): AppSettingsEntity {
        if (settings.useDynamicColors) return settings
        return settings.copy(
            themeMode = AppThemeMode.LIGHT.name,
            useAutoDarkWindow = false,
        )
    }

    private suspend fun hasExistingLocalUse(): Boolean {
        val hasSettings = repositories.settings.get() != null
        val hasEyeStats = database.dailyEyeStatsDao().getAll().isNotEmpty()
        val hasPomodoroStats = database.dailyPomodoroStatsDao().getAll().isNotEmpty()
        val hasRemoteSession = secureCredentials.load() != null
        return hasSettings || hasEyeStats || hasPomodoroStats || hasRemoteSession
    }

    private fun refreshOnboardingState(hadExistingLocalUse: Boolean) {
        val nowMillis = System.currentTimeMillis()
        val firstInstallAt = installProfile.packageFirstInstallAt
        val freshPackageInstall = firstInstallAt <= 0L ||
            nowMillis - firstInstallAt <= FRESH_INSTALL_WINDOW_MILLIS
        val newInstallDetected = !installProfile.hadDeviceCredentialBeforeLaunch &&
            (freshPackageInstall || installProfile.firstSeenAt >= nowMillis - FIRST_SEEN_GRACE_MILLIS)
        val shouldShow = installProfile.onboardingCompletedAt <= 0L &&
            !installProfile.hadDeviceCredentialBeforeLaunch &&
            !hadExistingLocalUse
        _onboardingState.value = ProjectLumenOnboardingState(
            visible = shouldShow,
            deviceFingerprint = deviceFingerprint,
            newInstallDetected = newInstallDetected,
        )
    }

    private inline fun reportIfThrows(block: () -> Unit) {
        runCatching(block).onFailure(stateStore::recordCrash)
    }

    private inline fun traceAction(name: String, block: () -> Unit) {
        CrashBreadcrumbs.record("Action $name")
        block()
    }

    private companion object {
        private const val TAG = "ProjectLumenViewModel"
        private const val FRESH_INSTALL_WINDOW_MILLIS = 3L * 24L * 60L * 60L * 1_000L
        private const val FIRST_SEEN_GRACE_MILLIS = 5L * 60L * 1_000L
    }
}
