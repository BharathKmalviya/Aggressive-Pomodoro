package com.pomodoro.presentation

import com.pomodoro.data.AppStore
import com.pomodoro.data.SnapshotStore
import com.pomodoro.domain.Phase
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.TimeMark
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.newSession
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopSessionControllerTest {
    @Test fun completionEffectsAreEmittedOnceAndPersisted() = runBlocking {
        val directory = Files.createTempDirectory("pomodoro-controller-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            val initial = ProductState(session = newSession(TimerSettings(
                focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 1)))
            var time = 0L
            val alerts = mutableListOf<Phase>()
            val controller = DesktopSessionController(store, initial, { TimeMark(time, time) },
                { "2026-09-28" }, { event, _ -> alerts += event.phase },
                CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
            controller.dispatchSession(SessionCommand.Start)
            time = 60_000
            controller.tick()
            controller.tick()
            assertEquals(listOf(Phase.FOCUS), alerts)
            assertEquals(SessionStatus.RUNNING, controller.state.status)
            time = 120_000
            controller.tick()
            assertEquals(listOf(Phase.FOCUS, Phase.SHORT_BREAK), alerts)
            assertEquals(SessionStatus.WAITING, controller.state.status)
            controller.close()
            assertEquals(2, store.load().session.pending.size)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun blockedStorageDoesNotBlockControls() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val initial = ProductState()
        val store = object : SnapshotStore {
            override fun load() = initial
            override fun save(snapshot: ProductState) {
                entered.countDown()
                release.await(3, TimeUnit.SECONDS)
            }
        }
        val controller = DesktopSessionController(store, initial, { TimeMark(0, 0) },
            { "2026-09-28" }, { _, _ -> }, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        try {
            controller.dispatchSession(SessionCommand.Start)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            controller.dispatchSession(SessionCommand.Pause)
            assertEquals(SessionStatus.PAUSED, controller.state.status)
        } finally {
            release.countDown()
            controller.close()
        }
    }
}
