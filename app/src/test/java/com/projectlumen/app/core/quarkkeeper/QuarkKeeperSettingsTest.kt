package com.projectlumen.app.core.quarkkeeper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [QuarkKeeperSettings] is the only place the reminder list is made legal, and everything downstream —
 * the alarm slots, the countdown, the settings screen — reads the sanitized form rather than the stored
 * one. These cases therefore pin the rules themselves, not any screen: a clamp that quietly moved would
 * arm a different set of alarms with no diff anywhere in the UI.
 *
 * The numbers are written out as literals instead of being read back from the constants under test. A
 * test that asks the code what its own window is would keep passing after the window silently changed,
 * which is exactly the change it exists to catch.
 */
class QuarkKeeperSettingsTest {

    private fun reminder(minuteOfDay: Int, enabled: Boolean = true) =
        QuarkKeeperReminder(minuteOfDay = minuteOfDay, enabled = enabled)

    /**
     * The shipped day is 06:30 / 12:00 / 21:30, all on. The count is the contract, not a detail of the
     * implementation: a device that has never been configured has to come up reminding, because a guard
     * that starts silent is one the user never discovers exists.
     */
    @Test
    fun theDefaultDayIsMorningNoonAndEveningAllOn() {
        val settings = QuarkKeeperSettings()

        assertEquals(listOf(reminder(390), reminder(720), reminder(1290)), settings.reminders)
        assertTrue(settings.reminders.all { it.enabled })
        assertEquals(listOf(390, 720, 1290), settings.enabledReminderMinutes)
    }

    /**
     * The cap is six because each entry is an alarm slot: a seventh entry is a reminder the user can see
     * in the list and that no alarm will ever be armed for, which is the one failure a reminder list must
     * not have.
     *
     * The cut happens after the sort, so what survives is the earliest six — the entries that keep the
     * guard's day in front of the deadline rather than the tail that was typed last.
     */
    @Test
    fun sanitizingCapsTheListAtSixEntries() {
        val settings = QuarkKeeperSettings(
            reminders = (1..7).map { index -> reminder(index * 60) },
        ).sanitized()

        assertEquals(6, settings.reminders.size)
        assertEquals(reminder(360), settings.reminders.last())
    }

    /**
     * A node at or past the deadline is pulled back to half an hour before it rather than dropped: the
     * user asked to be reminded and only the time is impossible, so the entry keeps its place and its
     * switch. The node *at* the deadline moves too — a reminder firing at the instant of the forced alert
     * is the forced alert, and the gap is what keeps the evening's first nudge an earlier one.
     *
     * The node that was already comfortably in front of the deadline is left exactly where the user put
     * it, so this is a clamp of one boundary and not a re-spacing of the whole list.
     */
    @Test
    fun aReminderAtOrPastTheDeadlineIsPulledBackBehindTheGap() {
        val settings = QuarkKeeperSettings(
            reminders = listOf(reminder(1260), reminder(1350)),
        ).sanitized()
        assertEquals(listOf(reminder(1260), reminder(1320)), settings.reminders)

        val late = QuarkKeeperSettings(reminders = listOf(reminder(1395))).sanitized()
        assertEquals(listOf(reminder(1320)), late.reminders)
    }

    /**
     * Two entries driven onto one minute are one entry, not two. Keeping both would put two alarm slots
     * on the same instant, where the second could only ever fire as a duplicate of the first; and the
     * list would show two rows reading 22:00 with switches whose difference no longer means anything.
     *
     * The survivor keeps the *first* entry's switch, because the first is the row the user can still
     * point at in the order they set up — and a mute they applied deliberately must not be undone by the
     * collapse that followed it.
     */
    @Test
    fun twoRemindersDrivenOntoTheSameMinuteCollapseIntoOne() {
        val settings = QuarkKeeperSettings(
            reminders = listOf(reminder(1395, enabled = false), reminder(1435, enabled = true)),
        ).sanitized()

        assertEquals(listOf(reminder(1320, enabled = false)), settings.reminders)
    }

    @Test
    fun sanitizingSortsTheListAscending() {
        val settings = QuarkKeeperSettings(
            reminders = listOf(reminder(1290), reminder(390), reminder(720)),
        ).sanitized()

        assertEquals(listOf(390, 720, 1290), settings.reminders.map { it.minuteOfDay })
    }

    /**
     * An empty list is a legal answer, not a broken one: it is what the user left behind after deleting
     * every entry. Refilling it with the defaults on the next read would undo those deletions behind
     * their back, and would keep raising alarms for a list the screen shows as empty.
     *
     * With nothing enabled there is no "first reminder of the day" to count down from, so the countdown
     * falls back to the deadline itself: those users still have the forced alert, and a countdown
     * measured from anything else would describe an alert that does not exist.
     */
    @Test
    fun sanitizingAnEmptyListLeavesItEmpty() {
        val settings = QuarkKeeperSettings(reminders = emptyList()).sanitized()

        assertTrue(settings.reminders.isEmpty())
        assertTrue(settings.enabledReminderMinutes.isEmpty())
        assertEquals(1350, settings.countdownFromMinuteOfDay)
    }

    /**
     * The alarm layer arms one slot per minute of this list and reads it straight off settings the user
     * has just edited, so the getter does the filtering and the ordering itself instead of trusting the
     * stored order. A muted node contributes no slot — that is the whole reason it stays in the list —
     * and the result ascends so slot *n* keeps meaning the same alarm across reconciles.
     *
     * Nothing here is sanitized first: the property is what a caller reads before a save is involved.
     */
    @Test
    fun enabledMinutesSkipTheMutedNodesAndComeBackAscending() {
        val settings = QuarkKeeperSettings(
            reminders = listOf(
                reminder(1290),
                reminder(720, enabled = false),
                reminder(390),
            ),
        )

        assertEquals(listOf(390, 1290), settings.enabledReminderMinutes)
        assertEquals(390, settings.countdownFromMinuteOfDay)
    }

    /**
     * The deadline is clamped against the constants and on its own, before any reminder is looked at. It
     * is the wall every reminder is measured against, so a floor derived from the list would let the wall
     * move with the thing it is supposed to bound — and `coerceIn` would throw outright the moment a list
     * of late entries pushed that floor above the ceiling.
     *
     * The floor is 00:30 rather than 00:00 because a deadline inside the first half hour of the day cannot
     * keep the half-hour gap behind it; 23:55 is the ceiling, five minutes short of midnight, so a node
     * clamped behind it never lands on the next day. The defaults are left in place deliberately: they can
     * no longer fit, and the wall must not follow them.
     */
    @Test
    fun theDeadlineIsClampedAgainstTheConstantsNotAgainstTheList() {
        assertEquals(30, QuarkKeeperSettings(forcedMinuteOfDay = 5).sanitized().forcedMinuteOfDay)
        assertEquals(1435, QuarkKeeperSettings(forcedMinuteOfDay = 1439).sanitized().forcedMinuteOfDay)
    }
}
