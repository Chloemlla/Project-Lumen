package com.projectlumen.app.core.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.quarkkeeper.QuarkKeeperSettings

/**
 * Arms the alarms behind the Quark check-in guard.
 *
 * The guard is built from independent single-shot alarms rather than one repeating alarm. Every node
 * of the day has a different trigger rule — the user's reminder nodes, the deadline alert, the snooze
 * re-alert, the day rollover and the 60-second return monitor — and a repeating alarm can only express
 * "every N minutes". Each fire re-arms whatever comes next instead, which is also what lets a chain
 * stop the moment the user checks in; a repeating alarm would have to be cancelled from every path that
 * completes a day, and missing one would keep nagging a user who is already done.
 *
 * The four fixed slots of the day each own their own request code *and* their own Intent action: the
 * action is what [QuarkKeeperReceiver] switches on to tell those fires apart. The reminder nodes are the
 * deliberate exception. They all carry [QuarkKeeperReceiver.ACTION_DAILY_REMINDER], because the receiver
 * does the same thing whichever of them fired, and they are told apart by request code alone — one code
 * per entry of [QuarkKeeperSettings.reminders], counting up from
 * [NotificationIds.QUARK_KEEPER_REMINDER_SLOT_BASE]. Sharing the action costs nothing: a [PendingIntent]'s
 * identity is `(requestCode, Intent.filterEquals)`, so distinct request codes already keep the alarms
 * from merging into one, and a distinct action per node would only add a dispatch table in the receiver
 * that has to grow with a list the user edits. What sharing does mean is that the reminder request codes
 * are load-bearing in a way the fixed slots' are not — reusing one would silently leave a node of the day
 * unarmed rather than misroute a fire, and the count is bounded by [QuarkKeeperSettings.MAX_REMINDERS]
 * precisely because each entry needs a code of its own.
 *
 * Every slot uses [AlarmManager.setExactAndAllowWhileIdle] and degrades to
 * [AlarmManager.setAndAllowWhileIdle]. The guard fires at user-visible clock times ("06:30", "22:30"),
 * so a Doze-deferred alarm that slips well past its node is wrong rather than merely late — the whole
 * point is that the deadline alert lands before the streak breaks at midnight. Exact-alarm permission
 * can still be revoked at any time, including between the capability check and the scheduling call, so
 * the inexact path is a safety net rather than an error case.
 */
class QuarkKeeperAlarmScheduler(private val context: Context) {

    /**
     * Arms the slot of reminder node [index].
     *
     * [index] is the node's position in [QuarkKeeperSettings.enabledReminderMinutes], not in the user's
     * full list: the codes are handed out over the armed nodes only, so that disabling an entry closes
     * the gap instead of leaving a hole the cancel sweep in [QuarkKeeperCoordinator] would have to know
     * about.
     */
    fun scheduleReminder(index: Int, triggerAtMillis: Long) {
        schedule(reminderSlot(index), triggerAtMillis)
    }

    fun cancelReminder(index: Int) {
        cancel(reminderSlot(index))
    }

    fun scheduleDeadline(triggerAtMillis: Long) {
        schedule(SLOT_DEADLINE, triggerAtMillis)
    }

    fun scheduleSnooze(triggerAtMillis: Long) {
        schedule(SLOT_SNOOZE, triggerAtMillis)
    }

    fun scheduleMidnight(triggerAtMillis: Long) {
        schedule(SLOT_MIDNIGHT, triggerAtMillis)
    }

    fun scheduleReturnMonitor(triggerAtMillis: Long) {
        schedule(SLOT_RETURN_MONITOR, triggerAtMillis)
    }

    fun cancelDeadline() {
        cancel(SLOT_DEADLINE)
    }

    fun cancelSnooze() {
        cancel(SLOT_SNOOZE)
    }

    fun cancelMidnight() {
        cancel(SLOT_MIDNIGHT)
    }

