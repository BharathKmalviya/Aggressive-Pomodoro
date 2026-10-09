package com.pomodoro.domain

/** Saved window-close preference; it owns no window or process resources. */
enum class CloseBehavior(val id: String, val label: String) {
    ASK("ask", "Ask every time"),
    BACKGROUND("background", "Run in background"),
    EXIT("exit", "Exit the app");

    companion object {
        fun fromId(id: String?): CloseBehavior = entries.firstOrNull { it.id == id } ?: ASK
    }
}
