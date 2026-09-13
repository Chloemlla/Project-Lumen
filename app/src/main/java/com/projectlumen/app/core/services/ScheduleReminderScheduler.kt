package com.projectlumen.app.core.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity

/**
 * Arms the exact alarm that wakes [AlarmReceiver] for a single schedule occurrence.
 *
 * The request code is derived from the occurrence id so a re-schedule of the same occurrence
 * replaces the pending alarm instead of stacking a duplicate one; two occurrences whose ids share
 * a slot modulo [NotificationIds.SCHEDULE_REMINDER_RANGE] would share an alarm, which is why the
 * range is far larger than the number of occurrences ever awaiting a reminder at once.
 */
class ScheduleReminderScheduler(private val context: Context) {

    fun schedule(occurrence: ScheduleOccurrenceEntity, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        scheduleAlarm(occurrence.id, triggerAtMillis)
    }

    fun cancel(occurrenceId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        existingPendingIntent(occurrenceId)?.let(alarmManager::cancel)
    }

    fun cancelAll(occurrences: List<ScheduleOccurrenceEntity>) {
        occurrences.forEach { occurrence -> cancel(occurrence.id) }
    }

    fun notificationIdFor(occurrenceId: Long): Int {
        return NotificationIds.SCHEDULE_REMINDER_BASE +
            (occurrenceId % NotificationIds.SCHEDULE_REMINDER_RANGE).toInt()
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /**
     * Android 14 stopped granting USE_FULL_SCREEN_INTENT by default to apps outside the
     * alarm/calling categories; without this check the platform silently downgrades the
     * full-screen alarm reminder to a heads-up notification.
     */
    fun canUseFullScreenIntents(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(occurrenceId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = scheduledPendingIntent(occurrenceId)
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Permission can change between the capability check and the scheduling call.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun scheduledPendingIntent(occurrenceId: Long): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            notificationIdFor(occurrenceId),
            alarmIntent(occurrenceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingPendingIntent(occurrenceId: Long): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            notificationIdFor(occurrenceId),
            alarmIntent(occurrenceId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmIntent(occurrenceId: Long): Intent {
        return Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_SCHEDULE_REMINDER)
            .setPackage(context.packageName)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
    }

    companion object {
        const val EXTRA_OCCURRENCE_ID = "com.projectlumen.app.extra.SCHEDULE_OCCURRENCE_ID"
    }
}
