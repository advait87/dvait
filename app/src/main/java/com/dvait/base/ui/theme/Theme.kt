package com.dvait.base.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.dvait.base.util.DynamicIconManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** CompositionLocal to expose the current accent palette anywhere in the tree. */
val LocalAccentPalette = compositionLocalOf { OrangePalette }

private fun buildLightScheme(palette: AccentPalette) = lightColorScheme(
    primary = palette.primary,
    onPrimary = palette.onPrimary,
    primaryContainer = palette.primaryMuted,
    secondary = palette.secondary,
    onSecondary = Bg,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    error = Error,
    outline = Border,
    outlineVariant = Divider,
    surfaceContainerHigh = Surface3,
    surfaceContainerHighest = SurfaceHover
)

private fun buildDarkScheme(palette: AccentPalette) = darkColorScheme(
    primary = palette.darkPrimary ?: palette.primary,
    onPrimary = palette.darkOnPrimary,
    primaryContainer = palette.primaryMuted,
    secondary = palette.darkSecondary ?: palette.secondary,
    onSecondary = DarkBg,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface1,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurface2,
    onSurfaceVariant = DarkTextSecondary,
    error = Error,
    outline = DarkBorder,
    outlineVariant = DarkDivider,
    surfaceContainerHigh = DarkSurface3,
    surfaceContainerHighest = DarkSurfaceHover
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun DvaitTheme(
    appTheme: String = "system",
    accentColor: String = "orange",
    content: @Composable () -> Unit
) {
    val palette = getAccentPalette(accentColor)
    val context = LocalContext.current


    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val darkTheme = when (appTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme
    }
    val colorScheme = if (darkTheme) buildDarkScheme(palette) else buildLightScheme(palette)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalAccentPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}

/** Helper to get the logo resource based on the current accent palette. */
fun getLogoForAccent(palette: AccentPalette): Int {
    return when (palette) {
        TealPalette -> com.dvait.base.R.drawable.dvait_logo_green
        BluePalette -> com.dvait.base.R.drawable.dvait_logo_blue
        MonoPalette -> com.dvait.base.R.drawable.dvait_logo_mono
        else -> com.dvait.base.R.drawable.dvait_logo_orange
    }
}
