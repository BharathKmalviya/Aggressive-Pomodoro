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
}