    fun cancelReturnMonitor() {
        cancel(SLOT_RETURN_MONITOR)
    }

    /** Clears every slot; callers that are not [QuarkKeeperCoordinator.reconcile] almost never want this. */
    fun cancelAll() {
        SLOTS.forEach { slot -> cancel(slot) }
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun schedule(slot: Slot, triggerAtMillis: Long) {
        // Never arm in the past. The platform would fire such an alarm immediately, which for the
        // deadline and midnight slots means acting on a node that has already been handled — a
        // catch-up alert the user did not earn, or a rollover that runs twice. A reminder node is no
        // different: the user asked to be reminded at that time, not to be told about it afterwards.
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = scheduledPendingIntent(slot)
        if (canScheduleExactAlarms(alarmManager)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Permission can change between the capability check and the scheduling call.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun cancel(slot: Slot) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        existingPendingIntent(slot)?.let(alarmManager::cancel)
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return alarmManager.canScheduleExactAlarms()
    }

    private fun scheduledPendingIntent(slot: Slot): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            slot.requestCode,
            alarmIntent(slot),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * [PendingIntent.FLAG_NO_CREATE] rather than [PendingIntent.FLAG_UPDATE_CURRENT]: cancelling must
     * not resurrect a slot the guard never armed, or [cancelAll] on a fresh install would leave every
     * one of them behind as a PendingIntent the platform could later deliver.
     */
    private fun existingPendingIntent(slot: Slot): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            slot.requestCode,
            alarmIntent(slot),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmIntent(slot: Slot): Intent {
        return Intent(context, QuarkKeeperReceiver::class.java)
            .setAction(slot.action)
            .setPackage(context.packageName)
    }

    /** One alarm slot: the request code that identifies it, and the action the receiver dispatches on. */
    private data class Slot(val requestCode: Int, val action: String)

    companion object {
        private val SLOT_DEADLINE = Slot(
            NotificationIds.QUARK_KEEPER_DEADLINE,
            QuarkKeeperReceiver.ACTION_DEADLINE_ALERT,
        )
        private val SLOT_SNOOZE = Slot(
            NotificationIds.QUARK_KEEPER_SNOOZE,
            QuarkKeeperReceiver.ACTION_SNOOZE_EXPIRED,
        )
        private val SLOT_MIDNIGHT = Slot(
            NotificationIds.QUARK_KEEPER_MIDNIGHT,
            QuarkKeeperReceiver.ACTION_MIDNIGHT_RESET,
        )
        private val SLOT_RETURN_MONITOR = Slot(
            NotificationIds.QUARK_KEEPER_RETURN_MONITOR,
            QuarkKeeperReceiver.ACTION_RETURN_MONITOR,
        )

        /**
         * The slot of reminder node [index], built rather than listed.
         *
         * Which nodes exist is the user's to change, so they cannot be a fixed set of constants; only
         * their request codes and the single action they share are settled. The notification id is
         * deliberately not one of the inputs — every node posts under
         * [NotificationIds.QUARK_KEEPER_DAILY], because the user asked for one reminder at several
         * times and not for one shade entry per time.
         */
        private fun reminderSlot(index: Int) = Slot(
            NotificationIds.QUARK_KEEPER_REMINDER_SLOT_BASE + index,
            QuarkKeeperReceiver.ACTION_DAILY_REMINDER,
        )

        /**
         * Every slot the guard can own; a cancel sweep that misses one leaves a live alarm behind.
         *
         * The whole reminder range is covered rather than the indices currently armed: a reconcile that
         * runs after an entry was deleted has to be able to cancel the slot that entry used to own.
         */
        private val SLOTS = (0 until QuarkKeeperSettings.MAX_REMINDERS).map(::reminderSlot) + listOf(
            SLOT_DEADLINE,
            SLOT_SNOOZE,
            SLOT_MIDNIGHT,
            SLOT_RETURN_MONITOR,
        )
    }
}
