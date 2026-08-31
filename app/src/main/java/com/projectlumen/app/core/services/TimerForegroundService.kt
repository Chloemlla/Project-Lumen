package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.PomodoroEngine
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import com.projectlumen.app.core.time.QuietHours
import com.projectlumen.app.core.time.MAX_SINGLE_ELAPSED_SECONDS
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class TimerForegroundService : LifecycleService() {
    private lateinit var notifications: NotificationService
    private lateinit var app: ProjectLumenApplication
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var runtimeRepository: RuntimeRepository
    private lateinit var statisticsRepository: StatisticsRepository
    private val reminderEngine = ReminderEngine()
    private val pomodoroEngine = PomodoroEngine()
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            if (::app.isInitialized) app.recordHandledFailure(throwable)
        },
    )
    @Volatile private var loopJob: Job? = null
    private var screenReceiverRegistered = false
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_SCREEN_ON,
                Intent.ACTION_USER_PRESENT -> scope.launch { handleScreenStateChange() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as ProjectLumenApplication
        notifications = app.notifications
        settingsRepository = app.settingsRepository()
        runtimeRepository = app.runtimeRepository()
        statisticsRepository = StatisticsRepository(
            app.database.dailyEyeStatsDao(),
            app.database.dailyPomodoroStatsDao(),
        )
        notifications.ensureChannels()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val promoted = ForegroundServiceController.promote(
            service = this,
            notificationId = NotificationIds.FOREGROUND_TIMER,
            notificationProvider = { notifications.buildOngoingStatusNotification() },
            foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!promoted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (loopJob?.isActive != true) {
            loopJob = scope.launch { runTimerLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        loopJob = null
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runTimerLoop() {
        tickingFlow().collect { nowMillis ->
            // Serialised against the alarm receiver so a due phase is never advanced twice and a
            // tick write never rolls back a transition the receiver just persisted.
            val stillRunning = RuntimeAdvanceGate.withAdvanceLock { processTick(nowMillis) }
            if (!stillRunning) {
                // Only request the stop; onDestroy owns cancelling the scope, so a restart that
                // reuses this instance can start a fresh loop.
                stopSelf()
            }
        }
    }

    private suspend fun processTick(nowMillis: Long): Boolean {
        val settings = settingsRepository.getOrDefault()
        if (!settings.keepAliveEnabled) return false
        val interactive = isDeviceInteractive()
        var idle = false
        var screenStateChanged = false
        val screenAdjustedState = runtimeRepository.update { current ->
            if (current.activeEngine == ActiveEngine.IDLE.name) {
                idle = true
                return@update current
            }
            val adjusted = adjustForScreenState(current, nowMillis, interactive)
            screenStateChanged = adjusted != current
            adjusted
        }
        if (idle) return false
        if (screenStateChanged) {
            if (interactive) {
                refreshRuntimeNotifications(settings, screenAdjustedState)
            } else {
                notifications.cancelAllScheduled()
            }
        }
        if (!interactive) {
            return true
        }
        val tickedState = recordIncrementalEyeStats(settings, nowMillis)
        // Refresh Live Update progress/chip; NotificationService dedupes identical payloads.
        if (settings.notificationEnabled || settings.keepAliveEnabled) {
            notifications.showOngoingStatus(tickedState)
        }
        advanceDuePhases(settings, nowMillis)
        return true
    }

    private fun tickingFlow() = flow {
        var nextTickAt = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive) {
            emit(System.currentTimeMillis())
            nextTickAt += 1_000L
            val sleepMillis = (nextTickAt - SystemClock.elapsedRealtime()).coerceAtLeast(25L)
            delay(sleepMillis)
            if (SystemClock.elapsedRealtime() - nextTickAt > 1_000L) {
                nextTickAt = SystemClock.elapsedRealtime()
            }
        }
    }

    private suspend fun handleScreenStateChange() {
        RuntimeAdvanceGate.withAdvanceLock { applyScreenStateChange() }
    }

    private suspend fun applyScreenStateChange() {
        val settings = settingsRepository.getOrDefault()
        val interactive = isDeviceInteractive()
        val nowMillis = System.currentTimeMillis()
        var changed = false
        val adjustedState = runtimeRepository.update { current ->
            if (current.activeEngine == ActiveEngine.IDLE.name) return@update current
            val adjusted = adjustForScreenState(current, nowMillis, interactive)
            changed = adjusted != current
            adjusted
        }
        if (!changed) return
        if (interactive) {
            refreshRuntimeNotifications(settings, adjustedState)
        } else {
            notifications.cancelAllScheduled()
        }
    }

    private suspend fun recordIncrementalEyeStats(
        settings: AppSettingsEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        // The stats increment is derived from the row read under the repository lock; only the
        // statistics write itself (suspending) happens afterwards.
        var pending: PendingEyeTick? = null
        val next = runtimeRepository.update { state ->
            pending = null
            if (!settings.statsEnabled || state.activeEngine != ActiveEngine.REMINDER.name) {
                return@update state
            }
            if (QuietHours.isPauseTimerActive(settings, nowMillis) && state.reminderPhase in activeWorkPhases) {
                val workEndAt = QuietHours.activeStartMillis(settings, nowMillis).coerceAtMost(nowMillis)
                val seconds = workEndAt.coerceElapsedSecondsSince(max(state.reminderStartedAt, state.lastStatsTickAt))
                if (seconds > 0L) {
                    pending = PendingEyeTick(
                        workingSeconds = seconds,
                        continuousWorkSeconds = workEndAt.coerceElapsedSecondsSince(state.reminderStartedAt),
                    )
                }
                // Park the cursor at now: carrying the remainder would bill the whole quiet window
                // as work on the first tick after quiet hours end.
                return@update state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis)
            }
            when (state.reminderPhase) {
                ReminderPhase.WORKING.name,
                ReminderPhase.PRE_ALERT.name,
                ReminderPhase.AWAITING_ACTION.name -> {
                    val base = max(state.reminderStartedAt, state.lastStatsTickAt)
                    val seconds = nowMillis.coerceElapsedSecondsSince(base)
                    if (seconds <= 0L) return@update state
                    // A single tick delta this large is a wall-clock change or the loop sleeping
                    // through the phase; the time is not verifiable as work, so bill nothing and
                    // drop the anomaly instead of writing a huge daily total.
                    if (seconds > MAX_SINGLE_ELAPSED_SECONDS) {
                        return@update state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis)
                    }
                    pending = PendingEyeTick(
                        workingSeconds = seconds,
                        continuousWorkSeconds = nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt),
                    )
                    // Carry the sub-second remainder: jumping the cursor to now would drop it every tick.
                    state.copy(lastStatsTickAt = base + seconds * 1_000L, updatedAt = nowMillis)
                }
                ReminderPhase.RESTING.name -> {
                    val base = max(state.breakStartedAt, state.lastStatsTickAt)
                    val seconds = nowMillis.coerceElapsedSecondsSince(base)
                    if (seconds <= 0L) return@update state
                    if (seconds > MAX_SINGLE_ELAPSED_SECONDS) {
                        return@update state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis)
                    }
                    pending = PendingEyeTick(restSeconds = seconds)
                    state.copy(lastStatsTickAt = base + seconds * 1_000L, updatedAt = nowMillis)
                }
                else -> state
            }
        }
        pending?.let { tick ->
            statisticsRepository.updateEyeStats(settings.statsEnabled, nowMillis) {
                it.copy(
                    workingSeconds = it.workingSeconds + tick.workingSeconds,
                    restSeconds = it.restSeconds + tick.restSeconds,
                    maxContinuousWorkSeconds = max(it.maxContinuousWorkSeconds, tick.continuousWorkSeconds),
                )
            }
        }
        return next
    }

    private suspend fun advanceDuePhases(
        settings: AppSettingsEntity,
        nowMillis: Long,
    ) {
        // The engine runs inside the repository lock: computing a whole snapshot outside it would
        // overwrite fields a concurrent writer (alarm receiver, sensors) just persisted.
        var transition: RuntimeTransition? = null
        runtimeRepository.update { current ->
            val computed = when (current.activeEngine) {
                ActiveEngine.REMINDER.name -> reminderEngine.advance(settings, current, nowMillis)
                ActiveEngine.POMODORO.name -> pomodoroEngine.advance(settings, current, nowMillis)
                else -> null
            } ?: return@update current
            transition = computed
            computed.nextRuntime
        }
        val applied = transition ?: return
        statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, applied.eyeStatsDelta)
        statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, applied.pomodoroStatsDelta)
        playAudioEvent(applied.audioEvent)
        showBlockingOverlayIfNeeded(settings, applied.nextRuntime)
        refreshRuntimeNotifications(settings, applied.nextRuntime)
    }

    private fun showBlockingOverlayIfNeeded(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        if (!settings.globalOverlayEnabled || state.activeEngine != ActiveEngine.REMINDER.name) return
        if (state.reminderPhase != ReminderPhase.RESTING.name && state.reminderPhase != ReminderPhase.AWAITING_ACTION.name) return
        EyeProtectionOverlayService.show(
            context = this,
            title = getString(com.projectlumen.app.R.string.overlay_break_title),
            message = getString(com.projectlumen.app.R.string.overlay_break_message),
            durationSeconds = settings.restDurationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds),
        )
    }

    private fun playAudioEvent(event: AudioEvent) {
        when (event) {
            AudioEvent.None -> Unit
            is AudioEvent.ReminderTone -> app.audio.playReminderTone(event)
        }
    }

    private fun refreshRuntimeNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        // Always resync alarms: the forced-rest overlay depends on them firing in the background
        // even when notifications are off. syncRuntimeAlarms decides internally what to schedule.
        notifications.syncRuntimeAlarms(settings, state)
        if (!settings.notificationEnabled) return
        notifications.showOngoingStatus(state)
    }

    private fun registerScreenReceiver() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenReceiver() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenReceiver) }
        screenReceiverRegistered = false
    }

    private fun isDeviceInteractive(): Boolean {
        return powerManager?.isInteractive != false
    }

    private fun adjustForScreenState(
        state: RuntimeStateEntity,
        nowMillis: Long,
        interactive: Boolean,
    ): RuntimeStateEntity {
        if (!interactive) {
            return if (state.lastBackgroundAt > 0L) {
                state
            } else {
                state.copy(lastBackgroundAt = nowMillis, updatedAt = nowMillis)
            }
        }
        val pausedMillis = nowMillis.coerceAtLeast(state.lastBackgroundAt) - state.lastBackgroundAt
        if (state.lastBackgroundAt <= 0L || pausedMillis <= 0L) return state
        return state.shiftRuntimeBy(pausedMillis, nowMillis)
    }

    private fun RuntimeStateEntity.shiftRuntimeBy(
        pausedMillis: Long,
        nowMillis: Long,
    ): RuntimeStateEntity {
        val shifted = when (activeEngine) {
            ActiveEngine.REMINDER.name -> shiftReminderRuntime(pausedMillis, nowMillis)
            ActiveEngine.POMODORO.name -> shiftPomodoroRuntime(pausedMillis, nowMillis)
            else -> this
        }
        return shifted.copy(
            lastForegroundAt = nowMillis,
            lastBackgroundAt = 0L,
            updatedAt = nowMillis,
        )
    }

    private fun RuntimeStateEntity.shiftReminderRuntime(
        pausedMillis: Long,
        nowMillis: Long,
    ): RuntimeStateEntity {
        return when (reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> copy(
                reminderStartedAt = reminderStartedAt.shiftedBy(pausedMillis),
                nextPreAlertAt = nextPreAlertAt.shiftedBy(pausedMillis),
                nextReminderAt = nextReminderAt.shiftedBy(pausedMillis),
                lastStatsTickAt = nowMillis,
            )
            ReminderPhase.RESTING.name -> copy(
                breakStartedAt = breakStartedAt.shiftedBy(pausedMillis),
                breakEndAt = breakEndAt.shiftedBy(pausedMillis),
                lastStatsTickAt = nowMillis,
            )
            else -> copy(lastStatsTickAt = nowMillis)
        }
    }

    private fun RuntimeStateEntity.shiftPomodoroRuntime(
        pausedMillis: Long,
        nowMillis: Long,
    ): RuntimeStateEntity {
        return copy(
            pomodoroPhaseStartedAt = pomodoroPhaseStartedAt.shiftedBy(pausedMillis),
            pomodoroPhaseEndAt = pomodoroPhaseEndAt.shiftedBy(pausedMillis),
            lastStatsTickAt = nowMillis,
        )
    }

    private fun Long.shiftedBy(deltaMillis: Long): Long {
        return if (this > 0L) this + deltaMillis else this
    }

    private data class PendingEyeTick(
        val workingSeconds: Long = 0L,
        val restSeconds: Long = 0L,
        val continuousWorkSeconds: Long = 0L,
    )

    private companion object {
        private val activeWorkPhases = setOf(
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
        )
    }

}
