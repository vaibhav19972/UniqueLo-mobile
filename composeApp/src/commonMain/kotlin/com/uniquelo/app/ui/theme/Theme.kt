package com.uniquelo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uniquelo.app.model.ThemeMode
import com.uniquelo.app.resources.Res
import com.uniquelo.app.resources.satoshi_black
import com.uniquelo.app.resources.satoshi_bold
import com.uniquelo.app.resources.satoshi_light
import com.uniquelo.app.resources.satoshi_medium
import com.uniquelo.app.resources.satoshi_regular
import com.uniquelo.app.ui.SystemBarsEffect
import org.jetbrains.compose.resources.Font as ResourceFont

/**
 * Phase 3 palette: the Uniqlo-inspired shell is paper/ink with one red accent.
 * `Rust` keeps its historical property name but is now the brand red; `Lime`
 * is the pale highlight surface. Universe colours stay in the catalogue data
 * and only tint universe cards/artwork, never the app chrome.
 */
@Immutable
data class UniqueLoColors(
    val Bone: Color,
    val Paper: Color,
    val Ink: Color,
    val Muted: Color,
    val Line: Color,
    val ControlLine: Color,
    val Sand: Color,
    val Lime: Color,
    val Rust: Color,
    val Cobalt: Color,
    val Olive: Color,
    val OnAccent: Color,
    val ImageScrim: Color,
    val OnImage: Color,
    val InverseSurface: Color,
    val OnInverse: Color,
    val InverseAccent: Color,
)

internal val LightUniqueLoColors = UniqueLoColors(
    Bone = Color(0xFFF7F7F5),
    Paper = Color(0xFFFFFFFF),
    Ink = Color(0xFF111111),
    Muted = Color(0xFF6B6B6B),
    Line = Color(0xFFE3E3E0),
    ControlLine = Color(0xFF767676),
    Sand = Color(0xFFF0F0ED),
    Lime = Color(0xFFFFE9E9),
    Rust = Color(0xFFD5001C),
    Cobalt = Color(0xFF0B57D0),
    Olive = Color(0xFF2F3A2B),
    OnAccent = Color(0xFFFFFFFF),
    ImageScrim = Color(0xCC000000),
    OnImage = Color(0xFFFFFFFF),
    InverseSurface = Color(0xFF111111),
    OnInverse = Color(0xFFFFFFFF),
    InverseAccent = Color(0xFFFF6B73),
)

internal val DarkUniqueLoColors = UniqueLoColors(
    Bone = Color(0xFF0B0B0B),
    Paper = Color(0xFF151515),
    Ink = Color(0xFFF4F4F2),
    Muted = Color(0xFFA6A6A2),
    Line = Color(0xFF2A2A2A),
    ControlLine = Color(0xFF767676),
    Sand = Color(0xFF1E1E1E),
    Lime = Color(0xFF3A1518),
    Rust = Color(0xFFFF4D57),
    Cobalt = Color(0xFF7EA6FF),
    Olive = Color(0xFF3A3A3A),
    OnAccent = Color(0xFF0B0B0B),
    ImageScrim = Color(0xCC000000),
    OnImage = Color(0xFFFFFFFF),
    InverseSurface = Color(0xFF111111),
    OnInverse = Color(0xFFFFFFFF),
    InverseAccent = Color(0xFFFF6B73),
)

internal val LocalUniqueLoColors = staticCompositionLocalOf { LightUniqueLoColors }

/**
 * Theme-aware semantic colours. Keeping the `Palette.X` call shape means the
 * existing screens switch between light and dark without per-screen plumbing;
 * new UI should still prefer `MaterialTheme.colorScheme` where it maps cleanly.
 */
val Palette: UniqueLoColors
    @Composable get() = LocalUniqueLoColors.current

@Composable
private fun satoshiFamily(): FontFamily = FontFamily(
    ResourceFont(Res.font.satoshi_light, FontWeight.Light),
    ResourceFont(Res.font.satoshi_regular, FontWeight.Normal),
    ResourceFont(Res.font.satoshi_medium, FontWeight.Medium),
    ResourceFont(Res.font.satoshi_bold, FontWeight.Bold),
    ResourceFont(Res.font.satoshi_black, FontWeight.Black),
)

private fun uniqueLoTypography(family: FontFamily): Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        lineHeight = 43.sp,
        letterSpacing = (-1.1).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp,
    ),
)

private val UniqueLoShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

private fun scheme(colors: UniqueLoColors, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = colors.Ink,
            onPrimary = colors.Paper,
            primaryContainer = colors.Lime,
            onPrimaryContainer = colors.Ink,
            secondary = colors.Rust,
            onSecondary = colors.OnAccent,
            secondaryContainer = colors.Sand,
            onSecondaryContainer = colors.Ink,
            tertiary = colors.Cobalt,
            onTertiary = colors.OnAccent,
            background = colors.Bone,
            onBackground = colors.Ink,
            surface = colors.Paper,
            onSurface = colors.Ink,
            surfaceVariant = colors.Sand,
            onSurfaceVariant = colors.Muted,
            outline = colors.Line,
            error = colors.Rust,
            onError = colors.OnAccent,
        )
    } else {
        lightColorScheme(
            primary = colors.Ink,
            onPrimary = colors.Paper,
            primaryContainer = colors.Lime,
            onPrimaryContainer = colors.Ink,
            secondary = colors.Rust,
            onSecondary = colors.OnAccent,
            secondaryContainer = colors.Sand,
            onSecondaryContainer = colors.Ink,
            tertiary = colors.Cobalt,
            onTertiary = colors.OnAccent,
            background = colors.Bone,
            onBackground = colors.Ink,
            surface = colors.Paper,
            onSurface = colors.Ink,
            surfaceVariant = colors.Sand,
            onSurfaceVariant = colors.Muted,
            outline = colors.Line,
            error = colors.Rust,
            onError = colors.OnAccent,
        )
    }

@Composable
fun UniqueLoTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val colors = if (dark) DarkUniqueLoColors else LightUniqueLoColors

    CompositionLocalProvider(LocalUniqueLoColors provides colors) {
        SystemBarsEffect(
            darkTheme = dark,
            followSystem = themeMode == ThemeMode.System,
        )
        MaterialTheme(
            colorScheme = scheme(colors, dark),
            typography = uniqueLoTypography(satoshiFamily()),
            shapes = UniqueLoShapes,
            content = content,
        )
    }
}
