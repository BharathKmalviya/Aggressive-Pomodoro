package com.pomodoro.platform

import java.util.Properties

object AppVersion {
    val value: String by lazy {
        val stream = javaClass.classLoader.getResourceAsStream("version.properties")
            ?: error("version.properties missing from application")
        Properties().apply { stream.use { load(it) } }.getProperty("version")
            ?: error("Application version missing")
    }
}
