package com.projectlumen.app.core.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.projectlumen.app.core.constants.NotificationIds

/**
 * Arms the five alarms behind the Quark check-in guard.
 *
 * The guard is built from independent single-shot alarms rather than one repeating alarm. Every node
 * of the day has a different trigger rule — the gentle reminder, the deadline alert, the snooze
 * re-alert, the day rollover and the 60-second return monitor — and a repeating alarm can only
 * express "every N minutes". Each fire re-arms whatever comes next instead, which is also what lets
 * a chain stop the moment the user checks in; a repeating alarm would have to be cancelled from
 * every path that completes a day, and missing one would keep nagging a user who is already done.
 *
 * Every slot owns its own request code *and* its own Intent action. The request code doubles as the
 * notification id the guard posts under, so the slots cannot share it; and the action is what
 * [QuarkKeeperReceiver] switches on to tell the five fires apart. A [PendingIntent]'s identity is
 * `(requestCode, Intent.filterEquals)`, so changing one slot's action to equal another's would not
 * just misroute a fire — it would merge the two into a single alarm and leave a node of the day
 * permanently unarmed.
 *
 * All five use [AlarmManager.setExactAndAllowWhileIdle] and degrade to
 * [AlarmManager.setAndAllowWhileIdle]. The guard fires at user-visible clock times ("20:00",
 * "22:30"), so a Doze-deferred alarm that slips well past its node is wrong rather than merely
 * late — the whole point is that the deadline alert lands before the streak breaks at midnight.
 * Exact-alarm permission can still be revoked at any time, including between the capability check
 * and the scheduling call, so the inexact path is a safety net rather than an error case.
 */
class QuarkKeeperAlarmScheduler(private val context: Context) {

    fun scheduleDaily(triggerAtMillis: Long) {
        schedule(SLOT_DAILY, triggerAtMillis)
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

    fun cancelDaily() {
        cancel(SLOT_DAILY)
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
        // catch-up alert the user did not earn, or a rollover that runs twice.
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
     * not resurrect a slot the guard never armed, or [cancelAll] on a fresh install would leave five
     * PendingIntents behind that the platform could later deliver.
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

    /** One alarm slot: the id it posts under, and the action the receiver dispatches on. */
    private data class Slot(val requestCode: Int, val action: String)

    companion object {
        private val SLOT_DAILY = Slot(
            NotificationIds.QUARK_KEEPER_DAILY,
            QuarkKeeperReceiver.ACTION_DAILY_REMINDER,
        )
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

        /** Every slot the guard can own; a cancel sweep that misses one leaves a live alarm behind. */
        private val SLOTS = listOf(
            SLOT_DAILY,
            SLOT_DEADLINE,
            SLOT_SNOOZE,
            SLOT_MIDNIGHT,
            SLOT_RETURN_MONITOR,
        )
    }
}
