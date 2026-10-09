package com.pomodoro.platform

import com.pomodoro.domain.Phase
import com.pomodoro.domain.AlarmSound
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class DesktopAlertTest {
    @Test fun allSuppliedAlarmsAreDistinctBoundedOfflinePcm() {
        val sounds = AlarmSound.entries.filter { it != AlarmSound.ORIGINAL }.map(::bundledAlarmSound)
        assertEquals(9, sounds.size)
        sounds.forEach { sound ->
            assertEquals(44_100, sound.sampleRate)
            assertTrue(sound.bytes.size in 2..MAX_ALARM_BYTES && sound.bytes.size % 2 == 0)
            assertTrue(sound.bytes.any { it != 0.toByte() })
            assertEquals(0, sound.bytes[0].toInt())
            assertEquals(0, sound.bytes[1].toInt())
            assertEquals(0, sound.bytes[sound.bytes.lastIndex].toInt())
            assertEquals(0, sound.bytes[sound.bytes.lastIndex - 1].toInt())
        }
        assertEquals(9, sounds.map { it.bytes.contentHashCode() }.distinct().size)
    }

    @Test fun selectionIsCachedAndDecodeFailureFallsBackThenRecovers() {
        val sounds = mutableListOf<PcmSound>()
        val reports = mutableListOf<String?>()
        val loaded = mutableListOf<AlarmSound>()
        var failing = true
        val custom = PcmSound(byteArrayOf(1, 2, 3, 4))
        val audio = AlertAudio(openClip = { sounds += it; FakeClip() }, onPlaybackResult = reports::add,
            loadAlarm = { choice ->
                loaded += choice
                if (choice == AlarmSound.FUNNY && failing) error("Missing resource")
                custom
            })
        try {
            audio.playCompletion(Phase.FOCUS, AlarmSound.HAPPY_BELLS)
            audio.playCompletion(Phase.SHORT_BREAK, AlarmSound.HAPPY_BELLS)
            audio.playCompletion(Phase.LONG_BREAK, AlarmSound.HAPPY_BELLS)
            assertEquals(listOf(AlarmSound.HAPPY_BELLS), loaded)
            assertTrue(sounds.take(3).all { it.bytes.contentEquals(custom.bytes) })
            audio.playCompletion(Phase.SHORT_BREAK, AlarmSound.FUNNY)
            assertTrue(sounds.last().bytes.contentEquals(completionSound(Phase.SHORT_BREAK).bytes))
            assertTrue(reports.last()?.contains("original phase alarm") == true)
            failing = false
            audio.playCompletion(Phase.FOCUS, AlarmSound.FUNNY)
            assertTrue(sounds.last().bytes.contentEquals(custom.bytes))
            assertNull(reports.last())
            assertEquals(listOf(AlarmSound.HAPPY_BELLS, AlarmSound.FUNNY, AlarmSound.FUNNY), loaded)
        } finally { audio.close() }
    }

    @Test fun muteDuringResourceDecodeNeverOpensAnAudioDevice() {
        val decoding = CountDownLatch(1)
        val release = CountDownLatch(1)
        val opened = AtomicInteger()
        val audio = AlertAudio(openClip = { opened.incrementAndGet(); FakeClip() }, loadAlarm = {
            decoding.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            PcmSound(byteArrayOf(1, 2))
        })
        val executor = Executors.newSingleThreadExecutor()
        try {
            val playback = executor.submit { audio.playCompletion(Phase.FOCUS, AlarmSound.LOFI) }
            assertTrue(decoding.await(2, TimeUnit.SECONDS))
            audio.stopCompletion()
            release.countDown()
            playback.get(2, TimeUnit.SECONDS)
            assertEquals(0, opened.get())
        } finally {
            release.countDown()
            audio.close()
            executor.shutdownNow()
        }
    }

    private class FakeClip : AudioClip {
        var starts = 0
        var closes = 0
        val closed = CountDownLatch(1)
        var finish: (() -> Unit)? = null
        override fun start(onFinished: () -> Unit) { starts++; finish = onFinished }
        override fun close() { closes++; closed.countDown() }
    }

    @Test fun completionAlarmContainsThreeAudiblePulsesSeparatedBySilence() {
        val sound = completionSound()
        assertEquals(44_100, sound.sampleRate)
        assertEquals(44_100 * 1_520 / 1_000 * 2, sound.bytes.size)
        fun maximum(fromMs: Int, toMs: Int): Int = (fromMs * 44_100 / 1_000 until toMs * 44_100 / 1_000)
            .maxOf { index -> abs(((sound.bytes[index * 2].toInt() and 255) or
                (sound.bytes[index * 2 + 1].toInt() shl 8)).toShort().toInt()) }
        for (start in listOf(0, 520, 1_040)) assertTrue(maximum(start + 20, start + 350) > 10_000)
        for (start in listOf(380, 900, 1_420)) assertEquals(0, maximum(start, start + 90))
        assertTrue(maximum(0, 1_520) < Short.MAX_VALUE, "The alarm must not clip its PCM range")
        assertTrue(clickSound().bytes.size < sound.bytes.size / 10)
    }

    @Test fun repeatedAlarmsReplacePlaybackAndCloseDisposesActiveClips() {
        val clips = mutableListOf<FakeClip>()
        val results = mutableListOf<String?>()
        val audio = AlertAudio(openClip = { FakeClip().also(clips::add) }, onPlaybackResult = results::add)
        audio.playCompletion()
        audio.playCompletion()
        assertEquals(1, clips[0].closes)
        assertEquals(1, clips[1].starts)
        audio.close()
        assertEquals(1, clips[1].closes)
        audio.playCompletion()
        audio.playClick()
        assertEquals(2, clips.size)
        assertEquals(listOf<String?>(null, null), results)
    }

    @Test fun canceledRequestCannotStartAfterMuteEvenIfItHadNotEnteredTheAdapter() {
        val clips = mutableListOf<FakeClip>()
        val audio = AlertAudio(openClip = { FakeClip().also(clips::add) })
        audio.stopCompletion()
        audio.playCompletion { false }
        assertTrue(clips.isEmpty())
        audio.close()
    }

    @Test fun rapidClicksAreThrottledAndSuppressedDuringTheAlarm() {
        var time = 0L
        val clips = mutableListOf<FakeClip>()
        val audio = AlertAudio(openClip = { FakeClip().also(clips::add) }, nanoTime = { time })
        audio.playClick()
        time = 79_999_999
        audio.playClick()
        assertEquals(1, clips.size)
        time = 80_000_000
        audio.playClick()
        assertEquals(2, clips.size)
        assertEquals(1, clips[0].closes)
        audio.playCompletion()
        assertEquals(1, clips[1].closes)
        time = 200_000_000
        audio.playClick()
        assertEquals(3, clips.size)
        clips.last().finish?.invoke()
        assertTrue(clips.last().closed.await(2, TimeUnit.SECONDS))
        assertEquals(1, clips.last().closes)
        audio.playClick()
        assertEquals(4, clips.size)
        audio.close()
    }

    @Test fun stoppingWhileAudioDeviceOpensPreventsStalePlayback() {
        val opening = CountDownLatch(1)
        val releaseOpen = CountDownLatch(1)
        val clip = FakeClip()
        val results = mutableListOf<String?>()
        val audio = AlertAudio(openClip = {
            opening.countDown()
            check(releaseOpen.await(2, TimeUnit.SECONDS))
            clip
        }, onPlaybackResult = results::add)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val playback = executor.submit { audio.playCompletion() }
            assertTrue(opening.await(2, TimeUnit.SECONDS))
            audio.stopCompletion()
            releaseOpen.countDown()
            playback.get(2, TimeUnit.SECONDS)
            assertEquals(0, clip.starts)
            assertEquals(1, clip.closes)
            assertTrue(results.isEmpty())
        } finally {
            releaseOpen.countDown()
            audio.close()
            executor.shutdownNow()
        }
    }

    @Test fun closeDuringAudioOpenPreventsPlaybackAndReleasesTheLateClip() {
        val opening = CountDownLatch(1)
        val releaseOpen = CountDownLatch(1)
        val clip = FakeClip()
        val audio = AlertAudio(openClip = {
            opening.countDown()
            check(releaseOpen.await(2, TimeUnit.SECONDS))
            clip
        })
        val executor = Executors.newSingleThreadExecutor()
        try {
            val playback = executor.submit { audio.playCompletion() }
            assertTrue(opening.await(2, TimeUnit.SECONDS))
            audio.close()
            releaseOpen.countDown()
            playback.get(2, TimeUnit.SECONDS)
            assertEquals(0, clip.starts)
            assertEquals(1, clip.closes)
        } finally {
            releaseOpen.countDown()
            audio.close()
            executor.shutdownNow()
        }
    }

    @Test fun completionFailuresStayVisibleUntilASuccessfulAlarmAndCallbacksCannotBreakCleanup() {
        var failing = true
        val results = mutableListOf<String?>()
        val clip = FakeClip()
        val audio = AlertAudio(openClip = {
            if (failing) error("No audio device") else clip
        }, onPlaybackResult = { results += it; error("UI feedback failed") })
        audio.playCompletion()
        assertNotNull(results.single())
        assertTrue(results.single()!!.contains("audio output"))
        audio.playClick()
        assertEquals(1, results.size)
        failing = false
        audio.playCompletion()
        assertNull(results.last())
        assertEquals(1, clip.starts)
        audio.stopCompletion()
        assertEquals(1, clip.closes)
        audio.close()
    }

    @Test fun playbackStartFailureClosesTheOpenedClipAndReportsFailure() {
        var closed = false
        var warning: String? = null
        val audio = AlertAudio(openClip = {
            object : AudioClip {
                override fun start(onFinished: () -> Unit) { error("Output disconnected") }
                override fun close() { closed = true }
            }
        }, onPlaybackResult = { warning = it })
        audio.playCompletion()
        assertTrue(closed)
        assertNotNull(warning)
        audio.close()
    }

    @Test fun phaseSoundsAreDistinctAndBundledClickIsDecodableOfflinePcm() {
        val focus = completionSound(Phase.FOCUS)
        val short = completionSound(Phase.SHORT_BREAK)
        val long = completionSound(Phase.LONG_BREAK)
        assertFalse(focus.bytes.contentEquals(short.bytes))
        assertTrue(short.bytes.contentEquals(long.bytes))
        val click = bundledClickSound()
        assertEquals(44_100, click.sampleRate)
        assertTrue(click.bytes.size in 2..8_820)
        assertTrue(click.bytes.any { it != 0.toByte() })
    }

    @Test fun completionUsesTheRequestedPhaseAndOldCallbacksCannotClearItsReplacement() {
        val sounds = mutableListOf<PcmSound>()
        val clips = mutableListOf<FakeClip>()
        val audio = AlertAudio(openClip = { sounds += it; FakeClip().also(clips::add) })
        audio.playCompletion(Phase.FOCUS)
        audio.playCompletion(Phase.SHORT_BREAK)
        assertTrue(sounds[0].bytes.contentEquals(completionSound(Phase.FOCUS).bytes))
        assertTrue(sounds[1].bytes.contentEquals(completionSound(Phase.SHORT_BREAK).bytes))
        clips[0].finish?.invoke()
        clips[0].finish?.invoke()
        audio.playClick()
        assertEquals(2, clips.size, "A stale STOP must not release alarm priority")
        audio.stopCompletion()
        assertEquals(1, clips[1].closes)
        assertEquals(1, clips[0].closes, "A replaced clip is closed once")
        audio.close()
    }

    @Test fun stoppingClicksWhileDeviceOpensPreventsLatePlayback() {
        val opening = CountDownLatch(1)
        val releaseOpen = CountDownLatch(1)
        val clip = FakeClip()
        val audio = AlertAudio(openClip = {
            opening.countDown()
            check(releaseOpen.await(2, TimeUnit.SECONDS))
            clip
        })
        val executor = Executors.newSingleThreadExecutor()
        try {
            val playback = executor.submit { audio.playClick() }
            assertTrue(opening.await(2, TimeUnit.SECONDS))
            audio.stopClick()
            releaseOpen.countDown()
            playback.get(2, TimeUnit.SECONDS)
            assertEquals(0, clip.starts)
            assertEquals(1, clip.closes)
        } finally {
            releaseOpen.countDown()
            audio.close()
            executor.shutdownNow()
        }
    }

    @Test fun completionCallbackNeverClosesItsClipOnTheAudioCallbackThread() {
        val callbackThread = Thread.currentThread()
        val released = CountDownLatch(1)
        var finish: (() -> Unit)? = null
        var closeThread: Thread? = null
        val audio = AlertAudio(openClip = {
            object : AudioClip {
                override fun start(onFinished: () -> Unit) { finish = onFinished }
                override fun close() { closeThread = Thread.currentThread(); released.countDown() }
            }
        })
        audio.playCompletion()
        finish!!.invoke()
        assertTrue(released.await(2, TimeUnit.SECONDS))
        assertTrue(closeThread !== callbackThread)
        audio.close()
    }

    @Test fun canceledQueuedClickNeverOpensAudio() {
        val audio = AlertAudio(openClip = { error("Canceled click opened device") })
        audio.playClick { false }
        audio.close()
    }

    @Test fun deviceOpenFailureAfterCancellationDoesNotPostStaleWarningOrHoldAlarmPriority() {
        var active = true
        var failing = true
        val results = mutableListOf<String?>()
        val click = FakeClip()
        val audio = AlertAudio(openClip = {
            if (failing) { active = false; error("Canceled device open") } else click
        }, onPlaybackResult = results::add)
        audio.playCompletion { active }
        assertTrue(results.isEmpty())
        failing = false
        audio.playClick()
        assertEquals(1, click.starts, "Canceled failed alarm must release click priority")
        audio.close()
    }

    @Test fun replacementClosesOldClipOutsideOwnershipLock() {
        val executor = Executors.newSingleThreadExecutor()
        var finish: (() -> Unit)? = null
        var opened = 0
        val replacement = FakeClip()
        val audio = AlertAudio(openClip = {
            if (++opened > 1) replacement else object : AudioClip {
                override fun start(onFinished: () -> Unit) { finish = onFinished }
                override fun close() {
                    // Model a native close waiting for its event thread to finish STOP delivery.
                    executor.submit { finish!!.invoke() }.get(2, TimeUnit.SECONDS)
                }
            }
        })
        try {
            audio.playCompletion()
            audio.playCompletion(Phase.SHORT_BREAK)
            assertEquals(1, replacement.starts)
            assertEquals(2, opened)
        } finally {
            audio.close()
            executor.shutdownNow()
        }
    }

    @Test fun concurrentReplacementCannotStartBeforeOldNativeCloseFinishes() {
        verifySlowCloseBlocksReplacement(stopFromUi = false)
    }

    @Test fun newAlarmWaitsForPendingMuteCleanupOffUi() {
        verifySlowCloseBlocksReplacement(stopFromUi = true)
    }

    private fun verifySlowCloseBlocksReplacement(stopFromUi: Boolean) {
        val closing = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val replacementOpened = CountDownLatch(1)
        val active = AtomicInteger()
        val peak = AtomicInteger()
        var opened = 0
        val audio = AlertAudio(openClip = {
            val first = ++opened == 1
            if (!first) replacementOpened.countDown()
            object : AudioClip {
                override fun start(onFinished: () -> Unit) {
                    peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                }
                override fun close() {
                    if (first) {
                        closing.countDown()
                        check(allowClose.await(2, TimeUnit.SECONDS))
                    }
                    active.decrementAndGet()
                }
            }
        })
        val executor = Executors.newFixedThreadPool(2)
        try {
            audio.playCompletion()
            val firstReplacement = if (stopFromUi) {
                SwingUtilities.invokeAndWait { audio.stopCompletion() }
                null
            } else executor.submit { audio.playCompletion() }
            assertTrue(closing.await(2, TimeUnit.SECONDS))
            val second = executor.submit { audio.playCompletion(Phase.SHORT_BREAK) }
            assertFalse(replacementOpened.await(100, TimeUnit.MILLISECONDS), "Replacement opened before old clip finished closing")
            allowClose.countDown()
            firstReplacement?.get(2, TimeUnit.SECONDS)
            second.get(2, TimeUnit.SECONDS)
            assertEquals(1, peak.get(), "Alarms must never overlap, including during cleanup")
        } finally {
            allowClose.countDown()
            audio.close()
            executor.shutdownNow()
        }
    }
}
