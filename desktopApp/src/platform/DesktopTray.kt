package com.pomodoro.platform

import com.pomodoro.domain.Phase
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities

/** Owns native notification-area resources for this process, never timer state. */
class DesktopTray private constructor(
    private val tray: SystemTray,
    private val icon: TrayIcon,
    private val onUnavailable: () -> Unit,
) : AutoCloseable {
    private var closed = false
    private val removed = PropertyChangeListener {
        SwingUtilities.invokeLater {
            if (!closed && icon !in tray.trayIcons) onUnavailable()
        }
    }

    init { tray.addPropertyChangeListener("trayIcons", removed) }

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
        tray.removePropertyChangeListener("trayIcons", removed)
        tray.remove(icon)
    }

    companion object {
        /** Call on the desktop event thread with an image already loaded off-thread. */
        fun create(image: Image, onShow: () -> Unit, onExit: () -> Unit, onUnavailable: () -> Unit): DesktopTray? {
            if (!SystemTray.isSupported()) return null
            var tray: SystemTray? = null
            var icon: TrayIcon? = null
            return try {
                tray = SystemTray.getSystemTray()
                val menu = PopupMenu().apply {
                    add(MenuItem("Show Aggressive Pomodoro").apply { addActionListener { onShow() } })
                    addSeparator()
                    add(MenuItem("Exit…").apply { addActionListener { onExit() } })
                }
                icon = TrayIcon(image, "Aggressive Pomodoro", menu).apply {
                    isImageAutoSize = true
                    addActionListener { onShow() }
                }
                tray.add(icon)
                DesktopTray(tray, icon, onUnavailable)
            } catch (error: Exception) {
                if (icon != null) runCatching { tray?.remove(icon) }
                System.err.println("System tray unavailable: ${error.message}")
                null
            }
        }
    }
}
