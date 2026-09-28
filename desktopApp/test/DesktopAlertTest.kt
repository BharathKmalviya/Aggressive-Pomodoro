package com.pomodoro.platform

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAlertTest {
    private class FakeClip : AudioClip {
        var starts = 0
        var closes = 0
        var finish: (() -> Unit)? = null
        override fun start(onFinished: () -> Unit) { starts++; finish = onFinished }
        override fun close() { closes++ }
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
}
