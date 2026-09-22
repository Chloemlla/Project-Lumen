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
import com.projectlumen.app.core.database.entities.ScheduleOccurrenceEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import com.projectlumen.app.core.enums.ReminderPhase
import com.projectlumen.app.core.enums.ScheduleReminderMethod
import com.projectlumen.app.core.overlay.LumenAlertPresenter
import com.projectlumen.app.core.toast.LumenToast
import com.projectlumen.app.core.toast.LumenToastKind
import com.projectlumen.app.core.toast.showLumenToast
import com.projectlumen.app.core.time.QuietHours
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

class NotificationService(private val context: Context) {
    private val lastPublishedLiveUpdateSignature = AtomicReference<String?>(null)

    // The content the Live Update is currently showing. A foreground-service promotion builds a
    // notification without a runtime state in hand, so without this it would fall back to the
    // generic "timer is running" copy, visibly replacing whatever phase was on screen and resetting
    // the dedupe signature below.
    private val lastPublishedLiveUpdate = AtomicReference<OngoingLiveUpdateContent?>(null)
    private val notificationManager by lazy { NotificationManagerCompat.from(context) }
    private val ongoingContentIntent by lazy { openAppPendingIntent(NotificationIds.FOREGROUND_TIMER) }
    private val ongoingStopIntent by lazy {
        actionPendingIntent(NotificationIds.STOP_TIMER_ACTION, ReminderActionReceiver.ACTION_STOP_ALL)
    }

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    NotificationChannels.REMINDER,
                    context.getString(R.string.channel_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_reminder)
                    // Transient cue only; continuous media belongs to mediaPlayback FGS under Android 17.
                    setSound(null, null)
                    enableVibration(true)
                },
                NotificationChannel(
                    NotificationChannels.POMODORO,
                    context.getString(R.string.channel_pomodoro),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.channel_pomodoro)
                    setSound(null, null)
                    enableVibration(true)
                },
                NotificationChannel(
                    NotificationChannels.STATUS,
                    context.getString(R.string.channel_status),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.channel_status)
                    setSound(null, null)
                    enableVibration(false)
                },
                NotificationChannel(
                    NotificationChannels.PROXIMITY,
                    context.getString(R.string.channel_proximity),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_proximity)
                    setSound(null, null)
                    enableVibration(true)
                },
                // Unlike the eye-care channels above, schedule reminders keep the default sound:
                // "notification vs alarm" is the user-visible meaning of the reminder type.
                NotificationChannel(
                    NotificationChannels.SCHEDULE_NOTIFICATION,
                    context.getString(R.string.channel_schedule_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_schedule_reminder)
                    enableVibration(true)
                },
                NotificationChannel(
                    NotificationChannels.SCHEDULE_ALARM,
                    context.getString(R.string.channel_schedule_alarm),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_schedule_alarm)
                    enableVibration(true)
                },
                NotificationChannel(
                    NotificationChannels.SCHEDULE_OVERDUE,
                    context.getString(R.string.channel_schedule_overdue),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_schedule_overdue)
                    enableVibration(true)
                },
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


    /**
     * Android 14+ FGS notifications should publish immediately and remain service-scoped.
     * Users may dismiss ongoing FGS notifications on Android 14 except for a few media/call styles;
     * we keep a stop action on the timer notification so dismissal is not the only exit path.
     */
    private fun NotificationCompat.Builder.applyForegroundServiceDefaults(): NotificationCompat.Builder {
        setOngoing(true)
        setOnlyAlertOnce(true)
        setCategory(NotificationCompat.CATEGORY_SERVICE)
        setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        setPriority(NotificationCompat.PRIORITY_LOW)
        setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return this
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /**
     * Android 14 stopped granting USE_FULL_SCREEN_INTENT by default to apps outside the
     * alarm/calling categories; without this check the platform silently downgrades the
     * full-screen reminder to a heads-up notification.
     */
    fun canUseFullScreenIntents(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    /**
     * @param blockingOverlayShown true when the caller is also raising the forced-rest window for this
     *   same break, in which case the screen is already covered and the popup would be redundant.
     */
    fun showReminderDue(blockingOverlayShown: Boolean = false) {
        show(
            id = NotificationIds.BREAK_DUE,
            channel = NotificationChannels.REMINDER,
            title = context.getString(R.string.break_title),
            message = context.getString(R.string.break_waiting_message),
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = true,
            fullScreen = true,
            alertKind = LumenToastKind.WARNING,
            blockingOverlayShown = blockingOverlayShown,
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
            alertKind = LumenToastKind.TIMER,
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
            alertKind = LumenToastKind.SUCCESS,
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
            alertKind = LumenToastKind.TIMER,
        )
    }

    fun showScheduleReminder(occurrence: ScheduleOccurrenceEntity) {
        val isAlarm = occurrence.reminderMethod == ScheduleReminderMethod.ALARM.name
        show(
            id = ScheduleReminderScheduler(context).notificationIdFor(occurrence.id),
            channel = if (isAlarm) {
                NotificationChannels.SCHEDULE_ALARM
            } else {
                NotificationChannels.SCHEDULE_NOTIFICATION
            },
            title = occurrence.title,
            message = context.getString(
                R.string.schedule_notification_message,
                formatClockTime(occurrence.startAt),
            ),
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = false,
            fullScreen = isAlarm,
            // The alarm type is the user's own "this one must not be missed", which is exactly the
            // case the full-screen intent used to promise and no longer delivers: since Android 14
            // the platform denies USE_FULL_SCREEN_INTENT to non-alarm apps by default, so before the
            // popup existed a backgrounded alarm reminder showed nothing at all.
            alertKind = if (isAlarm) LumenToastKind.WARNING else LumenToastKind.TIMER,
        )
    }

    /**
     * Re-posts the overdue nag on the same notification id every round. Re-alerting on each round
     * depends on [show] never setting `setOnlyAlertOnce(true)`: adding that flag there would turn
     * the whole nag into a single silent alert without any visible failure.
     *
     * The "check off" action is what lets the user end the chain from the notification instead of
     * opening the app; it is safe to wire to the occurrence id per round because re-posting the same
     * id replaces the previous action rather than stacking one per round.
     */
    fun showScheduleOverdue(occurrence: ScheduleOccurrenceEntity) {
        show(
            id = ScheduleOverdueNagScheduler(context).notificationIdFor(occurrence.id),
            channel = NotificationChannels.SCHEDULE_OVERDUE,
            title = occurrence.title,
            message = context.getString(R.string.schedule_overdue_message, formatClockTime(occurrence.endAt)),
            priority = NotificationCompat.PRIORITY_HIGH,
            includeBreakActions = false,
            overdueOccurrenceId = occurrence.id,
            alertKind = LumenToastKind.WARNING,
        )
    }

    /**
     * Dismisses the nag for one occurrence. Tapping an action button does **not** trigger the
     * builder's `setAutoCancel(true)` — that only fires for the content intent — so the receiver
     * has to clear the notification itself once the to-do has been checked off.
     */
    fun cancelScheduleOverdue(occurrenceId: Long) {
        notificationManager.cancel(ScheduleOverdueNagScheduler(context).notificationIdFor(occurrenceId))
    }

    fun showUpdateAvailable(tagName: String, releaseName: String) {
        if (!canPostNotifications()) return
        val found = context.getString(R.string.about_update_found, tagName)
        show(
            id = NotificationIds.UPDATE_AVAILABLE,
            channel = NotificationChannels.STATUS,
            title = context.getString(R.string.about_update_status),
            message = if (releaseName.isBlank() || releaseName == tagName) found else "$found · $releaseName",
            priority = NotificationCompat.PRIORITY_DEFAULT,
            includeBreakActions = false,
        )
    }

    fun buildOngoingStatusNotification(state: RuntimeStateEntity? = null): Notification {
        val content = if (state != null) {
            ongoingLiveUpdateContent(state, System.currentTimeMillis())
        } else {
            // No runtime state in hand: this is a foreground-service promotion re-posting the
            // ongoing notification. Show what the Live Update already shows rather than the generic
            // placeholder, which would otherwise replace the current phase on every promotion and,
            // with the screen off, stay there — the 1 Hz tick that would correct it is skipped then.
            lastPublishedLiveUpdate.get()
                ?: ongoingLiveUpdateContent(null, System.currentTimeMillis())
        }
        return buildOngoingStatusNotification(content)
    }

    private fun buildOngoingStatusNotification(content: OngoingLiveUpdateContent): Notification {
        lastPublishedLiveUpdateSignature.set(content.signature)
        lastPublishedLiveUpdate.set(content)
        val builder = NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(content.title)
            .setContentText(content.message)
            .setSubText(content.subText)
            .setContentIntent(ongoingContentIntent)
            .applyForegroundServiceDefaults()
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setRequestPromotedOngoing(content.requestPromotedOngoing)
            .setStyle(content.progressStyle)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.notification_action_stop),
                ongoingStopIntent,
            )

        // Official Live Update chip: prefer chronometer for >= 2 minutes, short text otherwise.
        // Avoid setting both, so the chip does not fight between countdown and custom text.
        if (content.whenMillis != null) {
            builder
                .setWhen(content.whenMillis)
                .setUsesChronometer(true)
                .setChronometerCountDown(content.chronometerCountDown)
                .setShowWhen(true)
        } else {
            content.shortCriticalText?.let(builder::setShortCriticalText)
        }
        return builder.build()
    }

    fun buildProximityForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.PROXIMITY)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.proximity_check_running_title))
            .setContentText(context.getString(R.string.proximity_check_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.PROXIMITY_FOREGROUND))
            .applyForegroundServiceDefaults()
            .build()
    }

    fun buildLightMonitorForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.light_monitor_running_title))
            .setContentText(context.getString(R.string.light_monitor_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.LOW_LIGHT_FOREGROUND))
            .applyForegroundServiceDefaults()
            .build()
    }

    fun buildOverlayForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.overlay_running_title))
            .setContentText(context.getString(R.string.overlay_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.OVERLAY_FOREGROUND))
            .applyForegroundServiceDefaults()
            .build()
    }

    fun buildDeveloperDebugForegroundNotification(): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(context.getString(R.string.developer_debug_running_title))
            .setContentText(context.getString(R.string.developer_debug_running_message))
            .setContentIntent(openAppPendingIntent(NotificationIds.DEVELOPER_DEBUG_FOREGROUND))
            .applyForegroundServiceDefaults()
            .build()
    }

    /**
     * The foreground notification for the background alert popup. It carries the reminder's own copy
     * rather than a generic "showing an alert" line: it shares a channel with the other service
     * placeholders, and a silent status entry that repeats the card is less confusing than one that
     * describes the popup instead of the reminder.
     */
    fun buildAlertOverlayForegroundNotification(title: String, message: String): Notification {
        return NotificationCompat.Builder(context, NotificationChannels.STATUS)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(openAppPendingIntent(NotificationIds.ALERT_OVERLAY))
            .applyForegroundServiceDefaults()
            .build()
    }

    /**
     * @param blockingOverlayShown true when the caller is also raising the forced-rest window for this
     *   same warning.
     */
    fun showProximityWarning(ratioPercent: Int, blockingOverlayShown: Boolean = false) {
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
            alertKind = LumenToastKind.WARNING,
            blockingOverlayShown = blockingOverlayShown,
        )
    }

    /** @param blockingOverlayShown true when the caller is also raising the forced-rest window. */
    fun showEyeDryWarning(blockingOverlayShown: Boolean = false) {
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
            alertKind = LumenToastKind.WARNING,
            blockingOverlayShown = blockingOverlayShown,
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
            alertKind = LumenToastKind.WARNING,
        )
    }

    fun showOngoingStatus(state: RuntimeStateEntity) {
        if (!canPostNotifications()) return
        val nowMillis = System.currentTimeMillis()
        val content = ongoingLiveUpdateContent(state, nowMillis)
        if (!shouldPublishLiveUpdate(content)) return
        try {
            notificationManager.notify(
                NotificationIds.FOREGROUND_TIMER,
                buildOngoingStatusNotification(content),
            )
        } catch (_: SecurityException) {
            return
        }
    }

    fun cancelOngoingStatus() {
        lastPublishedLiveUpdateSignature.set(null)
        lastPublishedLiveUpdate.set(null)
        notificationManager.cancel(NotificationIds.FOREGROUND_TIMER)
    }

    fun cancelAllScheduled() {
        val manager = context.getSystemService(AlarmManager::class.java)
        scheduledAlarmActions.forEach { (id, action) ->
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

    // Deliberately no setOnlyAlertOnce(true): the overdue nag re-posts on the same id every round
    // and relies on each notify() re-alerting (sound/vibration). Setting it would silently reduce
    // the whole nag chain to a single alert.
    //
    // Two independent deliveries, not one fallback for the other. The notification is the durable
    // record — it survives the reminder, lands in the shade, and is the only path on a device that
    // never granted the overlay permission. The popup is what makes the reminder *arrive*: a shade
    // entry is invisible to someone looking at another app. `alertKind` is null for the few posts
    // that are not reminders at all (an update notice), and `blockingOverlayShown` is set by callers
    // that are already covering the screen with a full-screen forced-rest window, where a card on top
    // would only obscure the thing the user has to act on.
    private fun show(
        id: Int,
        channel: String,
        title: String,
        message: String,
        priority: Int,
        includeBreakActions: Boolean,
        fullScreen: Boolean = false,
        overdueOccurrenceId: Long? = null,
        alertKind: LumenToastKind? = null,
        blockingOverlayShown: Boolean = false,
    ) {
        postNotification(
            id = id,
            channel = channel,
            title = title,
            message = message,
            priority = priority,
            includeBreakActions = includeBreakActions,
            fullScreen = fullScreen,
            overdueOccurrenceId = overdueOccurrenceId,
        )
        if (alertKind != null && !blockingOverlayShown) {
            LumenAlertPresenter.present(context, title, message, alertKind)
        }
    }

    private fun postNotification(
        id: Int,
        channel: String,
        title: String,
        message: String,
        priority: Int,
        includeBreakActions: Boolean,
        fullScreen: Boolean,
        overdueOccurrenceId: Long?,
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
        if (fullScreen && canUseFullScreenIntents()) {
            builder.setFullScreenIntent(openAppPendingIntent(id + FULL_SCREEN_REQUEST_CODE_OFFSET), true)
        }
        if (includeBreakActions) {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.start_break),
                actionPendingIntent(NotificationIds.START_BREAK_ACTION, ReminderActionReceiver.ACTION_START_BREAK),
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.skip_break),
                actionPendingIntent(NotificationIds.SKIP_BREAK_ACTION, ReminderActionReceiver.ACTION_SKIP_BREAK),
            )
        }
        if (overdueOccurrenceId != null) {
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.schedule_overdue_complete),
                overdueCompletePendingIntent(overdueOccurrenceId),
            )
        }
        try {
            notificationManager.notify(id, builder.build())
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
        ids.forEach { id -> notificationManager.cancel(id) }
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
        // Explicit component + package keeps notification launches safe under Android 16 intent redirection hardening.
        return Intent(context, MainActivity::class.java)
            .setClass(context, MainActivity::class.java)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private data class OngoingLiveUpdateContent(
        val title: String,
        val message: String,
        val subText: String? = null,
        val progressStyle: ProgressStyle,
        val requestPromotedOngoing: Boolean,
        val shortCriticalText: String? = null,
        val whenMillis: Long? = null,
        val chronometerCountDown: Boolean = true,
        val signature: String,
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
                phaseKey = "null",
            )
        }

        return when (state.activeEngine) {
            ActiveEngine.REMINDER.name -> when (state.reminderPhase) {
                ReminderPhase.WORKING.name -> timedLiveUpdate(
                    title = context.getString(R.string.live_update_title_working),
                    message = remainingStatusMessage(
                        templateRes = R.string.live_update_message_working,
                        endAt = state.nextReminderAt,
                        nowMillis = nowMillis,
                    ),
                    subText = context.getString(R.string.live_update_subtext_next_break),
                    startAt = state.reminderStartedAt,
                    endAt = state.nextReminderAt,
                    nowMillis = nowMillis,
                    milestoneAt = state.nextPreAlertAt.takeIf { it > state.reminderStartedAt && it < state.nextReminderAt },
                    phaseKey = "reminder-working",
                    staticMessage = context.getString(R.string.ongoing_status_working),
                )
                ReminderPhase.PRE_ALERT.name -> timedLiveUpdate(
                    title = context.getString(R.string.live_update_title_pre_alert),
                    message = remainingStatusMessage(
                        templateRes = R.string.live_update_message_pre_alert,
                        endAt = state.nextReminderAt,
                        nowMillis = nowMillis,
                    ),
                    subText = context.getString(R.string.live_update_subtext_prepare_rest),
                    startAt = state.reminderStartedAt,
                    endAt = state.nextReminderAt,
                    nowMillis = nowMillis,
                    milestoneAt = state.nextPreAlertAt.takeIf { it > state.reminderStartedAt && it < state.nextReminderAt },
                    phaseKey = "reminder-pre-alert",
                    staticMessage = context.getString(R.string.pre_alert_notification_message),
                )
                ReminderPhase.AWAITING_ACTION.name -> completedLiveUpdate(
                    title = context.getString(R.string.live_update_title_break_due),
                    message = context.getString(R.string.live_update_message_break_due),
                    shortCriticalText = context.getString(R.string.live_update_chip_due),
                    phaseKey = "reminder-awaiting",
                )
                ReminderPhase.RESTING.name -> timedLiveUpdate(
                    title = context.getString(R.string.live_update_title_resting),
                    message = remainingStatusMessage(
                        templateRes = R.string.live_update_message_resting,
                        endAt = state.breakEndAt,
                        nowMillis = nowMillis,
                    ),
                    subText = context.getString(R.string.live_update_subtext_rest),
                    startAt = state.breakStartedAt,
                    endAt = state.breakEndAt,
                    nowMillis = nowMillis,
                    phaseKey = "reminder-resting",
                    staticMessage = context.getString(R.string.ongoing_status_resting),
                )
                ReminderPhase.PAUSED.name -> indeterminateLiveUpdate(
                    title = context.getString(R.string.live_update_title_paused),
                    message = context.getString(R.string.ongoing_status_paused),
                    shortCriticalText = context.getString(R.string.live_update_chip_paused),
                    requestPromotedOngoing = false,
                    phaseKey = "reminder-paused",
                )
                else -> indeterminateLiveUpdate(
                    title = context.getString(R.string.ongoing_timer_title),
                    message = context.getString(R.string.ongoing_timer_message),
                    shortCriticalText = context.getString(R.string.live_update_chip_running),
                    phaseKey = "reminder-other",
                )
            }
            ActiveEngine.POMODORO.name -> when (state.pomodoroPhase) {
                PomodoroPhase.FOCUS.name -> timedLiveUpdate(
                    title = context.getString(R.string.live_update_title_pomodoro_focus),
                    message = remainingStatusMessage(
                        templateRes = R.string.live_update_message_pomodoro_focus,
                        endAt = state.pomodoroPhaseEndAt,
                        nowMillis = nowMillis,
                    ),
                    subText = context.getString(R.string.live_update_subtext_cycle, state.pomodoroCycleIndex.coerceAtLeast(1)),
                    startAt = state.pomodoroPhaseStartedAt,
                    endAt = state.pomodoroPhaseEndAt,
                    nowMillis = nowMillis,
                    phaseKey = "pomodoro-focus-${state.pomodoroCycleIndex}",
                    staticMessage = context.getString(R.string.ongoing_status_pomodoro),
                )
                PomodoroPhase.SHORT_BREAK.name -> timedLiveUpdate(
                    title = context.getString(R.string.live_update_title_pomodoro_short_break),
                    message = remainingStatusMessage(
                        templateRes = R.string.live_update_message_pomodoro_break,
                        endAt = state.pomodoroPhaseEndAt,
                        nowMillis = nowMillis,
                    ),
                    subText = context.getString(R.string.live_update_subtext_cycle, state.pomodoroCycleIndex.coerceAtLeast(1)),
                    startAt = state.pomodoroPhaseStartedAt,
                    endAt = state.pomodoroPhaseEndAt,
                    nowMillis = nowMillis,
                    phaseKey = "pomodoro-short-${state.pomodoroCycleIndex}",
                    staticMessage = context.getString(R.string.ongoing_status_pomodoro),
                )
                PomodoroPhase.LONG_BREAK.name -> timedLiveUpdate(
                    title = context.getString(R.string.live_update_title_pomodoro_long_break),
                    message = remainingStatusMessage(
                        templateRes = R.string.live_update_message_pomodoro_break,
                        endAt = state.pomodoroPhaseEndAt,
                        nowMillis = nowMillis,
                    ),
                    subText = context.getString(R.string.live_update_subtext_cycle, state.pomodoroCycleIndex.coerceAtLeast(1)),
                    startAt = state.pomodoroPhaseStartedAt,
                    endAt = state.pomodoroPhaseEndAt,
                    nowMillis = nowMillis,
                    phaseKey = "pomodoro-long-${state.pomodoroCycleIndex}",
                    staticMessage = context.getString(R.string.ongoing_status_pomodoro),
                )
                else -> indeterminateLiveUpdate(
                    title = context.getString(R.string.pomodoro_title),
                    message = context.getString(R.string.ongoing_status_pomodoro),
                    shortCriticalText = context.getString(R.string.live_update_chip_running),
                    phaseKey = "pomodoro-other",
                )
            }
            else -> indeterminateLiveUpdate(
                title = context.getString(R.string.ongoing_timer_title),
                message = context.getString(R.string.ongoing_timer_message),
                shortCriticalText = context.getString(R.string.live_update_chip_running),
                phaseKey = "idle-other",
            )
        }
    }

    private fun timedLiveUpdate(
        title: String,
        message: String,
        subText: String? = null,
        startAt: Long,
        endAt: Long,
        nowMillis: Long,
        milestoneAt: Long? = null,
        phaseKey: String,
        staticMessage: String? = null,
    ): OngoingLiveUpdateContent {
        val totalMillis = (endAt - startAt).coerceAtLeast(0L)
        if (startAt <= 0L || endAt <= 0L || totalMillis <= 0L) {
            return indeterminateLiveUpdate(
                title = title,
                message = message,
                subText = subText,
                shortCriticalText = context.getString(R.string.live_update_chip_running),
                phaseKey = "$phaseKey-indeterminate",
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

        milestoneAt?.let { markerAt ->
            val markerProgress = (((markerAt - startAt).coerceIn(0L, totalMillis) * LIVE_UPDATE_PROGRESS_MAX) / totalMillis)
                .toInt()
                .coerceIn(1, LIVE_UPDATE_PROGRESS_MAX)
            style.addProgressPoint(ProgressStyle.Point(markerProgress))
        }

        val remainingMillis = (endAt - nowMillis).coerceAtLeast(0L)
        val useChronometer = remainingMillis >= LIVE_UPDATE_CHRONOMETER_MIN_MILLIS
        // When chronometer is shown, keep body text stable so the system countdown owns the ticking.
        val resolvedMessage = if (useChronometer) {
            staticMessage ?: message
        } else {
            message
        }
        val shortText = if (useChronometer) {
            null
        } else {
            remainingChipText(endAt, nowMillis)
        }
        // Coalesce progress updates so tiny millisecond jitter does not spam NotificationManager.
        val progressBucket = progress / LIVE_UPDATE_PROGRESS_BUCKET
        val remainingBucketSeconds = if (useChronometer) {
            // Chronometer ticks on-device; only republish when progress meaningfully moves.
            -1L
        } else {
            remainingMillis / 1_000L
        }
        return OngoingLiveUpdateContent(
            title = title,
            message = resolvedMessage,
            subText = subText,
            progressStyle = style,
            requestPromotedOngoing = true,
            shortCriticalText = shortText,
            whenMillis = if (useChronometer) endAt else null,
            chronometerCountDown = true,
            signature = "$phaseKey|$progressBucket|$remainingBucketSeconds|$useChronometer|$endAt|" +
                "$title|$resolvedMessage|${subText.orEmpty()}|${shortText.orEmpty()}",
        )
    }

    private fun completedLiveUpdate(
        title: String,
        message: String,
        shortCriticalText: String,
        phaseKey: String,
    ): OngoingLiveUpdateContent {
        val style = ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(LIVE_UPDATE_PROGRESS_MAX)
            .addProgressSegment(ProgressStyle.Segment(LIVE_UPDATE_PROGRESS_MAX))
        return OngoingLiveUpdateContent(
            title = title,
            message = message,
            subText = context.getString(R.string.live_update_subtext_action_needed),
            progressStyle = style,
            requestPromotedOngoing = true,
            shortCriticalText = shortCriticalText,
            signature = "$phaseKey|$title|$message|$shortCriticalText",
        )
    }

    private fun indeterminateLiveUpdate(
        title: String,
        message: String,
        shortCriticalText: String?,
        requestPromotedOngoing: Boolean = true,
        subText: String? = null,
        phaseKey: String,
    ): OngoingLiveUpdateContent {
        return OngoingLiveUpdateContent(
            title = title,
            message = message,
            subText = subText,
            progressStyle = ProgressStyle().setProgressIndeterminate(true),
            requestPromotedOngoing = requestPromotedOngoing,
            shortCriticalText = shortCriticalText,
            signature = "$phaseKey|$requestPromotedOngoing|$title|$message|" +
                "${subText.orEmpty()}|${shortCriticalText.orEmpty()}",
        )
    }

    private fun remainingStatusMessage(templateRes: Int, endAt: Long, nowMillis: Long): String {
        val remaining = formatRemainingCompact(endAt, nowMillis)
            ?: return context.getString(R.string.live_update_message_due_now)
        return context.getString(templateRes, remaining)
    }

    private fun remainingChipText(endAt: Long, nowMillis: Long): String? {
        return formatRemainingCompact(endAt, nowMillis)
            ?: context.getString(R.string.live_update_chip_due)
    }

    private fun formatRemainingCompact(endAt: Long, nowMillis: Long): String? {
        if (endAt <= 0L) return null
        val remainingSeconds = ((endAt - nowMillis).coerceAtLeast(0L) + 999L) / 1_000L
        if (remainingSeconds <= 0L) return null
        val hours = remainingSeconds / 3_600L
        val minutes = (remainingSeconds % 3_600L) / 60L
        val seconds = remainingSeconds % 60L
        return when {
            hours > 0L -> context.getString(
                R.string.live_update_chip_hours_minutes,
                hours,
                minutes,
            )
            minutes > 0L -> context.getString(
                R.string.live_update_chip_minutes_seconds,
                minutes,
                seconds,
            )
            else -> context.getString(R.string.live_update_chip_seconds, seconds)
        }
    }

    private fun shouldPublishLiveUpdate(content: OngoingLiveUpdateContent): Boolean {
        val previous = lastPublishedLiveUpdateSignature.get()
        return previous != content.signature
    }

    private fun formatClockTime(millis: Long): String {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CLOCK_TIME_FORMATTER)
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

    // Request code is the occurrence's own notification id rather than a shared constant because
    // PendingIntent identity is (requestCode, Intent.filterEquals) and filterEquals ignores extras:
    // one shared code would make every occurrence's button the same PendingIntent, so the last one
    // posted would win and tapping any nag would check off whichever to-do was posted most recently.
    // It does not collide with the alarms, which reuse that id with a different action.
    private fun overdueCompletePendingIntent(occurrenceId: Long): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            ScheduleOverdueNagScheduler(context).notificationIdFor(occurrenceId),
            explicitReceiverIntent(
                AlarmReceiver.ACTION_SCHEDULE_OVERDUE_COMPLETE,
                AlarmReceiver::class.java,
            ).putExtra(ScheduleOverdueNagScheduler.EXTRA_OCCURRENCE_ID, occurrenceId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun explicitReceiverIntent(action: String, receiverClass: Class<*>): Intent {
        return Intent(context, receiverClass)
            .setAction(action)
            .setPackage(context.packageName)
    }

    private companion object {
        const val LIVE_UPDATE_PROGRESS_MAX = 1_000
        const val LIVE_UPDATE_PROGRESS_BUCKET = 10
        const val LIVE_UPDATE_CHRONOMETER_MIN_MILLIS = 2 * 60_000L
        const val FULL_SCREEN_REQUEST_CODE_OFFSET = 100
        val CLOCK_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
        val scheduledAlarmActions = listOf(
            NotificationIds.PRE_ALERT to AlarmReceiver.ACTION_PRE_ALERT,
            NotificationIds.BREAK_DUE to AlarmReceiver.ACTION_BREAK_DUE,
            NotificationIds.BREAK_DONE to AlarmReceiver.ACTION_BREAK_DONE,
            NotificationIds.POMODORO to AlarmReceiver.ACTION_POMODORO,
        )
    }

}
