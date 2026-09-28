package com.pomodoro.platform

import com.pomodoro.domain.TimeMark
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

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
