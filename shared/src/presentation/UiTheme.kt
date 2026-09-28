package com.pomodoro.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal object UiColor {
    val background = Color(0xFF111318)
    val panel = Color(0xFF1C2027)
    val panelRaised = Color(0xFF252A33)
    val border = Color(0xFF3A404B)
    val text = Color(0xFFF7F5F0)
    val muted = Color(0xFFADB4BF)
    val focus = Color(0xFFFF6654)
    val breakTime = Color(0xFF55D6BA)
}

@Composable
internal fun ProductTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = UiColor.focus,
            onPrimary = UiColor.background,
            background = UiColor.background,
            onBackground = UiColor.text,
            surface = UiColor.panel,
            onSurface = UiColor.text,
            surfaceVariant = UiColor.panelRaised,
            onSurfaceVariant = UiColor.muted,
            outline = UiColor.border,
        ),
        content = content,
    )
}
