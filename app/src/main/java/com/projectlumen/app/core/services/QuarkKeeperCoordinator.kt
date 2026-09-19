package com.projectlumen.app.core.services

import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperClock
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSettings
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperStore

/**
 * The guard's single reconciliation point: it reads the stored state and makes the armed alarms and
 * the posted notifications match it.
 *
 * Everything that can change the guard's situation calls [reconcile] rather than arming or cancelling
 * a specific alarm itself — boot, an exact-alarm permission change, a settings edit, and every
 * [QuarkKeeperReceiver] action. That is the same idempotent-reconciliation shape
 * [ScheduleOverdueNagScheduler.rearmAll] uses, and it exists for the same reason: an alarm chain whose
 * callers each patch one slot drifts, and the failure mode here is silent (a node of the day never
 * fires, and the streak breaks with no alert).
 *
 * Being callable from anywhere is what forces the two non-obvious rules below. The day's nodes are
 * anchored on "the next occurrence of that minute" rather than on today, so a reconcile running at the
 * exact moment a node fires arms tomorrow instead of going quiet for a day; and the boot catch-up takes
 * the day's first nag round instead of reading it, so neither a reconcile running right after an alert
 * nor two reconciles running side by side raise it again.
 */
object QuarkKeeperCoordinator {

    /**
     * Aligns the alarms and notifications with the stored state.
     *
     * Safe to call with the guard off, the day already done, and any number of times in a row.
     */
    suspend fun reconcile(app: ProjectLumenApplication, nowMillis: Long = System.currentTimeMillis()) {
        val context = app.applicationContext
        val scheduler = QuarkKeeperAlarmScheduler(context)
        val notifications = QuarkKeeperNotifications(context)
        val settings = QuarkKeeperStore.snapshot().settings
        // currentToday, not snapshot().today: the snapshot holds whatever day was last written, and a
        // reconcile running after midnight would otherwise read yesterday's check-in as today's.
        val today = QuarkKeeperStore.currentToday(nowMillis)
        if (!settings.enabled) {
            scheduler.cancelAll()
            notifications.cancelAll()
            QuarkKeeperAlertService.dismiss(context)
            return
        }
        // Armed whether or not today is done, because they are what starts tomorrow. A user who checks
        // in and never opens the app again still has to be reminded on the following day, and the
        // midnight slot below is the only other thing keeping the chain alive.
        armReminders(scheduler, settings, nowMillis)
        if (today.checkedIn) {
            // The day is answered: every remaining node of today is noise. Tomorrow's are re-armed
            // above and by the midnight slot, so nothing here has to survive the rollover.
            scheduler.cancelDeadline()
            scheduler.cancelSnooze()
            scheduler.cancelReturnMonitor()
            notifications.cancelOngoing()
            // An alert that is still on screen belongs to a day that has since been answered — the user
            // checked in from inside the app rather than from the overlay's own button, which is the
            // only path that tears the overlay down by itself. Dismissing here is what keeps "answered"
            // and "still interrupting" from being true at the same time.
            QuarkKeeperAlertService.dismiss(context)
        } else {
            scheduler.scheduleDeadline(nextAt(settings.forcedMinuteOfDay, nowMillis))
            // A snooze the user asked for has to outlive every reconcile between the tap and its own
            // expiry. Only the receiver's "later" button used to arm this slot, so the in-app snooze
            // wrote the stamp, showed "snoozed until …", and then nothing ever fired — the same action
            // behaved differently depending on which surface it was taken from. Arming it here is what
            // makes the two identical, and it is why the not-checked-in branch has to own the slot.
            //
            // Only a stamp still ahead of the clock is armed. An earlier round's stamp is left where it
            // is: resurrecting it would fire a re-alert the user has already sat through, on a night
            // where the deadline slot is about to interrupt them anyway.
            if (today.snoozedUntilMillis > nowMillis) {
                scheduler.scheduleSnooze(today.snoozedUntilMillis)
            }
            // PRD edge case 1: the device booted after the deadline with the day still open. Without
            // this the first alert of the day would wait for tomorrow's deadline, and the streak would
            // break unannounced.
            //
            // The round is *claimed* here rather than read and acted on. Reading nagRound and firing
            // afterwards lets two reconciles that overlap both see an unalerted day and both raise the
            // alert; claiming it under the store's lock is what holds the "one boot, one compensation"
            // rule, since the loser gets `false` and shows nothing. Every other alert path still goes
            // through [fireForceAlert], which counts its round the same way.
            if (deadlineReached(settings, nowMillis) && QuarkKeeperStore.claimFirstNagRound(nowMillis)) {
                showForceAlert(app, nowMillis)
            }
        }
        // Always armed. This is the only thing that starts a new day, so skipping it when the day is
        // done would leave yesterday's "checked in" standing as today's status after the clock rolls
        // over — the one failure that silently cancels the whole guard.
        //
        // It is an alarm rather than a pure "read the date on demand" design because the guard also has
        // to act at midnight without the app being opened: the countdown notification has to go, and
        // the new day's nodes have to be armed. The stored state is still date-keyed, so a midnight the
        // platform never delivers costs accuracy of presentation, never correctness of the day.
        scheduler.scheduleMidnight(QuarkKeeperClock.endOfDayMillis(nowMillis))
        if (!today.checkedIn && QuarkKeeperClock.minuteOfDay(nowMillis) >= settings.countdownFromMinuteOfDay) {
            // Gated on the day's first reminder so the countdown appears as the user's reminder window
            // opens rather than sitting there from midnight; before that it would describe a deadline
            // nobody is near. A list with nothing enabled falls back to the deadline itself, so the
            // countdown still exists for the users who kept only the forced alert.
            notifications.showOngoing(remainingHours(nowMillis))
        }
    }

