package com.pomodoro.presentation

import com.pomodoro.domain.Phase
import com.pomodoro.domain.ProductState
import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionStatus

data class DesktopTrayState(
    val phaseId: Long,
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
)

/** Projects existing state; the tray never calculates a transition or owns a timer. */
fun desktopTrayState(
    product: ProductState,
    remainingMs: Long,
    todayDate: String,
    updateStatus: UpdateStatus,
    blocked: Boolean = false,
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
    return DesktopTrayState(
        phaseId = session.phaseId,
        statusText = statusText,
        todayText = "Today: ${today.sessions} block${if (today.sessions == 1) "" else "s"} / ${today.focusedMs / 60_000} min",
        tooltip = "Aggressive Pomodoro\n$statusText",
        primaryLabel = when {
            pending || session.status == SessionStatus.WAITING -> "Review completed phase..."
            session.status == SessionStatus.RUNNING -> "Pause ${phase.lowercase()}"
            session.status == SessionStatus.PAUSED -> "Resume ${phase.lowercase()}"
            else -> "Start ${phase.lowercase()}"
        },
        primaryCommand = primary,
        actionsEnabled = !blocked,
        timerActionsEnabled = !blocked && !pending && session.status != SessionStatus.WAITING,
        navigationEnabled = !blocked && !pending,
        soundEnabled = session.settings.soundEnabled,
        remindersEnabled = session.settings.aggressiveAlertsEnabled,
        updateLabel = if (updateStatus in setOf(UpdateStatus.CHECKING, UpdateStatus.AVAILABLE,
                UpdateStatus.DOWNLOADING, UpdateStatus.READY, UpdateStatus.INSTALLING)) "View update..."
            else "Check for updates...",
    )
}
