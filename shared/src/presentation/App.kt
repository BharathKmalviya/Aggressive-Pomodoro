package com.pomodoro.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodoro.domain.FocusHistory
import com.pomodoro.domain.Phase
import com.pomodoro.domain.AlarmSound
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.TaskCommand

@Composable
fun App(
    product: ProductState,
    remainingMs: Long,
    todayDate: String,
    version: String,
    persistenceWarning: String?,
    audioWarning: String?,
    onPreviewAlarm: (Phase, AlarmSound) -> Unit,
    onStopPreview: () -> Unit,
    updateState: UpdateUiState,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onOpenRepository: () -> Unit,
    browserWarning: String?,
    closeRequested: Boolean = false,
    dialogsVisible: Boolean = true,
    onSessionCommand: (SessionCommand) -> Unit,
    onTaskCommand: (TaskCommand) -> Unit,
    onUiClick: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var reportsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    var updatesOpen by remember { mutableStateOf(false) }
    val session = product.session
    val today = product.history.on(todayDate)
    val pending = session.pending.isNotEmpty()
    val activeTask = product.board.tasks.firstOrNull { it.id == product.activeTaskId }
    LaunchedEffect(pending, closeRequested) {
        if (pending || closeRequested) {
            settingsOpen = false
            reportsOpen = false
            aboutOpen = false
            updatesOpen = false
        }
    }

    ProductTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(UiColor.background)) {
            val wide = maxWidth >= 850.dp
            Column(Modifier.fillMaxSize().padding(if (wide) 24.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("AGGRESSIVE", color = UiColor.focus, fontSize = 24.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp)
                        Text("POMODORO / OWN YOUR ATTENTION", color = UiColor.text, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    if (wide) AppActions(
                        onReports = { onUiClick(); reportsOpen = true },
                        onSettings = { onUiClick(); settingsOpen = true },
                        onAbout = { onUiClick(); aboutOpen = true },
                    )
                }
                if (!wide) AppActions(
                    onReports = { onUiClick(); reportsOpen = true },
                    onSettings = { onUiClick(); settingsOpen = true },
                    onAbout = { onUiClick(); aboutOpen = true },
                )
                Row(Modifier.fillMaxWidth().border(1.dp, UiColor.border).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TODAY / ${today.sessions} BLOCKS / ${today.focusedMs / 60_000} MIN",
                        color = UiColor.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold)
                    if (wide) Text("${if (session.settings.soundEnabled) "ALARM ARMED" else "ALARM MUTED"} · LOCAL ONLY",
                        color = if (session.settings.soundEnabled) UiColor.focus else UiColor.muted, fontSize = 11.sp)
                }
                persistenceWarning?.let { Text(it, color = UiColor.focus, fontWeight = FontWeight.Bold) }
                audioWarning?.let { Text(it, color = UiColor.focus, fontWeight = FontWeight.Bold) }
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        TimerPanel(session, remainingMs, activeTask, product.board.selected,
                            modifier = Modifier.weight(1.15f).fillMaxHeight(), scrollable = true,
                            onCommand = onSessionCommand, onUiClick = onUiClick)
                        TaskPanel(product.board, Modifier.weight(0.85f).fillMaxHeight(),
                            activeTaskId = product.activeTaskId, suppressDialogs = pending || closeRequested,
                            onTaskCommand = onTaskCommand, onUiClick = onUiClick)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        TimerPanel(session, remainingMs, activeTask, product.board.selected,
                            modifier = Modifier.fillMaxWidth(), onCommand = onSessionCommand, onUiClick = onUiClick)
                        TaskPanel(product.board, Modifier.fillMaxWidth().height(560.dp),
                            activeTaskId = product.activeTaskId, suppressDialogs = pending || closeRequested,
                            onTaskCommand = onTaskCommand, onUiClick = onUiClick)
                    }
                }
            }
        }

        session.pending.firstOrNull()?.takeIf { dialogsVisible && !closeRequested }?.let { event ->
            val accent = if (session.phase == Phase.FOCUS) UiColor.focus else UiColor.breakTime
            val action = when {
                session.pending.size > 1 -> "REVIEW NEXT ALERT"
                session.status == SessionStatus.WAITING -> "START ${session.phase.displayName()}"
                else -> "ACKNOWLEDGE"
            }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (event.phase == Phase.FOCUS) "BLOCK FINISHED." else "BREAK IS OVER.",
                    color = accent, fontWeight = FontWeight.Black) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("${event.phase.displayName()} COMPLETE · ${session.pending.size} ${if (session.pending.size == 1) "ALERT" else "ALERTS"} TO REVIEW",
                            color = UiColor.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(if (session.phase == Phase.FOCUS) "BACK TO WORK." else "STEP AWAY. RECOVER.",
                            fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Column(Modifier.fillMaxWidth().background(UiColor.background).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${session.phase.displayName()} · ${when (session.status) {
                                SessionStatus.RUNNING -> "RUNNING NOW"
                                SessionStatus.PAUSED -> "PAUSED"
                                else -> "READY NEXT"
                            }}", color = accent, fontWeight = FontWeight.Bold)
                            Text(timerText(remainingMs), fontSize = 42.sp, fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black)
                            Text(when {
                                session.status == SessionStatus.RUNNING -> "The clock is already moving. Acknowledging this alert keeps it running."
                                session.pending.size > 1 -> "Review each completed phase. The timer stays stopped until the last alert is acknowledged."
                                session.status == SessionStatus.PAUSED -> "The clock is stopped. Acknowledge this alert, then resume when ready."
                                else -> "Start this phase when you're ready. The clock is waiting for you."
                            }, color = UiColor.muted)
                        }
                        Text(if (session.settings.aggressiveAlertsEnabled) "This alert demands attention every 10 seconds until acknowledged."
                            else "Reminders are off. This alert stays here until acknowledged.", color = UiColor.muted)
                        if (!session.settings.soundEnabled) Text("ALARM MUTED · visual alerts remain active", color = accent)
                        audioWarning?.let { Text(it, color = UiColor.focus) }
                    }
                },
                confirmButton = { Button(onClick = { onSessionCommand(SessionCommand.AcknowledgeCompletion(event.phaseId)) },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Text(action, fontWeight = FontWeight.Black)
                } },
                dismissButton = {
                    if (session.settings.soundEnabled) TextButton(onClick = {
                        onSessionCommand(SessionCommand.ChangeSettings(session.settings.copy(soundEnabled = false)))
                    }) { Text("MUTE ALARM") }
                },
            )
        }
        if (!pending && !closeRequested && dialogsVisible && settingsOpen) {
            DisposableEffect(Unit) { onDispose { onStopPreview() } }
            SettingsDialog(session.settings, audioWarning, onPreviewAlarm, onStopPreview, onSave = {
                onStopPreview()
                onSessionCommand(SessionCommand.ChangeSettings(it)); settingsOpen = false
            }, onDismiss = { onStopPreview(); onUiClick(); settingsOpen = false })
        }
        if (!pending && !closeRequested && dialogsVisible && reportsOpen) ReportsDialog(product.history, todayDate,
            onClose = { onUiClick(); reportsOpen = false })
        if (!pending && !closeRequested && dialogsVisible && aboutOpen) AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("AGGRESSIVE POMODORO", fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("VERSION $version", fontWeight = FontWeight.Bold, color = UiColor.focus)
                    Text("Own your attention. Commit to one outcome. Finish the block. Recover. Repeat.")
                    Text("Your tasks and history stay on this computer. Updates are checked only when you ask.", color = UiColor.muted)
                    TextButton(onClick = onOpenRepository, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("OPEN GITHUB", fontWeight = FontWeight.Bold)
                            Text("https://github.com/BharathKmalviya/Aggressive-Pomodoro", fontSize = 12.sp)
                        }
                    }
                    browserWarning?.let { Text(it, color = UiColor.focus) }
                    if (updateState.status != UpdateStatus.IDLE) {
                        Text(when (updateState.status) {
                            UpdateStatus.CHECKING -> "Checking for an update…"
                            UpdateStatus.DOWNLOADING -> "An update is downloading in the background."
                            UpdateStatus.AVAILABLE -> "A new version is available."
                            UpdateStatus.READY -> "Your update is ready to install."
                            UpdateStatus.INSTALLING -> "Opening the Windows installer…"
                            UpdateStatus.UP_TO_DATE -> "The last check found no newer version."
                            else -> "The last update attempt needs attention."
                        }, color = UiColor.muted)
                        TextButton(onClick = { onUiClick(); aboutOpen = false; updatesOpen = true }) {
                            Text("VIEW UPDATE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(shape = MaterialTheme.shapes.small,
                    enabled = updateState.status !in setOf(UpdateStatus.CHECKING, UpdateStatus.DOWNLOADING, UpdateStatus.INSTALLING),
                    onClick = { aboutOpen = false; updatesOpen = true; onCheckUpdates() }) { Text("CHECK FOR UPDATES") }
            },
            dismissButton = { TextButton(onClick = { onUiClick(); aboutOpen = false }) { Text("CLOSE") } },
        )
        if (!pending && !closeRequested && dialogsVisible && updatesOpen) UpdateDialog(
            state = updateState, currentVersion = version,
            onCheck = onCheckUpdates, onDownload = onDownloadUpdate, onCancel = onCancelUpdate,
            onInstall = onInstallUpdate,
            onDismiss = { onUiClick(); updatesOpen = false },
        )
    }
}

@Composable
private fun AppActions(onReports: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onReports) { Text("REPORTS", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        TextButton(onClick = onSettings) { Text("SETTINGS", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        TextButton(onClick = onAbout) { Text("ABOUT", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ReportsDialog(history: FocusHistory, todayDate: String, onClose: () -> Unit) {
    val days = history.sevenDaysThrough(todayDate)
    val maxMinutes = (days.maxOfOrNull { it.focusedMs / 60_000 } ?: 1).coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("PROOF OF WORK", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (days.all { it.sessions == 0 }) Text("Finish your first block. Build a record you can see.")
                days.forEach { day ->
                    val minutes = day.focusedMs / 60_000
                    Text("${day.date} · ${day.sessions} blocks · $minutes min")
                    LinearProgressIndicator(progress = { minutes.toFloat() / maxMinutes },
                        modifier = Modifier.fillMaxWidth(), color = UiColor.focus)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("CLOSE") } },
    )
}
