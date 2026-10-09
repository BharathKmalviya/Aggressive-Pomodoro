package com.pomodoro.presentation

import com.pomodoro.domain.CloseBehavior
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopCloseActionTest {
    @Test fun savedWindowChoicesRetainFallbackAndExplicitTrayExit() {
        for (trayAvailable in listOf(false, true)) {
            assertEquals(DesktopCloseAction.ASK, windowCloseAction(CloseBehavior.ASK, trayAvailable))
            assertEquals(DesktopCloseAction.EXIT, windowCloseAction(CloseBehavior.EXIT, trayAvailable))
            assertEquals(if (trayAvailable) DesktopCloseAction.BACKGROUND else DesktopCloseAction.MINIMIZE,
                windowCloseAction(CloseBehavior.BACKGROUND, trayAvailable))
            CloseBehavior.entries.forEach { behavior ->
                assertEquals(DesktopCloseAction.ASK, windowCloseAction(behavior, trayAvailable, explicitTrayExit = true))
            }
        }
    }
}
