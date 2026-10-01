package com.pomodoro.presentation

enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, READY, INSTALLING, ERROR }

data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val latestVersion: String? = null,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val message: String? = null,
    val releaseNotes: String? = null,
)
