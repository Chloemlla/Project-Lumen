package com.projectlumen.app.core.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
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
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

class TimerForegroundService : Service() {
    private lateinit var notifications: NotificationService
    private lateinit var app: ProjectLumenApplication
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var runtimeRepository: RuntimeRepository
    private lateinit var statisticsRepository: StatisticsRepository
    private val reminderEngine = ReminderEngine()
    private val pomodoroEngine = PomodoroEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loopStarted = false

    override fun onCreate() {
        super.onCreate()
        app = application as ProjectLumenApplication
        notifications = NotificationService(this)
        settingsRepository = SettingsRepository(app.database.appSettingsDao())
        runtimeRepository = RuntimeRepository(app.database.runtimeStateDao())
        statisticsRepository = StatisticsRepository(
            app.database.dailyEyeStatsDao(),
            app.database.dailyPomodoroStatsDao(),
        )
        notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun runTimerLoop() {
        while (true) {
            val settings = settingsRepository.getOrDefault()
            val runtime = runtimeRepository.getOrDefault()
            if (!settings.keepAliveEnabled || runtime.activeEngine == ActiveEngine.IDLE.name) {
                stopSelf()
                return
            }
            val nowMillis = System.currentTimeMillis()
            val tickedState = recordIncrementalEyeStats(settings, runtime, nowMillis)
            advanceDuePhases(settings, tickedState, nowMillis)
            delay(1_000)
        }
    }

    private suspend fun recordIncrementalEyeStats(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        if (!settings.statsEnabled || state.activeEngine != ActiveEngine.REMINDER.name) return state
        return when (state.reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> {
                val seconds = nowMillis.coerceElapsedSecondsSince(max(state.reminderStartedAt, state.lastStatsTickAt))
                if (seconds > 0L) {
                    statisticsRepository.updateEyeStats(settings.statsEnabled, nowMillis) {
                        it.copy(workingSeconds = it.workingSeconds + seconds)
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
        refreshRuntimeNotifications(settings, transition.nextRuntime)
    }

    private fun playAudioEvent(event: AudioEvent) {
        when (event) {
            AudioEvent.None -> Unit
            is AudioEvent.ReminderTone -> app.audio.playReminderTone(event.enabled, event.path)
        }
    }

    private fun refreshRuntimeNotifications(settings: AppSettingsEntity, state: RuntimeStateEntity) {
        if (!settings.notificationEnabled) return
        notifications.cancelAllScheduled()
        when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> when (state.reminderPhase) {
                ReminderPhase.WORKING.name,
                ReminderPhase.PRE_ALERT.name,
                ReminderPhase.AWAITING_ACTION.name -> notifications.scheduleReminder(state.nextPreAlertAt, state.nextReminderAt)
                ReminderPhase.RESTING.name -> notifications.scheduleBreakDone(state.breakEndAt)
            }
        }
        notifications.showOngoingStatus(state)
    }

}
