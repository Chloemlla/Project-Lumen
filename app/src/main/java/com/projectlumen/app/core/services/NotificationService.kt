package com.projectlumen.app.core.services

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.projectlumen.app.MainActivity
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds

class NotificationService(private val context: Context) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    NotificationChannels.REMINDER,
                    context.getString(R.string.channel_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    NotificationChannels.POMODORO,
                    context.getString(R.string.channel_pomodoro),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun scheduleReminder(preAlertAt: Long, reminderAt: Long) {
        if (preAlertAt > System.currentTimeMillis()) {
            schedule(NotificationIds.PRE_ALERT, preAlertAt, AlarmReceiver.ACTION_PRE_ALERT)
        }
        if (reminderAt > System.currentTimeMillis()) {
            schedule(NotificationIds.BREAK_DUE, reminderAt, AlarmReceiver.ACTION_BREAK_DUE)
        }
    }

    fun scheduleBreakDone(endAt: Long) {
        if (endAt > System.currentTimeMillis()) {
            schedule(NotificationIds.BREAK_DONE, endAt, AlarmReceiver.ACTION_BREAK_DONE)
        }
    }

    fun showReminderDue() {
        show(
            id = NotificationIds.BREAK_DUE,
            channel = NotificationChannels.REMINDER,
            title = context.getString(R.string.break_title),
            message = context.getString(R.string.break_waiting_message),
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = true,
        )
    }

    fun showPreAlert() {
        show(
            id = NotificationIds.PRE_ALERT,
            channel = NotificationChannels.REMINDER,
            title = context.getString(R.string.pre_alert_notification_title),
            message = context.getString(R.string.pre_alert_notification_message),
            priority = NotificationCompat.PRIORITY_DEFAULT,
            includeBreakActions = false,
        )
    }

    fun showBreakDone() {
        show(
            id = NotificationIds.BREAK_DONE,
            channel = NotificationChannels.REMINDER,
            title = context.getString(R.string.break_done_title),
            message = context.getString(R.string.break_done_message),
            priority = NotificationCompat.PRIORITY_DEFAULT,
            includeBreakActions = false,
        )
    }

    fun showPomodoro(title: String, message: String) {
        show(
            id = NotificationIds.POMODORO,
            channel = NotificationChannels.POMODORO,
            title = title,
            message = message,
            priority = NotificationCompat.PRIORITY_DEFAULT,
            includeBreakActions = false,
        )
    }

    fun cancelAllScheduled() {
        val manager = context.getSystemService(AlarmManager::class.java)
        listOf(
            NotificationIds.PRE_ALERT to AlarmReceiver.ACTION_PRE_ALERT,
            NotificationIds.BREAK_DUE to AlarmReceiver.ACTION_BREAK_DUE,
            NotificationIds.BREAK_DONE to AlarmReceiver.ACTION_BREAK_DONE,
            NotificationIds.POMODORO to AlarmReceiver.ACTION_POMODORO,
        ).forEach { (id, action) ->
            existingPendingIntent(id, action)?.let(manager::cancel)
        }
    }

    private fun schedule(id: Int, triggerAtMillis: Long, action: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = pendingIntent(id, action, PendingIntent.FLAG_UPDATE_CURRENT)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun pendingIntent(id: Int, action: String, flags: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingPendingIntent(id: Int, action: String): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun show(
        id: Int,
        channel: String,
        title: String,
        message: String,
        priority: Int,
        includeBreakActions: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val openIntent = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setPriority(priority)
        if (includeBreakActions) {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.start_break),
                actionPendingIntent(NotificationIds.BREAK_DUE + 10, ReminderActionReceiver.ACTION_START_BREAK),
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.skip_break),
                actionPendingIntent(NotificationIds.BREAK_DUE + 11, ReminderActionReceiver.ACTION_SKIP_BREAK),
            )
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun actionPendingIntent(id: Int, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
