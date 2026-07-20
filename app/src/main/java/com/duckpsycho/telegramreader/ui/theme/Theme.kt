package com.duckpsycho.telegramreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemePreference {
    System,
    Light,
    Dark,
}

@Immutable
data class ReaderColors(
    val bg: Color,
    val bgElevated: Color,
    val text: Color,
    val textStrong: Color,
    val textMuted: Color,
    val border: Color,
    val hover: Color,
    val active: Color,
    val overlay: Color,
    val fabBg: Color,
    val fabText: Color,
    val error: Color,
    val blockquoteAccent: Color,
    val blockquoteBg: Color,
    val radius: Dp = 8.dp,
    val radiusLg: Dp = 12.dp,
)

private val LightReaderColors = ReaderColors(
    bg = LightBg,
    bgElevated = LightBgElevated,
    text = LightText,
    textStrong = LightTextStrong,
    textMuted = LightTextMuted,
    border = LightBorder,
    hover = LightHover,
    active = LightActive,
    overlay = Overlay,
    fabBg = LightFabBg,
    fabText = LightFabText,
    error = LightError,
    blockquoteAccent = LightBlockquoteAccent,
    blockquoteBg = LightBlockquoteBg,
)

private val DarkReaderColors = ReaderColors(
    bg = DarkBg,
    bgElevated = DarkBgElevated,
    text = DarkText,
    textStrong = DarkTextStrong,
    textMuted = DarkTextMuted,
    border = DarkBorder,
    hover = DarkHover,
    active = DarkActive,
    overlay = OverlayDark,
    fabBg = DarkFabBg,
    fabText = DarkFabText,
    error = DarkError,
    blockquoteAccent = DarkBlockquoteAccent,
    blockquoteBg = DarkBlockquoteBg,
)

val LocalReaderColors = staticCompositionLocalOf { LightReaderColors }

private val LightScheme = lightColorScheme(
    primary = LightTextStrong,
    onPrimary = LightBgElevated,
    secondary = LightTextMuted,
    onSecondary = LightBgElevated,
    background = LightBg,
    onBackground = LightTextStrong,
    surface = LightBgElevated,
    onSurface = LightTextStrong,
    surfaceVariant = LightBg,
    onSurfaceVariant = LightText,
    outline = LightBorder,
    error = LightError,
    onError = LightBgElevated,
)

private val DarkScheme = darkColorScheme(
    primary = DarkTextStrong,
    onPrimary = DarkBg,
    secondary = DarkTextMuted,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = DarkTextStrong,
    surface = DarkBgElevated,
    onSurface = DarkTextStrong,
    surfaceVariant = DarkBg,
    onSurfaceVariant = DarkText,
    outline = DarkBorder,
    error = DarkError,
    onError = DarkBg,
)

@Composable
fun TelegramReaderTheme(
    preference: ThemePreference = ThemePreference.System,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (preference) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
    val readerColors = if (darkTheme) DarkReaderColors else LightReaderColors
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalReaderColors provides readerColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}

object ReaderTheme {
    val colors: ReaderColors
        @Composable
        get() = LocalReaderColors.current
}
