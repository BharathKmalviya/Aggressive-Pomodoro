package com.pomodoro.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

@Composable
fun CloseDialog(trayAvailable: Boolean, onBackground: () -> Unit, onExit: () -> Unit, onCancel: () -> Unit) {
    DialogWindow(
        onCloseRequest = onCancel,
        title = "Close Aggressive Pomodoro",
        state = rememberDialogState(width = 540.dp, height = 250.dp),
        resizable = false,
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.Escape -> { onCancel(); true }
                Key.B -> { onBackground(); true }
                Key.E -> { onExit(); true }
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBackground) { Text(if (trayAvailable) "BACKGROUND (B)" else "MINIMIZE (B)") }
                        OutlinedButton(onClick = onExit) { Text("EXIT (E)") }
                        TextButton(onClick = onCancel) { Text("CANCEL") }
                    }
                }
            }
        }
    }
}
