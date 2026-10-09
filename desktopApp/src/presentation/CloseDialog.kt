package com.pomodoro.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

@Composable
fun CloseDialog(trayAvailable: Boolean, onBackground: (Boolean) -> Unit, onExit: (Boolean) -> Unit, onCancel: () -> Unit) {
    var rememberChoice by remember { mutableStateOf(false) }
    DialogWindow(
        onCloseRequest = onCancel,
        title = "Close Aggressive Pomodoro",
        state = rememberDialogState(width = 560.dp, height = 360.dp),
        resizable = false,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.Escape -> { onCancel(); true }
                Key.B -> { onBackground(rememberChoice); true }
                Key.E -> { onExit(rememberChoice); true }
                else -> false
            }
        },
    ) {
        ProductTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Keep your timer running?", style = MaterialTheme.typography.headlineSmall)
                    Text(if (trayAvailable)
                        "Run in the background to keep your timer and alarms active. Reopen from the tray icon. Exit saves your session and stops all alarms."
                    else
                        "The tray is unavailable. Minimize to keep your timer and alarms active, then reopen from the taskbar. Exit saves your session and stops all alarms.")
                    Row(Modifier.fillMaxWidth().toggleable(rememberChoice, role = Role.Checkbox,
                        onValueChange = { rememberChoice = it }), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(rememberChoice, onCheckedChange = null)
                        Text("Remember my choice")
                    }
                    Text("Use this choice for X and Alt+F4. Change it in Settings. Tray Exit saves and closes directly.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onBackground(rememberChoice) }) { Text(if (trayAvailable) "BACKGROUND (B)" else "MINIMIZE (B)") }
                        OutlinedButton(onClick = { onExit(rememberChoice) }) { Text("EXIT (E)") }
                        TextButton(onClick = onCancel) { Text("CANCEL") }
                    }
                }
            }
        }
    }
}
