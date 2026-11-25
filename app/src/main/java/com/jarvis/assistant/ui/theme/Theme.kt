package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBackground,
    primaryContainer = JarvisBlueDeep,
    onPrimaryContainer = JarvisCyanGlow,
    secondary = JarvisBlue,
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

@Composable
fun JarvisTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        content = content
    )
}
