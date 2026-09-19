package com.projectlumen.app.core.services

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
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStore

/**
 * Notification surface of the Quark check-in guard.
 *
 * Kept out of [NotificationService] rather than folded into it: the guard needs its own channel set
 * (a silent-vibrate nudge, an alert that keeps the system sound, a quiet countdown) and its own id
 * block, and the timer service's channel list is already long enough that a single shared owner would
 * make every change to one feature risk the other.
 *
 * Every entry point here is called from a broadcast receiver or a foreground service, so nothing may
 * assume the app is on screen, and [ensureChannels] runs before the first post on each of those paths:
 * posting into a channel that does not exist yet drops the notification without any error.
 */
class QuarkKeeperNotifications(private val context: Context) {

    private val notificationManager by lazy { NotificationManagerCompat.from(context) }

    /**
     * Creates only the guard's own three channels. The timer and eye-care ids belong to
     * [NotificationService]; re-declaring one of them here would push these defaults back over the
     * importance and sound the user picked in system settings.
     */
    fun ensureChannels() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    NotificationChannels.QUARK_KEEPER_REMINDER,
                    context.getString(R.string.channel_quark_keeper_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_quark_keeper_reminder)
                    // Same shape as the eye-reminder channel: a nudge rather than an alarm. The one
                    // case that genuinely has to be heard has its own channel below.
                    setSound(null, null)
                    enableVibration(true)
                },
                NotificationChannel(
                    NotificationChannels.QUARK_KEEPER_ALARM,
                    context.getString(R.string.channel_quark_keeper_alarm),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_quark_keeper_alarm)
                    // Unlike the other guard channels the default sound is kept: the deadline alert
                    // exists precisely to interrupt, and a silent one would be indistinguishable from
                    // the reminder that already failed to get the user to check in.
                    enableVibration(true)
                },
                NotificationChannel(
                    NotificationChannels.QUARK_KEEPER_ONGOING,
                    context.getString(R.string.channel_quark_keeper_ongoing),
                    // High rather than low: the countdown is a state the user has to be able to see the
                    // moment it becomes true, and a low-importance notification stays collapsed in the
                    // shade until the user goes looking for it. That matters most on the evening the
                    // device is only switched on after the reminder node — the catch-up then has nothing
                    // but this notification to put in front of the user until the deadline alarm fires.
                    // The alert that actually interrupts has its own channel above, so this one stays
                    // silent: it becomes visible, it does not demand attention.
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_quark_keeper_ongoing)
                    setSound(null, null)
                    enableVibration(false)
                },
            ),
        )
    }

    fun showDailyReminder() {
        if (!canPostNotifications()) return
        val builder = baseBuilder(
            id = NotificationIds.QUARK_KEEPER_DAILY,
            channel = NotificationChannels.QUARK_KEEPER_REMINDER,
            title = context.getString(R.string.quark_keeper_daily_reminder_title),
            message = context.getString(R.string.quark_keeper_daily_reminder_message),
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_REMINDER,
        )
            .setAutoCancel(true)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.quark_keeper_notification_action_go),
                goCheckInPendingIntent(),
            )
        notify(NotificationIds.QUARK_KEEPER_DAILY, builder.build())
    }

    /**
     * The deadline alert: full-screen when the platform still allows it, maximum priority otherwise.
     *
     * Deliberately without `setAutoCancel(true)`. Auto-cancel only fires for the content intent, so
     * tapping the body would clear the notification without checking anything in — and this is the one
     * notification whose entire job is to stay in the way until the user answers it with a button.
     */
    fun showForceAlert(remainingHours: Int) {
        if (!canPostNotifications()) return
        val builder = baseBuilder(
            id = NotificationIds.QUARK_KEEPER_FORCED_ALERT,
            channel = forceAlertChannel(),
            title = context.getString(R.string.quark_keeper_alert_title),
            message = context.getString(R.string.quark_keeper_alert_message, remainingPhrase(remainingHours)),
            priority = NotificationCompat.PRIORITY_MAX,
            category = NotificationCompat.CATEGORY_ALARM,
        )
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.quark_keeper_notification_action_go),
                goCheckInPendingIntent(),
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                snoozeLabel(),
                snoozePendingIntent(),
            )
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.quark_keeper_notification_action_check_in),
                markDonePendingIntent(),
            )
        // Without USE_FULL_SCREEN_INTENT the platform silently drops a full-screen intent and posts a
        // heads-up notification instead. That downgrade is the intended fallback — the alert still
        // arrives at max priority on whichever alert channel [forceAlertChannel] picked, and without the
        // permission there is nothing else to do beyond not asking for the full-screen behaviour.
        if (canUseFullScreenIntents()) {
            builder.setFullScreenIntent(
                openAppPendingIntent(NotificationIds.QUARK_KEEPER_FORCED_ALERT + FULL_SCREEN_REQUEST_CODE_OFFSET),
                true,
            )
        }
        // Vibration and sound are channel-owned from API 26 on, so they are configured once in
        // ensureChannels() rather than per notification, where setVibrate()/setSound() would be ignored
        // on every device this app supports (minSdk 29).
        notify(NotificationIds.QUARK_KEEPER_FORCED_ALERT, builder.build())
    }

    fun showOngoing(remainingHours: Int) {
        if (!canPostNotifications()) return
        notify(NotificationIds.QUARK_KEEPER_ONGOING, buildOngoing(remainingHours))
    }

    /**
     * The countdown, exposed as a builder result because [QuarkKeeperAlertService] promotes itself to
     * the foreground with this very notification and must not create a second entry in the shade.
     */
    fun buildOngoing(remainingHours: Int): Notification {
        return baseBuilder(
            id = NotificationIds.QUARK_KEEPER_ONGOING,
            channel = NotificationChannels.QUARK_KEEPER_ONGOING,
            title = context.getString(R.string.quark_keeper_ongoing_title),
            message = context.getString(R.string.quark_keeper_ongoing_message, remainingPhrase(remainingHours)),
            // Ignored from API 26 on, where the channel decides; kept in step with the channel's
            // importance so the two do not read as contradicting each other.
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_SERVICE,
        )
            // setOngoing is what makes it non-dismissible. The action is the way out: a countdown the
            // user could swipe away would read as the guard having given up on the day.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Meaningful on the foreground-service path, where Android 12+ may otherwise defer a
            // deferrable notification for ~10s and leave the countdown invisible.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                R.drawable.ic_launcher_foreground,
                context.getString(R.string.quark_keeper_notification_action_go),
                goCheckInPendingIntent(),
            )
            .build()
    }

    fun cancelOngoing() {
        notificationManager.cancel(NotificationIds.QUARK_KEEPER_ONGOING)
    }

    /** Everything this layer posts, so a finished or disabled day leaves no notification behind. */
    fun cancelAll() {
        postedNotificationIds.forEach(notificationManager::cancel)
    }

    private fun baseBuilder(
        id: Int,
        channel: String,
        title: String,
        message: String,
        priority: Int,
        category: String,
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification_lumen)
            .setContentTitle(title)
            .setContentText(message)
            // The notification id doubles as the request code, as in NotificationService. It cannot
            // collide with the guard's alarms despite sharing the number: those are broadcasts to
            // QuarkKeeperReceiver while this is an activity launch, and PendingIntent identity compares
            // the intent itself, not just the request code.
            .setContentIntent(openAppPendingIntent(id))
            .setPriority(priority)
            .setCategory(category)
    }

    /**
     * The duration phrase both bodies interpolate.
     *
     * Callers only ever hand over whole hours, and the countdown is read at least once in the final
     * hour, where the integer is already 0 — left alone that renders as "0 h left before the streak
     * breaks" and reads as "too late" at the exact moment the user can still fix it.
     */
    private fun remainingPhrase(remainingHours: Int): String {
        if (remainingHours < 1) return context.getString(R.string.quark_keeper_remaining_less_than_hour)
        return context.getString(R.string.quark_keeper_remaining_hours, remainingHours)
    }

    /**
     * The snooze button has to name the delay it will actually arm, and that delay is a user setting
     * (5..60 minutes), so it is read from the guard's own store instead of being hard-coded here — a
     * fixed number would make the button lie the moment the user changes it.
     */
    private fun snoozeLabel(): String {
        val snoozeMinutes = QuarkKeeperStore.snapshot().settings.snoozeMinutes
        return context.getString(R.string.quark_keeper_notification_action_snooze, snoozeMinutes)
    }

    /**
     * The channel the deadline alert is posted on, which is what makes the guard's "提醒声音" switch mean
     * something.
     *
     * A channel's sound is fixed when it is created and cannot be overridden per notification on any
     * device this app supports (minSdk 29), so a silent alert can only be a silent channel. Both
     * channels are IMPORTANCE_HIGH, so turning the sound off takes away the noise and nothing else: the
     * alert still arrives as a heads-up notification and still vibrates, which is what the PRD's forced
     * node actually needs.
     */
    private fun forceAlertChannel(): String {
        return if (QuarkKeeperStore.snapshot().settings.soundEnabled) {
            NotificationChannels.QUARK_KEEPER_ALARM
        } else {
            NotificationChannels.QUARK_KEEPER_REMINDER
        }
    }

    private fun goCheckInPendingIntent(): PendingIntent {
        return actionPendingIntent(NotificationIds.QUARK_KEEPER_ACTION_GO_CHECK_IN, ACTION_GO_CHECK_IN)
    }

    private fun snoozePendingIntent(): PendingIntent {
        return actionPendingIntent(NotificationIds.QUARK_KEEPER_ACTION_SNOOZE, ACTION_SNOOZE)
    }

    private fun markDonePendingIntent(): PendingIntent {
        return actionPendingIntent(NotificationIds.QUARK_KEEPER_ACTION_MARK_DONE, ACTION_MARK_DONE)
    }

    /**
     * Action request codes come from the guard's own block rather than being invented here: a
     * PendingIntent's identity is (requestCode, Intent.filterEquals), so a code shared with an armed
     * alarm whose action also matched would let the two replace each other, and the notification ids
     * above are exactly what the alarm chain reuses.
     */
    private fun actionPendingIntent(requestCode: Int, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            explicitReceiverIntent(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun explicitReceiverIntent(action: String): Intent {
        // Explicit component + package keeps the buttons safe under Android 16 intent redirection
        // hardening, which blocks implicit broadcasts coming out of a notification.
        return Intent(context, QuarkKeeperReceiver::class.java)
            .setAction(action)
            .setPackage(context.packageName)
    }

    private fun openAppPendingIntent(requestCode: Int): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            openAppIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): Intent {
        // Explicit component + package keeps notification launches safe under Android 16 intent
        // redirection hardening.
        return Intent(context, MainActivity::class.java)
            .setClass(context, MainActivity::class.java)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    /**
     * Android 14 stopped granting USE_FULL_SCREEN_INTENT by default to apps outside the alarm/calling
     * categories; without this check the platform silently downgrades the alert to a heads-up
     * notification.
     */
    private fun canUseFullScreenIntents(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        return context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun notify(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (_: SecurityException) {
            // The permission can be revoked between canPostNotifications() and the post itself.
            return
        }
    }

    private companion object {
        const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"

        // Duplicated from QuarkKeeperReceiver, which cannot be referenced from here without a circular
        // initialisation between the two. The receiver's companion carries the matching constants.
        const val ACTION_GO_CHECK_IN = "com.projectlumen.app.action.QUARK_KEEPER_GO_CHECK_IN"
        const val ACTION_SNOOZE = "com.projectlumen.app.action.QUARK_KEEPER_SNOOZE"
        const val ACTION_MARK_DONE = "com.projectlumen.app.action.QUARK_KEEPER_MARK_DONE"

        /** Matches NotificationService: the full-screen intent gets its own code beside the content one. */
        const val FULL_SCREEN_REQUEST_CODE_OFFSET = 100

        // Only the ids this layer actually posts. QUARK_KEEPER_SNOOZE, _MIDNIGHT and _RETURN_MONITOR are
        // alarm request codes owned by QuarkKeeperAlarmScheduler, not notifications.
        val postedNotificationIds = listOf(
            NotificationIds.QUARK_KEEPER_DAILY,
            NotificationIds.QUARK_KEEPER_FORCED_ALERT,
            NotificationIds.QUARK_KEEPER_ONGOING,
        )
    }
}
