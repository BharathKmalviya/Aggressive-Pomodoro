package com.pomodoro.domain

import kotlin.math.max

enum class Phase { FOCUS, SHORT_BREAK, LONG_BREAK }
enum class SessionStatus { IDLE, RUNNING, PAUSED, WAITING }

data class TimerSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakEvery: Int = 4,
    val automaticTransitions: Boolean = true,
    val soundEnabled: Boolean = true,
    val clickSoundEnabled: Boolean = true,
    val aggressiveAlertsEnabled: Boolean = true,
    val reduceMotion: Boolean = false,
    val focusAlarm: AlarmSound = AlarmSound.ORIGINAL,
    val breakAlarm: AlarmSound = AlarmSound.ORIGINAL,
) {
    fun isValid(): Boolean = focusMinutes in 1..180 && shortBreakMinutes in 1..60 &&
        longBreakMinutes in 1..60 && longBreakEvery in 2..12

    fun durationMs(phase: Phase): Long = when (phase) {
        Phase.FOCUS -> focusMinutes.toLong() * 60_000
        Phase.SHORT_BREAK -> shortBreakMinutes.toLong() * 60_000
        Phase.LONG_BREAK -> longBreakMinutes.toLong() * 60_000
    }

    fun alarmFor(phase: Phase): AlarmSound = if (phase == Phase.FOCUS) focusAlarm else breakAlarm
}

data class TimeMark(val monotonicMs: Long, val wallMs: Long)

data class Completion(val phaseId: Long, val phase: Phase)

data class SessionState(
    val phase: Phase = Phase.FOCUS,
    val status: SessionStatus = SessionStatus.IDLE,
    val phaseId: Long = 1,
    val completedFocus: Int = 0,
    val focusInCycle: Int = 0,
    val durationMs: Long = 25 * 60_000L,
    val remainingMs: Long = durationMs,
    val deadlineMonotonicMs: Long? = null,
    val deadlineWallMs: Long? = null,
    val lastMark: TimeMark? = null,
    val pending: List<Completion> = emptyList(),
    val settings: TimerSettings = TimerSettings(),
    val message: String? = null,
) {
    fun remainingAt(now: TimeMark): Long = if (status == SessionStatus.RUNNING && deadlineMonotonicMs != null)
        max(0L, deadlineMonotonicMs - now.monotonicMs) else remainingMs
}

fun newSession(settings: TimerSettings = TimerSettings(), message: String? = null): SessionState {
    val duration = settings.durationMs(Phase.FOCUS)
    return SessionState(durationMs = duration, remainingMs = duration, settings = settings, message = message)
}

sealed interface SessionCommand {
    data object Start : SessionCommand
    data object Pause : SessionCommand
    data object Resume : SessionCommand
    data object Reset : SessionCommand
    data object Skip : SessionCommand
    data object Tick : SessionCommand
    data object Acknowledge : SessionCommand
    data class StartPhase(val phaseId: Long) : SessionCommand
    data class PausePhase(val phaseId: Long) : SessionCommand
    data class ResumePhase(val phaseId: Long) : SessionCommand
    data class ResetPhase(val phaseId: Long) : SessionCommand
    data class SkipPhase(val phaseId: Long) : SessionCommand
    data class AcknowledgeCompletion(val phaseId: Long) : SessionCommand
    data class ChangeSettings(val settings: TimerSettings) : SessionCommand
}

/** Pure transition logic. All time and side effects are supplied by the caller. */
object SessionEngine {
    private const val CLOCK_DISAGREEMENT_MS = 120_000L

    /** Re-anchor a saved running phase on launch or after a likely system sleep. */
    fun recover(state: SessionState, now: TimeMark): SessionState {
        if (state.status != SessionStatus.RUNNING) return refreshUnstartedDuration(state)
        val wallDeadline = state.deadlineWallMs ?: return newSession(state.settings,
            "Session recovery failed. A new focus session is ready.")
        val remaining = max(0L, wallDeadline - now.wallMs)
        if (remaining > state.remainingMs) return pauseForClockChange(state, state.remainingMs)
        val anchored = state.copy(remainingMs = remaining,
            deadlineMonotonicMs = now.monotonicMs + remaining, lastMark = now)
        return if (remaining == 0L) tick(anchored, now) else anchored
    }

    fun reduce(state: SessionState, command: SessionCommand, now: TimeMark): SessionState {
        val intent = when (command) {
            is SessionCommand.StartPhase -> if (command.phaseId == state.phaseId) SessionCommand.Start else SessionCommand.Tick
            is SessionCommand.PausePhase -> if (command.phaseId == state.phaseId) SessionCommand.Pause else SessionCommand.Tick
            is SessionCommand.ResumePhase -> if (command.phaseId == state.phaseId) SessionCommand.Resume else SessionCommand.Tick
            is SessionCommand.ResetPhase -> if (command.phaseId == state.phaseId) SessionCommand.Reset else SessionCommand.Tick
            is SessionCommand.SkipPhase -> if (command.phaseId == state.phaseId) SessionCommand.Skip else SessionCommand.Tick
            is SessionCommand.AcknowledgeCompletion -> if (command.phaseId == state.pending.firstOrNull()?.phaseId)
                SessionCommand.Acknowledge else SessionCommand.Tick
            else -> command
        }
        if (intent == SessionCommand.Tick) return tick(state, now)
        val current = tick(state, now)
        // A command from the expired phase must not reset, skip, or pause its successor.
        // An already-visible completion can still be acknowledged when a second one arrives.
        if (current.phaseId != state.phaseId && intent !is SessionCommand.ChangeSettings &&
            !(intent == SessionCommand.Acknowledge && state.pending.isNotEmpty())) return current
        return applyCommand(current, intent, now)
    }

