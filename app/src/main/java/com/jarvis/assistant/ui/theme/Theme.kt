package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jarvis.assistant.domain.model.JarvisAccentTheme

@Composable
fun JarvisTheme(
    accentTheme: JarvisAccentTheme = JarvisAccentTheme.ARC_CYAN,
    content: @Composable () -> Unit
) {
    val primaryColor = Color(accentTheme.primaryHex)
    val secondaryColor = Color(accentTheme.secondaryHex)
    val glowColor = Color(accentTheme.glowHex)

    val colorScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = JarvisBackground,
        primaryContainer = JarvisBlueDeep,
        onPrimaryContainer = glowColor,
        secondary = secondaryColor,
        onSecondary = JarvisBackground,
        secondaryContainer = JarvisSurfaceVariant,
        onSecondaryContainer = JarvisTextPrimary,
        background = JarvisBackground,
        onBackground = JarvisTextPrimary,
        surface = JarvisSurface,
        onSurface = JarvisTextPrimary,
        surfaceVariant = JarvisSurfaceVariant,
        onSurfaceVariant = JarvisTextSecondary,
        outline = JarvisSurfaceBorder,
        outlineVariant = JarvisSurfaceBorder.copy(alpha = 0.5f)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        content = content
    )
}
