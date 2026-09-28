package com.pomodoro.data

import com.pomodoro.domain.Completion
import com.pomodoro.domain.FocusDay
import com.pomodoro.domain.FocusHistory
import com.pomodoro.domain.FocusTask
import com.pomodoro.domain.Phase
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionState
import com.pomodoro.domain.SessionStatus
import com.pomodoro.domain.TaskBoard
import com.pomodoro.domain.TimerSettings
import com.pomodoro.domain.newSession
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Properties

class AppStore(private val file: Path) : SnapshotStore {
    override fun load(): ProductState {
        if (!Files.exists(file)) return ProductState()
        return try {
            val values = Properties().apply { Files.newInputStream(file).use { load(it) } }
            val version = values.required("version")
            require(version in setOf("1", "2", "3"))
            val settings = TimerSettings(
                focusMinutes = values.int("focusMinutes"),
                shortBreakMinutes = values.int("shortBreakMinutes"),
                longBreakMinutes = values.int("longBreakMinutes"),
                longBreakEvery = values.int("longBreakEvery"),
                automaticTransitions = values.boolean("automaticTransitions"),
                soundEnabled = values.boolean("soundEnabled"),
                clickSoundEnabled = if (version == "1") true else values.boolean("clickSoundEnabled"),
                aggressiveAlertsEnabled = if (version == "3") values.boolean("aggressiveAlertsEnabled") else true,
            )
            require(settings.isValid())
            val phase = enumValueOf<Phase>(values.required("phase"))
            val status = enumValueOf<SessionStatus>(values.required("status"))
            val id = values.long("phaseId")
            val completed = values.int("completedFocus")
            val inCycle = values.int("focusInCycle")
            val duration = values.long("durationMs")
            val remaining = values.long("remainingMs")
            val maxDuration = if (phase == Phase.FOCUS) 180 * 60_000L else 60 * 60_000L
            require(id > 0 && completed >= 0 && inCycle in 0..12)
            require(duration in 60_000L..maxDuration && remaining in 0..duration)
            val deadline = values.getProperty("deadlineWallMs")?.toLong()
            require(status != SessionStatus.RUNNING || deadline != null)
            val pending = values.getProperty("pending", "").takeIf(String::isNotEmpty)
                ?.split(';')?.map {
                    val parts = it.split(':')
                    require(parts.size == 2)
                    Completion(parts[0].toLong(), enumValueOf<Phase>(parts[1]))
                } ?: emptyList()
            require(pending.size <= 2 && pending.map { it.phaseId }.distinct().size == pending.size)
            require(pending.all { it.phaseId in 1 until id })
            require(pending.zipWithNext().all { (first, second) -> first.phaseId < second.phaseId })
            require(status != SessionStatus.WAITING || (pending.isNotEmpty() && remaining == duration))
            require(status != SessionStatus.RUNNING || pending.size < 2)
            require(status == SessionStatus.RUNNING || deadline == null)
            val session = SessionState(phase, status, id, completed, inCycle, duration, remaining,
                deadlineWallMs = deadline, pending = pending, settings = settings)
            val board = if (version == "1") TaskBoard() else values.readBoard()
            val history = if (version == "1") FocusHistory() else values.readHistory()
            // An empty value means this block started without a task. Never substitute
            // a selection made later. Old releases could save a deleted captured ID;
            // discard just that reference rather than all the user's local data.
            val capturedTaskId = if (version == "1") null else values.getProperty("activeTaskId")
                ?.takeIf { it.isNotEmpty() }?.toLong()
            val activeTaskId = capturedTaskId?.takeIf { id -> phase == Phase.FOCUS &&
                status in setOf(SessionStatus.RUNNING, SessionStatus.PAUSED) && board.tasks.any { it.id == id } }
            ProductState(session, board, history, activeTaskId)
        } catch (_: Exception) {
            ProductState(session = newSession(message = "Saved state could not be recovered. A new focus session is ready."))
        }
    }