    private fun applyCommand(state: SessionState, command: SessionCommand, now: TimeMark): SessionState = when (command) {
        SessionCommand.Start -> if (state.status == SessionStatus.IDLE && state.pending.isEmpty())
            start(state, now) else state
        SessionCommand.Resume -> if (state.status == SessionStatus.PAUSED)
            start(state.copy(message = null), now) else state
        SessionCommand.Pause -> if (state.status == SessionStatus.RUNNING)
            state.copy(status = SessionStatus.PAUSED, remainingMs = state.remainingAt(now),
                deadlineMonotonicMs = null, deadlineWallMs = null, lastMark = null) else state
        SessionCommand.Reset -> if (state.status != SessionStatus.WAITING)
            refreshUnstartedDuration(state.copy(status = SessionStatus.IDLE,
                deadlineMonotonicMs = null, deadlineWallMs = null, lastMark = null, message = null)) else state
        SessionCommand.Skip -> if (state.status != SessionStatus.WAITING) advance(state, completed = false)
            else state
        SessionCommand.Tick -> tick(state, now)
        SessionCommand.Acknowledge -> acknowledge(state, now)
        is SessionCommand.ChangeSettings -> if (command.settings.isValid())
            refreshUnstartedDuration(state.copy(settings = command.settings)) else state
        is SessionCommand.StartPhase, is SessionCommand.PausePhase, is SessionCommand.ResumePhase,
        is SessionCommand.ResetPhase, is SessionCommand.SkipPhase, is SessionCommand.AcknowledgeCompletion -> state
    }

    // Idle/waiting blocks have no elapsed progress. Active and paused blocks keep theirs until reset.
    private fun refreshUnstartedDuration(state: SessionState): SessionState {
        if (state.status != SessionStatus.IDLE && state.status != SessionStatus.WAITING) return state
        val duration = state.settings.durationMs(state.phase)
        return state.copy(durationMs = duration, remainingMs = duration)
    }

    private fun start(state: SessionState, now: TimeMark): SessionState = state.copy(
        status = SessionStatus.RUNNING,
        deadlineMonotonicMs = now.monotonicMs + state.remainingMs,
        deadlineWallMs = now.wallMs + state.remainingMs,
        lastMark = now,
    )

    private fun tick(state: SessionState, now: TimeMark): SessionState {
        if (state.status != SessionStatus.RUNNING) return state
        val previous = state.lastMark
        val clockDisagreement = previous?.let {
            (now.wallMs - it.wallMs) - (now.monotonicMs - it.monotonicMs)
        }
        if (previous != null && clockDisagreement != null && kotlin.math.abs(clockDisagreement) > CLOCK_DISAGREEMENT_MS) {
            // A long monotonic gap suggests sleep; a quick gap with a large wall jump suggests clock editing.
            // Sleep cannot explain a backward wall-clock discrepancy.
            if (clockDisagreement > 0 && now.monotonicMs - previous.monotonicMs > 10_000L)
                return recover(state, now)
            return pauseForClockChange(state, state.remainingAt(now))
        }

        val remaining = state.remainingAt(now)
        if (remaining > 0) return state.copy(remainingMs = remaining, lastMark = now)
        val event = Completion(state.phaseId, state.phase)
        // A running phase can only complete once; advancing changes its phase ID immediately.
        val next = advance(state.copy(remainingMs = 0), completed = true)
        val queued = (state.pending + event).takeLast(2)
        val waiting = !state.settings.automaticTransitions || queued.size == 2
        return next.copy(pending = queued, status = if (waiting) SessionStatus.WAITING else SessionStatus.RUNNING,
            remainingMs = next.durationMs,
            deadlineMonotonicMs = if (waiting) null else now.monotonicMs + next.durationMs,
            deadlineWallMs = if (waiting) null else now.wallMs + next.durationMs,
            lastMark = if (waiting) null else now)
    }

    private fun pauseForClockChange(state: SessionState, remainingMs: Long): SessionState = state.copy(
        status = SessionStatus.PAUSED, remainingMs = remainingMs.coerceIn(0L, state.durationMs),
        deadlineMonotonicMs = null, deadlineWallMs = null, lastMark = null,
        message = "The system clock changed. Review the timer, then resume or reset it.",
    )

    private fun advance(state: SessionState, completed: Boolean): SessionState {
        val nextFocusCount = state.completedFocus + if (completed && state.phase == Phase.FOCUS) 1 else 0
        val nextCycleCount = state.focusInCycle + if (completed && state.phase == Phase.FOCUS) 1 else 0
        val nextPhase = when (state.phase) {
            Phase.FOCUS -> if (completed && nextCycleCount >= state.settings.longBreakEvery) Phase.LONG_BREAK else Phase.SHORT_BREAK
            Phase.SHORT_BREAK, Phase.LONG_BREAK -> Phase.FOCUS
        }
        val duration = state.settings.durationMs(nextPhase)
        return state.copy(phase = nextPhase, phaseId = state.phaseId + 1,
            completedFocus = nextFocusCount,
            focusInCycle = if (nextPhase == Phase.LONG_BREAK) 0 else nextCycleCount,
            status = SessionStatus.IDLE, durationMs = duration, remainingMs = duration,
            deadlineMonotonicMs = null, deadlineWallMs = null, lastMark = null, message = null)
    }

    private fun acknowledge(state: SessionState, now: TimeMark): SessionState {
        if (state.pending.isEmpty()) return state
        val next = state.copy(pending = state.pending.drop(1))
        return if (next.pending.isEmpty() && next.status == SessionStatus.WAITING) start(next, now) else next
    }
}
