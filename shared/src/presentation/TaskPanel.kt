package com.pomodoro.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pomodoro.domain.FocusTask
import com.pomodoro.domain.TaskBoard
import com.pomodoro.domain.TaskCommand
import kotlin.math.max

@Composable
internal fun TaskPanel(board: TaskBoard, modifier: Modifier = Modifier,
    onTaskCommand: (TaskCommand) -> Unit, onUiClick: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf("1") }
    var deleting by remember { mutableStateOf<FocusTask?>(null) }
    val parsedEstimate = estimate.toIntOrNull()
    val canAdd = title.trim().isNotEmpty() && title.trim().length <= 120 &&
        parsedEstimate != null && parsedEstimate in 1..20 && board.tasks.size < 200
    val remaining = board.tasks.filterNot { it.done }.sumOf { max(0, it.estimate - it.completed) }

    Card(modifier, colors = CardDefaults.cardColors(containerColor = UiColor.panel)) {
        Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("YOUR WORK", fontWeight = FontWeight.Black, color = UiColor.text)
                    Text("$remaining estimated focus blocks left", color = UiColor.muted)
                }
                Text("LOCAL ONLY", color = UiColor.breakTime, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("What will you finish?") }, singleLine = true,
                isError = title.length > 120)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(estimate, { estimate = it }, modifier = Modifier.width(120.dp),
                    label = { Text("Blocks") }, singleLine = true,
                    isError = parsedEstimate == null || parsedEstimate !in 1..20)
                Button(onClick = {
                    onTaskCommand(TaskCommand.Add(title, parsedEstimate!!))
                    title = ""
                }, enabled = canAdd) { Text("ADD TASK") }
            }
            if (board.tasks.size >= 200) Text("Task limit reached. Remove a finished task to add another.", color = UiColor.focus)
            else if (!canAdd && title.isNotBlank()) Text("Use a title under 120 characters and 1–20 blocks.", color = UiColor.focus)
            if (board.tasks.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No tasks yet", fontWeight = FontWeight.Bold)
                    Text("Add one clear outcome, then start the timer.", color = UiColor.muted)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(board.tasks, key = { it.id }) { task ->
                        TaskRow(task, selected = task.id == board.selectedId,
                            onTaskCommand = onTaskCommand,
                            onRemove = { onUiClick(); deleting = task })
                    }
                }
            }
        }
    }

    deleting?.let { task ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove task?") },
            text = { Text("${task.title} and its progress will be removed from the task list. Daily focus totals stay saved.") },
            confirmButton = { Button(onClick = { onTaskCommand(TaskCommand.Remove(task.id)); deleting = null }) { Text("REMOVE") } },
            dismissButton = { TextButton(onClick = { onUiClick(); deleting = null }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun TaskRow(task: FocusTask, selected: Boolean,
    onTaskCommand: (TaskCommand) -> Unit, onRemove: () -> Unit) {
    val color = if (selected) UiColor.focus else UiColor.border
    Row(Modifier.fillMaxWidth().border(1.dp, color).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = task.done, onCheckedChange = { onTaskCommand(TaskCommand.ToggleDone(task.id)) })
        TextButton(onClick = { onTaskCommand(TaskCommand.Select(task.id)) }, enabled = !task.done,
            modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth()) {
                Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (task.done) UiColor.muted else UiColor.text,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                Text("${task.completed}/${task.estimate} blocks", color = UiColor.muted)
            }
        }
        TextButton(onClick = onRemove) { Text("REMOVE") }
    }
}
