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
        assertEquals(state.settings, act(state, SessionCommand.ChangeSettings(invalid), 1).settings)
        val changed = shortSettings.copy(focusMinutes = 3, shortBreakMinutes = 2, longBreakEvery = 2)
        state = act(state, SessionCommand.ChangeSettings(changed), 1)
        assertEquals(60_000, state.durationMs)
        state = act(state, SessionCommand.Tick, 60_000)
        assertEquals(120_000, state.durationMs)
        state = act(state, SessionCommand.Acknowledge, 60_000)
        state = act(state, SessionCommand.Tick, 180_000)
        assertEquals(180_000, state.durationMs)
    }

    @Test fun savedDurationsRefreshEveryIdlePhaseBeforeStart() {
        val changed = shortSettings.copy(focusMinutes = 10, shortBreakMinutes = 3, longBreakMinutes = 7)
        for (phase in Phase.entries) {
            val initial = newSession(shortSettings).copy(phase = phase, completedFocus = 3, focusInCycle = 1)
            val saved = act(initial, SessionCommand.ChangeSettings(changed), 0)
            assertEquals(changed.durationMs(phase), saved.durationMs, phase.name)
            assertEquals(saved.durationMs, saved.remainingMs)
            assertEquals(initial.phaseId, saved.phaseId)
            assertEquals(initial.completedFocus, saved.completedFocus)
            assertEquals(initial.focusInCycle, saved.focusInCycle)
            val started = act(saved, SessionCommand.StartPhase(saved.phaseId), 5_000)
            assertEquals(5_000 + changed.durationMs(phase), started.deadlineMonotonicMs)
            assertEquals(started.deadlineMonotonicMs, started.deadlineWallMs)
            assertEquals(saved, act(saved, SessionCommand.ChangeSettings(changed.copy(focusMinutes = 0)), 0))
        }
    }

    @Test fun resetUsesLatestSavedRulesForRunningAndPausedPhases() {
        for (phase in Phase.entries) for (paused in listOf(false, true)) for (minutes in listOf(1, 10)) {
            val original = shortSettings.copy(focusMinutes = 5, shortBreakMinutes = 5, longBreakMinutes = 5)
            val changed = original.copy(focusMinutes = minutes, shortBreakMinutes = minutes, longBreakMinutes = minutes)
            var state = act(newSession(original).copy(phase = phase), SessionCommand.Start, 0)
            if (paused) state = act(state, SessionCommand.Pause, 20_000)
            state = act(state, SessionCommand.ChangeSettings(changed), 20_000)
            assertEquals(300_000L, state.durationMs)
            assertEquals(280_000L, state.remainingMs)
            val reset = act(state, SessionCommand.ResetPhase(state.phaseId), 20_000)
            assertEquals(phase, reset.phase)
            assertEquals(SessionStatus.IDLE, reset.status)
            assertEquals(minutes * 60_000L, reset.durationMs)
            assertEquals(reset.durationMs, reset.remainingMs)
            assertEquals(null, reset.deadlineMonotonicMs)
            assertEquals(null, reset.deadlineWallMs)
            assertEquals(null, reset.lastMark)
            assertEquals(0, reset.completedFocus)
            assertTrue(reset.pending.isEmpty())
            val started = act(reset, SessionCommand.Start, 30_000)
            assertEquals(30_000 + reset.durationMs, started.deadlineMonotonicMs)
        }
    }

    @Test fun pausedSaveAndResumeKeepProgressWhilePreferencesApplyImmediately() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Pause, 20_000)
        val changed = shortSettings.copy(focusMinutes = 10, automaticTransitions = false, soundEnabled = false,
            clickSoundEnabled = false, aggressiveAlertsEnabled = false, reduceMotion = true)
        val saved = act(state, SessionCommand.ChangeSettings(changed), 100_000)
        assertEquals(state.copy(settings = changed), saved)
        val resumed = act(saved, SessionCommand.Resume, 100_000)
        assertEquals(140_000L, resumed.deadlineMonotonicMs)
        val completed = act(resumed, SessionCommand.Tick, 140_000)
        assertEquals(SessionStatus.WAITING, completed.status)
        assertEquals(changed, completed.settings)
        assertEquals(1, completed.completedFocus)
    }

    @Test fun savedRulesRefreshWaitingPhaseWithoutDismissingCompletion() {
        var state = act(newSession(shortSettings.copy(automaticTransitions = false)), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 60_000)
        val changed = state.settings.copy(shortBreakMinutes = 4, automaticTransitions = true)
        val saved = act(state, SessionCommand.ChangeSettings(changed), 60_000)
        assertEquals(SessionStatus.WAITING, saved.status)
        assertEquals(240_000L, saved.remainingMs)
        assertEquals(state.pending, saved.pending)
        assertEquals(state.completedFocus, saved.completedFocus)
        assertEquals(null, saved.deadlineMonotonicMs)
        val started = act(saved, SessionCommand.AcknowledgeCompletion(saved.pending.single().phaseId), 70_000)
        assertEquals(310_000L, started.deadlineMonotonicMs)
        assertTrue(started.pending.isEmpty())
    }

    @Test fun recoveryRefreshesStaleUnstartedDurationsButPreservesPausedProgress() {
        val changed = shortSettings.copy(focusMinutes = 10, shortBreakMinutes = 3, longBreakMinutes = 7)
        for (phase in Phase.entries) {
            val idle = newSession(shortSettings).copy(phase = phase, settings = changed, phaseId = 3)
            val recovered = SessionEngine.recover(idle, at(0))
            assertEquals(changed.durationMs(phase), recovered.remainingMs)
            assertEquals(recovered.durationMs, recovered.remainingMs)
            val waiting = idle.copy(status = SessionStatus.WAITING,
                pending = listOf(com.pomodoro.domain.Completion(2, Phase.FOCUS)))
            assertEquals(waiting.copy(durationMs = changed.durationMs(phase), remainingMs = changed.durationMs(phase)),
                SessionEngine.recover(waiting, at(0)))
            val paused = idle.copy(status = SessionStatus.PAUSED, remainingMs = 40_000)
            assertEquals(paused, SessionEngine.recover(paused, at(0)))
        }
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

    @Test fun lateControlsPreserveCompletionAndDoNotAlterTheNextPhase() {
        for (command in listOf(SessionCommand.Pause, SessionCommand.Reset, SessionCommand.Skip)) {
            val running = act(newSession(shortSettings), SessionCommand.Start, 0)
            val completed = act(running, command, 60_000)
            assertEquals(1, completed.completedFocus, command.toString())
            assertEquals(Phase.SHORT_BREAK, completed.phase, command.toString())
            assertEquals(SessionStatus.RUNNING, completed.status, command.toString())
            assertEquals(60_000, completed.remainingMs, command.toString())
            assertEquals(1, completed.pending.size, command.toString())
            assertEquals(completed, act(completed, SessionCommand.Tick, 60_000))
        }
    }

    @Test fun lateControlInConfirmationModeKeepsTheNextPhaseWaiting() {
        val running = act(newSession(shortSettings.copy(automaticTransitions = false)), SessionCommand.Start, 0)
        val completed = act(running, SessionCommand.Skip, 60_000)
        assertEquals(1, completed.completedFocus)
        assertEquals(Phase.SHORT_BREAK, completed.phase)
        assertEquals(SessionStatus.WAITING, completed.status)
        assertEquals(1, completed.pending.size)
    }

    @Test fun acknowledgementCannotDismissAnUnseenCompletion() {
        val running = act(newSession(shortSettings), SessionCommand.Start, 0)
        val completed = act(running, SessionCommand.Acknowledge, 60_000)
        assertEquals(1, completed.pending.size)

        val secondCompleted = act(completed, SessionCommand.Acknowledge, 120_000)
        assertEquals(1, secondCompleted.pending.size)
        assertEquals(Phase.SHORT_BREAK, secondCompleted.pending.single().phase)
        assertEquals(SessionStatus.WAITING, secondCompleted.status)
        assertEquals(1, secondCompleted.completedFocus)
    }

    @Test fun settingsAtDeadlineDoNotRetroactivelyAlterTheCompletedPhaseTransition() {
        val running = act(newSession(shortSettings), SessionCommand.Start, 0)
        val settings = shortSettings.copy(shortBreakMinutes = 2, automaticTransitions = false)
        val completed = act(running, SessionCommand.ChangeSettings(settings), 60_000)
        assertEquals(1, completed.completedFocus)
        assertEquals(60_000, completed.durationMs)
        assertEquals(SessionStatus.RUNNING, completed.status)
        assertEquals(settings, completed.settings)
    }

    @Test fun backwardClockDuringLongGapPausesInsteadOfTreatingItAsSleep() {
        val running = SessionEngine.reduce(newSession(shortSettings), SessionCommand.Start, TimeMark(0, 300_000))
        val changed = SessionEngine.reduce(running, SessionCommand.Tick, TimeMark(20_000, 100_000))
        assertEquals(SessionStatus.PAUSED, changed.status)
        assertEquals(40_000, changed.remainingMs)
        assertEquals(0, changed.completedFocus)
        assertTrue(changed.message?.contains("clock changed") == true)
    }

    @Test fun restartAfterBackwardClockKeepsRemainingWithinSavedProgress() {
        var running = SessionEngine.reduce(newSession(shortSettings), SessionCommand.Start, TimeMark(0, 300_000))
        running = SessionEngine.reduce(running, SessionCommand.Tick, TimeMark(20_000, 320_000))
        val restored = SessionEngine.recover(running.copy(deadlineMonotonicMs = null, lastMark = null),
            TimeMark(0, 290_000))
        assertEquals(SessionStatus.PAUSED, restored.status)
        assertEquals(40_000, restored.remainingMs)
        assertEquals(null, restored.deadlineWallMs)
        assertTrue(restored.message?.contains("clock changed") == true)
        val resumed = act(restored, SessionCommand.Resume, 500_000)
        assertEquals(SessionStatus.RUNNING, resumed.status)
        assertEquals(40_000, resumed.remainingAt(at(500_000)))
    }

    @Test fun staleConfirmedControlsCannotAlterAnAlreadyAdvancedPhase() {
        val running = act(newSession(shortSettings), SessionCommand.Start, 0)
        val completed = act(running, SessionCommand.Tick, 60_000)
        for (command in listOf(SessionCommand.ResetPhase(running.phaseId), SessionCommand.SkipPhase(running.phaseId))) {
            assertEquals(completed, act(completed, command, 60_000))
            val later = act(completed, command, 70_000)
            assertEquals(completed.phaseId, later.phaseId)
            assertEquals(SessionStatus.RUNNING, later.status)
            assertEquals(50_000, later.remainingMs)
        }
    }

    @Test fun stalePrimaryControlsCannotAlterAnAlreadyAdvancedPhase() {
        val running = act(newSession(shortSettings), SessionCommand.Start, 0)
        val completed = act(running, SessionCommand.Tick, 60_000)
        for (command in listOf(SessionCommand.StartPhase(running.phaseId),
            SessionCommand.PausePhase(running.phaseId), SessionCommand.ResumePhase(running.phaseId))) {
            val later = act(completed, command, 70_000)
            assertEquals(completed.phaseId, later.phaseId, command.toString())
            assertEquals(SessionStatus.RUNNING, later.status, command.toString())
            assertEquals(50_000, later.remainingMs, command.toString())
            assertEquals(completed.pending, later.pending, command.toString())
        }
    }

    @Test fun repeatedOldAcknowledgementCannotDismissTheNextEvent() {
        var state = act(newSession(shortSettings), SessionCommand.Start, 0)
        state = act(state, SessionCommand.Tick, 60_000)
        state = act(state, SessionCommand.Tick, 120_000)
        val first = SessionCommand.AcknowledgeCompletion(state.pending.first().phaseId)
        state = act(state, first, 120_000)
        assertEquals(1, state.pending.size)
        assertEquals(SessionStatus.WAITING, state.status)
        assertEquals(state, act(state, first, 120_000))
        state = act(state, SessionCommand.AcknowledgeCompletion(state.pending.single().phaseId), 120_000)
        assertTrue(state.pending.isEmpty())
        assertEquals(SessionStatus.RUNNING, state.status)
    }
}
