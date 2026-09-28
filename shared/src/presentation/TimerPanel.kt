package com.pomodoro.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodoro.domain.FocusTask
import com.pomodoro.domain.Phase
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionState
import com.pomodoro.domain.SessionStatus

@Composable
internal fun TimerPanel(
    state: SessionState,
    remainingMs: Long,
    selectedTask: FocusTask?,
    modifier: Modifier = Modifier,
    onCommand: (SessionCommand) -> Unit,
    onUiClick: () -> Unit,
) {
    var confirmation by remember { mutableStateOf<SessionCommand?>(null) }
    val accent = if (state.phase == Phase.FOCUS) UiColor.focus else UiColor.breakTime
    val phase = when (state.phase) {
        Phase.FOCUS -> "FOCUS"
        Phase.SHORT_BREAK -> "SHORT BREAK"
        Phase.LONG_BREAK -> "LONG BREAK"
    }
    val status = when (state.status) {
        SessionStatus.IDLE -> "READY"
        SessionStatus.RUNNING -> "IN PROGRESS"
        SessionStatus.PAUSED -> "PAUSED"
        SessionStatus.WAITING -> "WAITING FOR YOU"
    }
    val seconds = (remainingMs + 999) / 1000
    val clock = "%02d:%02d".format(seconds / 60, seconds % 60)

    Card(modifier, colors = CardDefaults.cardColors(containerColor = UiColor.panel)) {
        Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(phase, color = accent, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text(status, color = UiColor.muted, fontWeight = FontWeight.Bold)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                Text(clock, modifier = Modifier.fillMaxWidth(), color = UiColor.text,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    fontSize = if (maxWidth < 470.dp) 76.sp else 106.sp,
                    lineHeight = if (maxWidth < 470.dp) 86.sp else 116.sp,
                    textAlign = TextAlign.Center, maxLines = 1)
            }
            LinearProgressIndicator(
                progress = { ((state.durationMs - remainingMs).toFloat() / state.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(12.dp), color = accent,
                trackColor = UiColor.panelRaised,
            )
            Text(if (state.phase == Phase.FOCUS) "One task. Finish the block." else "Take the break. Return ready.",
                fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(selectedTask?.let { "CURRENT TASK  ·  ${it.title}" } ?: "Choose a task to focus on, or start without one.",
                color = UiColor.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                when (state.status) {
                    SessionStatus.IDLE -> Button(onClick = { onCommand(SessionCommand.Start) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("START") }
                    SessionStatus.RUNNING -> Button(onClick = { onCommand(SessionCommand.Pause) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("PAUSE") }
                    SessionStatus.PAUSED -> Button(onClick = { onCommand(SessionCommand.Resume) },
                        colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("RESUME") }
                    SessionStatus.WAITING -> Unit
                }
                if (state.status != SessionStatus.WAITING) {
                    OutlinedButton(onClick = {
                        if (state.status == SessionStatus.RUNNING) { onUiClick(); confirmation = SessionCommand.Reset }
                        else onCommand(SessionCommand.Reset)
                    }) { Text("RESET") }
                    OutlinedButton(onClick = {
                        if (state.status == SessionStatus.RUNNING) { onUiClick(); confirmation = SessionCommand.Skip }
                        else onCommand(SessionCommand.Skip)
                    }) { Text("SKIP") }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("${state.completedFocus} completed focus blocks  ·  ${state.focusInCycle}/${state.settings.longBreakEvery} toward long break",
                color = UiColor.muted)
            state.message?.let { Text(it, color = accent, fontWeight = FontWeight.SemiBold) }
        }
    }

    confirmation?.let { command ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (command == SessionCommand.Skip) "Skip this phase?" else "Reset this phase?") },
            text = { Text(if (command == SessionCommand.Skip)
                "A skipped focus block does not count toward your completed work."
                else "The current phase returns to its full duration.") },
            confirmButton = { Button(onClick = { onCommand(command); confirmation = null }) { Text("CONFIRM") } },
            dismissButton = { TextButton(onClick = { onUiClick(); confirmation = null }) { Text("CANCEL") } },
        )
    }
}
