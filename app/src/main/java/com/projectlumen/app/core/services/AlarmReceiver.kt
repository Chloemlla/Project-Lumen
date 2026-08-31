package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.overlay.EyeProtectionOverlayService
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.AudioEvent
import com.projectlumen.app.core.runtime.EyeStatsDelta
import com.projectlumen.app.core.runtime.PomodoroEngine
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import com.projectlumen.app.core.time.QuietHours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ProjectLumenApplication
            runCatching {
                app ?: return@runCatching
                val notifications = app.notifications
                val settings = app.settingsRepository().getOrDefault()
                val nowMillis = System.currentTimeMillis()
                val reconciledRuntime = reconcileNow(app, notifications, settings, nowMillis)
                val suppressReminder = QuietHours.suppressesReminderNotifications(settings, nowMillis) &&
                    intent.action in REMINDER_ACTIONS
                notifications.ensureChannels()
                if (settings.notificationEnabled && !suppressReminder) {
                    when (intent.action) {
                        ACTION_PRE_ALERT -> {
                            if (reconciledRuntime.reminderPhase == ReminderPhase.PRE_ALERT.name) {
                                notifications.showPreAlert()
                            }
                        }
                        ACTION_BREAK_DUE -> {
                            if (reconciledRuntime.reminderPhase == ReminderPhase.AWAITING_ACTION.name) {
                                notifications.showReminderDue()
                            }
                        }
                        ACTION_BREAK_DONE -> {
                            if (reconciledRuntime.reminderPhase == ReminderPhase.WORKING.name) {
                                notifications.showBreakDone()
                            }
                        }
                        ACTION_POMODORO -> {
                            if (reconciledRuntime.activeEngine == ActiveEngine.POMODORO.name) {
                                notifications.showPomodoro(
                                    context.getString(com.projectlumen.app.R.string.pomodoro_title),
                                    context.getString(com.projectlumen.app.R.string.pomodoro_notification_message),
                                )
                            }
                        }
                    }
                }
                // Forced-rest overlay is a hard enforcement gated only on the overlay setting and
                // quiet hours — NOT on notificationEnabled. This mirrors the foreground path in
                // TimerForegroundService.showBlockingOverlayIfNeeded so behaviour is identical
                // whether the break becomes due in the foreground or after the app is backgrounded.
                if (
                    intent.action == ACTION_BREAK_DUE &&
                    settings.globalOverlayEnabled &&
                    !suppressReminder
                ) {
                    EyeProtectionOverlayService.show(
                        context = context.applicationContext,
                        title = context.getString(R.string.overlay_break_title),
                        message = context.getString(R.string.overlay_break_message),
                        durationSeconds = settings.restDurationSeconds.coerceAtLeast(settings.overlayRestDurationSeconds),
                    )
                }
                if (settings.keepAliveEnabled && reconciledRuntime.activeEngine != ActiveEngine.IDLE.name) {
                    app.startTimerService()
                }
            }
                .onFailure { throwable -> app?.recordHandledFailure(throwable) }
            pendingResult.finish()
        }
    }

    companion object {
        const val ACTION_PRE_ALERT = "com.projectlumen.app.action.PRE_ALERT"
        const val ACTION_BREAK_DUE = "com.projectlumen.app.action.BREAK_DUE"
        const val ACTION_BREAK_DONE = "com.projectlumen.app.action.BREAK_DONE"
        const val ACTION_POMODORO = "com.projectlumen.app.action.POMODORO"

        private val REMINDER_ACTIONS = setOf(ACTION_PRE_ALERT, ACTION_BREAK_DUE, ACTION_BREAK_DONE)

        /**
         * Advances any phase that is already due, persists the transition and re-arms the alarms.
         * Recovery callers (boot, reconciliation worker) pass [capStatsDelta] so a phase that
         * expired while the process was dead cannot bill hours of wall-clock time as work.
         */
        suspend fun reconcileNow(
            app: ProjectLumenApplication,
            notifications: NotificationService,
            settings: AppSettingsEntity,
            nowMillis: Long,
            capStatsDelta: Boolean = false,
        ): RuntimeStateEntity = RuntimeAdvanceGate.withAdvanceLock {
            // The engine runs inside the repository lock: a snapshot computed outside it would
            // overwrite whatever the timer service or the sensors persisted meanwhile.
            var transition: RuntimeTransition? = null
            val nextRuntime = app.runtimeRepository().update { current ->
                val computed = when (current.activeEngine) {
                    ActiveEngine.REMINDER.name -> ReminderEngine().advance(settings, current, nowMillis)
                    ActiveEngine.POMODORO.name -> PomodoroEngine().advance(settings, current, nowMillis)
                    else -> null
                } ?: return@update current
                val applied = if (capStatsDelta) {
                    computed.copy(eyeStatsDelta = computed.eyeStatsDelta.capped(settings))
                } else {
                    computed
                }
                transition = applied
                applied.nextRuntime
            }
            val applied = transition
            if (applied == null) {
                notifications.syncRuntimeAlarms(settings, nextRuntime, nowMillis)
                return@withAdvanceLock nextRuntime
            }
            val statisticsRepository = StatisticsRepository(
                app.database.dailyEyeStatsDao(),
                app.database.dailyPomodoroStatsDao(),
            )
            statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, applied.eyeStatsDelta)
            statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, applied.pomodoroStatsDelta)
            playAudioEvent(app, applied.audioEvent)
            notifications.syncRuntimeAlarms(settings, applied.nextRuntime, nowMillis)
            applied.nextRuntime
        }

        private fun playAudioEvent(app: ProjectLumenApplication, event: AudioEvent) {
            when (event) {
                AudioEvent.None -> Unit
                is AudioEvent.ReminderTone -> app.audio.playReminderTone(event)
            }
        }

        private fun EyeStatsDelta.capped(settings: AppSettingsEntity): EyeStatsDelta {
            val workingCap = settings.warnIntervalMinutes.coerceAtLeast(1).toLong() * 60L
            val restCap = settings.restDurationSeconds.coerceAtLeast(1).toLong()
            return copy(
                workingSeconds = workingSeconds.coerceAtMost(workingCap),
                restSeconds = restSeconds.coerceAtMost(restCap),
                maxContinuousWorkSeconds = maxContinuousWorkSeconds.coerceAtMost(workingCap),
            )
        }
    }
}
