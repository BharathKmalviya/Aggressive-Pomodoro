package com.pomodoro.platform

import com.pomodoro.domain.Completion
import com.pomodoro.domain.Phase
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus
import com.pomodoro.presentation.AppDestination
import com.pomodoro.presentation.DesktopTrayMode
import com.pomodoro.presentation.DesktopTrayState
import com.pomodoro.presentation.UpdateStatus
import com.pomodoro.presentation.desktopTrayState
import java.awt.CheckboxMenuItem
import java.awt.Menu
import java.awt.MenuItem
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Exercises real AWT menu resources on the event thread without installing an icon or opening UI. */
class DesktopTrayMenuTest {
    @Test fun closeChoiceDoesNotDisableNativeControlsAndSummariesAreActionable() = SwingUtilities.invokeAndWait {
        val fixture = Fixture(project(mode = DesktopTrayMode.CLOSE_CHOICE))
        with(fixture) {
            listOf("Cancel close and open app", state.statusText, "${state.todayText}...", "Pause focus",
                "Timer options", "Reset current block...", "Skip current phase...", "Alerts", "Alarm sound",
                "Repeat completion reminders", "Reports...", "Settings...", "Check for updates...",
                "About...", "Run in background", "Exit").forEach { assertTrue(item(it).isEnabled, it) }
            assertIs<Menu>(item("Timer options"))
            assertIs<Menu>(item("Alerts"))
            click(state.statusText)
            click("${state.todayText}...")
            click("Pause focus")
            click("Reset current block...")
            click("Run in background")
            click("Exit")
            assertEquals(1, shows)
            assertEquals(listOf(AppDestination.REPORTS to null, AppDestination.RESET to state.phaseId), opens)
            assertEquals(listOf<SessionCommand>(SessionCommand.PausePhase(state.phaseId)), commands)
            assertEquals(1, backgrounds)
            assertEquals(1, exits)
            menu.close()
        }
    }

    @Test fun queuedEventsHonorLatestRestrictionsAndRecoveryReenablesExistingMenu() = SwingUtilities.invokeAndWait {
        val fixture = Fixture(project())
        with(fixture) {
            menu.update(project(mode = DesktopTrayMode.SAVING))
            assertTrue(item("Saving session - open app").isEnabled)
            assertTrue(item(state.statusText).isEnabled)
            assertFalse(item("Pause focus").isEnabled)
            assertFalse(item("Timer options").isEnabled)
            assertFalse(item("Alerts").isEnabled)
            click("Pause focus") // A previously queued native event must also be guarded.
            click("Reset current block...")
            click("Exit")
            assertTrue(commands.isEmpty())
            assertTrue(opens.isEmpty())
            assertEquals(0, exits)
            menu.update(project(mode = DesktopTrayMode.SAVE_ERROR))
            click("Review save error...")
            assertEquals(1, exits)
            menu.update(project(phaseId = 9))
            assertTrue(item("Pause focus").isEnabled)
            assertTrue(item("Reports...").isEnabled)
            assertTrue(item("Alerts").isEnabled)
            click("Pause focus")
            click("Skip current phase...")
            assertEquals(listOf<SessionCommand>(SessionCommand.PausePhase(9)), commands)
            assertEquals(listOf<Pair<AppDestination, Long?>>(AppDestination.SKIP to 9L), opens)
            menu.close()
            click("Pause focus")
            click("Open Aggressive Pomodoro")
            assertEquals(1, commands.size)
            assertEquals(0, shows)
        }
    }

    @Test fun pendingCompletionKeepsReviewAndMuteAvailableButCannotOpenConflictingDialogs() = SwingUtilities.invokeAndWait {
        val state = project(pending = true)
        val fixture = Fixture(state)
        with(fixture) {
            assertTrue(item("Review completed phase...").isEnabled)
            assertTrue(item("Alerts").isEnabled)
            assertFalse(item("Timer options").isEnabled)
            assertFalse(item("${state.todayText}...").isEnabled)
            click("Review completed phase...")
            click("Reports...")
            click("Reset current block...")
            val sound = assertIs<CheckboxMenuItem>(item("Alarm sound"))
            sound.state = false
            sound.itemListeners.forEach { it.itemStateChanged(ItemEvent(sound, ItemEvent.ITEM_STATE_CHANGED,
                sound.label, ItemEvent.DESELECTED)) }
            assertEquals(listOf(false), sounds)
            assertEquals(1, shows)
            assertTrue(opens.isEmpty())
            assertTrue(commands.isEmpty())
            menu.update(project(mode = DesktopTrayMode.SAVING))
            sound.state = false // A queued native toggle must not leave a false unsaved checkmark.
            sound.itemListeners.forEach { it.itemStateChanged(ItemEvent(sound, ItemEvent.ITEM_STATE_CHANGED,
                sound.label, ItemEvent.DESELECTED)) }
            assertEquals(1, sounds.size)
            assertTrue(sound.state)
            menu.close()
        }
    }

    private fun project(mode: DesktopTrayMode = DesktopTrayMode.NORMAL, phaseId: Long = 1, pending: Boolean = false) =
        ProductState().let { desktopTrayState(it.copy(session = it.session.copy(
            phaseId = phaseId, status = SessionStatus.RUNNING,
            pending = if (pending) listOf(Completion(0, Phase.FOCUS)) else emptyList())),
            60_000, "2026-10-09", UpdateStatus.IDLE, mode) }

    private class Fixture(val state: DesktopTrayState) {
        var shows = 0
        var exits = 0
        var backgrounds = 0
        val commands = mutableListOf<SessionCommand>()
        val opens = mutableListOf<Pair<AppDestination, Long?>>()
        val sounds = mutableListOf<Boolean>()
        val menu = DesktopTrayMenu(state, { shows++ }, { exits++ }, { commands += it },
            { destination, phaseId -> opens += destination to phaseId }, { sounds += it }, {}, { backgrounds++ })
        fun item(label: String): MenuItem = find(menu.popup, label) ?: error("Missing menu item: $label")
        fun click(label: String) {
            val item = item(label)
            item.actionListeners.forEach { it.actionPerformed(ActionEvent(item, ActionEvent.ACTION_PERFORMED, label)) }
        }
        private fun find(menu: Menu, label: String): MenuItem? {
            for (index in 0 until menu.itemCount) {
                val item = menu.getItem(index)
                if (item.label == label) return item
                if (item is Menu) find(item, label)?.let { return it }
            }
            return null
        }
    }
}
