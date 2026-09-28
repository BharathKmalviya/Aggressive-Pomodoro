package com.pomodoro.domain

/** Public metadata for one validated stable release; no local file is executable from this model. */
data class AppRelease(
    val version: String,
    val releaseUrl: String,
    val installerName: String,
    val installerUrl: String,
    val installerSize: Long,
    val checksumUrl: String,
)
