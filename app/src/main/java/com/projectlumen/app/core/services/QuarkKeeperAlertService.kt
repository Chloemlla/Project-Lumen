package com.projectlumen.app.core.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.R
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperClock
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperRemaining
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSettings
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStore

/**
 * The last-chance alert for the Quark check-in guard: a full-screen
 * [WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY] window that stays until the user answers it.
 *
 * A foreground service rather than a plain overlay, for two reasons. Android 10+ only lets a
 * background app add an overlay window while it is in the foreground *as a service* — an overlay
 * posted from a broadcast receiver is silently dropped. And the alert must survive the process being
 * trimmed while the user reads it, which is exactly what the foreground promotion buys.
 *
 * The division of labour with the notification layer is deliberate and easy to get wrong:
 *
 * - **Waking the screen is not this service's job.** A full-screen intent notification (posted by
 *   [QuarkKeeperNotifications]) is the only thing that may turn a dark screen on, and it also covers
 *   the case where the overlay permission was never granted, so the alert still lands. This service
 *   only keeps the screen awake while its window is up, via `FLAG_KEEP_SCREEN_ON`.
 * - **The buttons do not act on the guard's state.** They broadcast to
 *   [QuarkKeeperReceiver], which owns the snooze chain, the check-in write and the alarms. Keeping
 *   the writes in the receiver means the alert, the notification actions and the in-app buttons all
 *   funnel through one implementation instead of three that drift.
 */
class QuarkKeeperAlertService : Service() {

    private lateinit var app: ProjectLumenApplication
    private lateinit var notifications: QuarkKeeperNotifications
    private var overlayView: View? = null

