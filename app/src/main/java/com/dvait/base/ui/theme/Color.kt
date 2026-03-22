package com.dvait.base.ui.theme

import androidx.compose.ui.graphics.Color

val LinkBlue = Color(0xFF3B82F6) // Modern vibrant blue

// Base palette — Light theme (white/light gray)
val Bg = Color(0xFFFFFFFF)
val BgElevated = Color(0xFFF9F9FB)
val Surface1 = Color(0xFFF3F3F6)
val Surface2 = Color(0xFFEBEBEF)
val Surface3 = Color(0xFFE2E2E8)
val SurfaceHover = Color(0xFFDCDCE2)

// Semantic
val Success = Color(0xFF10B981)
val Warning = Color(0xFFFFAA55)
val Error = Color(0xFFEF4444)
val ErrorMuted = Color(0xFFB91C1C)

// --- Light Theme Specific ---
val TextPrimary = Color(0xFF111111)     // Almost black
val TextSecondary = Color(0xFF555555)   // Dark gray
val TextMuted = Color(0xFF888888)       // Medium gray
val TextOnPrimary = Color(0xFF111111)   // Dark text on primary
val Divider = Color(0xFFE5E5E5)
val Border = Color(0xFFD4D4D4)

// --- Dark Theme Specific ---
val DarkBg = Color(0xFF000000)
val DarkBgElevated = Color(0xFF000000)
val DarkSurface1 = Color(0xFF050505)
val DarkSurface2 = Color(0xFF2C2C2C)
val DarkSurface3 = Color(0xFF383838)
val DarkSurfaceHover = Color(0xFF424242)

val DarkTextPrimary = Color(0xFFFFFFFF)
val DarkTextSecondary = Color(0xFFAAAAAA)
val DarkTextMuted = Color(0xFF777777)
val DarkTextOnPrimary = Color(0xFF111111) // Keep dark text on bright primary buttons
val DarkDivider = Color(0xFF333333)
val DarkBorder = Color(0xFF444444)

// ═══════════════════════════════════════════
// Accent Color Palettes
// ═══════════════════════════════════════════

data class AccentPalette(
    val primary: Color,
    val primaryMuted: Color,
    val primarySubtle: Color,
    val secondary: Color,
    val secondaryMuted: Color,
    val userBubble: Color,
    val onPrimary: Color = Color(0xFF111111),
    val darkOnPrimary: Color = Color(0xFF111111),
    val darkPrimary: Color? = null,
    val darkSecondary: Color? = null
)

val OrangePalette = AccentPalette(
    primary = Color(0xFFFFAA55),
    primaryMuted = Color(0xFFCC6600),
    primarySubtle = Color(0xFFFFE0B2),
    secondary = Color(0xFFFF9800),
    secondaryMuted = Color(0xFFF57C00),
    userBubble = Color(0xFFFFAA55)
)

val TealPalette = AccentPalette(
    primary = Color(0xFF2DD4A8),
    primaryMuted = Color(0xFF0D9373),
    primarySubtle = Color(0xFFB2EDDD),
    secondary = Color(0xFF14B88A),
    secondaryMuted = Color(0xFF0B8A65),
    userBubble = Color(0xFF2DD4A8)
)

val BluePalette = AccentPalette(
    primary = Color(0xFF60A5FA),
    primaryMuted = Color(0xFF2563EB),
    primarySubtle = Color(0xFFBBDEFB),
    secondary = Color(0xFF3B82F6),
    secondaryMuted = Color(0xFF1D4ED8),
    userBubble = Color(0xFF60A5FA)
)

val MonoPalette = AccentPalette(
    primary = Color.Black, // True black in light theme as requested
    primaryMuted = Color(0xFF666666),
    primarySubtle = Color(0xFFE0E0E0),
    secondary = Color(0xFF666666),
    secondaryMuted = Color(0xFF888888),
    userBubble = Color(0xFF999999),
    onPrimary = Color(0xFFFFFFFF),
    darkOnPrimary = Color(0xFF111111),
    darkPrimary = Color(0xFFEEEEEE), // Near white in dark theme
    darkSecondary = Color(0xFFCCCCCC)
)

fun getAccentPalette(accentName: String): AccentPalette = when (accentName) {
    "teal" -> TealPalette
    "blue" -> BluePalette
    "mono" -> MonoPalette
    else -> OrangePalette
}

// Legacy aliases — these point to the orange defaults for backward compat
// They are used in a few places; the dynamic theme overrides them at the MaterialTheme level.

val AssistantBubbleColor = Color(0xFFF3F3F6) // Light gray bubble

val AccentRose = Color(0xFFF472B6)
// val UserBubble = UserBubbleColor - REMOVED
val AssistantBubble = AssistantBubbleColor
