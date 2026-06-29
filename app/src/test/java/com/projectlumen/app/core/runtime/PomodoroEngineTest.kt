package com.projectlumen.app.core.runtime

import com.projectlumen.app.core.database.entities.AppSettingsEntity
import com.projectlumen.app.core.enums.ActiveEngine
import com.projectlumen.app.core.enums.PomodoroPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroEngineTest {
    private val engine = PomodoroEngine()

    @Test
    fun firstFocusAdvancesToShortBreak() {
        val start = 1_000_000L
        val settings = AppSettingsEntity(
            pomodoroWorkMinutes = 25,
            pomodoroShortBreakMinutes = 5,
        )
        val focus = engine.start(settings, start).nextRuntime

        val transition = engine.advance(settings, focus, focus.pomodoroPhaseEndAt)

        assertNotNull(transition)
        assertEquals(PomodoroPhase.SHORT_BREAK.name, transition!!.nextRuntime.pomodoroPhase)
        assertEquals(1, transition.nextRuntime.pomodoroCycleIndex)
        assertEquals(1, transition.pomodoroStatsDelta.completedFocusSessions)
        assertEquals(0, transition.pomodoroStatsDelta.completedTomatoCount)
        assertEquals(25 * 60L, transition.pomodoroStatsDelta.totalFocusSeconds)
        assertTrue(transition.audioEvent is AudioEvent.ReminderTone)
    }

    @Test
    fun fourthFocusAdvancesToLongBreakAndCountsTomato() {
        val start = 2_000_000L
        val settings = AppSettingsEntity(
            pomodoroWorkMinutes = 25,
            pomodoroLongBreakMinutes = 15,
        )
        val focus = engine.start(settings, start).nextRuntime.copy(pomodoroCycleIndex = 4)

        val transition = engine.advance(settings, focus, focus.pomodoroPhaseEndAt)

        assertNotNull(transition)
        assertEquals(PomodoroPhase.LONG_BREAK.name, transition!!.nextRuntime.pomodoroPhase)
        assertEquals(1, transition.pomodoroStatsDelta.completedTomatoCount)
        assertEquals(focus.pomodoroPhaseEndAt + 15 * 60_000L, transition.nextRuntime.pomodoroPhaseEndAt)
    }

    @Test
    fun longBreakCompletionResetsCycle() {
        val start = 3_000_000L
        val settings = AppSettingsEntity(
            pomodoroWorkMinutes = 25,
            pomodoroLongBreakMinutes = 15,
        )
        val longBreak = engine.start(settings, start).nextRuntime.copy(
            pomodoroPhase = PomodoroPhase.LONG_BREAK.name,
            pomodoroPhaseStartedAt = start,
            pomodoroPhaseEndAt = start + 15 * 60_000L,
            pomodoroCycleIndex = 4,
        )

        val transition = engine.advance(settings, longBreak, longBreak.pomodoroPhaseEndAt)

        assertNotNull(transition)
        assertEquals(PomodoroPhase.FOCUS.name, transition!!.nextRuntime.pomodoroPhase)
        assertEquals(1, transition.nextRuntime.pomodoroCycleIndex)
        assertEquals(15 * 60L, transition.pomodoroStatsDelta.totalBreakSeconds)
    }

    @Test
    fun stopRunningPomodoroCountsRestart() {
        val settings = AppSettingsEntity()
        val running = engine.start(settings, 4_000_000L).nextRuntime

        val transition = engine.stop(running, 4_001_000L)

        assertEquals(ActiveEngine.IDLE.name, transition.nextRuntime.activeEngine)
        assertEquals(PomodoroPhase.IDLE.name, transition.nextRuntime.pomodoroPhase)
        assertEquals(1, transition.pomodoroStatsDelta.restartCount)
    }
}
