package com.uniquelo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val Bone = Color(0xFFF3EFE6)
    val Paper = Color(0xFFFBF9F3)
    val Ink = Color(0xFF15150F)
    val Muted = Color(0xFF6E6A5E)
    val Line = Color(0xFFD9D3C4)
    val Sand = Color(0xFFE7E0CF)
    val Lime = Color(0xFFDDFF57)
    val Rust = Color(0xFFC4552D)
    val Cobalt = Color(0xFF2447E0)
    val Olive = Color(0xFF2F3A2B)
}

private val UniqueLoColorScheme = lightColorScheme(
    primary = Palette.Ink,
    onPrimary = Palette.Paper,
    primaryContainer = Palette.Lime,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Rust,
    onSecondary = Palette.Paper,
    secondaryContainer = Palette.Sand,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Cobalt,
    onTertiary = Palette.Paper,
    background = Palette.Bone,
    onBackground = Palette.Ink,
    surface = Palette.Paper,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.Sand,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Color(0xFFB3261E),
    onError = Palette.Paper,
)

private val UniqueLoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    ),
)

private val UniqueLoShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun UniqueLoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UniqueLoColorScheme,
        typography = UniqueLoTypography,
        shapes = UniqueLoShapes,
        content = content,
    )
}
