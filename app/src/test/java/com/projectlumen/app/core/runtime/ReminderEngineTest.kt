package com.projectlumen.app.core.runtime

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.enums.ReminderPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderEngineTest {
    private val engine = ReminderEngine()

    @Test
    fun workingAdvancesToPreAlert() {
        val start = 1_000_000L
        val settings = AppSettingsEntity(
            warnIntervalMinutes = 20,
            preAlertEnabled = true,
            preAlertSeconds = 60,
            soundEnabled = true,
            preAlertSoundEnabled = true,
        )
        val state = engine.newWorkingState(settings, start)

        val transition = engine.advance(settings, state, state.nextPreAlertAt)

        assertNotNull(transition)
        assertEquals(ReminderPhase.PRE_ALERT.name, transition!!.nextRuntime.reminderPhase)
        assertEquals(1, transition.eyeStatsDelta.preAlertCount)
        assertTrue(transition.audioEvent is AudioEvent.ReminderTone)
    }

    @Test
    fun disabledPreAlertDoesNotEnterPreAlertBeforeReminder() {
        val start = 2_000_000L
        val settings = AppSettingsEntity(
            warnIntervalMinutes = 20,
            preAlertEnabled = false,
            askBeforeBreak = true,
        )
        val state = engine.newWorkingState(settings, start)

        assertNull(engine.advance(settings, state, state.nextReminderAt - 1L))

        val transition = engine.advance(settings, state, state.nextReminderAt)
        assertNotNull(transition)
        assertEquals(ReminderPhase.AWAITING_ACTION.name, transition!!.nextRuntime.reminderPhase)
        assertEquals(20 * 60L, transition.eyeStatsDelta.workingSeconds)
    }

    @Test
    fun awaitingReminderRecordsWorkAndStartsRestWhenConfigured() {
        val start = 3_000_000L
        val settings = AppSettingsEntity(
            warnIntervalMinutes = 20,
            restDurationSeconds = 30,
            askBeforeBreak = false,
            preAlertEnabled = false,
        )
        val state = engine.newWorkingState(settings, start)

        val transition = engine.advance(settings, state, state.nextReminderAt)

        assertNotNull(transition)
        assertEquals(ReminderPhase.RESTING.name, transition!!.nextRuntime.reminderPhase)
        assertEquals(state.nextReminderAt + 30_000L, transition.nextRuntime.breakEndAt)
        assertEquals(20 * 60L, transition.eyeStatsDelta.workingSeconds)
    }

    @Test
    fun restingCompletionReturnsToWorkingAndRecordsRest() {
        val start = 4_000_000L
        val settings = AppSettingsEntity(restDurationSeconds = 20)
        val working = engine.newWorkingState(settings, start)
        val breakStart = start + 60_000L
        val resting = engine.startBreak(settings, working, breakStart).nextRuntime

        val transition = engine.advance(settings, resting, resting.breakEndAt)

        assertNotNull(transition)
        assertEquals(ReminderPhase.WORKING.name, transition!!.nextRuntime.reminderPhase)
        assertEquals(20L, transition.eyeStatsDelta.restSeconds)
        assertEquals(1, transition.eyeStatsDelta.completedBreakCount)
        assertTrue(transition.audioEvent is AudioEvent.ReminderTone)
    }

    @Test
    fun pauseForOneHourAutoResumesAfterSuspension() {
        val start = 5_000_000L
        val settings = AppSettingsEntity()
        val working = engine.newWorkingState(settings, start)
        val paused = engine.pauseForOneHour(working, start + 1_000L)

        assertNull(engine.advance(settings, paused, paused.suspendedUntil - 1L))

        val transition = engine.advance(settings, paused, paused.suspendedUntil)
        assertNotNull(transition)
        assertEquals(ReminderPhase.WORKING.name, transition!!.nextRuntime.reminderPhase)
        assertEquals(paused.suspendedUntil, transition.nextRuntime.reminderStartedAt)
    }

    @Test
    fun adjustForSettingsRealignsActiveReminderTimes() {
        val start = 6_000_000L
        val originalSettings = AppSettingsEntity(
            warnIntervalMinutes = 20,
            preAlertSeconds = 60,
        )
        val updatedSettings = originalSettings.copy(
            warnIntervalMinutes = 30,
            preAlertSeconds = 120,
        )
        val state = engine.newWorkingState(originalSettings, start)

        val adjusted = engine.adjustForSettings(state, updatedSettings, start + 5 * 60_000L)

        assertEquals(ReminderPhase.WORKING.name, adjusted.reminderPhase)
        assertEquals(start + 30 * 60_000L, adjusted.nextReminderAt)
        assertEquals(start + 28 * 60_000L, adjusted.nextPreAlertAt)
    }
}
