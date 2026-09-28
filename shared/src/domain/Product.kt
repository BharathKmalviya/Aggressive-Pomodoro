package com.pomodoro.domain

import java.time.LocalDate

data class FocusTask(
    val id: Long,
    val title: String,
    val estimate: Int,
    val completed: Int = 0,
    val done: Boolean = false,
)

data class TaskBoard(
    val tasks: List<FocusTask> = emptyList(),
    val selectedId: Long? = null,
    val nextId: Long = 1,
) {
    val selected: FocusTask? get() = tasks.firstOrNull { it.id == selectedId }
}

sealed interface TaskCommand {
    data class Add(val title: String, val estimate: Int) : TaskCommand
    data class Select(val id: Long) : TaskCommand
    data class ToggleDone(val id: Long) : TaskCommand
    data class Remove(val id: Long) : TaskCommand
}

data class FocusDay(val date: String, val sessions: Int, val focusedMs: Long)
data class FocusHistory(val days: List<FocusDay> = emptyList()) {
    fun on(date: String): FocusDay = days.firstOrNull { it.date == date } ?: FocusDay(date, 0, 0)

    fun sevenDaysThrough(date: String): List<FocusDay> {
        val today = LocalDate.parse(date)
        return (0L..6L).map { on(today.minusDays(it).toString()) }
    }
}

data class ProductState(
    val session: SessionState = newSession(),
    val board: TaskBoard = TaskBoard(),
    val history: FocusHistory = FocusHistory(),
    val activeTaskId: Long? = null,
)

sealed interface ProductCommand {
    data class Session(val command: SessionCommand) : ProductCommand
    data class Task(val command: TaskCommand) : ProductCommand
}

/** Keeps a completed focus block, its selected task, and the daily total in one transition. */
object ProductEngine {
    fun reduce(state: ProductState, command: ProductCommand, now: TimeMark, localDate: String): ProductState = when (command) {
        is ProductCommand.Task -> {
            val current = applySession(state, SessionCommand.Tick, now, localDate)
            val board = updateBoard(current.board, command.command)
            current.copy(board = board, activeTaskId = current.activeTaskId.takeIf { id -> board.tasks.any { it.id == id } })
        }
        is ProductCommand.Session -> applySession(state, command.command, now, localDate)
    }

    private fun applySession(state: ProductState, command: SessionCommand, now: TimeMark, localDate: String): ProductState {
        val before = state.session
        val after = SessionEngine.reduce(before, command, now)
        return if (after.completedFocus != before.completedFocus) state.copy(session = after,
            board = creditTask(state.board, state.activeTaskId),
            history = creditDay(state.history, localDate, before.durationMs), activeTaskId = null)
        else {
            val startedFocus = after.status == SessionStatus.RUNNING && after.phase == Phase.FOCUS &&
                (before.phase != Phase.FOCUS || before.status != SessionStatus.RUNNING)
            val leftFocus = before.phase == Phase.FOCUS && after.phase != Phase.FOCUS
            val reset = command == SessionCommand.Reset ||
                command is SessionCommand.ResetPhase && command.phaseId == before.phaseId
            state.copy(session = after, activeTaskId = when {
                startedFocus && before.status != SessionStatus.PAUSED -> state.board.selectedId
                leftFocus || (reset && after.status == SessionStatus.IDLE) -> null
                else -> state.activeTaskId
            })
        }
    }

    fun recover(state: ProductState, now: TimeMark, localDate: String): ProductState {
        val before = state.session
        val after = SessionEngine.recover(before, now)
        return if (after.completedFocus == before.completedFocus) state.copy(session = after,
            activeTaskId = if (before.phase != Phase.FOCUS && after.phase == Phase.FOCUS &&
                after.status == SessionStatus.RUNNING) state.board.selectedId else state.activeTaskId)
        else state.copy(session = after, board = creditTask(state.board, state.activeTaskId),
            history = creditDay(state.history, localDate, before.durationMs), activeTaskId = null)
    }

    private fun updateBoard(board: TaskBoard, command: TaskCommand): TaskBoard = when (command) {
        is TaskCommand.Add -> {
            val title = command.title.trim()
            if (title.isEmpty() || title.length > 120 || command.estimate !in 1..20 || board.tasks.size >= 200) board
            else board.copy(tasks = board.tasks + FocusTask(board.nextId, title, command.estimate),
                selectedId = board.selectedId ?: board.nextId, nextId = board.nextId + 1)
        }
        is TaskCommand.Select -> if (board.tasks.any { it.id == command.id && !it.done })
            board.copy(selectedId = command.id) else board
        is TaskCommand.ToggleDone -> {
            if (board.tasks.none { it.id == command.id }) board
            else {
                val updated = board.tasks.map { if (it.id == command.id) it.copy(done = !it.done) else it }
                board.copy(tasks = updated, selectedId = board.selectedId.takeIf { id -> updated.any { it.id == id && !it.done } })
            }
        }
        is TaskCommand.Remove -> if (board.tasks.none { it.id == command.id }) board
            else board.copy(tasks = board.tasks.filterNot { it.id == command.id },
                selectedId = board.selectedId.takeIf { it != command.id })
    }

    private fun creditTask(board: TaskBoard, taskId: Long?): TaskBoard = board.copy(tasks = board.tasks.map {
        if (it.id == taskId) it.copy(completed = it.completed + 1) else it
    })

    private fun creditDay(history: FocusHistory, date: String, durationMs: Long): FocusHistory {
        val current = history.on(date)
        val updated = current.copy(sessions = current.sessions + 1, focusedMs = current.focusedMs + durationMs)
        return FocusHistory((history.days.filterNot { it.date == date } + updated).sortedByDescending { it.date })
    }
}
