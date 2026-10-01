package com.pomodoro.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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

private data class PhaseConfirmation(val phaseId: Long, val command: SessionCommand)

internal fun Phase.displayName(): String = when (this) {
    Phase.FOCUS -> "FOCUS"
    Phase.SHORT_BREAK -> "SHORT BREAK"
    Phase.LONG_BREAK -> "LONG BREAK"
}

internal fun timerText(remainingMs: Long): String {
    val seconds = (remainingMs.coerceAtLeast(0) + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
internal fun TimerPanel(
    state: SessionState,
    remainingMs: Long,
    activeTask: FocusTask?,
    nextTask: FocusTask?,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
    onCommand: (SessionCommand) -> Unit,
    onUiClick: () -> Unit,
) {
    var confirmation by remember(state.phaseId, state.pending.isNotEmpty()) {
        mutableStateOf<PhaseConfirmation?>(null)
    }
    val focus = state.phase == Phase.FOCUS
    val motion = !state.settings.reduceMotion
    val accent by animateColorAsState(if (focus) UiColor.focus else UiColor.breakTime,
        animationSpec = if (motion) tween(220) else snap(), label = "phase accent")
    val finalMinute = state.status == SessionStatus.RUNNING && remainingMs in 1..60_000
    val emphasis = remember(state.phaseId) { Animatable(1f) }
    LaunchedEffect(state.phaseId, finalMinute, motion) {
        emphasis.snapTo(1f)
        if (finalMinute && motion) {
            emphasis.animateTo(1.025f, tween(140))
            emphasis.animateTo(1f, tween(200))
        }
    }
    val activeFocus = focus && state.status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED)
    val status = when (state.status) {
        SessionStatus.IDLE -> "READY TO COMMIT"
        SessionStatus.RUNNING -> if (finalMinute) "FINAL MINUTE" else "CLOCK IS RUNNING"
        SessionStatus.PAUSED -> "PAUSED · CLOCK STOPPED"
        SessionStatus.WAITING -> "YOUR ACKNOWLEDGEMENT REQUIRED"
    }
    val directive = when {
        state.status == SessionStatus.PAUSED -> "DON'T LOSE THE THREAD."
        state.status == SessionStatus.WAITING -> "MAKE THE NEXT MOVE."
        state.status == SessionStatus.IDLE && focus -> "COMMIT. THEN EXECUTE."
        state.status == SessionStatus.IDLE -> "TAKE YOUR BREAK."
        finalMinute && focus -> "FINISH STRONG."
        finalMinute -> "GET READY TO RETURN."
        focus -> "ONE TASK. NO DETOURS."
        state.phase == Phase.LONG_BREAK -> "STEP AWAY. RECHARGE."
        else -> "HANDS OFF. BREATHE."
    }
    val instruction = when {
        state.status == SessionStatus.PAUSED -> "Your progress is held. Resume when you can give it your attention."
        state.status == SessionStatus.WAITING -> "Review the completed phase to release the next block."
        finalMinute && focus -> "Close the loop. One minute left to move this task forward."
        finalMinute -> "Wrap up the break. Your next focus block is close."
        focus -> "Close the distractions. Stay with this block until the alarm."
        state.phase == Phase.LONG_BREAK -> "Stand up. Get water. Give your attention time to recover."
        else -> "Look away from the screen. This break is part of the work."
    }

    Card(modifier.border(1.dp, accent.copy(alpha = if (finalMinute) 1f else 0.45f)),
        colors = CardDefaults.cardColors(containerColor = UiColor.panel)) {
        val scroll = rememberScrollState()
        Column(
            (if (scrollable) Modifier.verticalScroll(scroll) else Modifier).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(state.phase.displayName(), color = accent, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("BLOCK ${(state.completedFocus.toLong() + 1).toString().padStart(2, '0')}",
                    color = UiColor.muted, fontFamily = FontFamily.Monospace)
            }
            TimerMotion(status, motion) { entrance ->
                Text(status, modifier = entrance.fillMaxWidth().background(accent.copy(alpha = 0.12f)).padding(12.dp),
                    color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val clockSize = (maxWidth.value / 3.8f).coerceIn(40f, 112f).sp
                Text(timerText(remainingMs), modifier = Modifier.fillMaxWidth().graphicsLayer {
                    scaleX = if (motion) emphasis.value else 1f
                    scaleY = if (motion) emphasis.value else 1f
                },
                    color = if (finalMinute) accent else UiColor.text,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Black,
                    fontSize = clockSize, lineHeight = clockSize,
                    textAlign = TextAlign.Center, maxLines = 1)
            }
            key(state.phaseId) {
                val target = (1f - remainingMs.coerceIn(0, state.durationMs.coerceAtLeast(1)).toFloat() /
                    state.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                val progress by animateFloatAsState(target,
                    animationSpec = if (motion && state.status == SessionStatus.RUNNING) tween(200) else snap(),
                    label = "elapsed progress")
                LinearProgressIndicator(
                    progress = { if (motion && state.status == SessionStatus.RUNNING) progress else target },
                    modifier = Modifier.fillMaxWidth().height(8.dp), color = accent,
                    trackColor = UiColor.panelRaised,
                )
            }
            TimerMotion(directive, motion) { entrance ->
                Column(entrance, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(directive, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black)
                    Text(instruction, color = UiColor.muted, lineHeight = 21.sp)
                }
            }
            when (state.status) {
                SessionStatus.IDLE, SessionStatus.RUNNING, SessionStatus.PAUSED -> {
                    val primary = when (state.status) {
                        SessionStatus.IDLE -> SessionCommand.StartPhase(state.phaseId)
                        SessionStatus.PAUSED -> SessionCommand.ResumePhase(state.phaseId)
                        else -> SessionCommand.PausePhase(state.phaseId)
                    }
                    val label = when (state.status) {
                        SessionStatus.IDLE -> if (focus) "START FOCUS" else "START BREAK"
                        SessionStatus.PAUSED -> if (focus) "GET BACK TO WORK" else "RESUME BREAK"
                        else -> if (focus) "PAUSE FOCUS" else "PAUSE BREAK"
                    }
                    Button(onClick = { onCommand(primary) }, modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Text(label, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(SessionCommand.ResetPhase(state.phaseId) to "RESET BLOCK",
                            SessionCommand.SkipPhase(state.phaseId) to "SKIP PHASE").forEach { (command, label) ->
                            OutlinedButton(onClick = {
                                if (state.status == SessionStatus.RUNNING || state.status == SessionStatus.PAUSED) {
                                    onUiClick()
                                    confirmation = PhaseConfirmation(state.phaseId, command)
                                } else onCommand(command)
                            }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) { Text(label, fontSize = 11.sp) }
                        }
                    }
                }
                SessionStatus.WAITING -> Text("ACKNOWLEDGE THE ALERT TO CONTINUE", color = accent, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.fillMaxWidth().background(UiColor.background).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (activeFocus) {
                    Text("THIS BLOCK", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(activeTask?.title ?: "Unassigned focus", fontWeight = FontWeight.SemiBold)
                    if (activeTask == null) Text("This block counts toward your daily total without task credit.",
                        color = UiColor.muted, fontSize = 12.sp)
                    if (nextTask?.id != activeTask?.id) {
                        Text("NEXT FOCUS · ${nextTask?.title ?: "No task selected"}", color = UiColor.muted, fontSize = 12.sp)
                    }
                } else {
                    Text("NEXT FOCUS", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(nextTask?.title ?: "Select one outcome, or focus without a task.", fontWeight = FontWeight.SemiBold)
                }
            }
            Text("${state.completedFocus} blocks finished · ${state.focusInCycle}/${state.settings.longBreakEvery} toward long break",
                color = UiColor.muted, fontSize = 12.sp)
            Text(if (state.settings.aggressiveAlertsEnabled) "RELENTLESS ALERTS ON · every 10 seconds until acknowledged"
                else "SINGLE ALERT · reminders disabled", color = accent, fontSize = 11.sp)
            state.message?.let { Text(it, color = accent, fontWeight = FontWeight.SemiBold) }
        }
    }

    confirmation?.takeIf { it.phaseId == state.phaseId && state.pending.isEmpty() }?.let { request ->
        val skip = request.command is SessionCommand.SkipPhase
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (skip) "ABANDON THIS PHASE?" else "RESTART THIS BLOCK?") },
            text = { Text(if (skip)
                "Skip ${state.phase.displayName().lowercase()} and move to the next phase. An unfinished focus block earns no task credit or completed focus time."
                else "Discard this block's elapsed time and return to ${timerText(state.durationMs)}. The timer will stop until you start it again.") },
            confirmButton = { Button(shape = MaterialTheme.shapes.small, onClick = {
                if (request.phaseId == state.phaseId && state.pending.isEmpty()) onCommand(request.command)
                confirmation = null
            }) { Text(if (skip) "SKIP PHASE" else "RESET BLOCK") } },
            dismissButton = { TextButton(onClick = { onUiClick(); confirmation = null }) { Text("KEEP THIS BLOCK") } },
        )
    }
}

/** Animate the current text in place; never retain outgoing controls or obsolete timer state. */
@Composable
private fun TimerMotion(value: String, enabled: Boolean, content: @Composable (Modifier) -> Unit) {
    val entrance = remember { Animatable(1f) }
    LaunchedEffect(value, enabled) {
        if (enabled) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(180))
        } else entrance.snapTo(1f)
    }
    content(if (!enabled) Modifier else Modifier.graphicsLayer {
        alpha = 0.65f + entrance.value * 0.35f
        translationY = (1f - entrance.value) * 4.dp.toPx()
    })
}
