package com.pomodoro.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pomodoro.domain.AppRelease
import com.pomodoro.domain.UpdateDownloadStage
import com.pomodoro.platform.UpdateService
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** UI-thread owner of the manual update flow; network and file work stay on IO. */
class DesktopUpdateController(
    private val version: String,
    private val directory: Path,
    private val service: UpdateService,
    private val scope: CoroutineScope,
    private val windows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
) {
    var state by mutableStateOf(UpdateUiState())
        private set
    private var release: AppRelease? = null
    private var installer: Path? = null
    private var cancelled = AtomicBoolean(false)
    private var operation = 0L
    private val busy get() = state.status in setOf(UpdateStatus.CHECKING, UpdateStatus.DOWNLOADING, UpdateStatus.INSTALLING)

    fun check(): Job? {
        if (busy) return null
        if (!windows) {
            state = UpdateUiState(status = UpdateStatus.ERROR,
                message = "In-app installers are available for Windows. Open GitHub for source and release information.")
            return null
        }
        val request = ++operation
        release = null
        installer = null
        state = UpdateUiState(status = UpdateStatus.CHECKING)
        return scope.launch {
            try {
                val latest = withContext(Dispatchers.IO) { service.check(version) }
                if (operation != request) return@launch
                release = latest.update
                state = UpdateUiState(
                    status = if (latest.update == null) UpdateStatus.UP_TO_DATE else UpdateStatus.AVAILABLE,
                    latestVersion = latest.latestVersion,
                    totalBytes = latest.update?.installerSize ?: 0,
                    releaseNotes = latest.releaseNotes,
                )
            } catch (error: Exception) {
                if (operation == request) fail(error, "Could not check for updates. Try again when you are online.")
            }
        }
    }

    fun download(): Job? {
        val latest = release ?: return null
        if (busy) return null
        val request = ++operation
        val token = AtomicBoolean(false).also { cancelled = it }
        installer = null
        state = state.copy(status = UpdateStatus.DOWNLOADING, downloadedBytes = 0,
            totalBytes = latest.installerSize, message = null, downloadStage = UpdateDownloadStage.FETCHING_CHECKSUM)
        return scope.launch {
            try {
                var lastProgressNs = 0L
                val file = withContext(Dispatchers.IO) {
                    service.download(latest, directory, onProgress = { received, total ->
                        val now = System.nanoTime()
                        if (received == total || now - lastProgressNs >= 100_000_000L) {
                            lastProgressNs = now
                            scope.launch {
                                if (operation == request && state.status == UpdateStatus.DOWNLOADING && !token.get()) {
                                    state = state.copy(downloadedBytes = received, totalBytes = total)
                                }
                            }
                        }
                    }, isCancelled = token::get, onStage = { stage ->
                        scope.launch {
                            if (operation == request && state.status == UpdateStatus.DOWNLOADING && !token.get()) {
                                state = state.copy(downloadStage = stage)
                            }
                        }
                    })
                }
                if (operation != request || token.get()) {
                    if (operation == request) cancelledState(latest)
                    return@launch
                }
                installer = file
                state = state.copy(status = UpdateStatus.READY,
                    downloadedBytes = latest.installerSize, totalBytes = latest.installerSize,
                    message = null, downloadStage = null)
            } catch (_: CancellationException) {
                if (operation == request) cancelledState(latest)
            } catch (error: Exception) {
                if (operation == request) {
                    if (token.get()) cancelledState(latest)
                    else fail(error, "Download failed. Please try again.")
                }
            }
        }
    }

    fun cancel() {
        if (state.status == UpdateStatus.DOWNLOADING) {
            cancelled.set(true)
            state = state.copy(message = "Cancelling download…")
        }
    }

    /** The caller saves/backups state and opens the installer; any failure keeps the app usable. */
    suspend fun install(onInstall: suspend (Path, AppRelease) -> Unit) {
        val latest = release ?: return
        val file = installer ?: return
        if (state.status != UpdateStatus.READY) return
        state = state.copy(status = UpdateStatus.INSTALLING, message = null)
        try {
            withContext(Dispatchers.IO) { service.verifyBeforeInstall(file, latest) }
            onInstall(file, latest)
        } catch (error: Exception) {
            fail(error, "Could not start installation. Your app is still open; try downloading again.")
        }
    }

    private fun cancelledState(latest: AppRelease) {
        state = state.copy(status = UpdateStatus.AVAILABLE, downloadedBytes = 0,
            totalBytes = latest.installerSize, message = "Download cancelled. Your current app is unchanged.", downloadStage = null)
    }

    private fun fail(error: Exception, fallback: String) {
        state = state.copy(status = UpdateStatus.ERROR, downloadStage = null,
            message = error.message?.takeIf { it.isNotBlank() } ?: fallback)
    }
}
