package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisNavy,
    primaryContainer = JarvisSurfaceElevated,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisGold,
    onSecondary = JarvisNavy,
    secondaryContainer = JarvisSurfaceElevated,
    onSecondaryContainer = JarvisGold,
    tertiary = JarvisEmerald,
    onTertiary = JarvisNavy,
    background = JarvisNavy,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceElevated,
    onSurfaceVariant = JarvisTextMuted,
    outline = JarvisBorder,
    error = JarvisAlertRed,
    onError = JarvisTextPrimary
)

@Composable
fun JarvisTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current)
    } else {
        JarvisDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
