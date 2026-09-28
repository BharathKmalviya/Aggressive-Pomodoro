package com.pomodoro.presentation

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodoro.domain.FocusHistory
import com.pomodoro.domain.Phase
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
    onSessionCommand: (SessionCommand) -> Unit,
    onTaskCommand: (TaskCommand) -> Unit,
    onUiClick: () -> Unit,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    var reportsOpen by remember { mutableStateOf(false) }
    var aboutOpen by remember { mutableStateOf(false) }
    val session = product.session
    val today = product.history.on(todayDate)

    ProductTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(UiColor.background)) {
            val wide = maxWidth >= 850.dp
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("AGGRESSIVE", color = UiColor.focus, fontSize = 20.sp, fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp)
                        Text("POMODORO", color = UiColor.text, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (wide) Text("TODAY  ${today.sessions} blocks  ·  ${today.focusedMs / 60_000} min",
                            color = UiColor.muted, modifier = Modifier.padding(end = 12.dp))
                        TextButton(onClick = { onUiClick(); reportsOpen = true }) { Text("REPORTS") }
                        TextButton(onClick = { onUiClick(); settingsOpen = true }) { Text("SETTINGS") }
                        TextButton(onClick = { onUiClick(); aboutOpen = true }) { Text("ABOUT") }
                    }
                }
                persistenceWarning?.let { Text(it, color = UiColor.focus, fontWeight = FontWeight.Bold) }
                if (wide) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        TimerPanel(session, remainingMs,
                            product.board.tasks.firstOrNull { it.id == product.activeTaskId } ?: product.board.selected,
                            Modifier.weight(1.15f).fillMaxHeight(), onSessionCommand, onUiClick)
                        TaskPanel(product.board, Modifier.weight(0.85f).fillMaxHeight(), onTaskCommand, onUiClick)
                    }
                } else {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("TODAY  ${today.sessions} blocks  ·  ${today.focusedMs / 60_000} min", color = UiColor.muted)
                        TimerPanel(session, remainingMs,
                            product.board.tasks.firstOrNull { it.id == product.activeTaskId } ?: product.board.selected,
                            Modifier.fillMaxWidth(), onSessionCommand, onUiClick)
                        TaskPanel(product.board, Modifier.fillMaxWidth().height(560.dp), onTaskCommand, onUiClick)
                    }
                }
            }
        }

        session.pending.firstOrNull()?.let { event ->
            val finished = when (event.phase) {
                Phase.FOCUS -> "Focus complete"
                Phase.SHORT_BREAK -> "Short break complete"
                Phase.LONG_BREAK -> "Long break complete"
            }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(finished) },
                text = { Text(if (session.status == SessionStatus.RUNNING)
                    "The next phase is already running. ${session.pending.size} alert(s) to review."
                    else "Review this completion to start the next phase. ${session.pending.size} alert(s) to review.") },
                confirmButton = { Button(onClick = { onSessionCommand(SessionCommand.Acknowledge) }) {
                    Text(if (session.status == SessionStatus.WAITING && session.pending.size == 1)
                        "START NEXT PHASE" else "ACKNOWLEDGE")
                } },
            )
        }
        if (settingsOpen) SettingsDialog(session.settings, onSave = {
            onSessionCommand(SessionCommand.ChangeSettings(it)); settingsOpen = false
        }, onDismiss = { onUiClick(); settingsOpen = false })
        if (reportsOpen) ReportsDialog(product.history, todayDate,
            onClose = { onUiClick(); reportsOpen = false })
        if (aboutOpen) AlertDialog(
            onDismissRequest = { aboutOpen = false },
            title = { Text("Aggressive Pomodoro") },
            text = { Text("Version $version\nFocus on one outcome. Finish the block. Your data stays on this computer.") },
            confirmButton = { TextButton(onClick = { onUiClick(); aboutOpen = false }) { Text("CLOSE") } },
        )
    }
}

@Composable
private fun ReportsDialog(history: FocusHistory, todayDate: String, onClose: () -> Unit) {
    val days = history.sevenDaysThrough(todayDate)
    val maxMinutes = (days.maxOfOrNull { it.focusedMs / 60_000 } ?: 1).coerceAtLeast(1)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("FOCUS REPORT") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (days.all { it.sessions == 0 }) Text("Your first completed focus block will appear here.")
                days.forEach { day ->
                    val minutes = day.focusedMs / 60_000
                    Text("${day.date}  ·  ${day.sessions} blocks  ·  $minutes min")
                    LinearProgressIndicator(progress = { minutes.toFloat() / maxMinutes },
                        modifier = Modifier.fillMaxWidth(), color = UiColor.focus)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("CLOSE") } },
    )
}
