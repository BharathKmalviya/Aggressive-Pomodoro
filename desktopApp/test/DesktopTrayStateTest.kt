package com.pomodoro.presentation

import com.pomodoro.domain.Completion
import com.pomodoro.domain.FocusDay
import com.pomodoro.domain.FocusHistory
import com.pomodoro.domain.Phase
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopTrayStateTest {
    private val date = "2026-10-09"

    @Test fun phaseControlsShowTruthfulRoundedTimeAndLocalDailyCredit() {
        val product = ProductState(history = FocusHistory(listOf(FocusDay(date, 2, 3_000_000),
            FocusDay("2026-10-08", 8, 12_000_000))))
        val idle = desktopTrayState(product, 60_001, date, UpdateStatus.IDLE)
        assertEquals("Focus - 01:01 - Ready", idle.statusText)
        assertEquals("Today: 2 blocks / 50 min", idle.todayText)
        assertEquals(SessionCommand.StartPhase(1), idle.primaryCommand)
        assertTrue(idle.tooltip.contains(idle.statusText))
        val running = desktopTrayState(product.copy(session = product.session.copy(
            phase = Phase.LONG_BREAK, phaseId = 8, status = SessionStatus.RUNNING)), 60_000, date, UpdateStatus.IDLE)
        assertEquals("Long break - 01:00 - Running", running.statusText)
        assertEquals("Pause long break", running.primaryLabel)
        assertEquals(SessionCommand.PausePhase(8), running.primaryCommand)
        val paused = desktopTrayState(product.copy(session = product.session.copy(
            phase = Phase.SHORT_BREAK, phaseId = 4, status = SessionStatus.PAUSED)), -1, date, UpdateStatus.IDLE)
        assertEquals("Short break - 00:00 - Paused", paused.statusText)
        assertEquals(SessionCommand.ResumePhase(4), paused.primaryCommand)
        assertEquals("Today: 0 blocks / 0 min", desktopTrayState(product, 0, "2026-10-10", UpdateStatus.IDLE).todayText)
    }

    @Test fun pendingAlertsPermitReviewAndMuteButBlockTimerAndSecondaryDialogs() {
        val product = ProductState()
        val pending = desktopTrayState(product.copy(session = product.session.copy(
            status = SessionStatus.WAITING, phaseId = 3,
            pending = listOf(Completion(1, Phase.FOCUS), Completion(2, Phase.SHORT_BREAK)))), 60_000, date, UpdateStatus.READY)
        assertEquals("Review completed phase...", pending.primaryLabel)
        assertTrue(pending.statusText.contains("Waiting / 2 alerts to review"))
        assertNull(pending.primaryCommand)
        assertTrue(pending.actionsEnabled)
        assertFalse(pending.timerActionsEnabled)
        assertFalse(pending.navigationEnabled)
        val blocked = desktopTrayState(product, 60_000, date, UpdateStatus.IDLE, mode = DesktopTrayMode.SAVING)
        assertFalse(blocked.actionsEnabled)
        assertFalse(blocked.timerActionsEnabled)
        assertFalse(blocked.navigationEnabled)
    }

    @Test fun uncommittedCloseChoiceKeepsNormalActionsAvailableInEveryTimerState() {
        SessionStatus.entries.forEach { status ->
            val product = ProductState().let { it.copy(session = it.session.copy(status = status)) }
            val normal = desktopTrayState(product, 60_000, date, UpdateStatus.IDLE)
            val choosing = desktopTrayState(product, 60_000, date, UpdateStatus.IDLE, DesktopTrayMode.CLOSE_CHOICE)
            assertTrue(choosing.actionsEnabled)
            assertTrue(choosing.exitEnabled)
            assertEquals(normal.timerActionsEnabled, choosing.timerActionsEnabled)
            assertEquals(normal.navigationEnabled, choosing.navigationEnabled)
            assertEquals(normal.primaryCommand, choosing.primaryCommand)
            assertEquals("Cancel close and open app", choosing.showLabel)
            assertTrue(choosing.tooltip.contains("cancel the open close choice"))
        }
    }

    @Test fun actualSavingInstallingAndSaveErrorExplainRestrictionsAndRecovery() {
        val product = ProductState()
        for (mode in listOf(DesktopTrayMode.SAVING, DesktopTrayMode.INSTALLING, DesktopTrayMode.SAVE_ERROR)) {
            val restricted = desktopTrayState(product, 60_000, date, UpdateStatus.IDLE, mode)
            assertFalse(restricted.actionsEnabled)
            assertFalse(restricted.timerActionsEnabled)
            assertFalse(restricted.navigationEnabled)
            assertTrue(restricted.showLabel.all { it.code < 128 })
            assertTrue(restricted.tooltip.lines().size >= 3)
            assertEquals(mode == DesktopTrayMode.SAVE_ERROR, restricted.exitEnabled)
            if (mode == DesktopTrayMode.SAVE_ERROR) {
                assertEquals("Resolve save error...", restricted.showLabel)
                assertEquals("Review save error...", restricted.exitLabel)
                assertTrue(restricted.tooltip.contains("retry or keep it open"))
            }
        }
        val recovered = desktopTrayState(product, 60_000, date, UpdateStatus.IDLE)
        assertTrue(recovered.actionsEnabled)
        assertTrue(recovered.timerActionsEnabled)
        assertTrue(recovered.navigationEnabled)
        assertEquals("Open Aggressive Pomodoro", recovered.showLabel)
        assertEquals("Exit...", recovered.exitLabel)
    }

    @Test fun activeUpdatesAreViewedAndSavedCheckboxesReflectCurrentPreferences() {
        val product = ProductState().let { it.copy(session = it.session.copy(
            settings = it.session.settings.copy(soundEnabled = false, aggressiveAlertsEnabled = false))) }
        UpdateStatus.entries.forEach { status ->
            val menu = desktopTrayState(product, 0, date, status)
            assertFalse(menu.soundEnabled)
            assertFalse(menu.remindersEnabled)
            val existing = status in setOf(UpdateStatus.CHECKING, UpdateStatus.AVAILABLE, UpdateStatus.DOWNLOADING,
                UpdateStatus.READY, UpdateStatus.INSTALLING)
            assertEquals(if (existing) "View update..." else "Check for updates...", menu.updateLabel)
            assertTrue(menu.primaryLabel.all { it.code < 128 })
        }
    }

    @Test fun confirmationRequestsCannotTargetAnotherPhaseOrPendingAlert() {
        val state = ProductState().session
        val reset = AppRequest(1, AppDestination.RESET, state.phaseId)
        assertEquals(SessionCommand.ResetPhase(state.phaseId), reset.confirmationCommand(state))
        assertEquals(SessionCommand.SkipPhase(state.phaseId), AppRequest(2, AppDestination.SKIP,
            state.phaseId).confirmationCommand(state))
        assertNull(reset.confirmationCommand(state.copy(phaseId = state.phaseId + 1)))
        assertNull(reset.confirmationCommand(state.copy(pending = listOf(Completion(0, Phase.FOCUS)))))
        assertNull(AppRequest(3, AppDestination.SETTINGS, state.phaseId).confirmationCommand(state))
    }
}
