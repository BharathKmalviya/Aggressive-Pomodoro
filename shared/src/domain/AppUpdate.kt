package com.pomodoro.domain

/** Latest stable metadata remains readable even when no newer installer is offered. */
data class UpdateCheckResult(
    val latestVersion: String,
    val releaseNotes: String,
    val update: AppRelease? = null,
)

/** Public metadata for one validated stable release; no local file is executable from this model. */
data class AppRelease(
    val version: String,
    val releaseUrl: String,
    val installerName: String,
    val installerUrl: String,
    val installerSize: Long,
    val checksumUrl: String,
)