    override fun save(snapshot: ProductState) {
        Files.createDirectories(file.parent)
        val state = snapshot.session
        val values = Properties().apply {
            setProperty("version", "3")
            setProperty("phase", state.phase.name)
            setProperty("status", state.status.name)
            setProperty("phaseId", state.phaseId.toString())
            setProperty("completedFocus", state.completedFocus.toString())
            setProperty("focusInCycle", state.focusInCycle.toString())
            setProperty("durationMs", state.durationMs.toString())
            setProperty("remainingMs", state.remainingMs.toString())
            state.deadlineWallMs?.let { setProperty("deadlineWallMs", it.toString()) }
            setProperty("pending", state.pending.joinToString(";") { "${it.phaseId}:${it.phase.name}" })
            setProperty("focusMinutes", state.settings.focusMinutes.toString())
            setProperty("shortBreakMinutes", state.settings.shortBreakMinutes.toString())
            setProperty("longBreakMinutes", state.settings.longBreakMinutes.toString())
            setProperty("longBreakEvery", state.settings.longBreakEvery.toString())
            setProperty("automaticTransitions", state.settings.automaticTransitions.toString())
            setProperty("soundEnabled", state.settings.soundEnabled.toString())
            setProperty("clickSoundEnabled", state.settings.clickSoundEnabled.toString())
            setProperty("aggressiveAlertsEnabled", state.settings.aggressiveAlertsEnabled.toString())
            setProperty("taskIds", snapshot.board.tasks.joinToString(",") { it.id.toString() })
            setProperty("selectedTaskId", snapshot.board.selectedId?.toString() ?: "")
            setProperty("nextTaskId", snapshot.board.nextId.toString())
            setProperty("activeTaskId", snapshot.activeTaskId?.toString() ?: "")
            snapshot.board.tasks.forEach { task ->
                val prefix = "task.${task.id}."
                setProperty(prefix + "title", task.title)
                setProperty(prefix + "estimate", task.estimate.toString())
                setProperty(prefix + "completed", task.completed.toString())
                setProperty(prefix + "done", task.done.toString())
            }
            setProperty("historyDays", snapshot.history.days.joinToString(",") { it.date })
            snapshot.history.days.forEach { day ->
                val prefix = "day.${day.date}."
                setProperty(prefix + "sessions", day.sessions.toString())
                setProperty(prefix + "focusedMs", day.focusedMs.toString())
            }
        }
        val temporary = Files.createTempFile(file.parent, "session-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { values.store(it, "Aggressive Pomodoro session") }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun Properties.required(key: String): String = getProperty(key) ?: error("Missing $key")
    private fun Properties.int(key: String): Int = required(key).toInt()
    private fun Properties.long(key: String): Long = required(key).toLong()
    private fun Properties.boolean(key: String): Boolean = when (required(key)) {
        "true" -> true
        "false" -> false
        else -> error("Invalid $key")
    }

    private fun Properties.readBoard(): TaskBoard {
        val ids = getProperty("taskIds", "").takeIf { it.isNotEmpty() }?.split(',')?.map(String::toLong) ?: emptyList()
        require(ids.size <= 200 && ids.distinct().size == ids.size && ids.all { it > 0 })
        val tasks = ids.map { id ->
            val prefix = "task.$id."
            FocusTask(id, required(prefix + "title"), int(prefix + "estimate"),
                int(prefix + "completed"), boolean(prefix + "done"))
        }
        require(tasks.all { it.title.isNotBlank() && it.title.length <= 120 && it.estimate in 1..20 && it.completed >= 0 })
        val nextId = long("nextTaskId")
        require(nextId > 0 && ids.all { it < nextId })
        val selected = getProperty("selectedTaskId", "").takeIf { it.isNotEmpty() }?.toLong()
        require(selected == null || tasks.any { it.id == selected && !it.done })
        return TaskBoard(tasks, selected, nextId)
    }

    private fun Properties.readHistory(): FocusHistory {
        val dates = getProperty("historyDays", "").takeIf { it.isNotEmpty() }?.split(',') ?: emptyList()
        require(dates.size <= 10_000 && dates.distinct().size == dates.size)
        return FocusHistory(dates.map { date ->
            LocalDate.parse(date)
            val prefix = "day.$date."
            FocusDay(date, int(prefix + "sessions"), long(prefix + "focusedMs"))
        }.also { require(it.all { day -> day.sessions >= 0 && day.focusedMs >= 0 }) })
    }
}
