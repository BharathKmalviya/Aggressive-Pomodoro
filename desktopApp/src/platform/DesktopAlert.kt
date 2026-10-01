package com.pomodoro.platform

import java.awt.Taskbar
import java.awt.Window
import com.pomodoro.domain.Phase
import java.util.concurrent.ForkJoinPool
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent
import javax.swing.SwingUtilities
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

class DesktopAlert(
    private val window: Window,
    onPlaybackResult: (String?) -> Unit = {},
) : AutoCloseable {
    private val audio = AlertAudio(onPlaybackResult = onPlaybackResult)

    fun requestAttention() {
        try {
            if (window.isFocused) return
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                taskbar.requestWindowUserAttention(window)
            }
        } catch (error: Exception) {
            System.err.println("Desktop attention unavailable: ${error.message}")
        }
    }

    // Audio opening and playback calls belong on the desktop IO dispatcher.
    fun playCompletion(phase: Phase = Phase.FOCUS, shouldPlay: () -> Boolean = { true }) = audio.playCompletion(phase, shouldPlay)
    fun playClick(shouldPlay: () -> Boolean = { true }) = audio.playClick(shouldPlay)
    fun stopCompletion() = audio.stopCompletion()
    fun stopClick() = audio.stopClick()
    override fun close() = audio.close()
}

internal data class PcmSound(val bytes: ByteArray, val sampleRate: Int = 44_100)

/** Three chord pulses, with separate completion/return motifs and click-free amplitude ramps. */
internal fun completionSound(phase: Phase = Phase.FOCUS): PcmSound {
    val frequencies = if (phase == Phase.FOCUS) doubleArrayOf(523.25, 659.25, 783.99)
        else doubleArrayOf(880.0, 1_108.73, 880.0)
    return synthesizeSound(1_520) { time ->
        val pulse = (time / 0.52).toInt()
        val local = time - pulse * 0.52
        if (pulse > 2 || local >= 0.38) 0.0 else {
            val envelope = min(1.0, min(local / 0.012, (0.38 - local) / 0.025)).coerceAtLeast(0.0)
            val frequency = frequencies[pulse]
            envelope * (sin(2 * PI * frequency * local) * 0.43 +
                sin(2 * PI * frequency * 1.5 * local) * 0.13 + sin(2 * PI * frequency * 2 * local) * 0.06)
        }
    }
}

internal fun clickSound(): PcmSound = synthesizeSound(35) { time ->
    val envelope = min(time / 0.003, (0.035 - time) / 0.032).coerceIn(0.0, 1.0)
    sin(2 * PI * 1_100 * time) * envelope * 0.16
}

internal fun bundledClickSound(): PcmSound {
    val resource = checkNotNull(DesktopAlert::class.java.getResource("/sounds/click.wav"))
    return AudioSystem.getAudioInputStream(resource).use { input ->
        val format = input.format
        require(format.sampleRate == 44_100f && format.sampleSizeInBits == 16 &&
            format.channels == 1 && !format.isBigEndian && format.encoding == AudioFormat.Encoding.PCM_SIGNED)
        PcmSound(input.readAllBytes()).also { require(it.bytes.isNotEmpty() && it.bytes.size % 2 == 0) }
    }
}

private fun synthesizeSound(durationMs: Int, sample: (Double) -> Double): PcmSound {
    val sampleRate = 44_100
    val bytes = ByteArray(sampleRate * durationMs / 1_000 * 2)
    for (index in 0 until bytes.size / 2) {
        val value = (sample(index.toDouble() / sampleRate).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
        bytes[index * 2] = value.toByte()
        bytes[index * 2 + 1] = (value shr 8).toByte()
    }
    return PcmSound(bytes, sampleRate)
}

internal interface AudioClip : AutoCloseable {
    fun start(onFinished: () -> Unit)
}

private fun openJavaSoundClip(sound: PcmSound): AudioClip {
    val clip = AudioSystem.getClip()
    try {
        clip.open(AudioFormat(sound.sampleRate.toFloat(), 16, 1, true, false), sound.bytes, 0, sound.bytes.size)
    } catch (error: Exception) {
        clip.close()
        throw error
    }
    return object : AudioClip {
        private var closed = false
        override fun start(onFinished: () -> Unit) {
            clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP) onFinished() }
            clip.start()
        }
        @Synchronized override fun close() {
            if (closed) return
            closed = true
            clip.close()
        }
    }
}

