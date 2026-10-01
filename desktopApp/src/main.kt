package com.pomodoro

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.pomodoro.data.AppStore
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.Phase
import com.pomodoro.platform.DesktopAlert
import com.pomodoro.platform.AppVersion
import com.pomodoro.platform.InstanceLock
import com.pomodoro.platform.applicationDirectory
import com.pomodoro.platform.currentTime
import com.pomodoro.platform.GitHubUpdateService
import com.pomodoro.platform.REPOSITORY_URL
import com.pomodoro.platform.backupBeforeUpdate
import com.pomodoro.platform.launchWindowsInstaller
import com.pomodoro.platform.openProjectPage
import com.pomodoro.presentation.App
import com.pomodoro.presentation.DesktopSessionController
import com.pomodoro.presentation.DesktopUpdateController
import com.pomodoro.presentation.UpdateStatus
import java.awt.Dimension
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

fun main() {
    val directory = applicationDirectory()
    val ownership = InstanceLock.acquire(directory)
    if (ownership == null) {
        JOptionPane.showMessageDialog(null, "Aggressive Pomodoro is already open.", "Already running", JOptionPane.INFORMATION_MESSAGE)
        return
    }
    val store = AppStore(directory.resolve("session.properties"))
    val saved = store.load()
    application {
        val scope = rememberCoroutineScope()
        var controller by remember { mutableStateOf<DesktopSessionController?>(null) }
        var confirmExit by remember { mutableStateOf(false) }
        var exitError by remember { mutableStateOf(false) }
        var closing by remember { mutableStateOf(false) }
        var audioWarning by remember { mutableStateOf<String?>(null) }
        var activeAlert by remember { mutableStateOf<DesktopAlert?>(null) }
        var completionPlayback by remember { mutableStateOf<Job?>(null) }
        var clickPlayback by remember { mutableStateOf<Job?>(null) }
        var previewing by remember { mutableStateOf(false) }
        var browserWarning by remember { mutableStateOf<String?>(null) }
        val updates = remember { DesktopUpdateController(AppVersion.value, directory.resolve("updates"),
            GitHubUpdateService(), scope) }
        fun openLink(url: String) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { openProjectPage(url) }
                    browserWarning = null
                } catch (_: Exception) {
                    browserWarning = "Could not open your browser. Visit $REPOSITORY_URL manually."
                }
            }
        }
        fun exit() {
            if (closing) return
            closing = true
            scope.launch {
                try {
                    controller?.close()
                    completionPlayback?.cancel()
                    clickPlayback?.cancel()
                    withContext(Dispatchers.IO) { activeAlert?.close() }
                    ownership.close()
                    exitApplication()
                } catch (_: Exception) {
                    closing = false
                    exitError = true
                }
            }
        }
        Window(
            onCloseRequest = {
                if (!closing) {
                    if (controller?.state?.status == SessionStatus.RUNNING) confirmExit = true else exit()
                }
            },
            title = "Aggressive Pomodoro",
            state = rememberWindowState(width = 1120.dp, height = 800.dp),
        ) {
            val alert = remember(window) { DesktopAlert(window, onPlaybackResult = { warning ->
                SwingUtilities.invokeLater { audioWarning = warning }
            }) }
            activeAlert = alert
            fun playAlarm(phase: Phase, preview: Boolean = false) {
                previewing = preview
                completionPlayback?.cancel()
                completionPlayback = scope.launch(Dispatchers.IO) {
                    ensureActive()
                    alert.playCompletion(phase) { isActive }
                }
            }
            fun stopAlarm() {
                completionPlayback?.cancel()
                previewing = false
                // Invalidate playback immediately; the adapter closes audio resources off the UI thread.
                alert.stopCompletion()
            }
            fun stopPreview() { if (previewing) stopAlarm() }
            fun playClick() {
                if (closing || controller?.state?.settings?.clickSoundEnabled != true) return
                clickPlayback?.cancel()
                clickPlayback = scope.launch(Dispatchers.IO) {
                    ensureActive()
                    alert.playClick { isActive }
                }
            }
            fun stopClick() {
                clickPlayback?.cancel()
                alert.stopClick()
            }
            val session = remember(window) {
                DesktopSessionController(store, saved, ::currentTime,
                    localDate = { LocalDate.now().toString() },
                    onCompletion = { event, soundEnabled ->
                        stopPreview()
                        alert.requestAttention()
                        if (soundEnabled) playAlarm(event.phase)
                    }, scope = scope)
            }
            controller = session
            LaunchedEffect(session.state.pending.isNotEmpty()) {
                if (session.state.pending.isNotEmpty()) confirmExit = false
            }
            LaunchedEffect(window) {
                window.minimumSize = Dimension(560, 620)
                javaClass.classLoader.getResourceAsStream("app.png")?.use { window.iconImage = ImageIO.read(it) }
            }
            LaunchedEffect(session) {
                while (true) {
                    if (!closing) session.tick()
                    delay(250.milliseconds)
                }
            }
            App(session.product, session.state.remainingAt(session.now), LocalDate.now().toString(), AppVersion.value,
                if (closing) "Saving session before closing…" else session.persistenceWarning,
                audioWarning = audioWarning,
                updateState = updates.state,
                browserWarning = browserWarning,
                onCheckUpdates = { if (!closing) updates.check() },
                onDownloadUpdate = { if (!closing) updates.download() },
                onCancelUpdate = { if (!closing) updates.cancel() },
                onOpenRepository = { if (!closing) openLink(REPOSITORY_URL) },
                onOpenRelease = { if (!closing) openLink(updates.releaseUrl()) },
                onInstallUpdate = {
                    if (!closing) session.tick()
                    if (!closing && session.state.pending.isEmpty() && updates.state.status == UpdateStatus.READY) {
                        closing = true
                        scope.launch {
                            try {
                                updates.install { installer, release ->
                                    session.close(beforeClose = {
                                        withContext(Dispatchers.IO) {
                                            backupBeforeUpdate(directory, AppVersion.value, release.version)
                                            launchWindowsInstaller(installer)
                                        }
                                    })
                                    completionPlayback?.cancel()
                                    clickPlayback?.cancel()
                                    withContext(Dispatchers.IO) { alert.close() }
                                    runCatching { ownership.close() }
                                    exitApplication()
                                }
                            } finally {
                                closing = false
                            }
                        }
                    }
                },
                onPreviewAlarm = { phase -> if (!closing) playAlarm(phase, preview = true) },
                onStopPreview = ::stopPreview,
                onSessionCommand = { command ->
                    if (!closing) {
                        if (command == SessionCommand.Acknowledge || command is SessionCommand.AcknowledgeCompletion ||
                            command is SessionCommand.ChangeSettings &&
                            (!command.settings.soundEnabled || !command.settings.aggressiveAlertsEnabled)) stopAlarm()
                        session.dispatchSession(command)
                        if (!session.state.settings.clickSoundEnabled) stopClick() else playClick()
                    }
                },
                onTaskCommand = { command ->
                    if (!closing) {
                        session.dispatchTask(command)
                        playClick()
                    }
                },
                onUiClick = {
                    playClick()
                })
            if (confirmExit) AlertDialog(
                onDismissRequest = { confirmExit = false },
                title = { Text("Exit active timer?") },
                text = { Text("The timer cannot alert you after the app exits. Your session will be saved for the next launch.") },
                confirmButton = { Button(onClick = { confirmExit = false; exit() }) { Text("EXIT") } },
                dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("KEEP RUNNING") } },
            )
            if (exitError) AlertDialog(
                onDismissRequest = { exitError = false },
                title = { Text("Could not save changes") },
                text = { Text("The latest session could not be saved. Check storage access, then retry. Exiting now may lose recent changes.") },
                confirmButton = { Button(onClick = { exitError = false; exit() }) { Text("RETRY SAVE") } },
                dismissButton = { TextButton(onClick = {
                    closing = true
                    scope.launch {
                        completionPlayback?.cancel()
                        clickPlayback?.cancel()
                        withContext(Dispatchers.IO) { alert.close() }
                        ownership.close()
                        exitApplication()
                    }
                }) { Text("EXIT ANYWAY") } },
            )
        }
    }
}
