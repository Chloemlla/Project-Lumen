package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.database.entities.DailyEyeStatsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.time.coerceElapsedSecondsSince
import com.projectlumen.app.core.time.todayKey
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
                val now = System.currentTimeMillis()
                when (intent.action) {
                    ACTION_START_BREAK -> {
                        if (settings != null) {
                            addWorkingSeconds(db, runtime, now)
                            val next = runtime.copy(
                                activeEngine = ActiveEngine.REMINDER.name,
                                reminderPhase = ReminderPhase.RESTING.name,
                                breakStartedAt = now,
                                breakEndAt = now + settings.restDurationSeconds * 1000L,
                                updatedAt = now,
                            )
                            db.runtimeStateDao().upsert(next)
                            if (settings.notificationEnabled) app.notifications.scheduleBreakDone(next.breakEndAt)
                        }
                    }

                    ACTION_SKIP_BREAK -> {
                        if (settings != null) {
                            addWorkingSeconds(db, runtime, now)
                            incrementEyeStats(db, now) { it.copy(skipCount = it.skipCount + 1) }
                            val reminderAt = now + settings.warnIntervalMinutes * 60_000L
                            val preAlertAt = if (settings.preAlertEnabled) {
                                reminderAt - settings.preAlertSeconds * 1000L
                            } else {
                                reminderAt
                            }
                            val next = RuntimeStateEntity(
                                activeEngine = ActiveEngine.REMINDER.name,
                                reminderPhase = ReminderPhase.WORKING.name,
                                reminderStartedAt = now,
                                nextPreAlertAt = preAlertAt.coerceAtLeast(now),
                                nextReminderAt = reminderAt,
                                updatedAt = now,
                            )
                            db.runtimeStateDao().upsert(next)
                            if (settings.notificationEnabled) app.notifications.scheduleReminder(next.nextPreAlertAt, next.nextReminderAt)
                        }
                    }

                    ACTION_STOP_ALL -> {
                        db.runtimeStateDao().upsert(RuntimeStateEntity(updatedAt = now))
                        app.notifications.cancelAllScheduled()
                        context.stopService(Intent(context, TimerForegroundService::class.java))
                    }
                }
            }
            pendingResult.finish()
        }
    }

    private suspend fun addWorkingSeconds(
        db: com.projectlumen.app.core.database.AppDatabase,
        runtime: RuntimeStateEntity,
        now: Long,
    ) {
        val seconds = now.coerceElapsedSecondsSince(runtime.reminderStartedAt)
        if (seconds <= 0L) return
        incrementEyeStats(db, now) { it.copy(workingSeconds = it.workingSeconds + seconds) }
    }

    private suspend fun incrementEyeStats(
        db: com.projectlumen.app.core.database.AppDatabase,
        now: Long,
        transform: (DailyEyeStatsEntity) -> DailyEyeStatsEntity,
    ) {
        val date = todayKey(now)
        val current = db.dailyEyeStatsDao().get(date) ?: DailyEyeStatsEntity(statDate = date)
        db.dailyEyeStatsDao().upsert(transform(current).copy(updatedAt = now))
    }

    companion object {
        const val ACTION_START_BREAK = "com.projectlumen.app.action.START_BREAK"
        const val ACTION_SKIP_BREAK = "com.projectlumen.app.action.SKIP_BREAK"
        const val ACTION_STOP_ALL = "com.projectlumen.app.action.STOP_ALL"
    }
}
