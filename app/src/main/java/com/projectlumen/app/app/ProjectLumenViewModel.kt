package com.projectlumen.app.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectlumen.app.core.api.ProjectLumenApiClient
import com.projectlumen.app.core.api.ProjectLumenApiDiagnostics
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PlanTier
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.DataBackupService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.security.SecureCredentialStore
import com.projectlumen.app.core.shizuku.ShizukuCapabilityManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ProjectLumenViewModel(
    database: AppDatabase,
    notifications: NotificationService,
    audio: AudioService,
    export: ExportService,
    backup: DataBackupService,
    apiClient: ProjectLumenApiClient,
    secureCredentials: SecureCredentialStore,
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
) : ViewModel() {
    private val repositories = ProjectLumenRepositories(database, eyeCarePreferences, secureCredentials)
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val stateStore = ProjectLumenStateStore(repositories, viewModelScope, now)
    private val runtimeEntry = ProjectLumenRuntimeFeatureEntry(
        scope = viewModelScope,
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
        scope = viewModelScope,
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
    private val templatesEntry = ProjectLumenTemplatesFeatureEntry(
        scope = viewModelScope,
        settingsRepository = repositories.settings,
        tipTemplateRepository = repositories.tipTemplates,
    )
    private val sharingEntry = ProjectLumenSharingFeatureEntry(
        export = export,
        stateProvider = { stateStore.uiState.value },
    )
    private val backupEntry = ProjectLumenBackupFeatureEntry(
        scope = viewModelScope,
        backup = backup,
        settingsRepository = repositories.settings,
        runtimeEntry = runtimeEntry,
    )
    private val entitlementEntry = ProjectLumenEntitlementFeatureEntry(
        scope = viewModelScope,
        settingsRepository = repositories.settings,
        entitlementRepository = repositories.entitlements,
    )
    private val remoteEntry = ProjectLumenRemoteFeatureEntry(
        scope = viewModelScope,
        apiClient = apiClient,
        credentials = secureCredentials,
        backup = backup,
        settingsRepository = repositories.settings,
        entitlementRepository = repositories.entitlements,
        featureFlagRepository = repositories.featureFlags,
    )
    private val _webPageRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val webPageRequests = _webPageRequests.asSharedFlow()
    val backupImportPreview = backupEntry.importPreview
    internal val remoteState = remoteEntry.state
    val shizukuState = shizuku.state
    val apiDiagnostics = ProjectLumenApiDiagnostics.traces
    val uiState = stateStore.uiState

    init {
        viewModelScope.launch {
            repositories.settings.ensureDefault()
            repositories.runtime.ensureDefault()
            repositories.dailyGoals.ensureDefault()
            templatesEntry.seedDefaultTemplates()
            runtimeEntry.restoreFromClock()
            val settings = repositories.settings.getOrDefault()
            settingsEntry.applyStartupMonitoring(settings)
            runtimeEntry.refreshActiveNotifications(settings, repositories.runtime.getOrDefault())
            runCatching { uploadTelemetrySnapshot() }
        }
        runtimeEntry.startClock(now)
    }

    fun navigateWebPage(url: String) {
        _webPageRequests.tryEmit(url)
    }

    fun startReminder() = runtimeEntry.startReminder()
    fun pauseReminder() = runtimeEntry.pauseReminder()
    fun pauseForOneHour() = runtimeEntry.pauseForOneHour()
    fun resumeReminder() = runtimeEntry.resumeReminder()
    fun stopAll() = runtimeEntry.stopAll()
    fun startBreak() = runtimeEntry.startBreak()
    fun skipBreak() = runtimeEntry.skipBreak()
    fun startPomodoro() = runtimeEntry.startPomodoro()
    fun stopPomodoro() = runtimeEntry.stopPomodoro()

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis, transform)
        settingsEntry.updateSettings(transform, nowMillis)
    }

    fun setReminderEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(reminderEnabled = enabled) }
        settingsEntry.setReminderEnabled(enabled, nowMillis)
    }

    fun setPomodoroEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(pomodoroEnabled = enabled) }
        settingsEntry.setPomodoroEnabled(enabled, nowMillis)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(notificationEnabled = enabled) }
        settingsEntry.setNotificationsEnabled(enabled, nowMillis)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(keepAliveEnabled = enabled) }
        settingsEntry.setKeepAliveEnabled(enabled, nowMillis)
    }

    fun setProximityMonitoringEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(proximityMonitoringEnabled = enabled) }
        settingsEntry.setProximityMonitoringEnabled(enabled, nowMillis)
    }

    fun setBlinkMonitoringEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(blinkMonitoringEnabled = enabled) }
        settingsEntry.setBlinkMonitoringEnabled(enabled, nowMillis)
    }

    fun setAmbientLightMonitoringEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(ambientLightMonitoringEnabled = enabled) }
        settingsEntry.setAmbientLightMonitoringEnabled(enabled, nowMillis)
    }

    fun setAutoBrightnessEnabled(enabled: Boolean) {
        val nowMillis = System.currentTimeMillis()
        previewSettings(nowMillis) { it.copy(autoBrightnessEnabled = enabled) }
        settingsEntry.setAutoBrightnessEnabled(enabled, nowMillis)
    }

    fun calibrateProximity() = settingsEntry.calibrateProximity()

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
    fun simulateLowMemory() = simulateDeveloperLowMemory()
    fun refreshShizukuState() {
        shizuku.refreshState()
        viewModelScope.launch {
            val settings = stateStore.uiState.value.settings
            shizuku.refreshForegroundContext()
            shizuku.refreshSystemContext(settings)
        }
    }
    fun requestShizukuAuthorization() = shizuku.requestPermission()
    fun uploadDiagnosticsNow() {
        viewModelScope.launch {
            runCatching { uploadTelemetrySnapshot() }
        }
    }

    fun selectTemplate(templateId: Long) {
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

    fun shareStatistics() = sharingEntry.shareStatistics()
    fun shareStatisticsImage() = sharingEntry.shareStatisticsImage()
    fun shareMonthlyReportPdf() = sharingEntry.shareMonthlyReportPdf()
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
}
