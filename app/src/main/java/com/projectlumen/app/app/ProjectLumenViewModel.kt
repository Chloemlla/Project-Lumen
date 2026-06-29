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
            if (settingsDao.get() == null) settingsDao.upsert(AppSettingsEntity())
            if (runtimeDao.get() == null) runtimeDao.upsert(RuntimeStateEntity())
            seedTemplates()
            restoreFromClock()
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (settings.proximityMonitoringEnabled) scheduleProximityMonitoring()
            refreshActiveNotifications(settings, runtimeDao.get() ?: RuntimeStateEntity())
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
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = newWorkingState(settings, nowMillis)
            runtimeDao.upsert(state)
            refreshActiveNotifications(settings, state)
        }
    }

    fun pauseReminder() {
        viewModelScope.launch {
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            if (state.activeEngine != ActiveEngine.REMINDER.name || state.reminderPhase == ReminderPhase.IDLE.name) return@launch
            notifications.cancelAllScheduled()
            stopTimerService()
            val nextState = state.copy(
                reminderPhase = ReminderPhase.PAUSED.name,
                isManuallyPaused = true,
                pausedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            runtimeDao.upsert(nextState)
            refreshActiveNotifications(settingsDao.get() ?: AppSettingsEntity(), nextState)
        }
    }

    fun pauseForOneHour() {
        viewModelScope.launch {
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            if (state.activeEngine != ActiveEngine.REMINDER.name || state.reminderPhase == ReminderPhase.IDLE.name) return@launch
            val nowMillis = System.currentTimeMillis()
            notifications.cancelAllScheduled()
            val nextState = state.copy(
                reminderPhase = ReminderPhase.PAUSED.name,
                isManuallyPaused = false,
                pausedAt = nowMillis,
                suspendedUntil = nowMillis + 60 * 60_000L,
                updatedAt = nowMillis,
            )
            runtimeDao.upsert(nextState)
            refreshActiveNotifications(settingsDao.get() ?: AppSettingsEntity(), nextState)
        }
    }

    fun resumeReminder() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = newWorkingState(settings, nowMillis)
            runtimeDao.upsert(state)
            refreshActiveNotifications(settings, state)
        }
    }

    fun stopAll() {
        viewModelScope.launch {
            runtimeDao.upsert(RuntimeStateEntity(updatedAt = System.currentTimeMillis()))
            notifications.cancelAllScheduled()
            notifications.cancelOngoingStatus()
            stopTimerService()
        }
    }

    fun startBreak() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            val nowMillis = System.currentTimeMillis()
            addWorkingSeconds(elapsedWorkingSeconds(state, nowMillis), nowMillis)
            val nextState = state.copy(
                    activeEngine = ActiveEngine.REMINDER.name,
                    reminderPhase = ReminderPhase.RESTING.name,
                    breakStartedAt = nowMillis,
                    breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                    lastStatsTickAt = nowMillis,
                    updatedAt = nowMillis,
            )
            runtimeDao.upsert(nextState)
            refreshActiveNotifications(settings, nextState)
        }
    }

    fun skipBreak() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            val nowMillis = System.currentTimeMillis()
            addWorkingSeconds(elapsedWorkingSeconds(state, nowMillis), nowMillis)
            incrementEyeStats(nowMillis) { it.copy(skipCount = it.skipCount + 1) }
            val nextState = newWorkingState(settings, nowMillis)
            runtimeDao.upsert(nextState)
            refreshActiveNotifications(settings, nextState)
        }
    }

    fun startPomodoro() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.pomodoroEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val nextState = RuntimeStateEntity(
                activeEngine = ActiveEngine.POMODORO.name,
                pomodoroPhase = PomodoroPhase.FOCUS.name,
                pomodoroPhaseStartedAt = nowMillis,
                pomodoroPhaseEndAt = nowMillis + settings.pomodoroWorkMinutes * 60_000L,
                pomodoroCycleIndex = 1,
                updatedAt = nowMillis,
            )
            runtimeDao.upsert(nextState)
            audio.playReminderTone(
                settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled,
                settings.pomodoroWorkStartSoundPath,
            )
            refreshActiveNotifications(settings, nextState)
        }
    }

    fun stopPomodoro() {
        viewModelScope.launch {
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            val nowMillis = System.currentTimeMillis()
            if (state.activeEngine == ActiveEngine.POMODORO.name && state.pomodoroPhase != PomodoroPhase.IDLE.name) {
                incrementPomodoroStats(nowMillis) { it.copy(restartCount = it.restartCount + 1) }
            }
            runtimeDao.upsert(RuntimeStateEntity(updatedAt = nowMillis))
            notifications.cancelOngoingStatus()
            stopTimerService()
        }
    }

    fun updateSettings(transform: (AppSettingsEntity) -> AppSettingsEntity) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            val nowMillis = System.currentTimeMillis()
            val updated = transform(current).copy(id = 1, updatedAt = nowMillis)
            val shouldRescheduleProximity = updated.proximityMonitoringEnabled && (
                current.proximityCheckIntervalMinutes != updated.proximityCheckIntervalMinutes ||
                    current.proximityCaptureSeconds != updated.proximityCaptureSeconds ||
                    current.proximityDistanceMultiplierPercent != updated.proximityDistanceMultiplierPercent ||
                    current.proximityFaceThresholdPercent != updated.proximityFaceThresholdPercent ||
                    current.proximityAlertCooldownSeconds != updated.proximityAlertCooldownSeconds
                )
            settingsDao.upsert(updated)
            applySettingsToActiveRuntime(updated, nowMillis)
            if (shouldRescheduleProximity) scheduleProximityMonitoring()
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            settingsDao.upsert(current.copy(reminderEnabled = enabled, updatedAt = System.currentTimeMillis()))
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            if (!enabled && state.activeEngine == ActiveEngine.REMINDER.name) {
                runtimeDao.upsert(RuntimeStateEntity(updatedAt = System.currentTimeMillis()))
                notifications.cancelAllScheduled()
                notifications.cancelOngoingStatus()
                stopTimerService()
            }
        }
    }

    fun setPomodoroEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            settingsDao.upsert(current.copy(pomodoroEnabled = enabled, updatedAt = System.currentTimeMillis()))
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            if (!enabled && state.activeEngine == ActiveEngine.POMODORO.name) {
                runtimeDao.upsert(RuntimeStateEntity(updatedAt = System.currentTimeMillis()))
                notifications.cancelOngoingStatus()
                stopTimerService()
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            val settings = current.copy(notificationEnabled = enabled, updatedAt = System.currentTimeMillis())
            settingsDao.upsert(settings)
            refreshActiveNotifications(settings, runtimeDao.get() ?: RuntimeStateEntity())
        }
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            val settings = current.copy(keepAliveEnabled = enabled, updatedAt = System.currentTimeMillis())
            settingsDao.upsert(settings)
            refreshActiveNotifications(settings, runtimeDao.get() ?: RuntimeStateEntity())
        }
    }

    fun setProximityMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            val settings = current.copy(proximityMonitoringEnabled = enabled, updatedAt = System.currentTimeMillis())
            settingsDao.upsert(settings)
            if (enabled) {
                scheduleProximityMonitoring()
            } else {
                cancelProximityMonitoring()
                val state = runtimeDao.get() ?: RuntimeStateEntity()
                runtimeDao.upsert(
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
            val current = settingsDao.get() ?: AppSettingsEntity()
            settingsDao.upsert(current.copy(languageCode = normalized, updatedAt = System.currentTimeMillis()))
        }
    }

    fun updateTemplateSystemBackground(template: TipTemplateEntity, backgroundValue: String, primaryColor: String) {
        viewModelScope.launch {
            tipTemplatesDao.upsert(
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
            tipTemplatesDao.upsert(
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
        val settings = settingsDao.get() ?: return
        val state = runtimeDao.get() ?: return
        when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> advanceReminder(settings, state, nowMillis)
            ActiveEngine.POMODORO.name -> advancePomodoro(settings, state, nowMillis)
        }
    }

    private suspend fun advanceReminder(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ) {
        when (state.reminderPhase) {
            ReminderPhase.PAUSED.name -> {
                if (!state.isManuallyPaused && state.suspendedUntil > 0L && nowMillis >= state.suspendedUntil) {
                    val nextState = newWorkingState(settings, nowMillis)
                    runtimeDao.upsert(nextState)
                    refreshActiveNotifications(settings, nextState)
                }
            }

            ReminderPhase.WORKING.name -> {
                if (settings.preAlertEnabled && nowMillis >= state.nextPreAlertAt && nowMillis < state.nextReminderAt) {
                    incrementEyeStats(nowMillis) { it.copy(preAlertCount = it.preAlertCount + 1) }
                    val nextState = state.copy(reminderPhase = ReminderPhase.PRE_ALERT.name, updatedAt = nowMillis)
                    runtimeDao.upsert(nextState)
                    audio.playReminderTone(settings.soundEnabled && settings.preAlertSoundEnabled)
                    refreshActiveNotifications(settings, nextState)
                } else if (nowMillis >= state.nextReminderAt) {
                    addWorkingSeconds(elapsedWorkingSeconds(state, nowMillis), nowMillis)
                    if (settings.askBeforeBreak) {
                        val nextState = state.copy(
                            reminderPhase = ReminderPhase.AWAITING_ACTION.name,
                            lastStatsTickAt = nowMillis,
                            updatedAt = nowMillis,
                        )
                        runtimeDao.upsert(nextState)
                        refreshActiveNotifications(settings, nextState)
                    } else {
                        val nextState = state.copy(
                                reminderPhase = ReminderPhase.RESTING.name,
                                breakStartedAt = nowMillis,
                                breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                                lastStatsTickAt = nowMillis,
                                updatedAt = nowMillis,
                        )
                        runtimeDao.upsert(nextState)
                        refreshActiveNotifications(settings, nextState)
                    }
                }
            }

            ReminderPhase.PRE_ALERT.name -> {
                if (nowMillis >= state.nextReminderAt) {
                    addWorkingSeconds(elapsedWorkingSeconds(state, nowMillis), nowMillis)
                    val nextState = if (settings.askBeforeBreak) {
                        state.copy(
                            reminderPhase = ReminderPhase.AWAITING_ACTION.name,
                            lastStatsTickAt = nowMillis,
                            updatedAt = nowMillis,
                        )
                    } else {
                        state.copy(
                            reminderPhase = ReminderPhase.RESTING.name,
                            breakStartedAt = nowMillis,
                            breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                            lastStatsTickAt = nowMillis,
                            updatedAt = nowMillis,
                        )
                    }
                    runtimeDao.upsert(nextState)
                    refreshActiveNotifications(settings, nextState)
                }
            }

            ReminderPhase.RESTING.name -> {
                if (nowMillis >= state.breakEndAt) {
                    addRestSeconds(elapsedRestSeconds(state, nowMillis), nowMillis)
                    incrementEyeStats(nowMillis) { it.copy(completedBreakCount = it.completedBreakCount + 1) }
                    audio.playReminderTone(settings.soundEnabled, settings.restSoundPath)
                    val nextState = newWorkingState(settings, nowMillis)
                    runtimeDao.upsert(nextState)
                    refreshActiveNotifications(settings, nextState)
                }
            }
        }
    }

    private suspend fun advancePomodoro(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ) {
        if (state.pomodoroPhaseEndAt <= 0L || nowMillis < state.pomodoroPhaseEndAt) return
        when (state.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> {
                val isLongBreak = state.pomodoroCycleIndex >= 4
                val nextPhase = if (isLongBreak) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
                val breakMinutes = if (isLongBreak) settings.pomodoroLongBreakMinutes else settings.pomodoroShortBreakMinutes
                incrementPomodoroStats(nowMillis) {
                    it.copy(
                        completedTomatoCount = it.completedTomatoCount + if (isLongBreak) 1 else 0,
                        completedFocusSessions = it.completedFocusSessions + 1,
                        totalFocusSeconds = it.totalFocusSeconds + max(0L, (state.pomodoroPhaseEndAt - state.pomodoroPhaseStartedAt) / 1000L),
                    )
                }
                val nextState = state.copy(
                    pomodoroPhase = nextPhase.name,
                    pomodoroPhaseStartedAt = nowMillis,
                    pomodoroPhaseEndAt = nowMillis + breakMinutes * 60_000L,
                    updatedAt = nowMillis,
                )
                runtimeDao.upsert(nextState)
                audio.playReminderTone(
                    settings.soundEnabled && settings.pomodoroWorkEndSoundEnabled,
                    settings.pomodoroWorkEndSoundPath,
                )
                refreshActiveNotifications(settings, nextState)
            }

            PomodoroPhase.SHORT_BREAK.name,
            PomodoroPhase.LONG_BREAK.name -> {
                val wasLongBreak = state.pomodoroPhase == PomodoroPhase.LONG_BREAK.name
                incrementPomodoroStats(nowMillis) {
                    it.copy(
                        totalBreakSeconds = it.totalBreakSeconds + max(0L, (state.pomodoroPhaseEndAt - state.pomodoroPhaseStartedAt) / 1000L),
                    )
                }
                val nextState = state.copy(
                    pomodoroPhase = PomodoroPhase.FOCUS.name,
                    pomodoroPhaseStartedAt = nowMillis,
                    pomodoroPhaseEndAt = nowMillis + settings.pomodoroWorkMinutes * 60_000L,
                    pomodoroCycleIndex = if (wasLongBreak) 1 else state.pomodoroCycleIndex + 1,
                    updatedAt = nowMillis,
                )
                runtimeDao.upsert(nextState)
                audio.playReminderTone(
                    settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled,
                    settings.pomodoroWorkStartSoundPath,
                )
                refreshActiveNotifications(settings, nextState)
            }
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
        val state = runtimeDao.get() ?: return
        val adjustedState = adjustRuntimeForSettings(state, settings, nowMillis)
        if (adjustedState != state) {
            runtimeDao.upsert(adjustedState)
        }
        advanceDuePhases(nowMillis)
        refreshActiveNotifications(settings, runtimeDao.get() ?: adjustedState)
    }

    private fun adjustRuntimeForSettings(
        state: RuntimeStateEntity,
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        return when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> adjustReminderRuntime(state, settings, nowMillis)
            ActiveEngine.POMODORO.name -> adjustPomodoroRuntime(state, settings, nowMillis)
            else -> state
        }
    }

    private fun adjustReminderRuntime(
        state: RuntimeStateEntity,
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        if (!settings.reminderEnabled) return RuntimeStateEntity(updatedAt = nowMillis)
        return when (state.reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> {
                val reminderStartedAt = state.reminderStartedAt.takeIf { it > 0L } ?: nowMillis
                val reminderAt = reminderStartedAt + settings.warnIntervalMinutes * 60_000L
                val preAlertAt = if (settings.preAlertEnabled) {
                    reminderAt - settings.preAlertSeconds * 1000L
                } else {
                    reminderAt
                }.coerceAtLeast(reminderStartedAt)
                val phase = when {
                    nowMillis >= reminderAt -> {
                        if (state.reminderPhase == ReminderPhase.AWAITING_ACTION.name && settings.askBeforeBreak) {
                            ReminderPhase.AWAITING_ACTION.name
                        } else {
                            ReminderPhase.WORKING.name
                        }
                    }
                    settings.preAlertEnabled && nowMillis >= preAlertAt -> ReminderPhase.PRE_ALERT.name
                    else -> ReminderPhase.WORKING.name
                }
                state.copy(
                    reminderPhase = phase,
                    reminderStartedAt = reminderStartedAt,
                    nextPreAlertAt = preAlertAt,
                    nextReminderAt = reminderAt,
                    updatedAt = nowMillis,
                )
            }
            ReminderPhase.RESTING.name -> {
                val breakStartedAt = state.breakStartedAt.takeIf { it > 0L } ?: nowMillis
                state.copy(
                    breakStartedAt = breakStartedAt,
                    breakEndAt = breakStartedAt + settings.restDurationSeconds * 1000L,
                    updatedAt = nowMillis,
                )
            }
            else -> state
        }
    }

    private fun adjustPomodoroRuntime(
        state: RuntimeStateEntity,
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        if (!settings.pomodoroEnabled) return RuntimeStateEntity(updatedAt = nowMillis)
        val durationMinutes = when (state.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> settings.pomodoroWorkMinutes
            PomodoroPhase.SHORT_BREAK.name -> settings.pomodoroShortBreakMinutes
            PomodoroPhase.LONG_BREAK.name -> settings.pomodoroLongBreakMinutes
            else -> return state
        }
        val phaseStartedAt = state.pomodoroPhaseStartedAt.takeIf { it > 0L } ?: nowMillis
        return state.copy(
            pomodoroPhaseStartedAt = phaseStartedAt,
            pomodoroPhaseEndAt = phaseStartedAt + durationMinutes * 60_000L,
            updatedAt = nowMillis,
        )
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
                primaryColor = "#246B73",
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
            if (tipTemplatesDao.get(template.id) == null) {
                tipTemplatesDao.upsert(template)
            }
        }
    }

    private fun newWorkingState(settings: AppSettingsEntity, nowMillis: Long): RuntimeStateEntity {
        val reminderAt = nowMillis + settings.warnIntervalMinutes * 60_000L
        val preAlertAt = if (settings.preAlertEnabled) {
            reminderAt - settings.preAlertSeconds * 1000L
        } else {
            reminderAt
        }
        return RuntimeStateEntity(
            activeEngine = ActiveEngine.REMINDER.name,
            reminderPhase = ReminderPhase.WORKING.name,
            reminderStartedAt = nowMillis,
            nextPreAlertAt = preAlertAt.coerceAtLeast(nowMillis),
            nextReminderAt = reminderAt,
            lastStatsTickAt = nowMillis,
            updatedAt = nowMillis,
        )
    }

    private fun elapsedWorkingSeconds(state: RuntimeStateEntity, nowMillis: Long): Long {
        val start = max(state.reminderStartedAt, state.lastStatsTickAt)
        return nowMillis.coerceElapsedSecondsSince(start)
    }

    private fun elapsedRestSeconds(state: RuntimeStateEntity, nowMillis: Long): Long {
        val start = max(state.breakStartedAt, state.lastStatsTickAt)
        return nowMillis.coerceElapsedSecondsSince(start)
    }

    private suspend fun addWorkingSeconds(seconds: Long, nowMillis: Long) {
        if (seconds <= 0L) return
        incrementEyeStats(nowMillis) { it.copy(workingSeconds = it.workingSeconds + seconds) }
    }

    private suspend fun addRestSeconds(seconds: Long, nowMillis: Long) {
        if (seconds <= 0L) return
        incrementEyeStats(nowMillis) { it.copy(restSeconds = it.restSeconds + seconds) }
    }

    private suspend fun incrementEyeStats(
        nowMillis: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        if ((settingsDao.get() ?: AppSettingsEntity()).statsEnabled.not()) return
        val date = todayKey(nowMillis)
        val current = eyeStatsDao.get(date) ?: DailyEyeStatsEntity(statDate = date)
        eyeStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    private suspend fun incrementPomodoroStats(
        nowMillis: Long,
        transform: (DailyPomodoroStatsEntity) -> DailyPomodoroStatsEntity,
    ) {
        if ((settingsDao.get() ?: AppSettingsEntity()).statsEnabled.not()) return
        val date = todayKey(nowMillis)
        val current = pomodoroStatsDao.get(date) ?: DailyPomodoroStatsEntity(statDate = date)
        pomodoroStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            settingsDao.upsert(current.copy(themeMode = mode.name, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setAutoUpdateCheckEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settingsDao.get() ?: AppSettingsEntity()
            settingsDao.upsert(current.copy(autoUpdateCheckEnabled = enabled, updatedAt = System.currentTimeMillis()))
        }
    }
}
