package com.projectlumen.app.core.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
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
import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.database.entities.RuntimeStateEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.core.toast.LumenToastKind
import com.projectlumen.app.core.toast.showLumenToast
import com.projectlumen.app.core.time.QuietHours

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

class NotificationService(private val context: Context) {
    fun ensureChannels() {
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
                NotificationChannel(
                    NotificationChannels.STATUS,
                    context.getString(R.string.channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                ),
                NotificationChannel(
                    NotificationChannels.PROXIMITY,
                    context.getString(R.string.channel_proximity),
                    NotificationManager.IMPORTANCE_HIGH,
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

    fun syncRuntimeAlarms(
        settings: AppSettingsEntity,
        state: RuntimeStateEntity,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        cancelAllScheduled()
        if (!settings.notificationEnabled) return
        when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> {
                if (QuietHours.suppressesReminderNotifications(settings, nowMillis)) return
                when (state.reminderPhase) {
                    ReminderPhase.WORKING.name,
                    ReminderPhase.PRE_ALERT.name,
                    ReminderPhase.AWAITING_ACTION.name -> scheduleReminder(state.nextPreAlertAt, state.nextReminderAt)
                    ReminderPhase.RESTING.name -> scheduleBreakDone(state.breakEndAt)
                }
            }
            ActiveEngine.POMODORO.name -> {
                if (state.pomodoroPhase != PomodoroPhase.IDLE.name && state.pomodoroPhaseEndAt > nowMillis) {
                    schedule(NotificationIds.POMODORO, state.pomodoroPhaseEndAt, AlarmReceiver.ACTION_POMODORO)
                }
            }
        }
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    fun showReminderDue() {
        show(
            id = NotificationIds.BREAK_DUE,
            channel = NotificationChannels.REMINDER,
            title = context.getString(R.string.break_title),
            message = context.getString(R.string.break_waiting_message),
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = true,
            fullScreen = true,
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

    fun showUpdateAvailable(tagName: String, releaseName: String) {
        if (!canPostNotifications()) return
        val updateIntent = Intent(context, MainActivity::class.java).setPackage(context.packageName)
        show(
            id = NotificationIds.POMODORO + 1000,
            channel = NotificationChannels.STATUS,
            title = context.getString(R.string.about_update_status),
            message = context.getString(R.string.about_update_found, tagName),
            priority = NotificationCompat.PRIORITY_DEFAULT,
            includeBreakActions = false,
        )
        try {
            NotificationManagerCompat.from(context).notify(
                NotificationIds.POMODORO + 1001,
                NotificationCompat.Builder(context, NotificationChannels.STATUS)
                    .setSmallIcon(R.drawable.ic_notification_lumen)
                    .setContentTitle(context.getString(R.string.about_update_status))
                    .setContentText("$tagName $releaseName")
                    .setContentIntent(PendingIntent.getActivity(
                        context,
                        NotificationIds.POMODORO + 1001,
                        updateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ))
                    .setAutoCancel(true)
                    .build(),
            )
        } catch (_: SecurityException) {
            return
        }
    }

    fun buildOngoingStatusNotification(state: RuntimeStateEntity? = null): Notification {
        val (title, message) = ongoingStatusText(state)
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openAppPendingIntent(NotificationIds.FOREGROUND_TIMER))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notification_action_stop),
                actionPendingIntent(NotificationIds.STOP_TIMER_ACTION, ReminderActionReceiver.ACTION_STOP_ALL),
            )
            .build()
    }

    fun buildProximityForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.PROXIMITY)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.proximity_check_running_title))
            .setContentText(context.getString(R.string.proximity_check_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.PROXIMITY_FOREGROUND))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildLightMonitorForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.light_monitor_running_title))
            .setContentText(context.getString(R.string.light_monitor_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.LOW_LIGHT_FOREGROUND))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildOverlayForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.overlay_running_title))
            .setContentText(context.getString(R.string.overlay_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.OVERLAY_FOREGROUND))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildDeveloperDebugForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.developer_debug_running_title))
            .setContentText(context.getString(R.string.developer_debug_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.DEVELOPER_DEBUG_FOREGROUND))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showProximityWarning(ratioPercent: Int) {
        val message = context.getString(R.string.proximity_warning_message, ratioPercent)
        context.showLumenToast(
            message = LumenToast.richMessage(
                text = message,
                keyword = "$ratioPercent%",
                color = LumenToastKind.WARNING.accentColor,
            ),
            kind = LumenToastKind.WARNING,
            long = true,
        )
        show(
            id = NotificationIds.PROXIMITY_WARNING,
            channel = NotificationChannels.PROXIMITY,
            title = context.getString(R.string.proximity_warning_title),
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = true,
            fullScreen = true,
        )
    }

    fun showEyeDryWarning() {
        context.showLumenToast(
            message = context.getString(R.string.eye_dry_warning_message),
            kind = LumenToastKind.TIMER,
            long = true,
            trailingIcon = true,
        )
        show(
            id = NotificationIds.EYE_DRY_WARNING,
            channel = NotificationChannels.PROXIMITY,
            title = context.getString(R.string.eye_dry_warning_title),
            message = context.getString(R.string.eye_dry_warning_message),
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = true,
            fullScreen = true,
        )
    }

    fun showLowLightWarning(lux: Float) {
        val message = context.getString(R.string.low_light_warning_message, lux)
        context.showLumenToast(
            message = LumenToast.richMessage(
                text = message,
                keyword = String.format("%.1f", lux),
                color = LumenToastKind.WARNING.accentColor,
            ),
            kind = LumenToastKind.WARNING,
            long = true,
        )
        show(
            id = NotificationIds.LOW_LIGHT_WARNING,
            channel = NotificationChannels.PROXIMITY,
            title = context.getString(R.string.low_light_warning_title),
            message = message,
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = false,
        )
    }

    fun showOngoingStatus(state: RuntimeStateEntity) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(context).notify(
                NotificationIds.FOREGROUND_TIMER,
                buildOngoingStatusNotification(state),
            )
        } catch (_: SecurityException) {
            return
        }
    }

    fun cancelOngoingStatus() {
        NotificationManagerCompat.from(context).cancel(NotificationIds.FOREGROUND_TIMER)
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

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(id: Int, triggerAtMillis: Long, action: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = scheduledPendingIntent(id, action)
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

    private fun scheduledPendingIntent(id: Int, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            id,
            alarmIntent(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingPendingIntent(id: Int, action: String): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            id,
            alarmIntent(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmIntent(action: String): Intent {
        return explicitReceiverIntent(action, AlarmReceiver::class.java)
    }

    private fun show(
        id: Int,
        channel: String,
        title: String,
        message: String,
        priority: Int,
        includeBreakActions: Boolean,
        fullScreen: Boolean = false,
    ) {
        if (!canPostNotifications()) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openAppPendingIntent(id))
            .setAutoCancel(true)
            .setPriority(priority)
            .setCategory(if (fullScreen) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
        if (fullScreen) {
            builder.setFullScreenIntent(openAppPendingIntent(id + 100), true)
        }
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
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            return
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openAppPendingIntent(id: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            id,
            openAppIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): Intent {
        return Intent(context, MainActivity::class.java)
            .setPackage(context.packageName)
    }

    private fun ongoingStatusText(state: RuntimeStateEntity?): Pair<String, String> {
        if (state == null) {
            return context.getString(R.string.ongoing_timer_title) to
                context.getString(R.string.ongoing_timer_message)
        }
        return when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> when (state.reminderPhase) {
                ReminderPhase.WORKING.name,
                ReminderPhase.PRE_ALERT.name,
                ReminderPhase.AWAITING_ACTION.name -> context.getString(R.string.ongoing_timer_title) to
                    context.getString(R.string.ongoing_status_working)
                ReminderPhase.RESTING.name -> context.getString(R.string.break_title) to
                    context.getString(R.string.ongoing_status_resting)
                ReminderPhase.PAUSED.name -> context.getString(R.string.ongoing_timer_title) to
                    context.getString(R.string.ongoing_status_paused)
                else -> context.getString(R.string.ongoing_timer_title) to
                    context.getString(R.string.ongoing_timer_message)
            }
            ActiveEngine.POMODORO.name -> when (state.pomodoroPhase) {
                PomodoroPhase.FOCUS.name,
                PomodoroPhase.SHORT_BREAK.name,
                PomodoroPhase.LONG_BREAK.name -> context.getString(R.string.pomodoro_title) to
                    context.getString(R.string.ongoing_status_pomodoro)
                else -> context.getString(R.string.ongoing_timer_title) to
                    context.getString(R.string.ongoing_timer_message)
            }
            else -> context.getString(R.string.ongoing_timer_title) to
                context.getString(R.string.ongoing_timer_message)
        }
    }

    private fun actionPendingIntent(id: Int, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            id,
            reminderActionIntent(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun reminderActionIntent(action: String): Intent {
        return explicitReceiverIntent(action, ReminderActionReceiver::class.java)
    }

    private fun explicitReceiverIntent(action: String, receiverClass: Class<*>): Intent {
        return Intent(context, receiverClass)
            .setAction(action)
            .setPackage(context.packageName)
    }

}
