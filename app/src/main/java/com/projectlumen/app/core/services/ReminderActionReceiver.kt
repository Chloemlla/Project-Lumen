package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.repositories.RuntimeRepository
import com.projectlumen.app.core.repositories.StatisticsRepository
import com.projectlumen.app.core.runtime.ReminderEngine
import com.projectlumen.app.core.runtime.RuntimeTransition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val app = context.applicationContext as ProjectLumenApplication
                val db = app.database
                val settings = db.appSettingsDao().get()
                val runtime = db.runtimeStateDao().get() ?: RuntimeStateEntity()
                val runtimeRepository = RuntimeRepository(db.runtimeStateDao())
                val statisticsRepository = StatisticsRepository(db.dailyEyeStatsDao(), db.dailyPomodoroStatsDao())
                val reminderEngine = ReminderEngine()
                val now = System.currentTimeMillis()
                when (intent.action) {
                    ACTION_START_BREAK -> {
                        if (settings != null) {
                            val transition = reminderEngine.startBreak(settings, runtime, now)
                            applyTransition(app, runtimeRepository, statisticsRepository, settings, now, transition)
                        }
                    }

                    ACTION_SKIP_BREAK -> {
                        if (settings != null) {
                            val transition = reminderEngine.skipBreak(settings, runtime, now)
                            applyTransition(app, runtimeRepository, statisticsRepository, settings, now, transition)
                        }
                    }

                    ACTION_STOP_ALL -> {
                        runtimeRepository.reset(now)
                        app.notifications.cancelAllScheduled()
                        app.notifications.cancelOngoingStatus()
                        context.stopService(Intent(context, TimerForegroundService::class.java))
                    }
                }
            }
            pendingResult.finish()
        }
    }

    private suspend fun applyTransition(
        app: ProjectLumenApplication,
        runtimeRepository: RuntimeRepository,
        statisticsRepository: StatisticsRepository,
        settings: AppSettingsEntity,
        now: Long,
        transition: RuntimeTransition,
    ) {
        statisticsRepository.applyEyeDelta(settings.statsEnabled, now, transition.eyeStatsDelta)
        runtimeRepository.upsert(transition.nextRuntime)
        refreshAfterAction(app, settings, transition.nextRuntime)
    }

    private fun refreshAfterAction(
        app: ProjectLumenApplication,
        settings: AppSettingsEntity,
        runtime: RuntimeStateEntity,
    ) {
        if (settings.keepAliveEnabled) app.startTimerService()
        if (!settings.notificationEnabled) return
        when (runtime.activeEngine) {
            ActiveEngine.REMINDER.name -> when (runtime.reminderPhase) {
                ReminderPhase.WORKING.name,
                ReminderPhase.PRE_ALERT.name,
                ReminderPhase.AWAITING_ACTION.name -> {
                    app.notifications.scheduleReminder(runtime.nextPreAlertAt, runtime.nextReminderAt)
                }
                ReminderPhase.RESTING.name -> app.notifications.scheduleBreakDone(runtime.breakEndAt)
            }
        }
        app.notifications.showOngoingStatus(runtime)
    }

    companion object {
        const val ACTION_START_BREAK = "com.projectlumen.app.action.START_BREAK"
        const val ACTION_SKIP_BREAK = "com.projectlumen.app.action.SKIP_BREAK"
        const val ACTION_STOP_ALL = "com.projectlumen.app.action.STOP_ALL"
    }
}
