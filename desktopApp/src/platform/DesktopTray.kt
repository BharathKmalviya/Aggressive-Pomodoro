package com.pomodoro.platform

import com.pomodoro.domain.Phase
import com.pomodoro.domain.SessionCommand
import com.pomodoro.presentation.AppDestination
import com.pomodoro.presentation.DesktopTrayState
import java.awt.CheckboxMenuItem
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
    initialState: DesktopTrayState,
    onShow: () -> Unit,
    onExit: () -> Unit,
    onTimerCommand: (SessionCommand) -> Unit,
    private val onOpen: (AppDestination, Long?) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
) : AutoCloseable {
    private var closed = false
    private var state = initialState
    private val statusItem = MenuItem().apply { isEnabled = false }
    private val todayItem = MenuItem().apply { isEnabled = false }
    private val primaryItem = MenuItem().apply {
        addActionListener {
            if (!closed && state.actionsEnabled) {
                state.primaryCommand?.let(onTimerCommand) ?: onShow()
            }
        }
    }
    private fun shortcut(label: String, destination: AppDestination, timer: Boolean = false) =
        MenuItem(label).apply {
            addActionListener {
                if (!closed && if (timer) state.timerActionsEnabled else state.navigationEnabled) {
                    onOpen(destination, if (timer) state.phaseId else null)
                }
            }
        }
    private val resetItem = shortcut("Reset current block...", AppDestination.RESET, timer = true)
    private val skipItem = shortcut("Skip current phase...", AppDestination.SKIP, timer = true)
    private val soundItem = CheckboxMenuItem("Alarm sound").apply {
        addItemListener { if (!closed && this@DesktopTray.state.actionsEnabled) onSoundChanged(state) }
    }
    private val remindersItem = CheckboxMenuItem("Repeat completion reminders").apply {
        addItemListener { if (!closed && this@DesktopTray.state.actionsEnabled) onRemindersChanged(state) }
    }
    private val reportsItem = shortcut("Reports...", AppDestination.REPORTS)
    private val settingsItem = shortcut("Settings...", AppDestination.SETTINGS)
    private val updateItem = shortcut("Check for updates...", AppDestination.UPDATES)
    private val aboutItem = shortcut("About...", AppDestination.ABOUT)
    private val exitItem = MenuItem("Exit...").apply {
        addActionListener { if (!closed && state.actionsEnabled) onExit() }
    }
    private val removed = PropertyChangeListener {
        SwingUtilities.invokeLater {
            if (!closed && icon !in tray.trayIcons) onUnavailable()
        }
    }

    init {
        icon.popupMenu = PopupMenu().apply {
            add(MenuItem("Show Aggressive Pomodoro").apply { addActionListener { if (!closed) onShow() } })
            addSeparator()
            add(statusItem)
            add(todayItem)
            addSeparator()
            add(primaryItem)
            add(resetItem)
            add(skipItem)
            addSeparator()
            add(soundItem)
            add(remindersItem)
            addSeparator()
            add(reportsItem)
            add(settingsItem)
            add(updateItem)
            add(aboutItem)
            addSeparator()
            add(exitItem)
        }
        icon.addActionListener { if (!closed) onShow() }
        update(initialState)
    }

    /** Called on the event thread; avoid redundant native writes on the 250ms timer tick. */
    fun update(next: DesktopTrayState) {
        if (closed) return
        state = next
        statusItem.setLabelIfChanged(next.statusText)
        todayItem.setLabelIfChanged(next.todayText)
        primaryItem.setLabelIfChanged(next.primaryLabel)
        updateItem.setLabelIfChanged(next.updateLabel)
        primaryItem.setEnabledIfChanged(next.actionsEnabled)
        resetItem.setEnabledIfChanged(next.timerActionsEnabled)
        skipItem.setEnabledIfChanged(next.timerActionsEnabled)
        listOf(reportsItem, settingsItem, updateItem, aboutItem).forEach { it.setEnabledIfChanged(next.navigationEnabled) }
        listOf(soundItem, remindersItem, exitItem).forEach { it.setEnabledIfChanged(next.actionsEnabled) }
        if (soundItem.state != next.soundEnabled) soundItem.state = next.soundEnabled
        if (remindersItem.state != next.remindersEnabled) remindersItem.state = next.remindersEnabled
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
                    onTimerCommand, onOpen, onSoundChanged, onRemindersChanged)
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

private fun MenuItem.setLabelIfChanged(value: String) { if (label != value) label = value }
private fun MenuItem.setEnabledIfChanged(value: Boolean) { if (isEnabled != value) isEnabled = value }
