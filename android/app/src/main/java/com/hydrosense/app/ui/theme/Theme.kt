package com.hydrosense.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hydrosense.app.R

// Same palette as the HydroSense web app.
val Navy950 = Color(0xFF071A33)
val Navy900 = Color(0xFF0B2545)
val Navy800 = Color(0xFF123459)
val Aqua400 = Color(0xFF35B6C9)
val Aqua600 = Color(0xFF0D7C96)
val Aqua50 = Color(0xFFEEF8FB)
val Mist = Color(0xFFF4F8FA)
val Line = Color(0xFFDFE8EE)
val Ink = Color(0xFF10263D)
val InkSoft = Color(0xFF46596B)

/** Colours for monitoring states. Always shown with an icon and a text label too. */
data class StatusColors(
    val ok: Color, val okSoft: Color, val watch: Color, val watchSoft: Color,
    val change: Color, val changeSoft: Color, val danger: Color, val dangerSoft: Color,
    val idle: Color, val idleSoft: Color, val chartLine: Color, val chartGrid: Color,
)

private val LightStatus = StatusColors(
    ok = Color(0xFF1C8F5F), okSoft = Color(0xFFE6F5EE), watch = Color(0xFFB07A00), watchSoft = Color(0xFFFDF3D7),
    change = Color(0xFFC8561B), changeSoft = Color(0xFFFDEBDF), danger = Color(0xFFC53030), dangerSoft = Color(0xFFFDE8E8),
    idle = Color(0xFF5C6F80), idleSoft = Color(0xFFEDF1F4), chartLine = Aqua600, chartGrid = Color(0xFFE6EDF2),
)
private val DarkStatus = StatusColors(
    ok = Color(0xFF4CC38A), okSoft = Color(0xFF12352A), watch = Color(0xFFF0C24B), watchSoft = Color(0xFF3A2F10),
    change = Color(0xFFF08A52), changeSoft = Color(0xFF3D2314), danger = Color(0xFFF07070), dangerSoft = Color(0xFF3F1B1B),
    idle = Color(0xFF9DB1C2), idleSoft = Color(0xFF1B2F47), chartLine = Aqua400, chartGrid = Color(0xFF21395A),
)
val LocalStatusColors = staticCompositionLocalOf { LightStatus }

private val LightColors = lightColorScheme(
    primary = Aqua600, onPrimary = Color.White, primaryContainer = Color(0xFFD7EFF5), onPrimaryContainer = Navy900,
    secondary = Navy900, onSecondary = Color.White, secondaryContainer = Aqua50, onSecondaryContainer = Navy900,
    tertiary = Color(0xFF1C8F5F), background = Mist, onBackground = Ink, surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFEDF1F4), onSurfaceVariant = InkSoft, outline = Color(0xFFB9C7D2), outlineVariant = Line,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHighest = Color(0xFFEDF1F4), error = Color(0xFFC53030),
)
private val DarkColors = darkColorScheme(
    primary = Aqua400, onPrimary = Navy950, primaryContainer = Color(0xFF0F4A5C), onPrimaryContainer = Color(0xFFD7EFF5),
    secondary = Color(0xFFD7EFF5), onSecondary = Navy950, secondaryContainer = Navy800, onSecondaryContainer = Color(0xFFD7EFF5),
    tertiary = Color(0xFF4CC38A), background = Navy950, onBackground = Color(0xFFE8F0F6), surface = Navy900, onSurface = Color(0xFFE8F0F6),
    surfaceVariant = Navy800, onSurfaceVariant = Color(0xFFA9BDCC), outline = Color(0xFF4A6582), outlineVariant = Color(0xFF21395A),
    surfaceContainer = Navy900, surfaceContainerHigh = Navy900, surfaceContainerLow = Navy900,
    surfaceContainerHighest = Navy800, error = Color(0xFFF07070),
)

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(R.font.manrope, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, weight: Int, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = Manrope, fontSize = size.sp, fontWeight = FontWeight(weight), lineHeight = line.sp, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    displaySmall = style(34, 800, 40, -0.5),
    headlineLarge = style(30, 800, 36, -0.4),
    headlineMedium = style(25, 800, 31, -0.3),
    headlineSmall = style(21, 700, 27, -0.2),
    titleLarge = style(19, 700, 25, -0.1),
    titleMedium = style(16, 700, 22),
    titleSmall = style(14, 700, 20),
    bodyLarge = style(16, 500, 24),
    bodyMedium = style(14, 500, 21),
    bodySmall = style(12, 500, 17),
    labelLarge = style(14, 700, 20),
    labelMedium = style(12, 700, 16),
    labelSmall = style(11, 700, 15, 0.6),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun HydroSenseTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
