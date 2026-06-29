package com.projectlumen.app.core.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.database.AppDatabase
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.DailyPomodoroStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import com.projectlumen.app.core.time.todayKey
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loopStarted = false

    override fun onCreate() {
        super.onCreate()
        app = application as ProjectLumenApplication
        notifications = NotificationService(this)
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
            val settings = app.database.appSettingsDao().get() ?: AppSettingsEntity()
            val runtime = app.database.runtimeStateDao().get() ?: RuntimeStateEntity()
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
                if (seconds > 0L) incrementEyeStats(app.database, nowMillis) { it.copy(workingSeconds = it.workingSeconds + seconds) }
                state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis).also {
                    app.database.runtimeStateDao().upsert(it)
                }
            }
            ReminderPhase.RESTING.name -> {
                val seconds = nowMillis.coerceElapsedSecondsSince(max(state.breakStartedAt, state.lastStatsTickAt))
                if (seconds > 0L) incrementEyeStats(app.database, nowMillis) { it.copy(restSeconds = it.restSeconds + seconds) }
                state.copy(lastStatsTickAt = nowMillis, updatedAt = nowMillis).also {
                    app.database.runtimeStateDao().upsert(it)
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
        when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> advanceReminder(settings, state, nowMillis)
            ActiveEngine.POMODORO.name -> advancePomodoro(settings, state, nowMillis)
        }
    }

    private suspend fun advanceReminder(settings: AppSettingsEntity, state: RuntimeStateEntity, nowMillis: Long) {
        when (state.reminderPhase) {
            ReminderPhase.PAUSED.name -> {
                if (!state.isManuallyPaused && state.suspendedUntil > 0L && nowMillis >= state.suspendedUntil) {
                    val next = newWorkingState(settings, nowMillis)
                    app.database.runtimeStateDao().upsert(next)
                    refreshRuntimeNotifications(settings, next)
                }
            }
            ReminderPhase.WORKING.name -> {
                if (settings.preAlertEnabled && nowMillis >= state.nextPreAlertAt && nowMillis < state.nextReminderAt) {
                    incrementEyeStats(app.database, nowMillis) { it.copy(preAlertCount = it.preAlertCount + 1) }
                    val next = state.copy(reminderPhase = ReminderPhase.PRE_ALERT.name, updatedAt = nowMillis)
                    app.database.runtimeStateDao().upsert(next)
                    app.audio.playReminderTone(settings.soundEnabled && settings.preAlertSoundEnabled)
                    refreshRuntimeNotifications(settings, next)
                } else if (nowMillis >= state.nextReminderAt) {
                    val next = if (settings.askBeforeBreak) {
                        state.copy(reminderPhase = ReminderPhase.AWAITING_ACTION.name, lastStatsTickAt = nowMillis, updatedAt = nowMillis)
                    } else {
                        state.copy(
                            reminderPhase = ReminderPhase.RESTING.name,
                            breakStartedAt = nowMillis,
                            breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                            lastStatsTickAt = nowMillis,
                            updatedAt = nowMillis,
                        )
                    }
                    app.database.runtimeStateDao().upsert(next)
                    refreshRuntimeNotifications(settings, next)
                }
            }
            ReminderPhase.PRE_ALERT.name -> {
                if (nowMillis >= state.nextReminderAt) {
                    val next = if (settings.askBeforeBreak) {
                        state.copy(reminderPhase = ReminderPhase.AWAITING_ACTION.name, lastStatsTickAt = nowMillis, updatedAt = nowMillis)
                    } else {
                        state.copy(
                            reminderPhase = ReminderPhase.RESTING.name,
                            breakStartedAt = nowMillis,
                            breakEndAt = nowMillis + settings.restDurationSeconds * 1000L,
                            lastStatsTickAt = nowMillis,
                            updatedAt = nowMillis,
                        )
                    }
                    app.database.runtimeStateDao().upsert(next)
                    refreshRuntimeNotifications(settings, next)
                }
            }
            ReminderPhase.RESTING.name -> {
                if (nowMillis >= state.breakEndAt) {
                    incrementEyeStats(app.database, nowMillis) { it.copy(completedBreakCount = it.completedBreakCount + 1) }
                    app.audio.playReminderTone(settings.soundEnabled, settings.restSoundPath)
                    val next = newWorkingState(settings, nowMillis)
                    app.database.runtimeStateDao().upsert(next)
                    refreshRuntimeNotifications(settings, next)
                }
            }
        }
    }

    private suspend fun advancePomodoro(settings: AppSettingsEntity, state: RuntimeStateEntity, nowMillis: Long) {
        if (state.pomodoroPhaseEndAt <= 0L || nowMillis < state.pomodoroPhaseEndAt) return
        when (state.pomodoroPhase) {
            PomodoroPhase.FOCUS.name -> {
                val isLongBreak = state.pomodoroCycleIndex >= 4
                val breakMinutes = if (isLongBreak) settings.pomodoroLongBreakMinutes else settings.pomodoroShortBreakMinutes
                incrementPomodoroStats(app.database, nowMillis) {
                    it.copy(
                        completedTomatoCount = it.completedTomatoCount + if (isLongBreak) 1 else 0,
                        completedFocusSessions = it.completedFocusSessions + 1,
                        totalFocusSeconds = it.totalFocusSeconds + max(0L, (state.pomodoroPhaseEndAt - state.pomodoroPhaseStartedAt) / 1000L),
                    )
                }
                val next = state.copy(
                    pomodoroPhase = if (isLongBreak) PomodoroPhase.LONG_BREAK.name else PomodoroPhase.SHORT_BREAK.name,
                    pomodoroPhaseStartedAt = nowMillis,
                    pomodoroPhaseEndAt = nowMillis + breakMinutes * 60_000L,
                    updatedAt = nowMillis,
                )
                app.database.runtimeStateDao().upsert(next)
                app.audio.playReminderTone(settings.soundEnabled && settings.pomodoroWorkEndSoundEnabled, settings.pomodoroWorkEndSoundPath)
                refreshRuntimeNotifications(settings, next)
            }
            PomodoroPhase.SHORT_BREAK.name,
            PomodoroPhase.LONG_BREAK.name -> {
                val wasLongBreak = state.pomodoroPhase == PomodoroPhase.LONG_BREAK.name
                incrementPomodoroStats(app.database, nowMillis) {
                    it.copy(totalBreakSeconds = it.totalBreakSeconds + max(0L, (state.pomodoroPhaseEndAt - state.pomodoroPhaseStartedAt) / 1000L))
                }
                val next = state.copy(
                    pomodoroPhase = PomodoroPhase.FOCUS.name,
                    pomodoroPhaseStartedAt = nowMillis,
                    pomodoroPhaseEndAt = nowMillis + settings.pomodoroWorkMinutes * 60_000L,
                    pomodoroCycleIndex = if (wasLongBreak) 1 else state.pomodoroCycleIndex + 1,
                    updatedAt = nowMillis,
                )
                app.database.runtimeStateDao().upsert(next)
                app.audio.playReminderTone(settings.soundEnabled && settings.pomodoroWorkStartSoundEnabled, settings.pomodoroWorkStartSoundPath)
                refreshRuntimeNotifications(settings, next)
            }
        }
    }

    private fun newWorkingState(settings: AppSettingsEntity, nowMillis: Long): RuntimeStateEntity {
        val reminderAt = nowMillis + settings.warnIntervalMinutes * 60_000L
        val preAlertAt = if (settings.preAlertEnabled) reminderAt - settings.preAlertSeconds * 1000L else reminderAt
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

    private suspend fun incrementEyeStats(
        database: AppDatabase,
        nowMillis: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        if (database.appSettingsDao().get()?.statsEnabled == false) return
        val date = todayKey(nowMillis)
        val current = database.dailyEyeStatsDao().get(date) ?: DailyEyeStatsEntity(statDate = date)
        database.dailyEyeStatsDao().upsert(transform(current).copy(updatedAt = nowMillis))
    }

    private suspend fun incrementPomodoroStats(
        database: AppDatabase,
        nowMillis: Long,
        transform: (DailyPomodoroStatsEntity) -> DailyPomodoroStatsEntity,
    ) {
        if (database.appSettingsDao().get()?.statsEnabled == false) return
        val date = todayKey(nowMillis)
        val current = database.dailyPomodoroStatsDao().get(date) ?: DailyPomodoroStatsEntity(statDate = date)
        database.dailyPomodoroStatsDao().upsert(transform(current).copy(updatedAt = nowMillis))
    }
}
