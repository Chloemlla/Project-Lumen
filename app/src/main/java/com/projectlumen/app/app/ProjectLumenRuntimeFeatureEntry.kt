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
import com.projectlumen.app.core.services.AuraAudioService
import com.projectlumen.app.core.services.NotificationService
import com.projectlumen.app.core.services.RuntimeAdvanceGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class ProjectLumenRuntimeFeatureEntry(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val runtimeRepository: RuntimeRepository,
    private val statisticsRepository: StatisticsRepository,
    private val notifications: NotificationService,
    private val audio: AuraAudioService,
    private val startTimerService: () -> Unit,
    private val stopTimerService: () -> Unit,
    private val uploadTelemetrySnapshot: suspend () -> Unit,
    private val recordHandledFailure: (Throwable) -> Unit,
) {
    private val reminderEngine = ReminderEngine()
    private val pomodoroEngine = PomodoroEngine()

    fun startClock(now: MutableStateFlow<Long>) {
        scope.launch {
            while (true) {
                val current = System.currentTimeMillis()
                now.value = current
                // One failing tick must not end the loop: every timer in the app is driven from here.
                val active = runCatching { advanceDuePhases(current) }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        recordHandledFailure(throwable)
                    }
                    .getOrDefault(true)
                if (active) {
                    delay(ACTIVE_TICK_MILLIS)
                } else {
                    // Nothing can come due while every engine is idle, so wait for one to start
                    // instead of re-reading settings and runtime once a second. The timeout is the
                    // safety net for a runtime write this process did not observe.
                    withTimeoutOrNull(IDLE_TICK_MILLIS) {
                        runtimeRepository.observe().first { state ->
                            state != null && state.activeEngine != ActiveEngine.IDLE.name
                        }
                    }
                }
            }
        }
    }

    fun startReminder() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = runtimeRepository.update { reminderEngine.newWorkingState(settings, nowMillis) }
            refreshActiveNotifications(settings, state)
        }
    }

    fun pauseReminder() {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            // Branch check and engine call both run inside the repository lock: a row read outside it
            // would overwrite the phase the 1 Hz tick advanced in the meantime.
            var pausedState: RuntimeStateEntity? = null
            runtimeRepository.update { current ->
                if (current.activeEngine != ActiveEngine.REMINDER.name ||
                    current.reminderPhase == ReminderPhase.IDLE.name
                ) {
                    return@update current
                }
                reminderEngine.pause(current, nowMillis).also { pausedState = it }
            }
            val paused = pausedState ?: return@launch
            notifications.cancelAllScheduled()
            stopTimerService()
            refreshActiveNotifications(settingsRepository.getOrDefault(), paused)
        }
    }

    fun pauseForOneHour() {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            var pausedState: RuntimeStateEntity? = null
            runtimeRepository.update { current ->
                if (current.activeEngine != ActiveEngine.REMINDER.name ||
                    current.reminderPhase == ReminderPhase.IDLE.name
                ) {
                    return@update current
                }
                reminderEngine.pauseForOneHour(current, nowMillis).also { pausedState = it }
            }
            val paused = pausedState ?: return@launch
            notifications.cancelAllScheduled()
            refreshActiveNotifications(settingsRepository.getOrDefault(), paused)
        }
    }

    fun resumeReminder() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            val state = runtimeRepository.update { reminderEngine.newWorkingState(settings, nowMillis) }
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
            val nowMillis = System.currentTimeMillis()
            applyTransition(settings, nowMillis) { current ->
                if (current.activeEngine == ActiveEngine.POMODORO.name) {
                    null
                } else {
                    val breakSourceState = if (
                        current.activeEngine == ActiveEngine.REMINDER.name &&
                        current.reminderPhase in reminderBreakStartPhases
                    ) {
                        current
                    } else {
                        reminderEngine.newWorkingState(settings, nowMillis)
                    }
                    reminderEngine.startBreak(settings, breakSourceState, nowMillis)
                }
            }
        }
    }

    fun skipBreak() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.reminderEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            applyTransition(settings, nowMillis) { current ->
                reminderEngine.skipBreak(settings, current, nowMillis)
            }
        }
    }

    fun startPomodoro() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            if (!settings.pomodoroEnabled) return@launch
            val nowMillis = System.currentTimeMillis()
            applyTransition(settings, nowMillis) { pomodoroEngine.start(settings, nowMillis) }
        }
    }

    fun stopPomodoro() {
        scope.launch {
            val settings = settingsRepository.getOrDefault()
            val nowMillis = System.currentTimeMillis()
            // stop() derives its restart counter from the stored row, so it has to read it under the lock.
            RuntimeAdvanceGate.withAdvanceLock {
                var transition: RuntimeTransition? = null
                runtimeRepository.update { current ->
                    pomodoroEngine.stop(current, nowMillis).also { transition = it }.nextRuntime
                }
                transition?.let { applied ->
                    statisticsRepository.applyPomodoroDelta(
                        settings.statsEnabled,
                        nowMillis,
                        applied.pomodoroStatsDelta,
                    )
                }
            }
            notifications.cancelAllScheduled()
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
        notifications.cancelAllScheduled()
        notifications.cancelOngoingStatus()
        stopTimerService()
    }

    suspend fun applySettingsToActiveRuntime(settings: AppSettingsEntity, nowMillis: Long) {
        // No stored row means no active runtime to adjust; update() would materialise a default one.
        if (runtimeRepository.get() == null) return
        val adjustedState = runtimeRepository.update { current ->
            adjustRuntimeForSettings(current, settings, nowMillis)
        }
        advanceDuePhases(nowMillis)
        refreshActiveNotifications(settings, runtimeRepository.get() ?: adjustedState)
    }

    fun refreshActiveNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        notifications.syncRuntimeAlarms(settings, state)
        if (!settings.keepAliveEnabled) stopTimerService()
        if (!settings.notificationEnabled && !settings.keepAliveEnabled) {
            notifications.cancelOngoingStatus()
            return
        }
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

    private suspend fun advanceDuePhases(nowMillis: Long): Boolean {
        // Runtime state comes from MMKV, settings from Room plus DataStore: reading the cheap one
        // first keeps the idle tick off the database entirely.
        val state = runtimeRepository.get() ?: return false
        if (state.activeEngine != ActiveEngine.REMINDER.name && state.activeEngine != ActiveEngine.POMODORO.name) {
            return false
        }
        val settings = settingsRepository.get() ?: return false
        // Unlocked probe: taking the write lock every second just to store the same row back would
        // turn the tick into a 1 Hz MMKV write. The authoritative recompute happens under the lock.
        if (dueTransition(settings, state, nowMillis) == null) return true
        applyTransition(settings, nowMillis) { current -> dueTransition(settings, current, nowMillis) }
        return true
    }

    private fun dueTransition(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeTransition? {
        return when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> reminderEngine.advance(settings, state, nowMillis)
            ActiveEngine.POMODORO.name -> pomodoroEngine.advance(settings, state, nowMillis)
            else -> null
        }
    }

    // computeTransition runs inside the repository lock so the engine sees the row that is actually
    // stored; returning null leaves it untouched.
    private suspend fun applyTransition(
        settings: AppSettingsEntity,
        nowMillis: Long,
        computeTransition: (RuntimeStateEntity) -> RuntimeTransition?,
    ) {
        // Same gate the timer service and the alarm receiver hold, so two peers cannot advance and
        // count the same due phase.
        val applied = RuntimeAdvanceGate.withAdvanceLock {
            var transition: RuntimeTransition? = null
            runtimeRepository.update { current ->
                val next = computeTransition(current)
                transition = next
                next?.nextRuntime ?: current
            }
            transition?.also { next ->
                statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, next.eyeStatsDelta)
                statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, next.pomodoroStatsDelta)
            }
        } ?: return
        playAudioEvent(applied.audioEvent)
        refreshActiveNotifications(settings, applied.nextRuntime)
        uploadTelemetrySnapshot()
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
            is AudioEvent.ReminderTone -> audio.playReminderTone(event)
        }
    }

    private companion object {
        private const val ACTIVE_TICK_MILLIS = 1_000L
        private const val IDLE_TICK_MILLIS = 30_000L

        private val reminderBreakStartPhases = setOf(
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
        )
    }
}
