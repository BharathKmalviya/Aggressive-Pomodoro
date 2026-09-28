package com.pomodoro.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    browserWarning: String?,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmInstall by remember(state.status, state.latestVersion) { mutableStateOf(false) }
    val working = state.status in setOf(UpdateStatus.CHECKING, UpdateStatus.DOWNLOADING, UpdateStatus.INSTALLING)
    val title = when (state.status) {
        UpdateStatus.IDLE -> "APP UPDATES"
        UpdateStatus.CHECKING -> "CHECKING FOR UPDATES"
        UpdateStatus.UP_TO_DATE -> "YOU'RE UP TO DATE"
        UpdateStatus.AVAILABLE -> "NEW VERSION AVAILABLE"
        UpdateStatus.DOWNLOADING -> "DOWNLOADING UPDATE"
        UpdateStatus.READY -> "READY TO INSTALL"
        UpdateStatus.INSTALLING -> "OPENING INSTALLER"
        UpdateStatus.ERROR -> "UPDATE NEEDS ATTENTION"
    }

    if (confirmInstall && state.status == UpdateStatus.READY) {
        AlertDialog(
            onDismissRequest = { confirmInstall = false },
            title = { Text("INSTALL & EXIT?", fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your session will be saved, the Windows installer will open, and Aggressive Pomodoro will close. Follow the installer to finish the update.")
                    Text("The timer cannot sound an alarm while the app is closed. Reopen it after installation to recover your session.",
                        color = UiColor.focus, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(onClick = { confirmInstall = false; onInstall() }, shape = MaterialTheme.shapes.small) {
                    Text("INSTALL & EXIT")
                }
            },
            dismissButton = { TextButton(onClick = { confirmInstall = false }) { Text("KEEP FOCUSING") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.Black) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("INSTALLED VERSION $currentVersion", color = UiColor.muted, fontSize = 12.sp,
                        fontWeight = FontWeight.Bold)
                    state.latestVersion?.let {
                        Text("LATEST VERSION $it", color = UiColor.breakTime, fontWeight = FontWeight.Bold)
                    }
                    Text(when (state.status) {
                        UpdateStatus.IDLE -> "Check GitHub for the latest Windows release when you're ready."
                        UpdateStatus.CHECKING -> "Looking for the latest Windows release on GitHub. You can close this dialog while the check finishes. Your timer keeps running."
                        UpdateStatus.UP_TO_DATE -> "You have the latest available version. Back to the work."
                        UpdateStatus.AVAILABLE -> "Download the Windows installer now. Choose when to install it after the download finishes."
                        UpdateStatus.DOWNLOADING -> "Keep focusing. You can close this dialog while the download continues. Reopen it from About."
                        UpdateStatus.READY -> "Your update is downloaded. Install it when you're ready to close the app."
                        UpdateStatus.INSTALLING -> "Saving your session and opening the Windows installer."
                        UpdateStatus.ERROR -> "The update could not finish. Your timer and saved work remain available."
                    })
                    when (state.status) {
                        UpdateStatus.CHECKING, UpdateStatus.INSTALLING -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(), color = UiColor.focus)
                        UpdateStatus.DOWNLOADING -> {
                            if (state.totalBytes > 0) {
                                val progress = (state.downloadedBytes.toDouble() / state.totalBytes).coerceIn(0.0, 1.0).toFloat()
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                                    color = UiColor.focus)
                                Text("${(progress * 100).toInt()}% · ${sizeText(state.downloadedBytes)} of ${sizeText(state.totalBytes)}",
                                    color = UiColor.muted, fontSize = 12.sp)
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = UiColor.focus)
                                Text("${sizeText(state.downloadedBytes)} downloaded", color = UiColor.muted, fontSize = 12.sp)
                            }
                        }
                        else -> Unit
                    }
                    state.message?.let {
                        Text(it, color = if (state.status == UpdateStatus.ERROR) UiColor.focus else UiColor.muted)
                    }
                    TextButton(onClick = onOpenRelease) { Text("VIEW RELEASE NOTES") }
                    browserWarning?.let { Text(it, color = UiColor.focus) }
                    if (state.status == UpdateStatus.DOWNLOADING) {
                        TextButton(onClick = onCancel) {
                            Text("CANCEL DOWNLOAD")
                        }
                    }
                    if (state.status == UpdateStatus.ERROR && state.latestVersion != null) {
                        TextButton(onClick = onDownload) { Text("DOWNLOAD AGAIN", fontWeight = FontWeight.Bold) }
                    }
                }
            },
            confirmButton = {
                if (!working) {
                    val action = when (state.status) {
                        UpdateStatus.AVAILABLE -> "DOWNLOAD UPDATE"
                        UpdateStatus.READY -> "INSTALL & EXIT"
                        UpdateStatus.ERROR -> "CHECK AGAIN"
                        else -> "CHECK FOR UPDATES"
                    }
                    Button(shape = MaterialTheme.shapes.small, onClick = {
                        when (state.status) {
                            UpdateStatus.AVAILABLE -> onDownload()
                            UpdateStatus.READY -> confirmInstall = true
                            else -> onCheck()
                        }
                    }) { Text(action, fontWeight = FontWeight.Bold) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        )
    }
}

private fun sizeText(bytes: Long): String = "%.1f MB".format(bytes.coerceAtLeast(0) / 1_048_576.0)
