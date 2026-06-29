package com.projectlumen.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.i18n.LocaleController
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.repositories.TipTemplateRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.PomodoroEngine
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class ProjectLumenViewModel(
    private val database: AppDatabase,
    private val notifications: NotificationService,
    private val audio: AudioService,
    private val export: ExportService,
    private val startTimerService: () -> Unit,
    private val stopTimerService: () -> Unit,
    private val scheduleProximityMonitoring: () -> Unit,
    private val cancelProximityMonitoring: () -> Unit,
    private val calibrateProximityMonitoring: () -> Unit,
) : ViewModel() {
    private val settingsRepository = SettingsRepository(database.appSettingsDao())
    private val runtimeRepository = RuntimeRepository(database.runtimeStateDao())
    private val statisticsRepository = StatisticsRepository(
        database.dailyEyeStatsDao(),
        database.dailyPomodoroStatsDao(),
    )
    private val tipTemplateRepository = TipTemplateRepository(database.tipTemplatesDao())
    private val reminderEngine = ReminderEngine()
    private val pomodoroEngine = PomodoroEngine()
    private val now = MutableStateFlow(System.currentTimeMillis())
    private val _webPageRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val webPageRequests = _webPageRequests.asSharedFlow()

    private val dataState = combine(
        settingsRepository.observe(),
        runtimeRepository.observe(),
        statisticsRepository.observeEyeStats(),
        statisticsRepository.observePomodoroStats(),
        tipTemplateRepository.observeAll(),
    ) { settings, runtime, eyeStats, pomodoroStats, templates ->
        ProjectLumenUiState(
            settings = settings ?: AppSettingsEntity(),
            runtime = runtime ?: RuntimeStateEntity(),
            eyeStats = eyeStats,
            pomodoroStats = pomodoroStats,
            templates = templates,
            isReady = settings != null && runtime != null,
        )
    }

    val uiState = combine(dataState, now) { state, nowMillis ->
        state.copy(nowMillis = nowMillis)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProjectLumenUiState(),
    )

    init {
        viewModelScope.launch {
            settingsRepository.ensureDefault()
            runtimeRepository.ensureDefault()
            seedTemplates()
            restoreFromClock()
            val settings = settingsRepository.getOrDefault()
            if (settings.proximityMonitoringEnabled) scheduleProximityMonitoring()
            refreshActiveNotifications(settings, runtimeRepository.getOrDefault())
        }
        viewModelScope.launch {
            while (true) {
                val current = System.currentTimeMillis()
                now.value = current
                advanceDuePhases(current)
                delay(1_000)
            }
        }
    }

    fun navigateWebPage(url: String) {
        _webPageRequests.tryEmit(url)
    }

    fun startReminder() {
        viewModelScope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = reminderEngine.newWorkingState(settings, nowMillis)
            runtimeRepository.upsert(state)
            refreshActiveNotifications(settings, state)
        }
    }

    fun pauseReminder() {
        viewModelScope.launch {
            val state = runtimeRepository.getOrDefault()
            if (state.activeEngine != ActiveEngine.REMINDER.name || state.reminderPhase == ReminderPhase.IDLE.name) return@launch
            notifications.cancelAllScheduled()
            stopTimerService()
            val nextState = reminderEngine.pause(state, System.currentTimeMillis())
            runtimeRepository.upsert(nextState)
            refreshActiveNotifications(settingsRepository.getOrDefault(), nextState)
        }
    }

    fun pauseForOneHour() {
        viewModelScope.launch {
            val state = runtimeRepository.getOrDefault()
            if (state.activeEngine != ActiveEngine.REMINDER.name || state.reminderPhase == ReminderPhase.IDLE.name) return@launch
            val nowMillis = System.currentTimeMillis()
            notifications.cancelAllScheduled()
            val nextState = reminderEngine.pauseForOneHour(state, nowMillis)
            runtimeRepository.upsert(nextState)
            refreshActiveNotifications(settingsRepository.getOrDefault(), nextState)
        }
    }

    fun resumeReminder() {
        viewModelScope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = reminderEngine.newWorkingState(settings, nowMillis)
            runtimeRepository.upsert(state)
            refreshActiveNotifications(settings, state)
        }
    }

    fun stopAll() {
        viewModelScope.launch {
            runtimeRepository.reset(System.currentTimeMillis())
            notifications.cancelAllScheduled()
            notifications.cancelOngoingStatus()
            stopTimerService()
        }
    }

    fun startBreak() {
        viewModelScope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val transition = reminderEngine.startBreak(settings, state, nowMillis)
            applyTransition(settings, nowMillis, transition)
        }
    }

    fun skipBreak() {
        viewModelScope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val transition = reminderEngine.skipBreak(settings, state, nowMillis)
            applyTransition(settings, nowMillis, transition)
        }
    }

    fun startPomodoro() {
        viewModelScope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.pomodoroEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val transition = pomodoroEngine.start(settings, nowMillis)
            applyTransition(settings, nowMillis, transition)
        }
    }

    fun stopPomodoro() {
        viewModelScope.launch {
            val settings = settingsRepository.getOrDefault()
            val state = runtimeRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val transition = pomodoroEngine.stop(state, nowMillis)
            statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, transition.pomodoroStatsDelta)
            runtimeRepository.upsert(transition.nextRuntime)
            notifications.cancelOngoingStatus()
            stopTimerService()
        }
    }

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        viewModelScope.launch {
            val current = settingsRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val updated = settingsRepository.update(nowMillis, transform)
            val shouldRescheduleProximity = updated.proximityMonitoringEnabled && (
                current.proximityCheckIntervalMinutes != updated.proximityCheckIntervalMinutes ||
                    current.proximityCaptureSeconds != updated.proximityCaptureSeconds ||
                    current.proximityDistanceMultiplierPercent != updated.proximityDistanceMultiplierPercent ||
                    current.proximityFaceThresholdPercent != updated.proximityFaceThresholdPercent ||
                    current.proximityAlertCooldownSeconds != updated.proximityAlertCooldownSeconds
                )
            applySettingsToActiveRuntime(updated, nowMillis)
            if (shouldRescheduleProximity) scheduleProximityMonitoring()
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val nowMillis = System.currentTimeMillis()
            settingsRepository.update(nowMillis) { it.copy(reminderEnabled = enabled) }
            val state = runtimeRepository.getOrDefault()
            if (!enabled && state.activeEngine == ActiveEngine.REMINDER.name) {
                runtimeRepository.reset(System.currentTimeMillis())
                notifications.cancelAllScheduled()
                notifications.cancelOngoingStatus()
                stopTimerService()
            }
        }
    }

    fun setPomodoroEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val nowMillis = System.currentTimeMillis()
            settingsRepository.update(nowMillis) { it.copy(pomodoroEnabled = enabled) }
            val state = runtimeRepository.getOrDefault()
            if (!enabled && state.activeEngine == ActiveEngine.POMODORO.name) {
                runtimeRepository.reset(System.currentTimeMillis())
                notifications.cancelOngoingStatus()
                stopTimerService()
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.update { it.copy(notificationEnabled = enabled) }
            refreshActiveNotifications(settings, runtimeRepository.getOrDefault())
        }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val settings = settingsRepository.update { it.copy(keepAliveEnabled = enabled) }
            refreshActiveNotifications(settings, runtimeRepository.getOrDefault())
        }
    }

    fun setProximityMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(proximityMonitoringEnabled = enabled) }
            if (enabled) {
                scheduleProximityMonitoring()
            } else {
                cancelProximityMonitoring()
                val state = runtimeRepository.getOrDefault()
                runtimeRepository.upsert(
                    state.copy(
                        proximityMonitoringActive = false,
                        proximityTooClose = false,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun calibrateProximity() {
        calibrateProximityMonitoring()
    }

    fun selectTemplate(templateId: Long) {
        updateSettings { it.copy(activeTipTemplateId = templateId) }
    }

    fun setLanguageCode(languageCode: String) {
        viewModelScope.launch {
            val normalized = LocaleController.normalize(languageCode)
            settingsRepository.update { it.copy(languageCode = normalized) }
        }
    }

    fun updateTemplateSystemBackground(template: TipTemplateEntity, backgroundValue: String, primaryColor: String) {
        viewModelScope.launch {
            tipTemplateRepository.upsert(
                template.copy(
                    backgroundType = TemplateBackgroundType.SYSTEM.name,
                    backgroundValue = backgroundValue,
                    primaryColor = primaryColor,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun updateTemplateImage(template: TipTemplateEntity, imagePath: String) {
        viewModelScope.launch {
            tipTemplateRepository.upsert(
                template.copy(
                    imagePath = imagePath,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun shareStatistics() {
        val state = uiState.value
        if (!state.settings.statsEnabled) return
        export.shareCsv(state.eyeStats, state.pomodoroStats)
    }

    fun shareStatisticsImage() {
        val state = uiState.value
        if (!state.settings.statsEnabled) return
        export.shareStatsImage(state.eyeStats, state.pomodoroStats)
    }

    private suspend fun restoreFromClock() {
        advanceDuePhases(System.currentTimeMillis())
    }

    private suspend fun advanceDuePhases(nowMillis: Long) {
        val settings = settingsRepository.get() ?: return
        val state = runtimeRepository.get() ?: return
        val transition = when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> reminderEngine.advance(settings, state, nowMillis)
            ActiveEngine.POMODORO.name -> pomodoroEngine.advance(settings, state, nowMillis)
            else -> null
        } ?: return
        applyTransition(settings, nowMillis, transition)
    }

    private suspend fun applyTransition(
        settings: AppSettingsEntity,
        nowMillis: Long,
        transition: RuntimeTransition,
    ) {
        statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, transition.eyeStatsDelta)
        statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, transition.pomodoroStatsDelta)
        runtimeRepository.upsert(transition.nextRuntime)
        playAudioEvent(transition.audioEvent)
        refreshActiveNotifications(settings, transition.nextRuntime)
    }

    private fun playAudioEvent(event: AudioEvent) {
        when (event) {
            AudioEvent.None -> Unit
            is AudioEvent.ReminderTone -> audio.playReminderTone(event.enabled, event.path)
        }
    }

    private fun scheduleActiveNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        if (!settings.notificationEnabled) return
        when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> when (state.reminderPhase) {
                ReminderPhase.WORKING.name,
                ReminderPhase.PRE_ALERT.name,
                ReminderPhase.AWAITING_ACTION.name -> {
                    notifications.scheduleReminder(state.nextPreAlertAt, state.nextReminderAt)
                }
                ReminderPhase.RESTING.name -> notifications.scheduleBreakDone(state.breakEndAt)
            }
        }
    }

    private suspend fun applySettingsToActiveRuntime(settings: AppSettingsEntity, nowMillis: Long) {
        val state = runtimeRepository.get() ?: return
        val adjustedState = adjustRuntimeForSettings(state, settings, nowMillis)
        if (adjustedState != state) {
            runtimeRepository.upsert(adjustedState)
        }
        advanceDuePhases(nowMillis)
        refreshActiveNotifications(settings, runtimeRepository.get() ?: adjustedState)
    }

    private fun adjustRuntimeForSettings(
        state: RuntimeStateEntity,
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        return when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> reminderEngine.adjustForSettings(state, settings, nowMillis)
            ActiveEngine.POMODORO.name -> pomodoroEngine.adjustForSettings(state, settings, nowMillis)
            else -> state
        }
    }

    private fun refreshActiveNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        notifications.cancelAllScheduled()
        if (!settings.keepAliveEnabled) stopTimerService()
        if (!settings.notificationEnabled && !settings.keepAliveEnabled) {
            notifications.cancelOngoingStatus()
            return
        }
        if (settings.notificationEnabled) scheduleActiveNotifications(settings, state)
        if (state.activeEngine != ActiveEngine.IDLE.name && settings.keepAliveEnabled) {
            startTimerService()
        }
        if (state.activeEngine != ActiveEngine.IDLE.name && settings.notificationEnabled) {
            notifications.showOngoingStatus(state)
        } else if (state.activeEngine == ActiveEngine.IDLE.name || !settings.keepAliveEnabled) {
            notifications.cancelOngoingStatus()
            if (!settings.keepAliveEnabled) stopTimerService()
        }
    }

    private suspend fun seedTemplates() {
        val nowMillis = System.currentTimeMillis()
        listOf(
            TipTemplateEntity(
                id = 1L,
                name = "Calm teal",
                backgroundValue = "#D4F2F0",
                primaryColor = "#126B66",
                sortOrder = 0,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 2L,
                name = "Soft sunrise",
                backgroundValue = "#FFE0D4",
                primaryColor = "#B9503E",
                sortOrder = 1,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 3L,
                name = "Focus indigo",
                backgroundValue = "#E0E3FF",
                primaryColor = "#575EA8",
                sortOrder = 2,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 4L,
                name = "System colors",
                backgroundType = TemplateBackgroundType.SYSTEM.name,
                backgroundValue = "primaryContainer",
                primaryColor = "primary",
                sortOrder = 3,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 5L,
                name = "Forest glass",
                backgroundValue = "#DFF7E8",
                primaryColor = "#1F7A4D",
                sortOrder = 4,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 6L,
                name = "Clear sky",
                backgroundValue = "#DCEBFF",
                primaryColor = "#2563EB",
                sortOrder = 5,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
            TipTemplateEntity(
                id = 7L,
                name = "Rose quartz",
                backgroundValue = "#FFE1EA",
                primaryColor = "#BE3455",
                sortOrder = 6,
                createdAt = nowMillis,
                updatedAt = nowMillis,
            ),
        ).forEach { template ->
            if (tipTemplateRepository.get(template.id) == null) {
                tipTemplateRepository.upsert(template)
            }
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(themeMode = mode.name) }
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(autoUpdateCheckEnabled = enabled) }
        }
    }
}
