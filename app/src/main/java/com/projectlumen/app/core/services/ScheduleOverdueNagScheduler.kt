package com.projectlumen.app.core.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.projectlumen.app.ProjectLumenApplication
import com.projectlumen.app.core.constants.NotificationIds
import com.projectlumen.app.core.schedule.ScheduleOverdueNag
import com.projectlumen.app.core.time.LumenTimeZone

/**
 * Arms the repeating "you never checked this off" alarms that wake [AlarmReceiver] for one overdue
 * occurrence. Each fired round re-arms the next one, so a chain only ends when the user checks the
 * to-do off, deletes it, or turns the feature off.
 *
 * One occurrence can own two slots at the same time: the interval slot, re-armed by [schedule] for
 * `now + interval`, and the evening slot, re-armed by [scheduleEvening] for the next configured
 * evening time. They are two alarms rather than one cleverer schedule because the evening follow-up
 * is an extra guaranteed fire: folding it into the interval arithmetic would only relocate one round
 * and would miss the configured time entirely whenever the interval happens to step over it.
 *
 * Both slots deliberately share the same request code (see [notificationIdFor]): a [PendingIntent]'s
 * identity is `(requestCode, Intent.filterEquals)`, and `filterEquals` compares action/data/type/
 * class/categories — the two Intents differ by action, so they stay two distinct PendingIntents.
 * Changing either action constant to equal the other would silently merge the two slots into one
 * alarm and leave the other schedule unarmed.
 *
 * Unlike [ScheduleReminderScheduler] both slots use [AlarmManager.setAndAllowWhileIdle] and never
 * touch the exact-alarm permission: a nag that repeats every couple of hours does not need
 * second-level precision, and staying inexact removes the need to degrade when the permission is
 * revoked. Doze allows one such alarm roughly every 9 minutes, which is why the smallest
 * user-selectable interval is 5 minutes. The same reasoning covers the evening slot: it stays inexact
 * even though the configured evening time now carries a seconds component.
 *
 * The request code is the notification id, derived from the occurrence id, so re-arming the same
 * occurrence replaces its pending alarm instead of stacking duplicates.
 */
class ScheduleOverdueNagScheduler(private val context: Context) {

    fun schedule(occurrenceId: Long, triggerAtMillis: Long) {
        scheduleFor(occurrenceId, triggerAtMillis, AlarmReceiver.ACTION_SCHEDULE_OVERDUE_NAG)
    }

    fun scheduleEvening(occurrenceId: Long, triggerAtMillis: Long) {
        scheduleFor(occurrenceId, triggerAtMillis, AlarmReceiver.ACTION_SCHEDULE_OVERDUE_EVENING)
    }