    /**
     * Raises one round of the deadline alert: the full-screen surface, the notification that backs it
     * up, and the countdown.
     *
     * The round is counted *before* anything is shown. It records that the user has been interrupted,
     * not that the interruption was delivered, and it is what tells [reconcile] the day has already been
     * alerted. Counting it afterwards would let a refused overlay or a dropped notification make every
     * later reconcile believe the day was never alerted and fire again.
     */
    suspend fun fireForceAlert(app: ProjectLumenApplication, nowMillis: Long = System.currentTimeMillis()) {
        QuarkKeeperStore.bumpNagRound(nowMillis)
        showForceAlert(app, nowMillis)
    }

    /**
     * The visible half of [fireForceAlert], without the round.
     *
     * Split out for [reconcile]'s catch-up, which has already claimed the round to decide whether it
     * may show anything at all — bumping again there would count one interruption as two.
     */
    private suspend fun showForceAlert(app: ProjectLumenApplication, nowMillis: Long) {
        val context = app.applicationContext
        val notifications = QuarkKeeperNotifications(context)
        // Before showForceAlert(), not before the overlay: posting to a channel that does not exist yet
        // drops the notification silently, and this is the one notification that cannot be missed.
        notifications.ensureChannels()
        val remaining = remainingHours(nowMillis)
        QuarkKeeperAlertService.show(context, remaining)
        // Posted on every round rather than only when the overlay reports failure. show() answers false
        // for a denied overlay permission, a blocked background activity start and a service the
        // platform killed alike, and it cannot distinguish "the user is looking at the alert" from "the
        // alert never appeared" — so the notification is the guaranteed half of the pair.
        notifications.showForceAlert(remaining)
        notifications.showOngoing(remaining)
    }

    /** True once the day's deadline node has passed; the streak itself breaks at midnight. */
    fun deadlineReached(settings: QuarkKeeperSettings, nowMillis: Long): Boolean {
        return QuarkKeeperClock.minuteOfDay(nowMillis) >= settings.forcedMinuteOfDay
    }

    /**
     * The next occurrence of [minuteOfDay], today when it is still ahead and tomorrow otherwise.
     *
     * Wrapped because every caller wants the same thing and the argument order of the clock's helper
     * reads backwards at a call site.
     */
    private fun nextAt(minuteOfDay: Int, nowMillis: Long): Long {
        return QuarkKeeperClock.nextOccurrenceOf(minuteOfDay, nowMillis)
    }

    /**
     * Whole hours left in the local day — the deadline the countdown counts down to.
     *
     * It is midnight rather than the configured deadline node: the node is when the guard interrupts,
     * while midnight is when the streak actually dies, so it is the only number that is still true
     * after the alert has fired and the only one that only ever decreases.
     */
    private fun remainingHours(nowMillis: Long): Int {
        return QuarkKeeperClock.remainingUntilEndOfDay(nowMillis).hours
    }

    /**
     * Arms one slot per enabled reminder node, and clears the slots the list no longer fills.
     *
     * The sweep over the tail is not optional upkeep. A slot index is the node's position in the armed
     * list, so an entry that is deleted or switched off shifts every later node down onto the code its
     * neighbour held: the loop above re-arms index *n* of the shorter list for its own new time, but the
     * codes between the new count and the old one stay armed with nothing left to overwrite them.
     * Without the sweep a reminder the user just removed would still fire — for a time they can no
     * longer see anywhere in the list, which is the one failure this list-shaped model has to avoid.
     * Disabling an entry is the same case as deleting one, which is why the sweep counts armed nodes
     * rather than the user's list: both leave fewer armed nodes than there are slots to fill.
     *
     * [nextAt] being strictly-next is what makes this safe to run in response to a node firing: the node
     * that just fired resolves to tomorrow, so the reconcile it triggered cannot arm it again for the
     * instant the user has already been reminded at.
     */
    private fun armReminders(
        scheduler: QuarkKeeperAlarmScheduler,
        settings: QuarkKeeperSettings,
        nowMillis: Long,
    ) {
        val armedMinutes = settings.enabledReminderMinutes
        armedMinutes.forEachIndexed { index, minuteOfDay ->
            scheduler.scheduleReminder(index, nextAt(minuteOfDay, nowMillis))
        }
        for (index in armedMinutes.size until QuarkKeeperSettings.MAX_REMINDERS) {
            scheduler.cancelReminder(index)
        }
    }
}