    /**
     * Drives the one scheduled rebuild the alert needs — the snooze cutoff — and is cleared whenever the
     * window is rebuilt, so a service answering several alerts in a night never leaves an older timer to
     * fire against a newer window.
     */
    private val cutoffHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        app = application as ProjectLumenApplication
        notifications = QuarkKeeperNotifications(this)
        // The alert and the countdown it interrupts share channels, and this service can be started
        // by an alarm on a process that was just spun up, so the channels are created before the
        // foreground promotion tries to post into one.
        notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Without the overlay permission there is nothing to show. The denial is already surfaced by
        // the full-screen-intent notification, so this path stays quiet and simply gives up the slot.
        if (!Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val remainingHours = resolveRemainingHours(intent)
        val promoted = ForegroundServiceController.promote(
            service = this,
            // The same id the countdown is posted under: the ongoing notification *is* the foreground
            // notification here, so promoting must not create a second entry in the shade.
            notificationId = NotificationIds.QUARK_KEEPER_ONGOING,
            notificationProvider = { notifications.buildOngoing(remainingHours) },
            foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        if (!promoted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (!showOverlay()) {
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cutoffHandler.removeCallbacksAndMessages(null)
        removeOverlay()
        // Detach rather than let the platform cancel: this service shares its notification with the
        // guard's ongoing countdown, and the day is still unchecked-in when the alert is answered.
        // The default teardown would delete a countdown the user still needs, leaving the next
        // notification to arrive only at the next alarm. The countdown is cancelled by the paths that
        // actually finish the day (check-in, guard disabled), not by the alert closing.
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun resolveRemainingHours(intent: Intent?): Int {
        val fromIntent = intent?.getIntExtra(EXTRA_REMAINING_HOURS, -1) ?: -1
        if (fromIntent >= 0) return fromIntent
        // No extra: derive the number from the clock instead of showing a placeholder. A wrong
        // countdown is worse than none, because this figure is what the user decides on.
        return QuarkKeeperClock.remainingUntilEndOfDay(System.currentTimeMillis()).hours
    }

    private fun showOverlay(): Boolean {
        // A second start while the alert is up means the deadline moved (a snooze expired, or a retry
        // landed). The window is rebuilt rather than left alone: the time left is the figure the user
        // decides on, so a stale one would be worse than the flicker of replacing it.
        removeOverlay()
        val windowManager = getSystemService(WindowManager::class.java) ?: return false
        val nowMillis = System.currentTimeMillis()
        val settings = QuarkKeeperStore.snapshot().settings
        val remaining = QuarkKeeperClock.remainingUntilEndOfDay(nowMillis)
        val view = QuarkKeeperAlertOverlayView.create(
            context = this,
            // Precisely, from the clock: the alert lands at the deadline, when the remainder is
            // usually well under an hour, so the whole-hours figure the notification uses would read
            // as "0 h left" at the exact moment the day can still be saved.
            remainingText = remainingLabel(remaining),
            // The same figure in minutes, for the disabled snooze button to state as its reason.
            // Handed over as a number rather than derived from the label: the label is localized, and
            // reading a count back out of it would mean parsing a sentence the translator owns.
            remainingMinutes = remaining.totalMinutes,
            snoozeAllowed = QuarkKeeperClock.isSnoozeAllowed(settings, nowMillis),
            snoozeMinutes = settings.snoozeMinutes,
            onGoCheckIn = { dispatch(ACTION_GO_CHECK_IN) },
            onSnooze = { dispatch(ACTION_SNOOZE) },
            onMarkDone = { dispatch(ACTION_MARK_DONE) },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayWindowFlags(),
            PixelFormat.TRANSLUCENT,
        )
        val added = runCatching { windowManager.addView(view, params) }
            .onFailure(app::recordHandledFailure)
            .isSuccess
        if (!added) return false
        overlayView = view
        forceImmersive(view)
        scheduleSnoozeCutoffRebuild(settings, nowMillis)
        return true
    }

    /**
     * Rebuilds the alert the moment the snooze cutoff arrives.
     *
     * The cutoff is a change to the screen the user is already looking at, not only to the next tap. An
     * alert raised at the deadline and left alone crosses it while still on display, and a button still
     * offering "remind me in 15 minutes" at 23:45 would promise a re-alert the guard has decided not to
     * arm — the receiver would refuse it, so the screen would be lying until the user found out by
     * tapping. Rebuilding the window is already how this service handles a figure that has moved, so the
     * cutoff rides the same path.
     *
     * Scheduled only while a snooze is still on offer: [QuarkKeeperClock.nextOccurrenceOf] answers with
     * tomorrow's cutoff once today's has passed, and a timer left running until then would rebuild the
     * alert at a moment nobody asked for.
     */
    private fun scheduleSnoozeCutoffRebuild(settings: QuarkKeeperSettings, nowMillis: Long) {
        cutoffHandler.removeCallbacksAndMessages(null)
        if (!QuarkKeeperClock.isSnoozeAllowed(settings, nowMillis)) return
        val delayMillis = QuarkKeeperClock.nextOccurrenceOf(settings.snoozeCutoffMinuteOfDay, nowMillis) - nowMillis
        if (delayMillis <= 0L) return
        cutoffHandler.postDelayed({ showOverlay() }, delayMillis)
    }

    /**
     * The time left before the streak breaks, in the most precise unit the guard's strings offer.
     *
     * The unit ladder (hours+minutes, minutes, "less than a minute") exists because the deadline
     * alert fires close enough to midnight that minutes are the only meaningful unit, while the
     * notification posted alongside it is deliberately whole-hours.
     */
    private fun remainingLabel(remaining: QuarkKeeperRemaining): String {
        return when {
            remaining.totalMinutes < 1 -> getString(R.string.quark_keeper_remaining_less_than_minute)
            remaining.hours < 1 -> getString(R.string.quark_keeper_remaining_minutes, remaining.minutes)
            else -> getString(
                R.string.quark_keeper_remaining_hours_minutes,
                remaining.hours,
                remaining.minutes,
            )
        }
    }

    /**
     * Hands the user's answer to [QuarkKeeperReceiver] and closes the alert.
     *
     * The broadcast is explicit (component + package) so no other app can receive it and so an
     * implicit-broadcast restriction cannot silently eat it. Every button closes the window
     * afterwards: the alert's job is to force a decision, and once one is made it must get out of
     * the way — the "go check in" path in particular would otherwise cover the very app the user was
     * sent to. If the check-in never happens, the return-monitor alarm re-alerts.
     */
    private fun dispatch(action: String) {
        val intent = Intent(this, QuarkKeeperReceiver::class.java)
            .setAction(action)
            .setPackage(packageName)
        runCatching { sendBroadcast(intent) }.onFailure(app::recordHandledFailure)
        stopSelf()
    }

    /**
     * The alert keeps the screen on and draws over the system bars, but takes no input focus:
     * [WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE] only removes key/IME focus, it does not make
     * the window touch-transparent, so the buttons still receive taps (the same flag combination
     * already carries EyeProtectionOverlayService's two buttons). What it buys is that the alert
     * cannot steal the keyboard from an app underneath, which on this screen would be a nuisance
     * rather than a safety win.
     */
    private fun overlayWindowFlags(): Int {
        val modernFlags =
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            modernFlags
        } else {
            modernFlags or legacyFullscreenWindowFlags()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyFullscreenWindowFlags(): Int {
        return WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
    }

    /**
     * Edge-to-edge with the system bars hidden, so the alert cannot be mistaken for a dismissible
     * card or partially obscured by a status bar the user might read while deciding.
     */
    private fun forceImmersive(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.windowInsetsController?.let { controller ->
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        } else {
            applyLegacyImmersiveFlags(view)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyLegacyImmersiveFlags(view: View) {
        view.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun removeOverlay() {
        val view = overlayView ?: return
        overlayView = null
        runCatching {
            getSystemService(WindowManager::class.java).removeView(view)
        }
    }

    companion object {
        const val ACTION_GO_CHECK_IN = "com.projectlumen.app.action.QUARK_KEEPER_GO_CHECK_IN"
        const val ACTION_SNOOZE = "com.projectlumen.app.action.QUARK_KEEPER_SNOOZE"
        const val ACTION_MARK_DONE = "com.projectlumen.app.action.QUARK_KEEPER_MARK_DONE"

        private const val EXTRA_REMAINING_HOURS = "remainingHours"

        /**
         * Returns false when the permission is missing or the platform refuses the background start;
         * both are outcomes the caller handles, not errors.
         */
        fun show(context: Context, remainingHours: Int): Boolean {
            if (!Settings.canDrawOverlays(context)) return false
            val intent = Intent(context, QuarkKeeperAlertService::class.java)
                .putExtra(EXTRA_REMAINING_HOURS, remainingHours)
            return ForegroundServiceController.start(context, intent)
        }

        /** Tears the alert down from outside — e.g. the user checked in from the notification. */
        fun dismiss(context: Context) {
            runCatching {
                context.stopService(Intent(context, QuarkKeeperAlertService::class.java))
            }
        }
    }
}
