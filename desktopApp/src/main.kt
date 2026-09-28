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
import com.pomodoro.data.AppStore
import com.pomodoro.domain.SessionStatus
import com.pomodoro.platform.DesktopAlert
import com.pomodoro.platform.AppVersion
import com.pomodoro.platform.InstanceLock
import com.pomodoro.platform.applicationDirectory
import com.pomodoro.platform.currentTime
import com.pomodoro.presentation.App
import com.pomodoro.presentation.DesktopSessionController
import java.awt.Dimension
import javax.imageio.ImageIO
import javax.swing.JOptionPane
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
        fun exit() {
            scope.launch {
                try {
                    controller?.close()
                    ownership.close()
                    exitApplication()
                } catch (_: Exception) {
                    exitError = true
                }
            }
        }
        Window(
            onCloseRequest = {
                if (controller?.state?.status == SessionStatus.RUNNING) confirmExit = true else exit()
            },
            title = "Aggressive Pomodoro",
        ) {
            val alert = remember(window) { DesktopAlert(window) }
            val session = remember(window) {
                DesktopSessionController(store, saved, ::currentTime,
                    localDate = { LocalDate.now().toString() },
                    onCompletion = { _, soundEnabled ->
                        alert.requestAttention()
                        if (soundEnabled) scope.launch(Dispatchers.IO) { alert.playCompletion() }
                    }, scope = scope)
            }
            controller = session
            LaunchedEffect(window) {
                window.minimumSize = Dimension(560, 620)
                javaClass.classLoader.getResourceAsStream("app.png")?.use { window.iconImage = ImageIO.read(it) }
            }
            LaunchedEffect(session) {
                while (true) {
                    session.tick()
                    delay(250.milliseconds)
                }
            }
            App(session.product, session.state.remainingAt(session.now), LocalDate.now().toString(), AppVersion.value,
                session.persistenceWarning,
                onSessionCommand = { command ->
                    if (session.state.settings.clickSoundEnabled) scope.launch(Dispatchers.IO) { alert.playClick() }
                    session.dispatchSession(command)
                },
                onTaskCommand = { command ->
                    if (session.state.settings.clickSoundEnabled) scope.launch(Dispatchers.IO) { alert.playClick() }
                    session.dispatchTask(command)
                },
                onUiClick = {
                    if (session.state.settings.clickSoundEnabled) scope.launch(Dispatchers.IO) { alert.playClick() }
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
                    ownership.close(); exitApplication()
                }) { Text("EXIT ANYWAY") } },
            )
        }
    }
}
