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
import androidx.core.app.NotificationCompat.ProgressStyle
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
        // The blocking overlay ("forced rest") is a hard enforcement that must fire in the
        // background even when notifications are turned off. Its only background wake-up source
        // once the app is killed/backgrounded is the AlarmManager exact alarm scheduled below, so
        // keep scheduling alarms whenever notifications OR the overlay enforcement are enabled.
        val overlayEnforcementEnabled = settings.globalOverlayEnabled
        if (!settings.notificationEnabled && !overlayEnforcementEnabled) {
            dismissTimerNotifications(allTimerNotificationIds)
            return
        }
        if (!settings.notificationEnabled) {
            // Don't leave stale notifications, but still schedule the enforcement alarms below.
            // AlarmReceiver already gates notification posting on notificationEnabled separately.
            dismissTimerNotifications(allTimerNotificationIds)
        } else {
            dismissStaleTimerNotifications(state)
        }
        when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> {
                if (QuietHours.suppressesReminderNotifications(settings, nowMillis)) {
                    dismissTimerNotifications(reminderNotificationIds)
                    return
                }
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
        val nowMillis = System.currentTimeMillis()
        val content = ongoingLiveUpdateContent(state, nowMillis)
        val builder = NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(content.title)
            .setContentText(content.message)
            .setContentIntent(openAppPendingIntent(NotificationIds.FOREGROUND_TIMER))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setRequestPromotedOngoing(content.requestPromotedOngoing)
            .setStyle(content.progressStyle)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notification_action_stop),
                actionPendingIntent(NotificationIds.STOP_TIMER_ACTION, ReminderActionReceiver.ACTION_STOP_ALL),
            )

        content.shortCriticalText?.let { shortText ->
            builder.setShortCriticalText(shortText)
        }
        content.whenMillis?.let { whenMillis ->
            builder
                .setWhen(whenMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(content.chronometerCountDown)
                .setShowWhen(true)
        }
        return builder.build()
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

    private fun dismissStaleTimerNotifications(state: RuntimeStateEntity) {
        val idsToCancel = when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> when (state.reminderPhase) {
                ReminderPhase.PRE_ALERT.name -> listOf(
                    NotificationIds.BREAK_DUE,
                    NotificationIds.BREAK_DONE,
                    NotificationIds.POMODORO,
                )
                ReminderPhase.AWAITING_ACTION.name -> listOf(
                    NotificationIds.PRE_ALERT,
                    NotificationIds.BREAK_DONE,
                    NotificationIds.POMODORO,
                )
                ReminderPhase.RESTING.name -> listOf(
                    NotificationIds.PRE_ALERT,
                    NotificationIds.BREAK_DUE,
                    NotificationIds.BREAK_DONE,
                    NotificationIds.POMODORO,
                )
                else -> listOf(
                    NotificationIds.PRE_ALERT,
                    NotificationIds.BREAK_DUE,
                    NotificationIds.BREAK_DONE,
                    NotificationIds.POMODORO,
                )
            }
            ActiveEngine.POMODORO.name -> listOf(
                NotificationIds.PRE_ALERT,
                NotificationIds.BREAK_DUE,
                NotificationIds.BREAK_DONE,
            )
            else -> listOf(
                NotificationIds.PRE_ALERT,
                NotificationIds.BREAK_DUE,
                NotificationIds.BREAK_DONE,
                NotificationIds.POMODORO,
            )
        }
        dismissTimerNotifications(idsToCancel)
    }

    private fun dismissTimerNotifications(ids: List<Int>) {
        val manager = NotificationManagerCompat.from(context)
        ids.forEach { id -> manager.cancel(id) }
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

    private data class OngoingLiveUpdateContent(
        val title: String,
        val message: String,
        val progressStyle: ProgressStyle,
        val requestPromotedOngoing: Boolean,
        val shortCriticalText: String? = null,
        val whenMillis: Long? = null,
        val chronometerCountDown: Boolean = true,
    )

    private fun ongoingLiveUpdateContent(
        state: RuntimeStateEntity?,
        nowMillis: Long,
    ): OngoingLiveUpdateContent {
        if (state == null) {
            return indeterminateLiveUpdate(
                title = context.getString(R.string.ongoing_timer_title),
                message = context.getString(R.string.ongoing_timer_message),
                shortCriticalText = context.getString(R.string.live_update_chip_running),
            )
        }

        return when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> when (state.reminderPhase) {
                ReminderPhase.WORKING.name,
                ReminderPhase.PRE_ALERT.name,
                ReminderPhase.AWAITING_ACTION.name -> timedLiveUpdate(
                    title = context.getString(R.string.ongoing_timer_title),
                    message = context.getString(R.string.ongoing_status_working),
                    startAt = state.reminderStartedAt,
                    endAt = state.nextReminderAt,
                    nowMillis = nowMillis,
                    shortCriticalText = remainingChipText(state.nextReminderAt, nowMillis),
                )
                ReminderPhase.RESTING.name -> timedLiveUpdate(
                    title = context.getString(R.string.break_title),
                    message = context.getString(R.string.ongoing_status_resting),
                    startAt = state.breakStartedAt,
                    endAt = state.breakEndAt,
                    nowMillis = nowMillis,
                    shortCriticalText = remainingChipText(state.breakEndAt, nowMillis),
                )
                ReminderPhase.PAUSED.name -> indeterminateLiveUpdate(
                    title = context.getString(R.string.ongoing_timer_title),
                    message = context.getString(R.string.ongoing_status_paused),
                    shortCriticalText = context.getString(R.string.live_update_chip_paused),
                    requestPromotedOngoing = false,
                )
                else -> indeterminateLiveUpdate(
                    title = context.getString(R.string.ongoing_timer_title),
                    message = context.getString(R.string.ongoing_timer_message),
                    shortCriticalText = context.getString(R.string.live_update_chip_running),
                )
            }
            ActiveEngine.POMODORO.name -> when (state.pomodoroPhase) {
                PomodoroPhase.FOCUS.name,
                PomodoroPhase.SHORT_BREAK.name,
                PomodoroPhase.LONG_BREAK.name -> timedLiveUpdate(
                    title = context.getString(R.string.pomodoro_title),
                    message = context.getString(R.string.ongoing_status_pomodoro),
                    startAt = state.pomodoroPhaseStartedAt,
                    endAt = state.pomodoroPhaseEndAt,
                    nowMillis = nowMillis,
                    shortCriticalText = remainingChipText(state.pomodoroPhaseEndAt, nowMillis),
                )
                else -> indeterminateLiveUpdate(
                    title = context.getString(R.string.ongoing_timer_title),
                    message = context.getString(R.string.ongoing_timer_message),
                    shortCriticalText = context.getString(R.string.live_update_chip_running),
                )
            }
            else -> indeterminateLiveUpdate(
                title = context.getString(R.string.ongoing_timer_title),
                message = context.getString(R.string.ongoing_timer_message),
                shortCriticalText = context.getString(R.string.live_update_chip_running),
            )
        }
    }

    private fun timedLiveUpdate(
        title: String,
        message: String,
        startAt: Long,
        endAt: Long,
        nowMillis: Long,
        shortCriticalText: String?,
    ): OngoingLiveUpdateContent {
        val totalMillis = (endAt - startAt).coerceAtLeast(0L)
        if (startAt <= 0L || endAt <= 0L || totalMillis <= 0L) {
            return indeterminateLiveUpdate(
                title = title,
                message = message,
                shortCriticalText = shortCriticalText,
            )
        }

        val elapsedMillis = (nowMillis - startAt).coerceIn(0L, totalMillis)
        val progress = ((elapsedMillis * LIVE_UPDATE_PROGRESS_MAX) / totalMillis)
            .toInt()
            .coerceIn(0, LIVE_UPDATE_PROGRESS_MAX)
        val style = ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(progress)
            .addProgressSegment(ProgressStyle.Segment(LIVE_UPDATE_PROGRESS_MAX))
        val remainingMillis = (endAt - nowMillis).coerceAtLeast(0L)
        val useChronometer = remainingMillis >= LIVE_UPDATE_CHRONOMETER_MIN_MILLIS
        return OngoingLiveUpdateContent(
            title = title,
            message = message,
            progressStyle = style,
            requestPromotedOngoing = true,
            shortCriticalText = shortCriticalText,
            whenMillis = if (useChronometer) endAt else null,
            chronometerCountDown = true,
        )
    }

    private fun indeterminateLiveUpdate(
        title: String,
        message: String,
        shortCriticalText: String?,
        requestPromotedOngoing: Boolean = true,
    ): OngoingLiveUpdateContent {
        return OngoingLiveUpdateContent(
            title = title,
            message = message,
            progressStyle = ProgressStyle().setProgressIndeterminate(true),
            requestPromotedOngoing = requestPromotedOngoing,
            shortCriticalText = shortCriticalText,
        )
    }

    private fun remainingChipText(endAt: Long, nowMillis: Long): String? {
        if (endAt <= 0L) return null
        val remainingSeconds = ((endAt - nowMillis).coerceAtLeast(0L) + 999L) / 1000L
        if (remainingSeconds <= 0L) {
            return context.getString(R.string.live_update_chip_due)
        }
        val minutes = remainingSeconds / 60L
        val seconds = remainingSeconds % 60L
        return if (minutes >= 60L) {
            val hours = minutes / 60L
            val remainMinutes = minutes % 60L
            context.getString(R.string.live_update_chip_hours_minutes, hours, remainMinutes)
        } else if (minutes > 0L) {
            context.getString(R.string.live_update_chip_minutes_seconds, minutes, seconds)
        } else {
            context.getString(R.string.live_update_chip_seconds, seconds)
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

    private companion object {
        const val LIVE_UPDATE_PROGRESS_MAX = 1_000
        const val LIVE_UPDATE_CHRONOMETER_MIN_MILLIS = 2 * 60_000L

        val reminderNotificationIds = listOf(
            NotificationIds.PRE_ALERT,
            NotificationIds.BREAK_DUE,
            NotificationIds.BREAK_DONE,
        )
        val allTimerNotificationIds = listOf(
            NotificationIds.PRE_ALERT,
            NotificationIds.BREAK_DUE,
            NotificationIds.BREAK_DONE,
            NotificationIds.POMODORO,
        )
    }

}
