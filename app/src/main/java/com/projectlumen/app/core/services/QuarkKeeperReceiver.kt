package com.projectlumen.app.core.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperClock
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperLauncher
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Every entry point of the Quark check-in guard: the five alarm fires armed by
 * [QuarkKeeperAlarmScheduler] and every notification button the guard posts.
 *
 * One receiver rather than several because all of them share the same three lines of setup — resolve
 * the application, read the stored day, re-arm — and because the manifest then declares one component
 * instead of a dozen. The actions stay distinct values, since that is what tells the fires apart.
 *
 * The day's rollover is not performed here. [QuarkKeeperStore] keys today's row by date, so a stale row
 * from yesterday already reads as "not checked in" through `currentToday()`, and every branch below
 * goes through it rather than through the raw snapshot. A rollover step that had to run before the rest
 * would be one more thing every entry point could forget — and the one that mattered, a fire crossing
 * midnight, is exactly the case a missing step would turn into "yesterday's check-in counts as today's".
 */
class QuarkKeeperReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ProjectLumenApplication
            runCatching {
                app ?: return@runCatching
                val action = intent.action ?: return@runCatching
                if (action !in HANDLED_ACTIONS) return@runCatching
                val appContext = context.applicationContext
                val nowMillis = System.currentTimeMillis()
                when (action) {
                    ACTION_DAILY_REMINDER -> handleDailyReminder(
                        app,
                        QuarkKeeperNotifications(appContext),
                        nowMillis,
                    )
                    ACTION_DEADLINE_ALERT -> handleDeadlineAlert(app, nowMillis)
                    ACTION_SNOOZE_EXPIRED -> handleSnoozeExpired(app, nowMillis)
                    ACTION_MIDNIGHT_RESET -> handleMidnightReset(
                        app,
                        QuarkKeeperNotifications(appContext),
                        nowMillis,
                    )
                    ACTION_RETURN_MONITOR -> handleReturnMonitor(app, nowMillis)
                    ACTION_GO_CHECK_IN -> handleGoCheckIn(
                        appContext,
                        QuarkKeeperAlarmScheduler(appContext),
                        nowMillis,
                    )
                    ACTION_SNOOZE -> handleSnooze(
                        app,
                        appContext,
                        QuarkKeeperAlarmScheduler(appContext),
                        nowMillis,
                    )
                    ACTION_MARK_DONE -> handleMarkDone(
                        app,
                        appContext,
                        QuarkKeeperAlarmScheduler(appContext),
                        QuarkKeeperNotifications(appContext),
                        nowMillis,
                    )
                    ACTION_UNDO -> handleUndo(app, nowMillis)
                    // The two buttons on the "Quark is not installed" notification. They reach this
                    // receiver for the same reason the alert's buttons do: it is already the component
                    // the guard's notifications point at.
                    ACTION_OPEN_STORE -> QuarkKeeperLauncher.openStoreListing(appContext)
                    ACTION_OPEN_WEB -> QuarkKeeperLauncher.openWebCheckIn(appContext)
                }
            }
                .onFailure { throwable -> app?.recordHandledFailure(throwable) }
            pendingResult.finish()
        }
    }

    /**
     * The evening reminder, and the start of the escalation for tonight.
     *
     * [QuarkKeeperCoordinator.reconcile] does the re-arming, including tonight's deadline and the
     * countdown notification: the reminder node is the one moment the guard is certain the user's day is
     * still open and the evening has begun, which is exactly when both become due.
     */
    private suspend fun handleDailyReminder(
        app: ProjectLumenApplication,
        notifications: QuarkKeeperNotifications,
        nowMillis: Long,
    ) {
        if (!QuarkKeeperStore.snapshot().settings.enabled) return
        if (QuarkKeeperStore.currentToday(nowMillis).checkedIn) return
        notifications.ensureChannels()
        notifications.showDailyReminder()
        QuarkKeeperCoordinator.reconcile(app, nowMillis)
    }

    /** The deadline node: the first forced round of the night. */
    private suspend fun handleDeadlineAlert(app: ProjectLumenApplication, nowMillis: Long) {
        if (!QuarkKeeperStore.snapshot().settings.enabled) return
        if (QuarkKeeperStore.currentToday(nowMillis).checkedIn) return
        QuarkKeeperCoordinator.fireForceAlert(app, nowMillis)
        // After the alert, never before: fireForceAlert bumps the day's nag round, and reconciling
        // first would let the boot catch-up read a round of zero and raise the alert a second time.
        QuarkKeeperCoordinator.reconcile(app, nowMillis)
    }

    /**
     * A snooze the user asked for has run out.
     *
     * Fires even past the snooze cutoff. After 23:30 the button is gone — there is no longer enough of
     * the day left to postpone into — but the alert itself is precisely what the user still needs, so
     * only the offer to postpone ends at the cutoff, never the guard.
     */
    private suspend fun handleSnoozeExpired(app: ProjectLumenApplication, nowMillis: Long) {
        if (!QuarkKeeperStore.snapshot().settings.enabled) return
        if (QuarkKeeperStore.currentToday(nowMillis).checkedIn) return
        QuarkKeeperCoordinator.fireForceAlert(app, nowMillis)
    }

    /**
     * The local day just ended: the countdown's target is gone and a fresh day has to be armed.
     *
     * The ongoing notification is cancelled first and explicitly. [QuarkKeeperCoordinator.reconcile]
     * cannot clear it here — at 00:00 the evening node is hours away, so its own gate declines to
     * repost and the notification would simply be left standing, still counting down to a deadline that
     * passed. The day that just ended is a missed check-in; the stored log only ever holds completed
     * days, so there is nothing to write for it.
     *
     * No `enabled` check: reconcile is what turns a disabled guard's leftover alarms off, and returning
     * early on that branch would leave the midnight slot armed forever.
     */
    private suspend fun handleMidnightReset(
        app: ProjectLumenApplication,
        notifications: QuarkKeeperNotifications,
        nowMillis: Long,
    ) {
        notifications.cancelOngoing()
        QuarkKeeperCoordinator.reconcile(app, nowMillis)
    }

    /**
     * 60 seconds after the user left for Quark. Still open means they did not actually check in —
     * the launch failed, they were distracted, or they tapped "go" and changed their mind.
     */
    private suspend fun handleReturnMonitor(app: ProjectLumenApplication, nowMillis: Long) {
        if (!QuarkKeeperStore.snapshot().settings.enabled) return
        if (QuarkKeeperStore.currentToday(nowMillis).checkedIn) return
        QuarkKeeperCoordinator.fireForceAlert(app, nowMillis)
    }

    /**
     * The "go and check in" button: hand the user to Quark and start watching for their return.
     *
     * Nothing is dismissed here because nothing needs to be: the overlay tears itself down the moment
     * one of its buttons is tapped, before this receiver even runs. Leaving it up would also deadlock —
     * it is a full-screen, touch-consuming window, so it would cover the very app the user was sent to.
     *
     * That tear-down is what opens the gap this method closes: nothing confirms a check-in actually
     * happened, so the 60-second monitor below is the only thing between "opened Quark" and "cleared the
     * guard by opening Quark and backing out".
     */
    private fun handleGoCheckIn(
        context: Context,
        scheduler: QuarkKeeperAlarmScheduler,
        nowMillis: Long,
    ) {
        if (!QuarkKeeperLauncher.launchCheckIn(context)) {
            // Not a Toast: a broadcast receiver has no window to attach one to, and it would be dropped
            // silently on the devices that most need the message. The store listing is also the action
            // the user can actually take once told Quark is missing, and the notification tap that got
            // us here is what grants the background activity start it needs.
            QuarkKeeperLauncher.openStoreListing(context)
        }
        // Armed whether or not Quark opened, so a launch that failed still leads back to the alert
        // rather than letting the day quietly expire.
        scheduler.scheduleReturnMonitor(nowMillis + RETURN_MONITOR_DELAY_MILLIS)
    }

    /**
     * The "later" button. Past the cutoff nothing is armed — that is the whole meaning of the cutoff,
     * and the alert stays up because there is still time to check in, just not to postpone.
     *
     * Reconciled on both branches: the refused path still has to leave the countdown correct, and the
     * accepted one has to keep it posted while the alert is off screen.
     */
    private suspend fun handleSnooze(
        app: ProjectLumenApplication,
        context: Context,
        scheduler: QuarkKeeperAlarmScheduler,
        nowMillis: Long,
    ) {
        val settings = QuarkKeeperStore.snapshot().settings
        if (QuarkKeeperClock.isSnoozeAllowed(settings, nowMillis)) {
            val snoozeUntilMillis = nowMillis + settings.snoozeMinutes * MILLIS_PER_MINUTE
            QuarkKeeperStore.setSnooze(snoozeUntilMillis, nowMillis)
            scheduler.scheduleSnooze(snoozeUntilMillis)
            QuarkKeeperAlertService.dismiss(context)
        }
        QuarkKeeperCoordinator.reconcile(app, nowMillis)
    }

    /**
     * The "done" button: the user is telling the guard they have checked in.
     *
     * The alert is taken down here, unlike the "go" button above — the answer has been given, so there
     * is nothing left to return to. Reconciling afterwards is what cancels tonight's deadline slot and
     * re-arms the daily node for tomorrow, so the guard restarts without the user reopening the app.
     */
    private suspend fun handleMarkDone(
        app: ProjectLumenApplication,
        context: Context,
        scheduler: QuarkKeeperAlarmScheduler,
        notifications: QuarkKeeperNotifications,
        nowMillis: Long,
    ) {
        QuarkKeeperStore.markCheckedIn(nowMillis)
        scheduler.cancelSnooze()
        scheduler.cancelReturnMonitor()
        notifications.cancelAll()
        QuarkKeeperAlertService.dismiss(context)
        QuarkKeeperCoordinator.reconcile(app, nowMillis)
    }

    /**
     * The "undo" button, which exists because "done" is a one-tap confirmation on a screen the user is
     * trying to get rid of — a mis-tap would otherwise end the day's monitoring with no way back.
     *
     * Reconciling restores the evening only while its deadline is still ahead. Once the deadline has
     * passed the catch-up stays silent by design (the day's nag round is already above zero), so an undo
     * at 23:00 — the case where the guard was one tap away from being dismissed for the night — has to
     * ask for the alert explicitly.
     */
    private suspend fun handleUndo(app: ProjectLumenApplication, nowMillis: Long) {
        val dateKey = QuarkKeeperStore.currentToday(nowMillis).dateKey
        QuarkKeeperStore.undoCheckIn(dateKey, nowMillis)
        val settings = QuarkKeeperStore.snapshot().settings
        QuarkKeeperCoordinator.reconcile(app, nowMillis)
        if (settings.enabled && QuarkKeeperCoordinator.deadlineReached(settings, nowMillis)) {
            QuarkKeeperCoordinator.fireForceAlert(app, nowMillis)
        }
    }

    companion object {
        const val ACTION_DAILY_REMINDER = "com.projectlumen.app.action.QUARK_KEEPER_DAILY_REMINDER"
        const val ACTION_DEADLINE_ALERT = "com.projectlumen.app.action.QUARK_KEEPER_DEADLINE_ALERT"
        const val ACTION_SNOOZE_EXPIRED = "com.projectlumen.app.action.QUARK_KEEPER_SNOOZE_EXPIRED"
        const val ACTION_MIDNIGHT_RESET = "com.projectlumen.app.action.QUARK_KEEPER_MIDNIGHT_RESET"
        const val ACTION_RETURN_MONITOR = "com.projectlumen.app.action.QUARK_KEEPER_RETURN_MONITOR"

        // Not alarms: the alert's action buttons. They travel through this receiver only because its
        // component is already the one the guard's notifications point at, so reusing it avoids a
        // second <receiver> in the manifest. The literals are duplicated in QuarkKeeperNotifications,
        // which cannot reference them from here without a circular initialisation between the two.
        const val ACTION_GO_CHECK_IN = "com.projectlumen.app.action.QUARK_KEEPER_GO_CHECK_IN"
        const val ACTION_SNOOZE = "com.projectlumen.app.action.QUARK_KEEPER_SNOOZE"
        const val ACTION_MARK_DONE = "com.projectlumen.app.action.QUARK_KEEPER_MARK_DONE"
        const val ACTION_UNDO = "com.projectlumen.app.action.QUARK_KEEPER_UNDO"

        /** The install / web fallbacks offered when Quark cannot be launched; not alarms either. */
        const val ACTION_OPEN_STORE = "com.projectlumen.app.action.QUARK_KEEPER_OPEN_STORE"
        const val ACTION_OPEN_WEB = "com.projectlumen.app.action.QUARK_KEEPER_OPEN_WEB"

        /** How long the user is given to check in before the guard asks again. */
        private const val RETURN_MONITOR_DELAY_MILLIS = 60_000L

        private const val MILLIS_PER_MINUTE = 60_000L

        /**
         * An unknown action is ignored rather than treated as an error: the receiver is exported to no
         * one, but a stale PendingIntent from an older build can still be delivered after an upgrade.
         */
        private val HANDLED_ACTIONS = setOf(
            ACTION_DAILY_REMINDER,
            ACTION_DEADLINE_ALERT,
            ACTION_SNOOZE_EXPIRED,
            ACTION_MIDNIGHT_RESET,
            ACTION_RETURN_MONITOR,
            ACTION_GO_CHECK_IN,
            ACTION_SNOOZE,
            ACTION_MARK_DONE,
            ACTION_UNDO,
            ACTION_OPEN_STORE,
            ACTION_OPEN_WEB,
        )
    }
}
