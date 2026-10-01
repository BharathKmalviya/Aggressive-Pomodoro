package com.pomodoro.presentation

import com.pomodoro.data.AppStore
import com.pomodoro.data.SnapshotStore
import com.pomodoro.domain.Phase
import com.pomodoro.domain.Completion
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.TimeMark
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.TaskCommand
import com.pomodoro.domain.newSession
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicBoolean

class DesktopSessionControllerTest {
    private class MemoryStore : SnapshotStore {
        val snapshots = mutableListOf<ProductState>()
        override fun load() = ProductState()
        @Synchronized override fun save(snapshot: ProductState) { snapshots += snapshot }
    }

    private class Fixture(
        settings: TimerSettings = TimerSettings(focusMinutes = 1, shortBreakMinutes = 1, longBreakMinutes = 1),
        saved: ProductState = ProductState(session = newSession(settings)),
        val store: SnapshotStore = MemoryStore(),
        callback: ((Completion, Boolean) -> Unit)? = null,
    ) {
        var monotonic = 0L
        var wall = 0L
        val alerts = mutableListOf<Pair<Completion, Boolean>>()
        val controller = DesktopSessionController(store, saved, { TimeMark(monotonic, wall) },
            { "2026-09-28" }, { event, sound -> alerts += event to sound; callback?.invoke(event, sound) },
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        fun at(time: Long) { monotonic = time; wall = time }
        fun tick(time: Long) { at(time); controller.tick() }
    }

    @Test fun savedRulesAndResetSurviveRealStorageAndRelaunch() = runBlocking {
        val directory = Files.createTempDirectory("pomodoro-rules-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            val fixture = Fixture(store = store)
            val controller = fixture.controller
            controller.dispatchTask(TaskCommand.Add("Keep this task", 2))
            controller.dispatchSession(SessionCommand.Start)
            fixture.at(20_000)
            controller.dispatchSession(SessionCommand.Pause)
            val changed = controller.state.settings.copy(focusMinutes = 10, shortBreakMinutes = 3,
                longBreakMinutes = 7, longBreakEvery = 2, automaticTransitions = false,
                soundEnabled = false, clickSoundEnabled = false, aggressiveAlertsEnabled = false, reduceMotion = true)
            controller.dispatchSession(SessionCommand.ChangeSettings(changed))
            controller.close()
            val paused = store.load()
            assertEquals(changed, paused.session.settings)
            assertEquals(40_000L, paused.session.remainingMs)
            assertEquals(60_000L, paused.session.durationMs)
            val reopened = Fixture(saved = paused, store = store).controller
            try {
                reopened.dispatchSession(SessionCommand.ResetPhase(reopened.state.phaseId))
                assertEquals(600_000L, reopened.state.remainingMs)
                assertEquals(null, reopened.product.activeTaskId)
                assertEquals(paused.board, reopened.product.board)
                assertEquals(paused.history, reopened.product.history)
            } finally {
                reopened.close()
            }
            val idle = store.load()
            assertEquals(SessionStatus.IDLE, idle.session.status)
            assertEquals(600_000L, idle.session.durationMs)
            val third = Fixture(saved = idle, store = store).controller
            try {
                assertEquals(changed, third.state.settings)
                third.dispatchSession(SessionCommand.StartPhase(third.state.phaseId))
                assertEquals(600_000L, third.state.deadlineMonotonicMs)
            } finally {
                third.close()
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun legacyIdleRuleMismatchIsRepairedAndPersistedOnLaunch() = runBlocking {
        val directory = Files.createTempDirectory("pomodoro-legacy-rules-test")
        try {
            val store = AppStore(directory.resolve("session.properties"))
            val legacy = ProductState(session = newSession(TimerSettings(focusMinutes = 1))
                .copy(settings = TimerSettings(focusMinutes = 10)))
            store.save(legacy)
            val fixture = Fixture(saved = store.load(), store = store)
            try {
                assertEquals(600_000L, fixture.controller.state.remainingMs)
                assertEquals(legacy.board, fixture.controller.product.board)
                assertEquals(legacy.history, fixture.controller.product.history)
            } finally {
                fixture.controller.close()
            }
            assertEquals(600_000L, store.load().session.durationMs)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test fun remindersRepeatAtTenSecondsWithoutChangingCompletionCredit() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.controller
        controller.dispatchSession(SessionCommand.Start)
        fixture.tick(60_000)
        val firstCompletion = controller.state.pending.single()
        fixture.tick(69_999)
        assertEquals(1, fixture.alerts.size)
        fixture.tick(70_000)
        fixture.tick(70_000)
        assertEquals(listOf(firstCompletion, firstCompletion), fixture.alerts.map { it.first })
        assertEquals(listOf(firstCompletion), controller.state.pending)
        assertEquals(1, controller.product.history.on("2026-09-28").sessions)
        controller.close()
    }

    @Test fun delayedWaitingChecksSendOneReminderAndNewCompletionsTakePriority() = runBlocking {
        val fixture = Fixture()
        fixture.controller.dispatchSession(SessionCommand.Start)
        fixture.tick(60_000)
        fixture.tick(120_000)
        assertEquals(listOf(Phase.FOCUS, Phase.SHORT_BREAK), fixture.alerts.map { it.first.phase })
        fixture.tick(600_000)
        fixture.tick(600_001)
        assertEquals(listOf(Phase.FOCUS, Phase.SHORT_BREAK, Phase.FOCUS), fixture.alerts.map { it.first.phase })
        assertEquals(2, fixture.controller.state.pending.size)
        assertEquals(1, fixture.controller.product.history.on("2026-09-28").sessions)
        fixture.controller.close()
    }

    @Test fun acknowledgementResetsRemainingQueueIntervalAndStopsWhenEmpty() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.controller
        controller.dispatchSession(SessionCommand.Start)
        fixture.tick(60_000)
        fixture.tick(120_000)
        fixture.at(129_000)
        controller.dispatchSession(SessionCommand.Acknowledge)
        fixture.tick(138_999)
        assertEquals(2, fixture.alerts.size)
        fixture.tick(139_000)
        assertEquals(Phase.SHORT_BREAK, fixture.alerts.last().first.phase)
        controller.dispatchSession(SessionCommand.Acknowledge)
        controller.dispatchSession(SessionCommand.Pause)
        fixture.tick(160_000)
        assertEquals(3, fixture.alerts.size)
        assertTrue(controller.state.pending.isEmpty())
        controller.close()
    }

    @Test fun mutedRemindersRemainVisualAndReminderSettingIsIndependent() = runBlocking {
        val fixture = Fixture(settings = TimerSettings(focusMinutes = 1, soundEnabled = false))
        val controller = fixture.controller
        controller.dispatchSession(SessionCommand.Start)
        fixture.tick(60_000)
        fixture.tick(70_000)
        assertEquals(listOf(false, false), fixture.alerts.map { it.second })
        controller.dispatchSession(SessionCommand.ChangeSettings(controller.state.settings.copy(aggressiveAlertsEnabled = false)))
        fixture.tick(100_000)
        assertEquals(2, fixture.alerts.size)
        controller.dispatchSession(SessionCommand.ChangeSettings(controller.state.settings.copy(aggressiveAlertsEnabled = true)))
        fixture.tick(109_999)
        assertEquals(2, fixture.alerts.size)
        fixture.tick(110_000)
        assertEquals(3, fixture.alerts.size)
        controller.close()
    }

    @Test fun disabledRemindersStillDeliverTheInitialCompletion() = runBlocking {
        val fixture = Fixture(settings = TimerSettings(focusMinutes = 1, aggressiveAlertsEnabled = false))
        fixture.controller.dispatchSession(SessionCommand.Start)
        fixture.tick(60_000)
        fixture.tick(90_000)
        assertEquals(1, fixture.alerts.size)
        fixture.controller.close()
    }

    @Test fun restoredPendingEventIsDeliveredOnceThenUsesMonotonicTime() = runBlocking {
        val event = Completion(1, Phase.FOCUS)
        val saved = ProductState(session = newSession().copy(phase = Phase.SHORT_BREAK, phaseId = 2,
            status = SessionStatus.WAITING, pending = listOf(event)))
        val fixture = Fixture(saved = saved)
        assertEquals(listOf(event), fixture.alerts.map { it.first })
        fixture.monotonic = 9_999
        fixture.wall = 3_600_000
        fixture.controller.tick()
        assertEquals(1, fixture.alerts.size)
        fixture.monotonic = 10_000
        fixture.wall = -3_600_000
        fixture.controller.tick()
        assertEquals(2, fixture.alerts.size)
        fixture.controller.close()
    }

    @Test fun throwingEffectsDoNotLoseCompletionOrPersistence() = runBlocking {
        val fixture = Fixture(callback = { _, _ -> error("audio failed") })
        fixture.controller.dispatchSession(SessionCommand.Start)
        fixture.tick(60_000)
        fixture.tick(70_000)
        fixture.controller.close()
        val persisted = (fixture.store as MemoryStore).snapshots.last()
        assertEquals(1, persisted.session.pending.size)
        assertEquals(1, persisted.history.on("2026-09-28").sessions)
        assertEquals(2, fixture.alerts.size)
    }

    @Test fun commandsUseFreshTimeWithoutWaitingForATick() = runBlocking {
        val fixture = Fixture()
        fixture.at(5_000)
        fixture.controller.dispatchSession(SessionCommand.Start)
        assertEquals(65_000L, fixture.controller.state.deadlineMonotonicMs)
        fixture.at(20_000)
        fixture.controller.dispatchSession(SessionCommand.Pause)
        assertEquals(45_000L, fixture.controller.state.remainingMs)
        fixture.at(30_000)
        fixture.controller.dispatchSession(SessionCommand.Resume)
        assertEquals(75_000L, fixture.controller.state.deadlineMonotonicMs)
        fixture.at(75_000)
        fixture.controller.dispatchSession(SessionCommand.Reset)
        assertEquals(Phase.SHORT_BREAK, fixture.controller.state.phase)
        assertEquals(1, fixture.alerts.size)
        fixture.controller.close()
    }

    @Test fun failedCloseLeavesWriterAvailableForFurtherChangesAndRetry() = runBlocking {
        val failing = AtomicBoolean(true)
        val savedAfterFailure = CountDownLatch(1)
        val snapshots = mutableListOf<ProductState>()
        val store = object : SnapshotStore {
            override fun load() = ProductState()
            override fun save(snapshot: ProductState) {
                check(!failing.get()) { "Disk unavailable" }
                snapshots += snapshot
                savedAfterFailure.countDown()
            }
        }
        val fixture = Fixture(store = store)
        fixture.controller.dispatchSession(SessionCommand.Start)
        assertFailsWith<IllegalStateException> { fixture.controller.close() }
        failing.set(false)
        fixture.at(5_000)
        fixture.controller.dispatchSession(SessionCommand.Pause)
        assertTrue(savedAfterFailure.await(2, TimeUnit.SECONDS))
        fixture.controller.close()
        assertEquals(SessionStatus.PAUSED, snapshots.last().session.status)
        assertEquals(55_000L, snapshots.last().session.remainingMs)
    }

    @Test fun failedInstallerPreparationKeepsTheSavedSessionAndWriterUsable() = runBlocking {
        val fixture = Fixture()
        val controller = fixture.controller
        controller.dispatchSession(SessionCommand.Start)
        assertFailsWith<IllegalStateException> {
            controller.close(beforeClose = {
                assertEquals(SessionStatus.RUNNING, (fixture.store as MemoryStore).snapshots.last().session.status)
                error("Installer launch failed")
            })
        }
        fixture.at(5_000)
        controller.dispatchSession(SessionCommand.Pause)
        controller.close()
        assertEquals(SessionStatus.PAUSED, (fixture.store as MemoryStore).snapshots.last().session.status)
        assertEquals(55_000L, controller.state.remainingMs)
    }

    @Test fun slowStorageConflatesQueuedChangesAndCloseSavesTheLatestSnapshot() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val snapshots = mutableListOf<ProductState>()
        val store = object : SnapshotStore {
            override fun load() = ProductState()
            override fun save(snapshot: ProductState) {
                if (first.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.await(3, TimeUnit.SECONDS))
                }
                snapshots += snapshot
            }
        }
        val fixture = Fixture(store = store)
        val controller = fixture.controller
        try {
            controller.dispatchSession(SessionCommand.Start)
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            repeat(20) { controller.dispatchTask(TaskCommand.Add("Task $it", 1)) }
            val closing = async(start = CoroutineStart.UNDISPATCHED) { controller.close() }
            controller.dispatchSession(SessionCommand.Pause)
            assertEquals(SessionStatus.RUNNING, controller.state.status)
            release.countDown()
            closing.await()
            assertEquals(2, snapshots.size)
            assertEquals(20, snapshots.last().board.tasks.size)
        } finally {
            release.countDown()
            controller.close()
        }
    }

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
