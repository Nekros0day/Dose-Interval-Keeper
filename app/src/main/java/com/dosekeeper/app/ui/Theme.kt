package com.dosekeeper.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun DoseKeeperTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Color(0xFF2F6FED),
        secondary = Color(0xFF1DBA91),
        tertiary = Color(0xFFFFB86B),
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEAF0F7),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF111827),
        onSurface = Color(0xFF111827),
    )

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}
