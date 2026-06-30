package com.projectlumen.app.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyGoalEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.preferences.EyeCarePreferencesDataStore
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.DataBackupService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
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
    private val repositories = ProjectLumenRepositories(database, eyeCarePreferences)
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
    private val _webPageRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val webPageRequests = _webPageRequests.asSharedFlow()
    val backupImportPreview = backupEntry.importPreview
    val shizukuState = shizuku.state
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
            uploadTelemetrySnapshot()
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

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) = settingsEntry.updateSettings(transform)
    fun setReminderEnabled(enabled: Boolean) = settingsEntry.setReminderEnabled(enabled)
    fun setPomodoroEnabled(enabled: Boolean) = settingsEntry.setPomodoroEnabled(enabled)
    fun setNotificationsEnabled(enabled: Boolean) = settingsEntry.setNotificationsEnabled(enabled)
    fun setKeepAliveEnabled(enabled: Boolean) = settingsEntry.setKeepAliveEnabled(enabled)
    fun setProximityMonitoringEnabled(enabled: Boolean) = settingsEntry.setProximityMonitoringEnabled(enabled)
    fun setBlinkMonitoringEnabled(enabled: Boolean) = settingsEntry.setBlinkMonitoringEnabled(enabled)
    fun setAmbientLightMonitoringEnabled(enabled: Boolean) = settingsEntry.setAmbientLightMonitoringEnabled(enabled)
    fun setAutoBrightnessEnabled(enabled: Boolean) = settingsEntry.setAutoBrightnessEnabled(enabled)
    fun calibrateProximity() = settingsEntry.calibrateProximity()
    fun setLanguageCode(languageCode: String) = settingsEntry.setLanguageCode(languageCode)
    fun setThemeMode(mode: AppThemeMode) = settingsEntry.setThemeMode(mode)
    fun setAutoUpdateCheckEnabled(enabled: Boolean) = settingsEntry.setAutoUpdateCheckEnabled(enabled)
    fun updateDailyGoal(transform: (DailyGoalEntity) -> DailyGoalEntity) = settingsEntry.updateDailyGoal(transform)
    fun simulateLowMemory() = simulateDeveloperLowMemory()
    fun refreshShizukuState() {
        shizuku.refreshState()
        viewModelScope.launch { shizuku.refreshForegroundContext() }
    }
    fun requestShizukuAuthorization() = shizuku.requestPermission()

    fun selectTemplate(templateId: Long) = templatesEntry.selectTemplate(templateId)
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
}
