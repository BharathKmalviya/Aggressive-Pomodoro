package com.pomodoro.platform

import com.pomodoro.domain.TimeMark
import java.nio.file.Path

fun currentTime(): TimeMark = TimeMark(System.nanoTime() / 1_000_000, System.currentTimeMillis())

fun applicationDirectory(): Path {
    val appData = System.getenv("APPDATA")
    val base = if (appData.isNullOrBlank()) Path.of(System.getProperty("user.home"), ".local", "share") else Path.of(appData)
    return base.resolve("AggressivePomodoro")
}
