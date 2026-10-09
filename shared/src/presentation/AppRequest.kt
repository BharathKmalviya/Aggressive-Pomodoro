package com.pomodoro.presentation

import com.pomodoro.domain.SessionCommand
import com.pomodoro.domain.SessionState

enum class AppDestination { SETTINGS, REPORTS, ABOUT, UPDATES, RESET, SKIP }

/** A transient navigation/confirmation request; repeated clicks have distinct identities. */
data class AppRequest(val sequence: Long, val destination: AppDestination, val phaseId: Long? = null) {
    fun confirmationCommand(state: SessionState): SessionCommand? {
        if (phaseId != state.phaseId || state.pending.isNotEmpty()) return null
        return when (destination) {
            AppDestination.RESET -> SessionCommand.ResetPhase(state.phaseId)
            AppDestination.SKIP -> SessionCommand.SkipPhase(state.phaseId)
            else -> null
        }
    }
}
