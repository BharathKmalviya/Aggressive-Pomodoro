package com.pomodoro.platform

import com.pomodoro.domain.Phase
import com.pomodoro.domain.SessionCommand
import com.pomodoro.presentation.AppDestination
import com.pomodoro.presentation.DesktopTrayState
import java.awt.Image
import java.awt.SystemTray
import java.awt.TrayIcon
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/** Owns native notification-area resources for this process, never timer state. */
class DesktopTray private constructor(
    private val tray: SystemTray,
    private val icon: TrayIcon,
    private val onUnavailable: () -> Unit,
    initialState: DesktopTrayState,
    onShow: () -> Unit,
    onExit: () -> Unit,
    onTimerCommand: (SessionCommand) -> Unit,
    onOpen: (AppDestination, Long?) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onBackground: () -> Unit,
) : AutoCloseable {
    private var closed = false
    private val menu = DesktopTrayMenu(initialState, onShow, onExit, onTimerCommand, onOpen,
        onSoundChanged, onRemindersChanged, onBackground)
    private val removed = PropertyChangeListener {
        SwingUtilities.invokeLater {
            if (!closed && icon !in tray.trayIcons) onUnavailable()
        }
    }

    init {
        icon.popupMenu = menu.popup
        icon.addActionListener { if (!closed) onShow() }
        update(initialState)
    }

    /** Called on the event thread; avoid redundant native writes on the 250ms timer tick. */
    fun update(next: DesktopTrayState) {
        if (closed) return
        menu.update(next)
        if (icon.toolTip != next.tooltip) icon.toolTip = next.tooltip
    }

    fun notifyCompletion(phase: Phase) {
        if (closed) return
        try {
            icon.displayMessage(
                if (phase == Phase.FOCUS) "Focus block finished" else "Break is over",
                "Open Aggressive Pomodoro from the tray to review your timer.",
                TrayIcon.MessageType.INFO,
            )
        } catch (error: Exception) {
            System.err.println("Tray notification unavailable: ${error.message}")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        menu.close()
        tray.removePropertyChangeListener("trayIcons", removed)
        tray.remove(icon)
    }

    companion object {
        /** Call on the desktop event thread with an image already loaded off-thread. */
        fun create(
            image: Image,
            initialState: DesktopTrayState,
            onShow: () -> Unit,
            onExit: () -> Unit,
            onTimerCommand: (SessionCommand) -> Unit,
            onOpen: (AppDestination, Long?) -> Unit,
            onSoundChanged: (Boolean) -> Unit,
            onRemindersChanged: (Boolean) -> Unit,
            onBackground: () -> Unit,
            onUnavailable: () -> Unit,
        ): DesktopTray? {
            if (!SystemTray.isSupported()) return null
            var tray: SystemTray? = null
            var icon: TrayIcon? = null
            return try {
                tray = SystemTray.getSystemTray()
                icon = TrayIcon(image, "Aggressive Pomodoro").apply {
                    isImageAutoSize = true
                }
                val adapter = DesktopTray(tray, icon, onUnavailable, initialState, onShow, onExit,
                    onTimerCommand, onOpen, onSoundChanged, onRemindersChanged, onBackground)
                tray.add(icon)
                tray.addPropertyChangeListener("trayIcons", adapter.removed)
                adapter
            } catch (error: Exception) {
                if (icon != null) runCatching { tray?.remove(icon) }
                System.err.println("System tray unavailable: ${error.message}")
                null
            }
        }
    }
}
