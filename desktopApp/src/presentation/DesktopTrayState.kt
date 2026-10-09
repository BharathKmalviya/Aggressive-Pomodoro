package com.pomodoro.presentation

import com.pomodoro.domain.Phase
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus

enum class DesktopTrayMode { NORMAL, CLOSE_CHOICE, SAVE_ERROR, SAVING, INSTALLING }

data class DesktopTrayState(
    val phaseId: Long,
    val showLabel: String,
    val statusText: String,
    val todayText: String,
    val tooltip: String,
    val primaryLabel: String,
    val primaryCommand: SessionCommand?,
    val actionsEnabled: Boolean,
    val timerActionsEnabled: Boolean,
    val navigationEnabled: Boolean,
    val soundEnabled: Boolean,
    val remindersEnabled: Boolean,
    val updateLabel: String,
    val exitLabel: String,
    val exitEnabled: Boolean,
)

/** Projects existing state; the tray never calculates a transition or owns a timer. */
fun desktopTrayState(
    product: ProductState,
    remainingMs: Long,
    todayDate: String,
    updateStatus: UpdateStatus,
    mode: DesktopTrayMode = DesktopTrayMode.NORMAL,
): DesktopTrayState {
    val session = product.session
    val phase = when (session.phase) {
        Phase.FOCUS -> "Focus"
        Phase.SHORT_BREAK -> "Short break"
        Phase.LONG_BREAK -> "Long break"
    }
    val seconds = remainingMs.coerceAtLeast(0) / 1000 + if (remainingMs > 0 && remainingMs % 1000 != 0L) 1 else 0
    val countdown = "%02d:%02d".format(seconds / 60, seconds % 60)
    val pending = session.pending.isNotEmpty()
    val status = when {
        session.status == SessionStatus.RUNNING -> "Running"
        session.status == SessionStatus.PAUSED -> "Paused"
        session.status == SessionStatus.WAITING -> "Waiting"
        else -> "Ready"
    }
    val primary = if (pending) null else when (session.status) {
        SessionStatus.IDLE -> SessionCommand.StartPhase(session.phaseId)
        SessionStatus.RUNNING -> SessionCommand.PausePhase(session.phaseId)
        SessionStatus.PAUSED -> SessionCommand.ResumePhase(session.phaseId)
        SessionStatus.WAITING -> null
    }
    val today = product.history.on(todayDate)
    val alerts = if (pending) " / ${session.pending.size} alert${if (session.pending.size == 1) "" else "s"} to review" else ""
    val statusText = "$phase - $countdown - $status$alerts"
    val actionsEnabled = mode == DesktopTrayMode.NORMAL || mode == DesktopTrayMode.CLOSE_CHOICE
    val reason = when (mode) {
        DesktopTrayMode.CLOSE_CHOICE -> "Tray actions cancel the open close choice."
        DesktopTrayMode.SAVE_ERROR -> "Save failed. Open the app to retry or keep it open."
        DesktopTrayMode.SAVING -> "Saving session before exiting."
        DesktopTrayMode.INSTALLING -> "Preparing update installation."
        DesktopTrayMode.NORMAL -> if (pending) "Review the completed phase to use timer options and app shortcuts." else null
    }
    return DesktopTrayState(
        phaseId = session.phaseId,
        showLabel = when (mode) {
            DesktopTrayMode.CLOSE_CHOICE -> "Cancel close and open app"
            DesktopTrayMode.SAVE_ERROR -> "Resolve save error..."
            DesktopTrayMode.SAVING -> "Saving session - open app"
            DesktopTrayMode.INSTALLING -> "Preparing update - open app"
            DesktopTrayMode.NORMAL -> "Open Aggressive Pomodoro"
        },
        statusText = statusText,
        todayText = "Today: ${today.sessions} block${if (today.sessions == 1) "" else "s"} / ${today.focusedMs / 60_000} min",
        tooltip = "Aggressive Pomodoro\n$statusText" + (reason?.let { "\n$it" } ?: ""),
        primaryLabel = when {
            pending || session.status == SessionStatus.WAITING -> "Review completed phase..."
            session.status == SessionStatus.RUNNING -> "Pause ${phase.lowercase()}"
            session.status == SessionStatus.PAUSED -> "Resume ${phase.lowercase()}"
            else -> "Start ${phase.lowercase()}"
        },
        primaryCommand = primary,
        actionsEnabled = actionsEnabled,
        timerActionsEnabled = actionsEnabled && !pending && session.status != SessionStatus.WAITING,
        navigationEnabled = actionsEnabled && !pending,
        soundEnabled = session.settings.soundEnabled,
        remindersEnabled = session.settings.aggressiveAlertsEnabled,
        updateLabel = if (updateStatus in setOf(UpdateStatus.CHECKING, UpdateStatus.AVAILABLE,
                UpdateStatus.DOWNLOADING, UpdateStatus.READY, UpdateStatus.INSTALLING)) "View update..."
            else "Check for updates...",
        exitLabel = if (mode == DesktopTrayMode.SAVE_ERROR) "Review save error..." else "Exit...",
        exitEnabled = mode != DesktopTrayMode.SAVING && mode != DesktopTrayMode.INSTALLING,
    )
}
