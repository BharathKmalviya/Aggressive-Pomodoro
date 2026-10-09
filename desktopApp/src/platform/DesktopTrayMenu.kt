package com.pomodoro.platform

import com.pomodoro.domain.SessionCommand
import com.pomodoro.presentation.AppDestination
import com.pomodoro.presentation.DesktopTrayState
import java.awt.CheckboxMenuItem
import java.awt.Menu
import java.awt.MenuItem
import java.awt.PopupMenu

/** Native menu resources and event routing, independent of icon installation and timer ownership. */
internal class DesktopTrayMenu(
    initialState: DesktopTrayState,
    onShow: () -> Unit,
    onExit: () -> Unit,
    onTimerCommand: (SessionCommand) -> Unit,
    private val onOpen: (AppDestination, Long?) -> Unit,
    onSoundChanged: (Boolean) -> Unit,
    onRemindersChanged: (Boolean) -> Unit,
    onBackground: () -> Unit,
) {
    private var closed = false
    private var state = initialState
    private val showItem = MenuItem().apply { addActionListener { if (!closed) onShow() } }
    private val statusItem = MenuItem().apply { addActionListener { if (!closed) onShow() } }
    private fun shortcut(label: String, destination: AppDestination, timer: Boolean = false) =
        MenuItem(label).apply {
            addActionListener {
                if (!closed && if (timer) state.timerActionsEnabled else state.navigationEnabled) {
                    onOpen(destination, if (timer) state.phaseId else null)
                }
            }
        }
    private val todayItem = shortcut("", AppDestination.REPORTS)
    private val primaryItem = MenuItem().apply {
        addActionListener {
            if (!closed && state.actionsEnabled) {
                state.primaryCommand?.let(onTimerCommand) ?: onShow()
            }
        }
    }
    private val resetItem = shortcut("Reset current block...", AppDestination.RESET, timer = true)
    private val skipItem = shortcut("Skip current phase...", AppDestination.SKIP, timer = true)
    private val timerMenu = Menu("Timer options").apply { add(resetItem); add(skipItem) }
    private val soundItem = CheckboxMenuItem("Alarm sound").apply {
        addItemListener {
            val current = this@DesktopTrayMenu.state
            if (!closed && current.actionsEnabled) onSoundChanged(state)
            else if (state != current.soundEnabled) state = current.soundEnabled
        }
    }
    private val remindersItem = CheckboxMenuItem("Repeat completion reminders").apply {
        addItemListener {
            val current = this@DesktopTrayMenu.state
            if (!closed && current.actionsEnabled) onRemindersChanged(state)
            else if (state != current.remindersEnabled) state = current.remindersEnabled
        }
    }
    private val alertsMenu = Menu("Alerts").apply { add(soundItem); add(remindersItem) }
    private val reportsItem = shortcut("Reports...", AppDestination.REPORTS)
    private val settingsItem = shortcut("Settings...", AppDestination.SETTINGS)
    private val updateItem = shortcut("Check for updates...", AppDestination.UPDATES)
    private val aboutItem = shortcut("About...", AppDestination.ABOUT)
    private val backgroundItem = MenuItem("Run in background").apply {
        addActionListener { if (!closed && state.actionsEnabled) onBackground() }
    }
    private val exitItem = MenuItem().apply {
        addActionListener { if (!closed && state.exitEnabled) onExit() }
    }
    val popup = PopupMenu().apply {
        add(showItem)
        add(statusItem)
        add(todayItem)
        addSeparator()
        add(primaryItem)
        add(timerMenu)
        add(alertsMenu)
        addSeparator()
        add(reportsItem)
        add(settingsItem)
        add(updateItem)
        add(aboutItem)
        addSeparator()
        add(backgroundItem)
        add(exitItem)
    }

    init { update(initialState) }

    /** Event-thread only; avoid redundant peer writes on each timer tick. */
    fun update(next: DesktopTrayState) {
        if (closed) return
        state = next
        showItem.setLabelIfChanged(next.showLabel)
        statusItem.setLabelIfChanged(next.statusText)
        todayItem.setLabelIfChanged("${next.todayText}...")
        primaryItem.setLabelIfChanged(next.primaryLabel)
        updateItem.setLabelIfChanged(next.updateLabel)
        exitItem.setLabelIfChanged(next.exitLabel)
        primaryItem.setEnabledIfChanged(next.actionsEnabled)
        listOf(timerMenu, resetItem, skipItem).forEach { it.setEnabledIfChanged(next.timerActionsEnabled) }
        listOf(todayItem, reportsItem, settingsItem, updateItem, aboutItem).forEach {
            it.setEnabledIfChanged(next.navigationEnabled)
        }
        listOf(alertsMenu, soundItem, remindersItem, backgroundItem).forEach { it.setEnabledIfChanged(next.actionsEnabled) }
        exitItem.setEnabledIfChanged(next.exitEnabled)
        if (soundItem.state != next.soundEnabled) soundItem.state = next.soundEnabled
        if (remindersItem.state != next.remindersEnabled) remindersItem.state = next.remindersEnabled
    }

    fun close() { closed = true }
}

private fun MenuItem.setLabelIfChanged(value: String) { if (label != value) label = value }
private fun MenuItem.setEnabledIfChanged(value: Boolean) { if (isEnabled != value) isEnabled = value }
