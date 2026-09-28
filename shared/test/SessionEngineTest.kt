package com.pomodoro

import com.pomodoro.domain.Phase
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionEngine
import com.pomodoro.domain.SessionState
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.TimeMark
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.newSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionEngineTest {
    private val shortSettings = TimerSettings(focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 1)
    private fun at(ms: Long) = TimeMark(ms, ms)
    private fun act(state: SessionState, command: SessionCommand, ms: Long) = SessionEngine.reduce(state, command, at(ms))

    @Test fun defaultsAndInvalidCommands() {
        val initial = newSession()
        assertEquals(25 * 60_000L, initial.remainingMs)
        assertEquals(Phase.FOCUS, initial.phase)
        assertEquals(0, initial.completedFocus)
        assertEquals(initial, act(initial, SessionCommand.Pause, 0))
        assertEquals(initial, act(initial, SessionCommand.Resume, 0))
        assertEquals(initial, act(initial, SessionCommand.Acknowledge, 0))
    }

    @Test fun fourthCompletedFocusGetsLongBreak() {
        var state = newSession(shortSettings)
        var time = 0L
        repeat(4) { index ->
            state = act(state, SessionCommand.Start, time)
            time += 60_000
            state = act(state, SessionCommand.Tick, time)
            assertEquals(if (index == 3) Phase.LONG_BREAK else Phase.SHORT_BREAK, state.phase)
            assertEquals(index + 1, state.completedFocus)
            state = act(state, SessionCommand.Acknowledge, time)
            if (index < 3) {
                time += 60_000
                state = act(state, SessionCommand.Tick, time)
                state = act(state, SessionCommand.Acknowledge, time)
            }
        }
        assertEquals(0, state.focusInCycle)
    }

    @Test fun skipAndResetDoNotEarnFocus() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 12_000)
        state = act(state, SessionCommand.Reset, 12_000)
        assertEquals(SessionStatus.IDLE, state.status)
        assertEquals(60_000, state.remainingMs)
        state = act(state, SessionCommand.Skip, 13_000)
        assertEquals(Phase.SHORT_BREAK, state.phase)
        assertEquals(0, state.completedFocus)
        assertEquals(0, state.focusInCycle)
    }

    @Test fun delayedTickPauseAndRepeatedCompletion() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 20_000)
        assertEquals(40_000, state.remainingMs)
        state = act(state, SessionCommand.Pause, 20_000)
        assertEquals(40_000, state.remainingAt(at(90_000)))
        state = act(state, SessionCommand.Resume, 90_000)
        state = act(state, SessionCommand.Tick, 130_000)
        assertEquals(Phase.SHORT_BREAK, state.phase)
        assertEquals(1, state.pending.size)
        assertEquals(0, state.remainingAt(at(130_000)) - state.durationMs)
        val repeated = act(state, SessionCommand.Tick, 130_000)
        assertEquals(state.pending, repeated.pending)
        assertEquals(1, repeated.completedFocus)
    }

    @Test fun settingsValidateAndApplyOnlyToNextPhase() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        val invalid = shortSettings.copy(focusMinutes = 181)
        assertFalse(invalid.isValid())
        assertFalse(shortSettings.copy(focusMinutes = 0).isValid())
        assertFalse(shortSettings.copy(shortBreakMinutes = 0).isValid())
        assertFalse(shortSettings.copy(shortBreakMinutes = 61).isValid())
        assertFalse(shortSettings.copy(longBreakMinutes = 61).isValid())
        assertFalse(shortSettings.copy(longBreakEvery = 1).isValid())
        assertFalse(shortSettings.copy(longBreakEvery = 13).isValid())
        assertTrue(TimerSettings(focusMinutes = 180, shortBreakMinutes = 60,
            longBreakMinutes = 60, longBreakEvery = 12).isValid())
        assertEquals(state, act(state, SessionCommand.ChangeSettings(invalid), 1))
        val changed = shortSettings.copy(focusMinutes = 3, shortBreakMinutes = 2, longBreakEvery = 2)
        state = act(state, SessionCommand.ChangeSettings(changed), 1)
        assertEquals(60_000, state.durationMs)
        state = act(state, SessionCommand.Tick, 60_000)
        assertEquals(120_000, state.durationMs)
        state = act(state, SessionCommand.Acknowledge, 60_000)
        state = act(state, SessionCommand.Tick, 180_000)
        assertEquals(180_000, state.durationMs)
    }

    @Test fun automaticQueueStopsAfterSecondUnacknowledgedCompletion() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 60_000)
        assertEquals(SessionStatus.RUNNING, state.status)
        assertEquals(1, state.pending.size)
        state = act(state, SessionCommand.Tick, 120_000)
        assertEquals(SessionStatus.WAITING, state.status)
        assertEquals(2, state.pending.size)
        assertEquals(state, act(state, SessionCommand.Tick, 500_000))
        state = act(state, SessionCommand.Acknowledge, 500_000)
        assertEquals(SessionStatus.WAITING, state.status)
        state = act(state, SessionCommand.Acknowledge, 500_000)
        assertEquals(SessionStatus.RUNNING, state.status)
        assertTrue(state.pending.isEmpty())
    }

    @Test fun confirmationModeWaitsBothDirections() {
        val settings = shortSettings.copy(automaticTransitions = false)
        var state = act(newSession(settings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 60_000)
        assertEquals(SessionStatus.WAITING, state.status)
        state = act(state, SessionCommand.Acknowledge, 100_000)
        assertEquals(SessionStatus.RUNNING, state.status)
        assertEquals(Phase.SHORT_BREAK, state.phase)
        state = act(state, SessionCommand.Tick, 160_000)
        assertEquals(SessionStatus.WAITING, state.status)
        assertEquals(Phase.FOCUS, state.phase)
    }

    @Test fun recoverExpiresOnceAndClockJumpPauses() {
        val running = act(newSession(shortSettings), SessionCommand.Start, 0)
        val restored = SessionEngine.recover(running.copy(deadlineMonotonicMs = null, lastMark = null), at(180_000))
        assertEquals(1, restored.completedFocus)
        assertEquals(1, restored.pending.size)
        assertEquals(1, act(restored, SessionCommand.Tick, 180_000).completedFocus)
        val jumped = SessionEngine.reduce(running, SessionCommand.Tick, TimeMark(1_000, 200_000))
        assertEquals(SessionStatus.PAUSED, jumped.status)
        assertTrue(jumped.message != null)
    }

    @Test fun expiredBreakRecoveryStopsAfterTwoAlertsWithoutBackfill() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 60_000)
        assertEquals(Phase.SHORT_BREAK, state.phase)
        val restored = SessionEngine.recover(state.copy(deadlineMonotonicMs = null, lastMark = null), at(600_000))
        assertEquals(Phase.FOCUS, restored.phase)
        assertEquals(SessionStatus.WAITING, restored.status)
        assertEquals(2, restored.pending.size)
        assertEquals(1, restored.completedFocus)
    }

    @Test fun longSleepGapReconcilesOnePhase() {
        val running = act(newSession(shortSettings), SessionCommand.Start, 0)
        val afterSleep = SessionEngine.reduce(running, SessionCommand.Tick, TimeMark(20_000, 300_000))
        assertEquals(Phase.SHORT_BREAK, afterSleep.phase)
        assertEquals(1, afterSleep.completedFocus)
        assertEquals(1, afterSleep.pending.size)
    }
}
