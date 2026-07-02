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
            runCatching {
                val app = context.applicationContext as ProjectLumenApplication
                val notifications = NotificationService(context.applicationContext)
                val settings = app.settingsRepository().getOrDefault()
                val runtime = app.runtimeRepository().getOrDefault()
                val nowMillis = System.currentTimeMillis()
                val reconciledRuntime = reconcileRuntime(app, notifications, settings, runtime, nowMillis)
                val suppressReminder = QuietHours.suppressesReminderNotifications(settings, nowMillis) &&
                    intent.action in setOf(ACTION_PRE_ALERT, ACTION_BREAK_DUE, ACTION_BREAK_DONE)
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
                if (
                    intent.action == ACTION_BREAK_DUE &&
                    settings.notificationEnabled &&
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
            pendingResult.finish()
        }
    }

    private suspend fun reconcileRuntime(
        app: ProjectLumenApplication,
        notifications: NotificationService,
        settings: AppSettingsEntity,
        runtime: RuntimeStateEntity,
        nowMillis: Long,
    ): RuntimeStateEntity {
        val transition = when (runtime.activeEngine) {
            ActiveEngine.REMINDER.name -> ReminderEngine().advance(settings, runtime, nowMillis)
            ActiveEngine.POMODORO.name -> PomodoroEngine().advance(settings, runtime, nowMillis)
            else -> null
        } ?: return runtime.also {
            notifications.syncRuntimeAlarms(settings, it, nowMillis)
        }
        val statisticsRepository = StatisticsRepository(
            app.database.dailyEyeStatsDao(),
            app.database.dailyPomodoroStatsDao(),
        )
        applyTransition(app, statisticsRepository, settings, nowMillis, transition)
        notifications.syncRuntimeAlarms(settings, transition.nextRuntime, nowMillis)
        return transition.nextRuntime
    }

    private suspend fun applyTransition(
        app: ProjectLumenApplication,
        statisticsRepository: StatisticsRepository,
        settings: AppSettingsEntity,
        nowMillis: Long,
        transition: RuntimeTransition,
    ) {
        statisticsRepository.applyEyeDelta(settings.statsEnabled, nowMillis, transition.eyeStatsDelta)
        statisticsRepository.applyPomodoroDelta(settings.statsEnabled, nowMillis, transition.pomodoroStatsDelta)
        app.runtimeRepository().upsert(transition.nextRuntime)
        playAudioEvent(app, transition.audioEvent)
    }

    private fun playAudioEvent(app: ProjectLumenApplication, event: AudioEvent) {
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

    companion object {
        const val ACTION_PRE_ALERT = "com.projectlumen.app.action.PRE_ALERT"
        const val ACTION_BREAK_DUE = "com.projectlumen.app.action.BREAK_DUE"
        const val ACTION_BREAK_DONE = "com.projectlumen.app.action.BREAK_DONE"
        const val ACTION_POMODORO = "com.projectlumen.app.action.POMODORO"
    }
}
