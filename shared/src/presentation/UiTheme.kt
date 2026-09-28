package com.pomodoro.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal object UiColor {
    val background = Color(0xFF0A0B0D)
    val panel = Color(0xFF131518)
    val panelRaised = Color(0xFF202327)
    val border = Color(0xFF3B3E43)
    val text = Color(0xFFFFFAF4)
    val muted = Color(0xFFB5B7BC)
    val focus = Color(0xFFFF5147)
    val breakTime = Color(0xFF83E2AA)
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
        shapes = Shapes(
            extraSmall = RoundedCornerShape(2.dp),
            small = RoundedCornerShape(2.dp),
            medium = RoundedCornerShape(4.dp),
            large = RoundedCornerShape(4.dp),
            extraLarge = RoundedCornerShape(6.dp),
        ),
        content = content,
    )
}
