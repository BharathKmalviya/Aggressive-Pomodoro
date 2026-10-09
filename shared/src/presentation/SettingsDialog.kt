package com.pomodoro.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.Phase
import com.pomodoro.domain.AlarmSound

@Composable
internal fun SettingsDialog(
    settings: TimerSettings,
    audioWarning: String?,
    onPreviewAlarm: (Phase, AlarmSound) -> Unit,
    onStopPreview: () -> Unit,
    onSave: (TimerSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    // A tray toggle refreshes its own field without discarding other unsaved edits.
    var focus by remember(settings.focusMinutes) { mutableStateOf(settings.focusMinutes.toString()) }
    var short by remember(settings.shortBreakMinutes) { mutableStateOf(settings.shortBreakMinutes.toString()) }
    var long by remember(settings.longBreakMinutes) { mutableStateOf(settings.longBreakMinutes.toString()) }
    var interval by remember(settings.longBreakEvery) { mutableStateOf(settings.longBreakEvery.toString()) }
    var automatic by remember(settings.automaticTransitions) { mutableStateOf(settings.automaticTransitions) }
    var sound by remember(settings.soundEnabled) { mutableStateOf(settings.soundEnabled) }
    var clicks by remember(settings.clickSoundEnabled) { mutableStateOf(settings.clickSoundEnabled) }
    var reminders by remember(settings.aggressiveAlertsEnabled) { mutableStateOf(settings.aggressiveAlertsEnabled) }
    var reduceMotion by remember(settings.reduceMotion) { mutableStateOf(settings.reduceMotion) }
    var focusAlarm by remember(settings.focusAlarm) { mutableStateOf(settings.focusAlarm) }
    var breakAlarm by remember(settings.breakAlarm) { mutableStateOf(settings.breakAlarm) }
    var previewRequested by remember { mutableStateOf(false) }
    val candidate = settings.copy(
        focusMinutes = focus.toIntOrNull() ?: 0,
        shortBreakMinutes = short.toIntOrNull() ?: 0,
        longBreakMinutes = long.toIntOrNull() ?: 0,
        longBreakEvery = interval.toIntOrNull() ?: 0,
        automaticTransitions = automatic,
        soundEnabled = sound,
        clickSoundEnabled = clicks,
        aggressiveAlertsEnabled = reminders,
        reduceMotion = reduceMotion,
        focusAlarm = focusAlarm,
        breakAlarm = breakAlarm,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SET YOUR RULES", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Build a pace you can commit to.", color = UiColor.muted)
                OutlinedTextField(focus, { focus = it }, label = { Text("Focus minutes (1–180)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = candidate.focusMinutes !in 1..180)
                OutlinedTextField(short, { short = it }, label = { Text("Short break minutes (1–60)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = candidate.shortBreakMinutes !in 1..60)
                OutlinedTextField(long, { long = it }, label = { Text("Long break minutes (1–60)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = candidate.longBreakMinutes !in 1..60)
                OutlinedTextField(interval, { interval = it }, label = { Text("Focus blocks per cycle (2–12)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), isError = candidate.longBreakEvery !in 2..12)
                SettingToggle("Start next phase automatically", automatic, { automatic = it })
                SettingToggle("Relentless completion reminders", reminders, { reminders = it })
                Text("Demand attention every 10 seconds until the completion alert is acknowledged. Works independently of sound.",
                    color = UiColor.muted, fontSize = 12.sp)
                SettingToggle("Play completion alarm", sound, { sound = it })
                AlarmChoice("Focus completion sound", "TEST FOCUS ALARM", focusAlarm, {
                    onStopPreview(); previewRequested = false; focusAlarm = it
                }, {
                    previewRequested = true; onPreviewAlarm(Phase.FOCUS, focusAlarm)
                })
                AlarmChoice("Break completion sound", "TEST BREAK ALARM", breakAlarm, {
                    onStopPreview(); previewRequested = false; breakAlarm = it
                }, {
                    previewRequested = true; onPreviewAlarm(Phase.SHORT_BREAK, breakAlarm)
                })
                Text("Both breaks use the break sound. Previews play your unsaved choice for up to 8 seconds, even when sound is off. SAVE RULES keeps your selections.",
                    color = UiColor.muted, fontSize = 12.sp)
                SettingToggle("Play button click sounds", clicks, { clicks = it })
                SettingToggle("Reduce motion", reduceMotion, { reduceMotion = it })
                Text("Keep timer transitions and emphasis still. Countdown and alerts work as usual.",
                    color = UiColor.muted, fontSize = 12.sp)
                if (audioWarning != null) Text(audioWarning, color = UiColor.focus)
                else if (previewRequested) Text("Test requested. If you hear nothing, check your Windows output device and volume.",
                    color = UiColor.breakTime, fontSize = 12.sp)
                if (!candidate.isValid()) Text("Enter values within the ranges shown above.", color = UiColor.focus)
                Text("Saving updates blocks that have not started. Running or paused blocks keep their time; RESET BLOCK uses the new duration. Other preferences apply on save.", color = UiColor.muted)
            }
        },
        confirmButton = { Button(enabled = candidate.isValid(), shape = MaterialTheme.shapes.small,
            onClick = { onSave(candidate) }) { Text("SAVE RULES") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun AlarmChoice(label: String, previewLabel: String, selected: AlarmSound, onSelect: (AlarmSound) -> Unit, onPreview: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("${selected.label} ▾")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AlarmSound.entries.forEach { choice ->
                    DropdownMenuItem(text = { Text(if (choice == selected) "✓ ${choice.label}" else choice.label) },
                        onClick = { onSelect(choice); expanded = false })
                }
            }
        }
        TextButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
            Text(previewLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f))
    }
}
