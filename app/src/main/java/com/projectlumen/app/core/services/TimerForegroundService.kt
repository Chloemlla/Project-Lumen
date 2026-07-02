package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
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
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loopStarted = false
    private var screenReceiverRegistered = false
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
        notifications = NotificationService(this)
        settingsRepository = app.settingsRepository()
        runtimeRepository = RuntimeRepository(app.database.runtimeStateDao())
        statisticsRepository = StatisticsRepository(
            app.database.dailyEyeStatsDao(),
            app.database.dailyPomodoroStatsDao(),
        )
        notifications.ensureChannels()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        ServiceCompat.startForeground(
            this,
            NotificationIds.FOREGROUND_TIMER,
            notifications.buildOngoingStatusNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!loopStarted) {
            loopStarted = true
            scope.launch { runTimerLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun runTimerLoop() {
        tickingFlow().collect { nowMillis ->
            val settings = settingsRepository.getOrDefault()
            val runtime = runtimeRepository.getOrDefault()
            if (!settings.keepAliveEnabled || runtime.activeEngine == ActiveEngine.IDLE.name) {
                stopSelf()
                scope.cancel()
                return@collect
            }
            val interactive = isDeviceInteractive()
            val screenAdjustedState = adjustForScreenState(runtime, nowMillis, interactive)
            if (screenAdjustedState != runtime) {
                runtimeRepository.upsert(screenAdjustedState)
                if (interactive) {
                    refreshRuntimeNotifications(settings, screenAdjustedState)
                } else {
                    notifications.cancelAllScheduled()
                }
            }
            if (!interactive) {
                return@collect
            }
            val tickedState = recordIncrementalEyeStats(settings, screenAdjustedState, nowMillis)
            advanceDuePhases(settings, tickedState, nowMillis)
        }
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
        val settings = settingsRepository.getOrDefault()
        val runtime = runtimeRepository.getOrDefault()
        if (runtime.activeEngine == ActiveEngine.IDLE.name) return
        val interactive = isDeviceInteractive()
        val nowMillis = System.currentTimeMillis()
        val adjustedState = adjustForScreenState(runtime, nowMillis, interactive)
        if (adjustedState == runtime) return
        runtimeRepository.upsert(adjustedState)
        if (interactive) {
            refreshRuntimeNotifications(settings, adjustedState)
        } else {
            notifications.cancelAllScheduled()
        }
    }

    private suspend fun recordIncrementalEyeStats(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        if (!settings.statsEnabled || state.activeEngine != ActiveEngine.REMINDER.name) return state
        if (QuietHours.isPauseTimerActive(settings, nowMillis) && state.reminderPhase in activeWorkPhases) {
            val workEndAt = QuietHours.activeStartMillis(settings, nowMillis).coerceAtMost(nowMillis)
            val seconds = workEndAt.coerceElapsedSecondsSince(max(state.reminderStartedAt, state.lastStatsTickAt))
            if (seconds > 0L) {
                val continuousSeconds = workEndAt.coerceElapsedSecondsSince(state.reminderStartedAt)
                statisticsRepository.updateEyeStats(settings.statsEnabled, nowMillis) {
                    it.copy(
                        workingSeconds = it.workingSeconds + seconds,
                        maxContinuousWorkSeconds = max(it.maxContinuousWorkSeconds, continuousSeconds),
                    )
                }
            }
            return state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis).also {
                runtimeRepository.upsert(it)
            }
        }
        return when (state.reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> {
                val seconds = nowMillis.coerceElapsedSecondsSince(max(state.reminderStartedAt, state.lastStatsTickAt))
                if (seconds > 0L) {
                    val continuousSeconds = nowMillis.coerceElapsedSecondsSince(state.reminderStartedAt)
                    statisticsRepository.updateEyeStats(settings.statsEnabled, nowMillis) {
                        it.copy(
                            workingSeconds = it.workingSeconds + seconds,
                            maxContinuousWorkSeconds = max(it.maxContinuousWorkSeconds, continuousSeconds),
                        )
                    }
                }
                state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis).also {
                    runtimeRepository.upsert(it)
                }
            }
            ReminderPhase.RESTING.name -> {
                val seconds = nowMillis.coerceElapsedSecondsSince(max(state.breakStartedAt, state.lastStatsTickAt))
                if (seconds > 0L) {
                    statisticsRepository.updateEyeStats(settings.statsEnabled, nowMillis) {
                        it.copy(restSeconds = it.restSeconds + seconds)
                    }
                }
                state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis).also {
                    runtimeRepository.upsert(it)
                }
            }
            else -> state
        }
    }

    private suspend fun advanceDuePhases(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ) {
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
        showBlockingOverlayIfNeeded(settings, transition.nextRuntime)
        refreshRuntimeNotifications(settings, transition.nextRuntime)
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
            is AudioEvent.ReminderTone -> app.audio.playReminderTone(
                enabled = event.enabled,
                soundPath = event.path,
                volumePercent = event.volumePercent,
                vibrate = event.vibrate,
            )
        }
    }

    private fun refreshRuntimeNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        if (!settings.notificationEnabled) return
        notifications.syncRuntimeAlarms(settings, state)
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
        return getSystemService(PowerManager::class.java)?.isInteractive != false
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

    private companion object {
        private val activeWorkPhases = setOf(
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name,
        )
    }

}
