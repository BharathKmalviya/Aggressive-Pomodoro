package com.pomodoro

import com.pomodoro.domain.ProductCommand
import com.pomodoro.domain.ProductEngine
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.FocusDay
import com.pomodoro.domain.FocusHistory
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.TaskCommand
import com.pomodoro.domain.TimeMark
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.newSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProductEngineTest {
    private val date = "2026-09-28"
    private fun act(state: ProductState, command: ProductCommand, ms: Long = 0) =
        ProductEngine.reduce(state, command, TimeMark(ms, ms), date)

    @Test fun taskValidationSelectionAndCompletion() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("  Ship the report  ", 2)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("", 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Invalid", 21)))
        assertEquals(1, state.board.tasks.size)
        assertEquals("Ship the report", state.board.selected?.title)
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        assertEquals(1, state.board.selected?.completed)
        assertEquals(1, state.history.on(date).sessions)
        assertEquals(60_000, state.history.on(date).focusedMs)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        assertEquals(1, state.history.on(date).sessions)
        state = act(state, ProductCommand.Task(TaskCommand.ToggleDone(1)), 60_000)
        assertNull(state.board.selected)
        state = act(state, ProductCommand.Task(TaskCommand.Remove(1)), 60_000)
        assertEquals(0, state.board.tasks.size)
    }

    @Test fun skippedFocusDoesNotCreditTaskOrHistory() {
        var state = act(ProductState(), ProductCommand.Task(TaskCommand.Add("Draft", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.Skip), 10_000)
        assertEquals(0, state.board.selected?.completed)
        assertEquals(0, state.history.on(date).sessions)
    }

    @Test fun expiredRecoveryCreditsOnce() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Finish", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        val expired = ProductEngine.recover(state, TimeMark(120_000, 120_000), date)
        assertEquals(1, expired.board.selected?.completed)
        assertEquals(1, expired.history.on(date).sessions)
        val repeated = ProductEngine.recover(expired, TimeMark(120_000, 120_000), date)
        assertEquals(1, repeated.history.on(date).sessions)
    }

    @Test fun changingSelectionDoesNotRedirectRunningFocusCredit() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1, shortBreakMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("First", 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Next", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Task(TaskCommand.Select(2)), 10_000)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        assertEquals(1, state.board.tasks.first().completed)
        assertEquals(0, state.board.tasks.last().completed)
        state = act(state, ProductCommand.Session(SessionCommand.Acknowledge), 60_000)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 120_000)
        assertEquals(2, state.activeTaskId)
    }

    @Test fun dailyTotalsFollowCompletionDate() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1, shortBreakMinutes = 1)))
        state = ProductEngine.reduce(state, ProductCommand.Session(SessionCommand.Start), TimeMark(0, 0), "2026-09-28")
        state = ProductEngine.reduce(state, ProductCommand.Session(SessionCommand.Tick),
            TimeMark(60_000, 60_000), "2026-09-29")
        assertEquals(0, state.history.on("2026-09-28").sessions)
        assertEquals(1, state.history.on("2026-09-29").sessions)
    }

    @Test fun sevenDayReportIncludesEmptyDaysAndExcludesOlderHistory() {
        val history = FocusHistory(listOf(
            FocusDay("2026-09-28", 1, 60_000),
            FocusDay("2026-09-26", 2, 120_000),
            FocusDay("2026-09-20", 3, 180_000),
        ))
        val days = history.sevenDaysThrough("2026-09-29")
        assertEquals(7, days.size)
        assertEquals("2026-09-29", days.first().date)
        assertEquals(0, days.first().sessions)
        assertEquals(1, days[1].sessions)
        assertEquals(2, days[3].sessions)
        assertEquals("2026-09-23", days.last().date)
        assertEquals(false, days.any { it.date == "2026-09-20" })
    }

    @Test fun expiredBreakRecoveryCapturesSelectedTaskForNextFocus() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1, shortBreakMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Next focus", 2)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        state = act(state, ProductCommand.Session(SessionCommand.Acknowledge), 60_000)
        val recovered = ProductEngine.recover(state, TimeMark(120_000, 120_000), date)
        assertEquals(1, recovered.activeTaskId)
        assertEquals(1, recovered.board.selected?.completed)
        val completed = ProductEngine.reduce(recovered, ProductCommand.Session(SessionCommand.Tick),
            TimeMark(180_000, 180_000), date)
        assertEquals(2, completed.board.selected?.completed)
    }

    @Test fun deletingTheActiveTaskClearsItsReferenceWithoutRedirectingCredit() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Current", 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Next", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Task(TaskCommand.Select(2)), 10_000)
        state = act(state, ProductCommand.Task(TaskCommand.Remove(1)), 20_000)
        assertNull(state.activeTaskId)
        assertEquals(2, state.board.selectedId)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        assertEquals(0, state.board.tasks.single().completed)
        assertEquals(1, state.history.on(date).sessions)
    }

    @Test fun markingTheActiveTaskDoneDoesNotDiscardItsEarnedBlock() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Current", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Task(TaskCommand.ToggleDone(1)), 10_000)
        assertEquals(1, state.activeTaskId)
        assertNull(state.board.selectedId)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        assertEquals(true, state.board.tasks.single().done)
        assertEquals(1, state.board.tasks.single().completed)
        assertEquals(1, state.history.on(date).sessions)
    }

    @Test fun pauseAndSelectionChangePreserveTheStartedTask() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Current", 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Next", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.Pause), 20_000)
        state = act(state, ProductCommand.Task(TaskCommand.Select(2)), 30_000)
        state = act(state, ProductCommand.Session(SessionCommand.Resume), 100_000)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 140_000)
        assertEquals(1, state.board.tasks.first().completed)
        assertEquals(0, state.board.tasks.last().completed)
    }

    @Test fun lateResetCreditsTheCompletedBlockExactlyOnce() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Current", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.Reset), 60_000)
        assertEquals(1, state.board.tasks.single().completed)
        assertEquals(1, state.history.on(date).sessions)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        assertEquals(1, state.board.tasks.single().completed)
        assertEquals(1, state.history.on(date).sessions)
    }

    @Test fun selectionAtExpiredBreakDoesNotRedirectTheAutomaticallyStartedBlock() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1, shortBreakMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Current", 2)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Next", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 60_000)
        state = act(state, ProductCommand.Session(SessionCommand.Acknowledge), 60_000)
        state = act(state, ProductCommand.Task(TaskCommand.Select(2)), 120_000)
        assertEquals(1, state.activeTaskId)
        assertEquals(2, state.board.selectedId)
        state = act(state, ProductCommand.Session(SessionCommand.Tick), 180_000)
        assertEquals(2, state.board.tasks.first().completed)
        assertEquals(0, state.board.tasks.last().completed)
        assertEquals(2, state.history.on(date).sessions)
    }

    @Test fun guardedResetClearsTaskOnlyWhenItsPhaseMatches() {
        var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        state = act(state, ProductCommand.Task(TaskCommand.Add("Current", 1)))
        state = act(state, ProductCommand.Session(SessionCommand.Start))
        state = act(state, ProductCommand.Session(SessionCommand.ResetPhase(state.session.phaseId + 1)), 10_000)
        assertEquals(1, state.activeTaskId)
        state = act(state, ProductCommand.Session(SessionCommand.ResetPhase(state.session.phaseId)), 20_000)
        assertNull(state.activeTaskId)
        assertEquals(60_000, state.session.remainingMs)
        assertEquals(0, state.board.tasks.single().completed)
    }
}
