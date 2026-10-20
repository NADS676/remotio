package app.remotio.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.remotio.ui.RemoteLayout

/** Touchpad: dark slate with a teal accent, whatever the system theme, so the pad reads like a device. */
private val TouchpadColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF04201C),
    primaryContainer = Color(0xFF123B37),
    onPrimaryContainer = Color(0xFF99F6E4),
    secondaryContainer = Color(0xFF1A1F27),
    onSecondaryContainer = Color(0xFFDCE2EA),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE8ECF2),
    onSurfaceVariant = Color(0xFF8B95A5),
    surfaceContainerLowest = Color(0xFF0B0E12),
    surfaceContainerLow = Color(0xFF12161C),
    surfaceContainer = Color(0xFF141920),
    surfaceContainerHigh = Color(0xFF1A1F27),
    surfaceContainerHighest = Color(0xFF252C36),
    outline = Color(0xFF3A4452),
    outlineVariant = Color(0xFF252D38),
    errorContainer = Color(0xFF2A1416),
    onErrorContainer = Color(0xFFFF8A80),
    inverseSurface = Color(0xFFE8ECF2),
    inverseOnSurface = Color(0xFF0E1116),
)

/** One-thumb: cool grey ground, white control panel, blue accent. */
private val OneThumbLightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondaryContainer = Color(0xFFEEF2F7),
    onSecondaryContainer = Color(0xFF1F2937),
    background = Color(0xFFEEF2F7),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFEEF2F7),
    onSurface = Color(0xFF111827),
    onSurfaceVariant = Color(0xFF4B5563),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F8FB),
    surfaceContainer = Color(0xFFE8EDF4),
    surfaceContainerHigh = Color(0xFFDDE4EE),
    surfaceContainerHighest = Color(0xFFD3DBE6),
    outline = Color(0xFF9CA3AF),
    outlineVariant = Color(0xFFD1D5DB),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFFB91C1C),
    inverseSurface = Color(0xFF111827),
    inverseOnSurface = Color(0xFFFFFFFF),
)

private val OneThumbDarkColors = darkColorScheme(
    primary = Color(0xFF6E9BFF),
    onPrimary = Color(0xFF0A1B3D),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBE5FF),
    secondaryContainer = Color(0xFF252E3A),
    onSecondaryContainer = Color(0xFFE5EAF1),
    background = Color(0xFF0F141B),
    onBackground = Color(0xFFE5EAF1),
    surface = Color(0xFF0F141B),
    onSurface = Color(0xFFE5EAF1),
    onSurfaceVariant = Color(0xFFA3ADBB),
    surfaceContainerLowest = Color(0xFF1A212B),
    surfaceContainerLow = Color(0xFF161C25),
    surfaceContainer = Color(0xFF1C232D),
    surfaceContainerHigh = Color(0xFF242C38),
    surfaceContainerHighest = Color(0xFF2E3744),
    outline = Color(0xFF5B6575),
    outlineVariant = Color(0xFF2E3744),
    errorContainer = Color(0xFF3B1414),
    onErrorContainer = Color(0xFFFCA5A5),
    inverseSurface = Color(0xFFE5EAF1),
    inverseOnSurface = Color(0xFF0F141B),
)

/** The colors for [layout]; sheets and dialogs over the remote use them too, so they match it. */
@Composable
fun remoteColorScheme(layout: RemoteLayout): ColorScheme = when {
    layout == RemoteLayout.Touchpad -> TouchpadColors
    isSystemInDarkTheme() -> OneThumbDarkColors
    else -> OneThumbLightColors
}

@Composable
fun RemoteLayoutTheme(layout: RemoteLayout, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = layout == RemoteLayout.Touchpad || systemDark

    // The touchpad is dark even in light mode, so the status and nav bar icons have to follow the layout, not the system.
    val view = LocalView.current
    DisposableEffect(dark) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = !dark
        controller?.isAppearanceLightNavigationBars = !dark
        onDispose {
            controller?.isAppearanceLightStatusBars = !systemDark
            controller?.isAppearanceLightNavigationBars = !systemDark
        }
    }

    MaterialTheme(colorScheme = remoteColorScheme(layout), typography = MaterialTheme.typography, content = content)
}
