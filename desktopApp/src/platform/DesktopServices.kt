package com.pomodoro.platform

import com.pomodoro.domain.TimeMark
import java.awt.Taskbar
import java.awt.Window
import java.io.BufferedInputStream
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.LineEvent

fun currentTime(): TimeMark = TimeMark(System.nanoTime() / 1_000_000, System.currentTimeMillis())

fun applicationDirectory(): Path {
    val appData = System.getenv("APPDATA")
    val base = if (appData.isNullOrBlank()) Path.of(System.getProperty("user.home"), ".local", "share") else Path.of(appData)
    return base.resolve("AggressivePomodoro")
}

class InstanceLock private constructor(private val channel: FileChannel, private val lock: FileLock) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }

    companion object {
        fun acquire(directory: Path): InstanceLock? {
            Files.createDirectories(directory)
            val channel = FileChannel.open(directory.resolve("instance.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
            if (lock == null) {
                channel.close()
                return null
            }
            return InstanceLock(channel, lock)
        }
    }
}

class DesktopAlert(private val window: Window) {
    fun requestAttention() {
        if (window.isFocused) return
        try {
            val taskbar = Taskbar.getTaskbar()
            if (taskbar.isSupported(Taskbar.Feature.USER_ATTENTION_WINDOW)) {
                taskbar.requestWindowUserAttention(window)
            }
        } catch (error: Exception) {
            System.err.println("Desktop attention unavailable: ${error.message}")
        }
    }

    fun playCompletion() = play("alert.wav")
    fun playClick() = play("click.wav")

    private fun play(resource: String) {
        try {
            val stream = javaClass.classLoader.getResourceAsStream(resource) ?: error("$resource missing")
            BufferedInputStream(stream).use { input ->
                AudioSystem.getAudioInputStream(input).use { audio ->
                    val clip = AudioSystem.getClip()
                    try {
                        clip.open(audio)
                        clip.addLineListener { event -> if (event.type == LineEvent.Type.STOP) clip.close() }
                        clip.start()
                    } catch (error: Exception) {
                        clip.close()
                        throw error
                    }
                }
            }
        } catch (error: Exception) {
            System.err.println("Sound unavailable ($resource): ${error.message}")
        }
    }
}
