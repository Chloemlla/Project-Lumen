package com.projectlumen.app.core.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.max

class AppLifecycleCoordinator(
    private val app: ProjectLumenApplication,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settingsRepository = SettingsRepository(
        app.database.appSettingsDao(),
        app.eyeCarePreferences,
    )
    private val runtimeRepository = RuntimeRepository(app.database.runtimeStateDao())

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            val settings = settingsRepository.getOrDefault()
            val runtime = runtimeRepository.getOrDefault()
            val shouldResumePausedTime = !settings.keepAliveEnabled
            val foregroundRuntime = runtime.resumeAfterBackgroundPause(
                nowMillis = nowMillis,
                shouldResumePausedTime = shouldResumePausedTime,
            )
            val persistedRuntime = foregroundRuntime.copy(
                lastForegroundAt = nowMillis,
                lastBackgroundAt = if (shouldResumePausedTime) 0L else foregroundRuntime.lastBackgroundAt,
                updatedAt = nowMillis,
            )
            runtimeRepository.upsert(persistedRuntime)
            app.notifications.syncRuntimeAlarms(settings, persistedRuntime)
            if (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled) {
                app.scheduleProximityMonitoring()
            }
            if (settings.ambientLightMonitoringEnabled || settings.autoBrightnessEnabled) {
                app.startLightMonitoring()
            }
            if (foregroundRuntime.activeEngine != ActiveEngine.IDLE.name && settings.keepAliveEnabled) {
                app.startTimerService()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch {
            val nowMillis = System.currentTimeMillis()
            val settings = settingsRepository.getOrDefault()
            val runtime = runtimeRepository.getOrDefault()
            if (!settings.keepAliveEnabled) {
                runtimeRepository.upsert(
                    runtime.copy(
                        lastBackgroundAt = nowMillis,
                        updatedAt = nowMillis,
                    ),
                )
                app.notifications.cancelAllScheduled()
                app.notifications.cancelOngoingStatus()
                app.stopTimerService()
            } else {
                runtimeRepository.upsert(runtime.copy(updatedAt = nowMillis))
            }
            if (settings.proximityMonitoringEnabled || settings.blinkMonitoringEnabled) {
                app.cancelProximityMonitoring()
            }
            if (settings.ambientLightMonitoringEnabled || settings.autoBrightnessEnabled) {
                app.stopLightMonitoring()
            }
        }
    }

    private fun RuntimeStateEntity.resumeAfterBackgroundPause(
        nowMillis: Long,
        shouldResumePausedTime: Boolean,
    ): RuntimeStateEntity {
        if (!shouldResumePausedTime || lastBackgroundAt <= 0L || nowMillis <= lastBackgroundAt) {
            return this
        }
        val pauseMillis = nowMillis - max(lastBackgroundAt, lastForegroundAt)
        if (pauseMillis <= 0L) return this
        return when (activeEngine) {
            ActiveEngine.REMINDER.name -> resumeReminderAfterPause(pauseMillis, nowMillis)
            ActiveEngine.POMODORO.name -> resumePomodoroAfterPause(pauseMillis, nowMillis)
            else -> this
        }
    }

    private fun RuntimeStateEntity.resumeReminderAfterPause(
        pauseMillis: Long,
        nowMillis: Long,
    ): RuntimeStateEntity {
        return when (reminderPhase) {
            ReminderPhase.WORKING.name,
            ReminderPhase.PRE_ALERT.name,
            ReminderPhase.AWAITING_ACTION.name -> copy(
                reminderStartedAt = reminderStartedAt.shiftIfSet(pauseMillis),
                nextPreAlertAt = nextPreAlertAt.shiftIfSet(pauseMillis),
                nextReminderAt = nextReminderAt.shiftIfSet(pauseMillis),
                lastStatsTickAt = nowMillis,
            )
            ReminderPhase.RESTING.name -> copy(
                breakStartedAt = breakStartedAt.shiftIfSet(pauseMillis),
                breakEndAt = breakEndAt.shiftIfSet(pauseMillis),
                lastStatsTickAt = nowMillis,
            )
            else -> this
        }
    }

    private fun RuntimeStateEntity.resumePomodoroAfterPause(
        pauseMillis: Long,
        nowMillis: Long,
    ): RuntimeStateEntity {
        return when (pomodoroPhase) {
            PomodoroPhase.FOCUS.name,
            PomodoroPhase.SHORT_BREAK.name,
            PomodoroPhase.LONG_BREAK.name -> copy(
                pomodoroPhaseStartedAt = pomodoroPhaseStartedAt.shiftIfSet(pauseMillis),
                pomodoroPhaseEndAt = pomodoroPhaseEndAt.shiftIfSet(pauseMillis),
                lastStatsTickAt = nowMillis,
            )
            else -> this
        }
    }

    private fun Long.shiftIfSet(deltaMillis: Long): Long {
        return if (this > 0L) this + deltaMillis else this
    }
}
