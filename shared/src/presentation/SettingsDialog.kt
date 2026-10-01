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

@Composable
internal fun SettingsDialog(
    settings: TimerSettings,
    audioWarning: String?,
    onPreviewAlarm: (Phase) -> Unit,
    onSave: (TimerSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    var focus by remember(settings) { mutableStateOf(settings.focusMinutes.toString()) }
    var short by remember(settings) { mutableStateOf(settings.shortBreakMinutes.toString()) }
    var long by remember(settings) { mutableStateOf(settings.longBreakMinutes.toString()) }
    var interval by remember(settings) { mutableStateOf(settings.longBreakEvery.toString()) }
    var automatic by remember(settings) { mutableStateOf(settings.automaticTransitions) }
    var sound by remember(settings) { mutableStateOf(settings.soundEnabled) }
    var clicks by remember(settings) { mutableStateOf(settings.clickSoundEnabled) }
    var reminders by remember(settings) { mutableStateOf(settings.aggressiveAlertsEnabled) }
    var reduceMotion by remember(settings) { mutableStateOf(settings.reduceMotion) }
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
                SettingToggle("Play button click sounds", clicks, { clicks = it })
                SettingToggle("Reduce motion", reduceMotion, { reduceMotion = it })
                Text("Keep timer transitions and emphasis still. Countdown and alerts work as usual.",
                    color = UiColor.muted, fontSize = 12.sp)
                for ((phase, label) in listOf(Phase.FOCUS to "TEST FOCUS ALARM", Phase.SHORT_BREAK to "TEST BREAK ALARM")) {
                    OutlinedButton(onClick = { previewRequested = true; onPreviewAlarm(phase) }, modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small) {
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Plays once even if completion sound is off. Your sound preference is unchanged.",
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
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange)
        .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onCheckedChange = null)
        Text(label, modifier = Modifier.weight(1f))
    }
}
