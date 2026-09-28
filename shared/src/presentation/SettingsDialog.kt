package com.pomodoro.presentation

import com.pomodoro.domain.TimerSettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsDialog(settings: TimerSettings, onSave: (TimerSettings) -> Unit, onDismiss: () -> Unit) {
    var focus by remember(settings) { mutableStateOf(settings.focusMinutes.toString()) }
    var short by remember(settings) { mutableStateOf(settings.shortBreakMinutes.toString()) }
    var long by remember(settings) { mutableStateOf(settings.longBreakMinutes.toString()) }
    var interval by remember(settings) { mutableStateOf(settings.longBreakEvery.toString()) }
    var automatic by remember(settings) { mutableStateOf(settings.automaticTransitions) }
    var sound by remember(settings) { mutableStateOf(settings.soundEnabled) }
    var clicks by remember(settings) { mutableStateOf(settings.clickSoundEnabled) }
    val candidate = TimerSettings(focus.toIntOrNull() ?: 0, short.toIntOrNull() ?: 0,
        long.toIntOrNull() ?: 0, interval.toIntOrNull() ?: 0, automatic, sound, clicks)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SET YOUR PACE") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(focus, { focus = it }, label = { Text("Focus minutes (1–180)") }, singleLine = true)
                OutlinedTextField(short, { short = it }, label = { Text("Short break minutes (1–60)") }, singleLine = true)
                OutlinedTextField(long, { long = it }, label = { Text("Long break minutes (1–60)") }, singleLine = true)
                OutlinedTextField(interval, { interval = it }, label = { Text("Focus blocks per cycle (2–12)") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(automatic, onCheckedChange = { automatic = it }); Text("Start next phase automatically")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(sound, onCheckedChange = { sound = it }); Text("Play completion sound")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(clicks, onCheckedChange = { clicks = it }); Text("Play button click sounds")
                }
                if (!candidate.isValid()) Text("Enter values within the ranges shown above.", color = UiColor.focus)
                Text("New durations apply when the next phase begins.", color = UiColor.muted)
            }
        },
        confirmButton = { Button(enabled = candidate.isValid(), onClick = { onSave(candidate) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
