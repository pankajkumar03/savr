package com.savr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand
val Olive = Color(0xFF6B7A3A)
val OliveDark = Color(0xFF4F5B2A)
val OliveTint = Color(0xFFE8ECD8)

// iOS system colours (UIKit values), used for icon badges and semantic accents
object Sys {
    val Blue = Color(0xFF007AFF); val Green = Color(0xFF34C759); val Orange = Color(0xFFFF9500); val Red = Color(0xFFFF3B30)
    val Purple = Color(0xFFAF52DE); val Pink = Color(0xFFFF2D55); val Indigo = Color(0xFF5856D6); val Teal = Color(0xFF30B0C7)
    val Yellow = Color(0xFFFFCC00); val Gray = Color(0xFF8E8E93)
}
val ErrorRed = Sys.Red
val Secondary = Color(0xFF8E8E93)

/** iOS semantic colours (systemGroupedBackground, label, separator...) for the current appearance. */
@Immutable
data class IosColors(
    val bg: Color,          // systemGroupedBackground: page background
    val card: Color,        // secondarySystemGroupedBackground: grouped cells / cards
    val elevated: Color,    // sheets, alerts
    val fill: Color,        // tertiarySystemFill: text fields, segmented track, tinted-gray buttons
    val label: Color, val secondary: Color, val tertiary: Color,
    val separator: Color,
    val bar: Color,         // translucent nav / tab bar
    val tint: Color, val tintSoft: Color,
    val thumb: Color,       // segmented-control thumb
    val dark: Boolean,
)

private val LightIos = IosColors(
    bg = Color(0xFFF2F2F7), card = Color.White, elevated = Color.White, fill = Color(0x1F767680),
    label = Color.Black, secondary = Color(0x993C3C43), tertiary = Color(0x4D3C3C43), separator = Color(0x4A3C3C43),
    bar = Color(0xFAF9F9F9), tint = Olive, tintSoft = Color(0x1F6B7A3A), thumb = Color.White, dark = false)
private val DarkIos = IosColors(
    bg = Color.Black, card = Color(0xFF1C1C1E), elevated = Color(0xFF2C2C2E), fill = Color(0x3D767680),
    label = Color.White, secondary = Color(0x99EBEBF5), tertiary = Color(0x4DEBEBF5), separator = Color(0x99545458),
    bar = Color(0xFA1C1C1E), tint = Color(0xFFA7BC6E), tintSoft = Color(0x29A7BC6E), thumb = Color(0xFF636366), dark = true)

val LocalIos = staticCompositionLocalOf { LightIos }
val LocalIsDark = compositionLocalOf { false }
val Ios: IosColors @Composable @ReadOnlyComposable get() = LocalIos.current

/** iOS Dynamic Type sizes (large / default), with Apple's tracking. */
object IosType {
    private val F = FontFamily.SansSerif
    val LargeTitle = TextStyle(fontFamily = F, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.37.sp)
    val Title1 = TextStyle(fontFamily = F, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.36.sp)
    val Title2 = TextStyle(fontFamily = F, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.35.sp)
    val Title3 = TextStyle(fontFamily = F, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.38.sp)
    val Headline = TextStyle(fontFamily = F, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.41).sp)
    val Body = TextStyle(fontFamily = F, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp)
    val Callout = TextStyle(fontFamily = F, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.32).sp)
    val Subhead = TextStyle(fontFamily = F, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp)
    val Footnote = TextStyle(fontFamily = F, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp)
    val Caption = TextStyle(fontFamily = F, fontSize = 12.sp, lineHeight = 16.sp)
    val Caption2 = TextStyle(fontFamily = F, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.07.sp)
}

private val Type = Typography(
    headlineMedium = IosType.LargeTitle, titleLarge = IosType.Headline, titleMedium = IosType.Headline,
    bodyLarge = IosType.Body, bodyMedium = IosType.Subhead, bodySmall = IosType.Footnote, labelLarge = IosType.Headline)

@Composable
fun SavrTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val ios = if (dark) DarkIos else LightIos
    // Material components still used (sheets, text fields, swipe boxes) are mapped onto the iOS palette
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = ios.tint, onPrimary = Color.White, primaryContainer = ios.tintSoft, onPrimaryContainer = ios.tint,
        background = ios.bg, surface = ios.card, surfaceVariant = ios.fill, surfaceContainerLow = ios.elevated,
        onBackground = ios.label, onSurface = ios.label, onSurfaceVariant = ios.secondary, error = Sys.Red,
        outlineVariant = ios.separator, outline = ios.separator)
    CompositionLocalProvider(LocalIsDark provides dark, LocalIos provides ios) {
        // Default text/icon colour for screens not wrapped in a Scaffold/Surface: otherwise black-on-black in dark mode
        MaterialTheme(colorScheme = scheme, typography = Type) {
            CompositionLocalProvider(LocalContentColor provides ios.label, content = content) }
    }
}