/** Owns clips across concurrent IO calls, including mute/close while an audio device opens. */
internal class AlertAudio(
    private val openClip: (PcmSound) -> AudioClip = ::openJavaSoundClip,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onPlaybackResult: (String?) -> Unit = {},
) : AutoCloseable {
    private class OwnedClip(private val delegate: AudioClip) : AudioClip {
        private var closed = false
        override fun start(onFinished: () -> Unit) = delegate.start(onFinished)
        @Synchronized override fun close() {
            if (closed) return
            closed = true
            delegate.close()
        }
    }
    private val lock = Any()
    // Serialize native open/start/replacement work on IO, separately from UI invalidation.
    private val playbackLock = Any()
    private var completion: AudioClip? = null
    private var click: AudioClip? = null
    private var completionGeneration = 0L
    private var clickGeneration = 0L
    private var completionRequested = false
    private var lastClickNs: Long? = null
    private var closed = false
    private val focusAlarm by lazy { completionSound(Phase.FOCUS) }
    private val breakAlarm by lazy { completionSound(Phase.SHORT_BREAK) }
    private val button by lazy { runCatching(::bundledClickSound).getOrElse { clickSound() } }

    fun playCompletion(phase: Phase = Phase.FOCUS, shouldPlay: () -> Boolean = { true }) =
        synchronized(playbackLock) { playCompletionSerial(phase, shouldPlay) }

    private fun playCompletionSerial(phase: Phase, shouldPlay: () -> Boolean) {
        val generation: Long
        val previous: List<AudioClip?>
        synchronized(lock) {
            if (closed || !shouldPlay()) return
            generation = ++completionGeneration
            ++clickGeneration
            completionRequested = true
            previous = listOf(completion, click)
            completion = null
            click = null
        }
        previous.forEach(::release)
        var opened: AudioClip? = null
        try {
            val clip = OwnedClip(openClip(if (phase == Phase.FOCUS) focusAlarm else breakAlarm))
            opened = clip
            val accepted = synchronized(lock) {
                if (closed || generation != completionGeneration || !shouldPlay()) {
                    if (generation == completionGeneration) completionRequested = false
                    false
                } else {
                    completion = clip
                    clip.start { finishCompletion(generation, clip) }
                    report(null)
                    true
                }
            }
            if (!accepted) release(clip)
        } catch (error: Exception) {
            synchronized(lock) {
                if (generation == completionGeneration) {
                    completion = null
                    completionRequested = false
                    if (!closed && shouldPlay()) report("Alarm could not play. Check your audio output and volume, then test an alarm in Settings.")
                }
            }
            release(opened)
            System.err.println("Completion sound unavailable: ${error.message}")
        }
    }

    fun playClick(shouldPlay: () -> Boolean = { true }) =
        synchronized(playbackLock) { playClickSerial(shouldPlay) }

    private fun playClickSerial(shouldPlay: () -> Boolean) {
        val generation: Long
        val previous: AudioClip?
        synchronized(lock) {
            val now = nanoTime()
            if (closed || !shouldPlay() || completionRequested || lastClickNs?.let { now - it < 80_000_000L } == true) return
            lastClickNs = now
            generation = ++clickGeneration
            previous = click
            click = null
        }
        releaseOffUi(previous)
        var opened: AudioClip? = null
        try {
            val clip = OwnedClip(openClip(button))
            opened = clip
            val accepted = synchronized(lock) {
                if (closed || !shouldPlay() || completionRequested || generation != clickGeneration) {
                    false
                } else {
                    click = clip
                    clip.start {
                        synchronized(lock) { if (generation == clickGeneration) click = null }
                        releaseAsync(clip)
                    }
                    true
                }
            }
            if (!accepted) release(clip)
        } catch (error: Exception) {
            synchronized(lock) { if (generation == clickGeneration) click = null }
            release(opened)
            // Button feedback must not clear or overwrite a completion-alarm failure.
            System.err.println("Button sound unavailable: ${error.message}")
        }
    }

    fun stopCompletion() {
        val generation: Long
        val previous = synchronized(lock) {
            generation = ++completionGeneration
            completionRequested = false
            completion
        }
        // Retain the pointer until cleanup finishes so a new request also waits for its close.
        releaseOffUi(previous) {
            synchronized(lock) { if (generation == completionGeneration) completion = null }
        }
    }

    fun stopClick() {
        val generation: Long
        val previous = synchronized(lock) {
            generation = ++clickGeneration
            click
        }
        releaseOffUi(previous) {
            synchronized(lock) { if (generation == clickGeneration) click = null }
        }
    }

    private fun finishCompletion(generation: Long, clip: AudioClip) {
        synchronized(lock) {
            if (generation == completionGeneration) {
                completion = null
                completionRequested = false
            }
        }
        releaseAsync(clip)
    }

    override fun close() {
        val previous = synchronized(lock) {
            closed = true
            ++completionGeneration
            ++clickGeneration
            completionRequested = false
            listOf(completion, click).also { completion = null; click = null }
        }
        previous.forEach(::releaseOffUi)
    }

    private fun report(message: String?) {
        try { onPlaybackResult(message) }
        catch (error: Exception) { System.err.println("Audio feedback unavailable: ${error.message}") }
    }

    private fun release(clip: AudioClip?) {
        try { clip?.close() }
        catch (error: Exception) { System.err.println("Audio cleanup unavailable: ${error.message}") }
    }

    private fun releaseOffUi(clip: AudioClip?, afterRelease: () -> Unit = {}) {
        val cleanup = { release(clip); afterRelease() }
        if (SwingUtilities.isEventDispatchThread()) ForkJoinPool.commonPool().execute(cleanup)
        else cleanup()
    }

    private fun releaseAsync(clip: AudioClip) = ForkJoinPool.commonPool().execute { release(clip) }
}
