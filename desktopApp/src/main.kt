package com.pomodoro

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.Phase
import com.pomodoro.domain.AlarmSound
import com.pomodoro.platform.DesktopAlert
import com.pomodoro.platform.DesktopTray
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
import com.pomodoro.presentation.AppDestination
import com.pomodoro.presentation.AppRequest
import com.pomodoro.presentation.desktopTrayState
import com.pomodoro.presentation.CloseDialog
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
        JOptionPane.showMessageDialog(null, "Aggressive Pomodoro is already running. Open it from the tray icon or taskbar.", "Already running", JOptionPane.INFORMATION_MESSAGE)
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
        var windowVisible by remember { mutableStateOf(true) }
        var tray by remember { mutableStateOf<DesktopTray?>(null) }
        var trayAvailable by remember { mutableStateOf(false) }
        val windowState = rememberWindowState(width = 1120.dp, height = 800.dp)
        var audioWarning by remember { mutableStateOf<String?>(null) }
        var activeAlert by remember { mutableStateOf<DesktopAlert?>(null) }
        var completionPlayback by remember { mutableStateOf<Job?>(null) }
        var clickPlayback by remember { mutableStateOf<Job?>(null) }
        var previewing by remember { mutableStateOf(false) }
        var browserWarning by remember { mutableStateOf<String?>(null) }
        var appRequest by remember { mutableStateOf<AppRequest?>(null) }
        var requestSequence by remember { mutableStateOf(0L) }
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
                    tray?.close()
                    ownership.close()
                    exitApplication()
                } catch (_: Exception) {
                    closing = false
                    windowVisible = true
                    windowState.isMinimized = false
                    exitError = true
                }
            }
        }
        Window(
            onCloseRequest = {
                if (!closing && !exitError) {
                    confirmExit = true
                }
            },
            title = "Aggressive Pomodoro",
            state = windowState,
            visible = windowVisible,
        ) {
            fun showWindow() {
                if (closing) return
                windowVisible = true
                windowState.isMinimized = false
                SwingUtilities.invokeLater { window.toFront(); window.requestFocus() }
            }
            fun background() {
                if (closing) return
                confirmExit = false
                if (trayAvailable) windowVisible = false else windowState.isMinimized = true
            }
            val alert = remember(window) { DesktopAlert(window, onPlaybackResult = { warning ->
                SwingUtilities.invokeLater { audioWarning = warning }
            }) }
            activeAlert = alert
            fun playAlarm(phase: Phase, selection: AlarmSound = controller?.state?.settings?.alarmFor(phase)
                ?: saved.session.settings.alarmFor(phase), preview: Boolean = false) {
                previewing = preview
                completionPlayback?.cancel()
                completionPlayback = scope.launch(Dispatchers.IO) {
                    ensureActive()
                    alert.playCompletion(phase, selection) { isActive }
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
                        if (!windowVisible) tray?.notifyCompletion(event.phase)
                        alert.requestAttention()
                        if (soundEnabled) playAlarm(event.phase)
                    }, scope = scope)
            }
            controller = session
            fun dispatchSession(command: SessionCommand) {
                if (closing) return
                if (command == SessionCommand.Acknowledge || command is SessionCommand.AcknowledgeCompletion ||
                    command is SessionCommand.ChangeSettings &&
                    (!command.settings.soundEnabled || !command.settings.aggressiveAlertsEnabled ||
                        command.settings.focusAlarm != session.state.settings.focusAlarm ||
                        command.settings.breakAlarm != session.state.settings.breakAlarm)) stopAlarm()
                session.dispatchSession(command)
                if (!session.state.settings.clickSoundEnabled) stopClick() else playClick()
            }
            fun openFromTray(destination: AppDestination, phaseId: Long?) {
                if (closing || confirmExit || exitError) return
                session.tick()
                showWindow()
                if (session.state.pending.isNotEmpty() || phaseId != null && phaseId != session.state.phaseId) return
                appRequest = AppRequest(++requestSequence, destination, phaseId)
                playClick()
            }
            val remainingMs = session.state.remainingAt(session.now)
            val todayDate = LocalDate.now().toString()
            val trayState = desktopTrayState(session.product, remainingMs, todayDate, updates.state.status,
                blocked = closing || confirmExit || exitError)
            LaunchedEffect(session.state.pending.map { it.phaseId }) {
                if (session.state.pending.isNotEmpty()) confirmExit = false
            }
            LaunchedEffect(window) {
                window.minimumSize = Dimension(560, 620)
                val image = withContext(Dispatchers.IO) {
                    javaClass.classLoader.getResourceAsStream("app.png")?.use { ImageIO.read(it) }
                }
                if (image != null) {
                    window.iconImage = image
                    tray = DesktopTray.create(image, initialState = trayState, onShow = ::showWindow, onExit = {
                        if (!closing && !exitError) { showWindow(); confirmExit = true }
                    }, onTimerCommand = { command ->
                        if (!closing && !confirmExit && !exitError) dispatchSession(command)
                    }, onOpen = ::openFromTray, onSoundChanged = { enabled ->
                        if (!closing && !confirmExit && !exitError) {
                            dispatchSession(SessionCommand.ChangeSettings(session.state.settings.copy(soundEnabled = enabled)))
                        }
                    }, onRemindersChanged = { enabled ->
                        if (!closing && !confirmExit && !exitError) {
                            dispatchSession(SessionCommand.ChangeSettings(session.state.settings.copy(aggressiveAlertsEnabled = enabled)))
                        }
                    }, onUnavailable = {
                        trayAvailable = false
                        if (!windowVisible) showWindow()
                    })
                    trayAvailable = tray != null
                }
            }
            LaunchedEffect(tray, trayState) { tray?.update(trayState) }
            DisposableEffect(window) { onDispose { tray?.close() } }
            LaunchedEffect(session) {
                while (true) {
                    if (!closing) session.tick()
                    delay(250.milliseconds)
                }
            }
            App(session.product, remainingMs, todayDate, AppVersion.value,
                if (closing) "Saving session before closing…" else session.persistenceWarning,
                audioWarning = audioWarning,
                updateState = updates.state,
                browserWarning = browserWarning,
                closeRequested = confirmExit || exitError,
                dialogsVisible = windowVisible && !windowState.isMinimized,
                appRequest = appRequest,
                onAppRequestHandled = { request -> if (appRequest == request) appRequest = null },
                onCheckUpdates = { if (!closing) updates.check() },
                onDownloadUpdate = { if (!closing) updates.download() },
                onCancelUpdate = { if (!closing) updates.cancel() },
                onOpenRepository = { if (!closing) openLink(REPOSITORY_URL) },
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
                                    tray?.close()
                                    runCatching { ownership.close() }
                                    exitApplication()
                                }
                            } finally {
                                closing = false
                            }
                        }
                    }
                },
                onPreviewAlarm = { phase, selection -> if (!closing) playAlarm(phase, selection, preview = true) },
                onStopPreview = ::stopPreview,
                onSessionCommand = ::dispatchSession,
                onTaskCommand = { command ->
                    if (!closing) {
                        session.dispatchTask(command)
                        playClick()
                    }
                },
                onUiClick = {
                    playClick()
                })
            if (confirmExit) CloseDialog(
                trayAvailable = trayAvailable,
                onBackground = ::background,
                onExit = { confirmExit = false; exit() },
                onCancel = { confirmExit = false },
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
                        tray?.close()
                        ownership.close()
                        exitApplication()
                    }
                }) { Text("EXIT ANYWAY") } },
            )
        }
    }
}
