package com.projectlumen.app.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.database.entities.TipTemplateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.AppThemeMode
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.enums.TemplateBackgroundType
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.ExportService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import com.projectlumen.app.core.time.todayKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

class ProjectLumenViewModel(
    private val database: AppDatabase,
    private val notifications: NotificationService,
    private val audio: AudioService,
    private val export: ExportService,
    private val startTimerService: () -> Unit,
    private val stopTimerService: () -> Unit,
) : ViewModel() {
    private val settingsDao = database.appSettingsDao()
    private val runtimeDao = database.runtimeStateDao()
    private val eyeStatsDao = database.dailyEyeStatsDao()
    private val pomodoroStatsDao = database.dailyPomodoroStatsDao()
    private val tipTemplatesDao = database.tipTemplatesDao()
    private val now = MutableStateFlow(System.currentTimeMillis())

    private val dataState = combine(
        settingsDao.observe(),
        runtimeDao.observe(),
        eyeStatsDao.observeAll(),
        pomodoroStatsDao.observeAll(),
        tipTemplatesDao.observeAll(),
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

    fun startReminder() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = newWorkingState(settings, nowMillis)
            runtimeDao.upsert(state)
            if (settings.notificationEnabled) {
                startTimerService()
                notifications.scheduleReminder(state.nextPreAlertAt, state.nextReminderAt)
                notifications.showOngoingStatus(state)
            }
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
            if ((settingsDao.get() ?: AppSettingsEntity()).notificationEnabled) notifications.showOngoingStatus(nextState)
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
            if ((settingsDao.get() ?: AppSettingsEntity()).notificationEnabled) notifications.showOngoingStatus(nextState)
        }
    }

    fun resumeReminder() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = newWorkingState(settings, nowMillis)
            runtimeDao.upsert(state)
            if (settings.notificationEnabled) {
                startTimerService()
                notifications.scheduleReminder(state.nextPreAlertAt, state.nextReminderAt)
                notifications.showOngoingStatus(state)
            }
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
            addWorkingSeconds(nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt), nowMillis)
            val nextState = state.copy(
                    activeEngine = ActiveEngine.REMINDER.name,
                    reminderPhase = ReminderPhase.RESTING.name,
                    breakStartedAt = nowMillis,
                    breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                    updatedAt = nowMillis,
            )
            runtimeDao.upsert(nextState)
            if (settings.notificationEnabled) {
                startTimerService()
                notifications.scheduleBreakDone(nextState.breakEndAt)
                notifications.showOngoingStatus(nextState)
            }
        }
    }

    fun skipBreak() {
        viewModelScope.launch {
            val settings = settingsDao.get() ?: AppSettingsEntity()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            val nowMillis = System.currentTimeMillis()
            addWorkingSeconds(nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt), nowMillis)
            incrementEyeStats(nowMillis) { it.copy(skipCount = it.skipCount + 1) }
            val nextState = newWorkingState(settings, nowMillis)
            runtimeDao.upsert(nextState)
            if (settings.notificationEnabled) {
                startTimerService()
                notifications.scheduleReminder(nextState.nextPreAlertAt, nextState.nextReminderAt)
                notifications.showOngoingStatus(nextState)
            }
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
            audio.playReminderTone(settings.soundEnabled)
            if (settings.notificationEnabled) {
                startTimerService()
                notifications.showOngoingStatus(nextState)
            }
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
            settingsDao.upsert(transform(current).copy(id = 1, updatedAt = System.currentTimeMillis()))
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
            val state = runtimeDao.get() ?: RuntimeStateEntity()
            if (enabled) {
                scheduleActiveNotifications(settings, state)
                if (state.activeEngine != ActiveEngine.IDLE.name) {
                    startTimerService()
                    notifications.showOngoingStatus(state)
                }
            } else {
                notifications.cancelAllScheduled()
                notifications.cancelOngoingStatus()
                stopTimerService()
            }
        }
    }

    fun selectTemplate(templateId: Long) {
        updateSettings { it.copy(activeTipTemplateId = templateId) }
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

    fun shareStatistics() {
        val state = uiState.value
        export.shareCsv(state.eyeStats, state.pomodoroStats)
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
                    if (settings.notificationEnabled) {
                        startTimerService()
                        notifications.scheduleReminder(nextState.nextPreAlertAt, nextState.nextReminderAt)
                        notifications.showOngoingStatus(nextState)
                    }
                }
            }

            ReminderPhase.WORKING.name -> {
                if (settings.preAlertEnabled && nowMillis >= state.nextPreAlertAt && nowMillis < state.nextReminderAt) {
                    incrementEyeStats(nowMillis) { it.copy(preAlertCount = it.preAlertCount + 1) }
                    val nextState = state.copy(reminderPhase = ReminderPhase.PRE_ALERT.name, updatedAt = nowMillis)
                    runtimeDao.upsert(nextState)
                    if (settings.notificationEnabled) notifications.showOngoingStatus(nextState)
                } else if (nowMillis >= state.nextReminderAt) {
                    addWorkingSeconds(nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt), nowMillis)
                    if (settings.askBeforeBreak && !settings.disableSkip) {
                        val nextState = state.copy(
                            reminderPhase = ReminderPhase.AWAITING_ACTION.name,
                            updatedAt = nowMillis,
                        )
                        runtimeDao.upsert(nextState)
                        if (settings.notificationEnabled) notifications.showOngoingStatus(nextState)
                    } else {
                        val nextState = state.copy(
                                reminderPhase = ReminderPhase.RESTING.name,
                                breakStartedAt = nowMillis,
                                breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                                updatedAt = nowMillis,
                        )
                        runtimeDao.upsert(nextState)
                        if (settings.notificationEnabled) {
                            notifications.scheduleBreakDone(nextState.breakEndAt)
                            notifications.showOngoingStatus(nextState)
                        }
                    }
                }
            }

            ReminderPhase.PRE_ALERT.name -> {
                if (nowMillis >= state.nextReminderAt) {
                    addWorkingSeconds(nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt), nowMillis)
                    val nextState = state.copy(
                        reminderPhase = ReminderPhase.AWAITING_ACTION.name,
                        updatedAt = nowMillis,
                    )
                    runtimeDao.upsert(nextState)
                    if (settings.notificationEnabled) notifications.showOngoingStatus(nextState)
                }
            }

            ReminderPhase.RESTING.name -> {
                if (nowMillis >= state.breakEndAt) {
                    addRestSeconds(nowMillis.coerceElapsedSecondsSince(state.breakStartedAt), nowMillis)
                    incrementEyeStats(nowMillis) { it.copy(completedBreakCount = it.completedBreakCount + 1) }
                    audio.playReminderTone(settings.soundEnabled)
                    val nextState = newWorkingState(settings, nowMillis)
                    runtimeDao.upsert(nextState)
                    if (settings.notificationEnabled) {
                        notifications.scheduleReminder(nextState.nextPreAlertAt, nextState.nextReminderAt)
                        notifications.showOngoingStatus(nextState)
                    }
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
                audio.playReminderTone(settings.soundEnabled)
                if (settings.notificationEnabled) notifications.showOngoingStatus(nextState)
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
                audio.playReminderTone(settings.soundEnabled)
                if (settings.notificationEnabled) notifications.showOngoingStatus(nextState)
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

    private suspend fun seedTemplates() {
        val nowMillis = System.currentTimeMillis()
        if (tipTemplatesDao.count() == 0) {
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
            ).forEach { tipTemplatesDao.upsert(it) }
        }
        if (tipTemplatesDao.get(4L) == null) {
            tipTemplatesDao.upsert(
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
            )
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
            updatedAt = nowMillis,
        )
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
        val date = todayKey(nowMillis)
        val current = eyeStatsDao.get(date) ?: DailyEyeStatsEntity(statDate = date)
        eyeStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    private suspend fun incrementPomodoroStats(
        nowMillis: Long,
        transform: (DailyPomodoroStatsEntity) -> DailyPomodoroStatsEntity,
    ) {
        val date = todayKey(nowMillis)
        val current = pomodoroStatsDao.get(date) ?: DailyPomodoroStatsEntity(statDate = date)
        pomodoroStatsDao.upsert(transform(current).copy(updatedAt = nowMillis))
    }

    fun setThemeMode(mode: AppThemeMode) {
        updateSettings { it.copy(themeMode = mode.name) }
    }
}
