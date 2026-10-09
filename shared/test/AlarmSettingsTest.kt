package com.pomodoro.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmSettingsTest {
    @Test fun alarmChoicesApplyToCompletedPhaseWithoutResettingProgress() {
        val running = SessionEngine.reduce(newSession(TimerSettings(focusMinutes = 1)),
            SessionCommand.Start, TimeMark(0, 0))
        val settings = running.settings.copy(focusAlarm = AlarmSound.KITCHEN_TIMER, breakAlarm = AlarmSound.URGENT_TONE)
        val updated = SessionEngine.reduce(running, SessionCommand.ChangeSettings(settings), TimeMark(10_000, 10_000))
        assertEquals(settings, updated.settings)
        assertEquals(50_000L, updated.remainingMs)
        assertEquals(running.phaseId, updated.phaseId)
        assertEquals(running.deadlineMonotonicMs, updated.deadlineMonotonicMs)
        assertEquals(AlarmSound.KITCHEN_TIMER, settings.alarmFor(Phase.FOCUS))
        assertEquals(AlarmSound.URGENT_TONE, settings.alarmFor(Phase.SHORT_BREAK))
        assertEquals(AlarmSound.URGENT_TONE, settings.alarmFor(Phase.LONG_BREAK))
        val completed = SessionEngine.reduce(updated, SessionCommand.Tick, TimeMark(60_000, 60_000))
        assertEquals(AlarmSound.KITCHEN_TIMER, completed.settings.alarmFor(completed.pending.single().phase))
        assertEquals(AlarmSound.URGENT_TONE, completed.settings.alarmFor(completed.phase))
        assertEquals(1, completed.completedFocus)
    }
}