    private fun scheduleFor(occurrenceId: Long, triggerAtMillis: Long, action: String) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            scheduledPendingIntent(occurrenceId, action),
        )
    }

    /**
     * Clears both slots. Cancelling only the interval slot would let an occurrence keep firing every
     * evening after the feature was switched off.
     */
    fun cancel(occurrenceId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        NAG_ACTIONS.forEach { action ->
            existingPendingIntent(occurrenceId, action)?.let(alarmManager::cancel)
        }
    }

    /**
     * Clears only the evening slot, leaving the interval slot armed.
     *
     * This exists for occurrences that stay overdue but leave the morning-ended subset — the case
     * where an already-nagging to-do has its end time edited across noon. Such a row is still in the
     * overdue set, so [rearmAll] never reaches the [cancel] pass for it, and without this its
     * previously armed evening alarm would keep firing every night forever.
     */
    fun cancelEvening(occurrenceId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        existingPendingIntent(occurrenceId, AlarmReceiver.ACTION_SCHEDULE_OVERDUE_EVENING)
            ?.let(alarmManager::cancel)
    }

    fun notificationIdFor(occurrenceId: Long): Int {
        return NotificationIds.SCHEDULE_OVERDUE_BASE +
            (occurrenceId % NotificationIds.SCHEDULE_OVERDUE_RANGE).toInt()
    }

    private fun scheduledPendingIntent(occurrenceId: Long, action: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            notificationIdFor(occurrenceId),
            alarmIntent(occurrenceId, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun existingPendingIntent(occurrenceId: Long, action: String): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            notificationIdFor(occurrenceId),
            alarmIntent(occurrenceId, action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmIntent(occurrenceId: Long, action: String): Intent {
        return Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .setPackage(context.packageName)
            .putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
    }

    companion object {
        const val EXTRA_OCCURRENCE_ID = "com.projectlumen.app.extra.SCHEDULE_OVERDUE_OCCURRENCE_ID"

        /** Every slot an occurrence can own; a cancel that misses one leaves a live alarm behind. */
        private val NAG_ACTIONS = listOf(
            AlarmReceiver.ACTION_SCHEDULE_OVERDUE_NAG,
            AlarmReceiver.ACTION_SCHEDULE_OVERDUE_EVENING,
        )

        /**
         * Aligns the armed nag alarms with the full set of active occurrences.
         *
         * This is idempotent by design: it never assumes it knows what is already armed, it only
         * reconciles the desired state against the current one. That is what lets boot, an
         * exact-alarm permission change, a settings change and every schedule write all call it
         * unconditionally.
         *
         * The first interval round is anchored at [nowMillis] rather than `endAt`: for a to-do that
         * was already overdue before the feature was switched on, `endAt` lies in the past and would
         * be swallowed by [schedule]'s "never arm in the past" rule. Rounds are staggered by index so
         * enabling the feature with a dozen legacy items does not fire a dozen notifications at once.
         *
         * The evening slot covers the morning-ended subset of the same overdue set, at the user's
         * configured time; see the note on that loop below.
         */
        suspend fun rearmAll(
            app: ProjectLumenApplication,
            nowMillis: Long = System.currentTimeMillis(),
        ) {
            val scheduler = ScheduleOverdueNagScheduler(app)
            val settings = app.settingsRepository().getOrDefault()
            val all = app.database.scheduleOccurrencesDao().getActive()
            if (!settings.scheduleOverdueNagEnabled) {
                all.forEach { occurrence -> scheduler.cancel(occurrence.id) }
                return
            }
            // Resolved once, not per occurrence: a sweep that runs across midnight would otherwise
            // judge "morning" and "next evening" against two different local days.
            val zone = LumenTimeZone.zoneId()
            val eveningSecond = (settings.scheduleOverdueNagEveningMinute * 60 +
                settings.scheduleOverdueNagEveningSecond).coerceIn(0, 86_399)
            val overdue = ScheduleOverdueNag.overdueItems(all, nowMillis)
            val overdueIds = overdue.mapTo(mutableSetOf()) { occurrence -> occurrence.id }
            // Completed, not-yet-due and past-the-age-cap occurrences keep no alarm: whichever of
            // them had one armed by an earlier round is cancelled here, and cancel clears both slots.
            all.filter { occurrence -> occurrence.id !in overdueIds }
                .forEach { occurrence -> scheduler.cancel(occurrence.id) }
            overdue.forEachIndexed { index, occurrence ->
                // index + 1, not index: an offset of zero is already in the past by the time
                // schedule() re-reads the clock below, so the most-neglected item of every sweep
                // would be silently left unarmed.
                scheduler.schedule(
                    occurrence.id,
                    nowMillis + (index + 1) * ScheduleOverdueNag.SWEEP_STAGGER_MILLIS,
                )
            }
            // The evening slot, for the morning-ended subset of the overdue set. Deliberately not
            // staggered by index, unlike the interval loop above: the evening follow-up is one fixed
            // "end of the day" moment, and every to-do that should have been finished in the morning
            // arriving at that same moment is the entire point — staggering would drift it to
            // 21:30 + n * 15s. The interval loop staggers for the opposite reason: a dozen rounds
            // erupting the instant the switch is flipped is noise, and there is no configured clock
            // time for it to protect.
            overdue.forEach { occurrence ->
                if (ScheduleOverdueNag.endedInMorning(occurrence, zone)) {
                    scheduler.scheduleEvening(
                        occurrence.id,
                        ScheduleOverdueNag.nextEveningNagAt(
                            nowMillis = nowMillis,
                            eveningSecondOfDay = eveningSecond,
                            zoneId = zone,
                        ),
                    )
                } else {
                    // The overdue set is not a subset of the morning-ended set, so a row can be in
                    // it while owing no evening slot. That is not just the ordinary afternoon case:
                    // an already-nagging to-do whose end time is edited across noon lands here too,
                    // and the cancel pass above skipped it because it is still overdue. Without this
                    // branch its old evening alarm survives the edit and keeps firing every night.
                    scheduler.cancelEvening(occurrence.id)
                }
            }
        }
    }
}
