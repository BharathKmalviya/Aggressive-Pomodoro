package com.pomodoro.presentation

import com.pomodoro.domain.CloseBehavior

enum class DesktopCloseAction { ASK, BACKGROUND, MINIMIZE, EXIT }

/** Tray Exit is deliberate; saved window choices apply only to X/Alt+F4 requests. */
fun windowCloseAction(behavior: CloseBehavior, trayAvailable: Boolean, explicitTrayExit: Boolean = false): DesktopCloseAction =
    if (explicitTrayExit) DesktopCloseAction.EXIT else when (behavior) {
        CloseBehavior.ASK -> DesktopCloseAction.ASK
        CloseBehavior.BACKGROUND -> if (trayAvailable) DesktopCloseAction.BACKGROUND else DesktopCloseAction.MINIMIZE
        CloseBehavior.EXIT -> DesktopCloseAction.EXIT
    }
