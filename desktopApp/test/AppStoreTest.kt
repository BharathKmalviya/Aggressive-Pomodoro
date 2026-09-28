package com.pomodoro.data

import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionEngine
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.ProductCommand
import com.pomodoro.domain.ProductEngine
import com.pomodoro.domain.TaskCommand
import com.pomodoro.domain.TimeMark
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.newSession
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStoreTest {
    @Test fun firstRunAndPausedSessionRoundTrip() {
        val directory = Files.createTempDirectory("pomodoro-store-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            assertEquals(ProductState(), store.load())
            val settings = TimerSettings(focusMinutes = 3, automaticTransitions = false, soundEnabled = false)
            var state = newSession(settings)
            state = SessionEngine.reduce(state, SessionCommand.Start, TimeMark(0, 0))
            state = SessionEngine.reduce(state, SessionCommand.Pause, TimeMark(30_000, 30_000))
            store.save(ProductState(session = state))
            val restored = store.load().session
            assertEquals(SessionStatus.PAUSED, restored.status)
            assertEquals(150_000, restored.remainingMs)
            assertEquals(settings, restored.settings)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun runningSessionRestoresWallDeadline() {
        val directory = Files.createTempDirectory("pomodoro-store-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            val state = SessionEngine.reduce(newSession(), SessionCommand.Start, TimeMark(10, 100))
            store.save(ProductState(session = state))
            val restored = store.load().session
            assertEquals(SessionStatus.RUNNING, restored.status)
            assertEquals(state.deadlineWallMs, restored.deadlineWallMs)
            assertEquals(null, restored.deadlineMonotonicMs)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun corruptAndUnsupportedSnapshotsShowRecoveryMessage() {
        val directory = Files.createTempDirectory("pomodoro-store-test")
        try {
            val file = directory.resolve("session.properties")
            val store = AppStore(file)
            Files.writeString(file, "broken")
            assertTrue(store.load().session.message?.contains("could not be recovered") == true)
            Files.writeString(file, "version=999")
            assertTrue(store.load().session.message?.contains("could not be recovered") == true)
            Files.writeString(directory.resolve("session-orphan.tmp"), "incomplete")
            assertEquals(0, store.load().session.completedFocus)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun tasksAndDailyTotalsRoundTripWithSoundSettings() {
        val directory = Files.createTempDirectory("pomodoro-store-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            val date = "2026-09-28"
            var state = ProductState(session = newSession(TimerSettings(focusMinutes = 1, clickSoundEnabled = false)))
            state = ProductEngine.reduce(state, ProductCommand.Task(TaskCommand.Add("Write docs", 2)), TimeMark(0, 0), date)
            state = ProductEngine.reduce(state, ProductCommand.Session(SessionCommand.Start), TimeMark(0, 0), date)
            state = ProductEngine.reduce(state, ProductCommand.Session(SessionCommand.Tick), TimeMark(60_000, 60_000), date)
            store.save(state)
            val restored = store.load()
            assertEquals(1, restored.board.selected?.completed)
            assertEquals(60_000, restored.history.on(date).focusedMs)
            assertEquals(false, restored.session.settings.clickSoundEnabled)
            assertEquals(state.session.pending, restored.session.pending)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun orphanTemporaryFileDoesNotReplaceLastValidSnapshot() {
        val directory = Files.createTempDirectory("pomodoro-store-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            val saved = ProductState(session = newSession(TimerSettings(focusMinutes = 3)))
            store.save(saved)
            Files.writeString(directory.resolve("session-interrupted.tmp"), "partial write")
            assertEquals(saved, store.load())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun reminderPreferenceRoundTripsAndLegacySnapshotsEnableIt() = withStore { store, file ->
        val snapshot = ProductState(session = newSession(TimerSettings(
            aggressiveAlertsEnabled = false, soundEnabled = false, clickSoundEnabled = false)))
        store.save(snapshot)
        assertEquals(snapshot, store.load())
        for (version in listOf("1", "2")) {
            store.save(snapshot)
            editProperties(file) {
                setProperty("version", version)
                remove("aggressiveAlertsEnabled")
            }
            val restored = store.load()
            assertTrue(restored.session.settings.aggressiveAlertsEnabled)
            assertEquals(false, restored.session.settings.soundEnabled)
            assertEquals(null, restored.session.message)
        }
    }

    @Test fun unassignedFocusStaysUnassignedAfterSelectionAndRestart() = withStore { store, _ ->
        var snapshot = ProductState(session = newSession(TimerSettings(focusMinutes = 1)))
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Session(SessionCommand.Start), TimeMark(0, 0), date)
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Task(TaskCommand.Add("Next block", 1)), TimeMark(5_000, 5_000), date)
        store.save(snapshot)
        val restored = ProductEngine.recover(store.load(), TimeMark(1_000, 60_000), date)
        assertEquals(0, restored.board.selected?.completed)
        assertEquals(1, restored.history.on(date).sessions)
    }

    @Test fun removingCapturedTaskPreservesTimerAndOtherDataAfterRestart() = withStore { store, _ ->
        var snapshot = ProductState()
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Task(TaskCommand.Add("Active", 1)), TimeMark(0, 0), date)
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Task(TaskCommand.Add("Keep me", 2)), TimeMark(0, 0), date)
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Session(SessionCommand.Start), TimeMark(0, 0), date)
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Task(TaskCommand.Remove(1)), TimeMark(5_000, 5_000), date)
        store.save(snapshot)
        val restored = store.load()
        assertEquals(SessionStatus.RUNNING, restored.session.status)
        assertEquals(listOf("Keep me"), restored.board.tasks.map { it.title })
        assertEquals(null, restored.activeTaskId)
        assertEquals(null, restored.session.message)
    }

    @Test fun legacyDanglingCapturedTaskIsRepairedWithoutDiscardingData() = withStore { store, file ->
        var snapshot = ProductEngine.reduce(ProductState(), ProductCommand.Task(TaskCommand.Add("Keep me", 2)), TimeMark(0, 0), date)
        snapshot = ProductEngine.reduce(snapshot, ProductCommand.Session(SessionCommand.Start), TimeMark(0, 0), date)
        store.save(snapshot)
        editProperties(file) {
            setProperty("version", "2")
            setProperty("activeTaskId", "999")
        }
        assertEquals("Keep me", store.load().board.selected?.title)
        assertEquals(null, store.load().activeTaskId)
        assertEquals(SessionStatus.RUNNING, store.load().session.status)
    }

    @Test fun impossibleWaitingAndRunningSnapshotsAreRejected() = withStore { store, file ->
        store.save(ProductState())
        editProperties(file) { setProperty("status", "WAITING") }
        assertTrue(store.load().session.message != null)

        store.save(ProductState(session = SessionEngine.reduce(newSession(), SessionCommand.Start, TimeMark(0, 0))))
        editProperties(file) {
            setProperty("phaseId", "3")
            setProperty("pending", "1:FOCUS;2:SHORT_BREAK")
        }
        assertTrue(store.load().session.message != null)

        store.save(ProductState())
        editProperties(file) { setProperty("aggressiveAlertsEnabled", "invalid") }
        assertTrue(store.load().session.message != null)
    }

    private val date = "2026-09-28"

    private fun withStore(check: (AppStore, Path) -> Unit) {
        val directory = Files.createTempDirectory("pomodoro-store-regression")
        try {
            val file = directory.resolve("session.properties")
            check(AppStore(file), file)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun editProperties(file: Path, edit: Properties.() -> Unit) {
        val values = Properties().apply { Files.newInputStream(file).use { load(it) }; edit() }
        Files.newOutputStream(file).use { values.store(it, "Test fixture") }
    }
}
