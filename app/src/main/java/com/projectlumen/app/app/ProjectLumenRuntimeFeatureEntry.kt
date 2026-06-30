package com.projectlumen.app.app

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.PomodoroEngine
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import com.projectlumen.app.core.services.AudioService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.time.QuietHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class ProjectLumenRuntimeFeatureEntry(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val runtimeRepository: RuntimeRepository,
    private val statisticsRepository: StatisticsRepository,
    private val notifications: NotificationService,
    private val audio: AudioService,
    private val startTimerService: () -> Unit,
    private val stopTimerService: () -> Unit,
) {
    private val reminderEngine = ReminderEngine()
    private val pomodoroEngine = PomodoroEngine()

    fun startClock(now: MutableStateFlow<Long>) {
        scope.launch {
            while (true) {
                val current = System.currentTimeMillis()
                now.value = current
                advanceDuePhases(current)
                delay(1_000)
            }
        }
    }

    fun startReminder() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = reminderEngine.newWorkingState(settings, nowMillis)
            runtimeRepository.upsert(state)
            refreshActiveNotifications(settings, state)
        }
    }

    fun pauseReminder() {
        scope.launch {
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
        scope.launch {
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
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = reminderEngine.newWorkingState(settings, nowMillis)
            runtimeRepository.upsert(state)
            refreshActiveNotifications(settings, state)
        }
    }

    fun stopAll() {
        scope.launch {
            stopReminderRuntime()
        }
    }

    fun startBreak() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val transition = reminderEngine.startBreak(settings, state, nowMillis)
            applyTransition(settings, nowMillis, transition)
        }
    }

    fun skipBreak() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val state = runtimeRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            val transition = reminderEngine.skipBreak(settings, state, nowMillis)
            applyTransition(settings, nowMillis, transition)
        }
    }

    fun startPomodoro() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.pomodoroEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val transition = pomodoroEngine.start(settings, nowMillis)
            applyTransition(settings, nowMillis, transition)
        }
    }

    fun stopPomodoro() {
        scope.launch {
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

    suspend fun restoreFromClock() {
        advanceDuePhases(System.currentTimeMillis())
    }

    suspend fun stopReminderRuntime() {
        runtimeRepository.reset(System.currentTimeMillis())
        notifications.cancelAllScheduled()
        notifications.cancelOngoingStatus()
        stopTimerService()
    }

    suspend fun stopPomodoroRuntime() {
        runtimeRepository.reset(System.currentTimeMillis())
        notifications.cancelOngoingStatus()
        stopTimerService()
    }

    suspend fun applySettingsToActiveRuntime(settings: AppSettingsEntity, nowMillis: Long) {
        val state = runtimeRepository.get() ?: return
        val adjustedState = adjustRuntimeForSettings(state, settings, nowMillis)
        if (adjustedState != state) {
            runtimeRepository.upsert(adjustedState)
        }
        advanceDuePhases(nowMillis)
        refreshActiveNotifications(settings, runtimeRepository.get() ?: adjustedState)
    }

    fun refreshActiveNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
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

    private fun playAudioEvent(event: AudioEvent) {
        when (event) {
            AudioEvent.None -> Unit
            is AudioEvent.ReminderTone -> audio.playReminderTone(
                enabled = event.enabled,
                soundPath = event.path,
                volumePercent = event.volumePercent,
                vibrate = event.vibrate,
            )
        }
    }

    private fun scheduleActiveNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        if (!settings.notificationEnabled) return
        if (QuietHours.suppressesReminderNotifications(settings, System.currentTimeMillis())) return
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
}
