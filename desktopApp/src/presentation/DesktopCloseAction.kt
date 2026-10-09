package com.pomodoro.presentation

import com.pomodoro.domain.CloseBehavior

enum class DesktopCloseAction { ASK, BACKGROUND, MINIMIZE, EXIT }

/** Explicit tray Exit always restores the chooser; unavailable trays keep a taskbar restore path. */
fun windowCloseAction(behavior: CloseBehavior, trayAvailable: Boolean, explicitTrayExit: Boolean = false): DesktopCloseAction =
    if (explicitTrayExit) DesktopCloseAction.ASK else when (behavior) {
        CloseBehavior.ASK -> DesktopCloseAction.ASK
        CloseBehavior.BACKGROUND -> if (trayAvailable) DesktopCloseAction.BACKGROUND else DesktopCloseAction.MINIMIZE
        CloseBehavior.EXIT -> DesktopCloseAction.EXIT
    }
