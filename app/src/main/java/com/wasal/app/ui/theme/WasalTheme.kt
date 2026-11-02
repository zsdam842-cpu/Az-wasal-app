package com.wasal.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ألوان الهوية: كحلي داكن + أزرق مميز، بوضعين داكن وفاتح.
private val WasalNavy = Color(0xFF001636)
private val WasalCardDark = Color(0xFF001636)
private val WasalAccent = Color(0xFF2BA0FF)
private val WasalTextLight = Color(0xFFEAF3FF)
private val WasalMuted = Color(0xFF8FA9C9)

private val LightBackground = Color(0xFFF4F6FB)
private val LightCard = Color(0xFFFFFFFF)
private val LightText = Color(0xFF001636)
private val LightMuted = Color(0xFF5B6B8C)

private val DarkScheme = darkColorScheme(
    primary = WasalAccent,
    onPrimary = Color.White,
    background = Color(0xFF000E26),
    onBackground = WasalTextLight,
    surface = WasalNavy,
    onSurface = WasalTextLight,
    surfaceVariant = WasalCardDark,
    onSurfaceVariant = WasalMuted,
    secondary = WasalAccent,
    secondaryContainer = WasalCardDark,
    primaryContainer = WasalCardDark,
    onPrimaryContainer = WasalTextLight
)

private val LightScheme = lightColorScheme(
    primary = WasalAccent,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightMuted,
    secondary = WasalAccent,
    secondaryContainer = LightCard,
    primaryContainer = Color(0xFFDDE5FF),
    onPrimaryContainer = LightText
)

/**
 * ثيم وصال: داكن أو فاتح حسب اختيار المستخدم.
 * useSystemDefault = true يعتمد إعداد النظام.
 */
@Composable
fun WasalTheme(
    darkMode: Boolean = true,
    useSystemDefault: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = if (useSystemDefault) isSystemInDarkTheme() else darkMode

    MaterialTheme(
        colorScheme = if (useDark) DarkScheme else LightScheme,
        content = content
    )
}

/** ألوان عامة يستخدمها شريط التبويب والرأس. */
object WasalPalette {
    val Navy = WasalNavy
    val Accent = WasalAccent
    val Muted = WasalMuted
    val DarkBackground = Color(0xFF000E26)
    val DarkCard = WasalCardDark
    val DarkText = WasalTextLight
    val LightBg = LightBackground
    val LightCardColor = LightCard
    val LightTextColor = LightText
}
