package com.callflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Navy = Color(0xFFF8FAFC)
val Indigo = Color(0xFF6366F1)
val IndigoSoft = Color(0xFF1E2540)
val Emerald = Color(0xFF10B981)
val WarmBackground = Color(0xFF0D111A)
val Slate = Color(0xFF94A3B8)
val Border = Color(0xFF263043)

private val CallFlowColors = darkColorScheme(
    primary = Indigo, onPrimary = Color(0xFFFFFFFF), primaryContainer = IndigoSoft, onPrimaryContainer = Color(0xFFA5B4FC),
    secondary = Indigo, onSecondary = Color(0xFFFFFFFF), secondaryContainer = IndigoSoft, onSecondaryContainer = Color(0xFFA5B4FC),
    tertiary = Emerald, onTertiary = Color(0xFF022C22), tertiaryContainer = Color(0xFF064E3B), onTertiaryContainer = Color(0xFF6EE7B7),
    background = WarmBackground, onBackground = Navy, surface = Color(0xFF151B26), onSurface = Navy,
    surfaceVariant = Color(0xFF1E2536), onSurfaceVariant = Slate, outline = Border, outlineVariant = Color(0xFF232B3C),
    surfaceDim = WarmBackground, surfaceBright = Color(0xFF273248),
    surfaceContainerLowest = Color(0xFF0B0E14), surfaceContainerLow = Color(0xFF111622),
    surfaceContainer = Color(0xFF151B26), surfaceContainerHigh = Color(0xFF1E2536), surfaceContainerHighest = Color(0xFF273248),
    error = Color(0xFFFB7185), onError = Color(0xFF4C0519), errorContainer = Color(0xFF881337), onErrorContainer = Color(0xFFFFE4E6),
)

private val CallFlowTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
)
private val CallFlowShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CallFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CallFlowColors, typography = CallFlowTypography, shapes = CallFlowShapes) {
        androidx.compose.material3.Surface(color = WarmBackground, contentColor = Navy, content = content)
    }
}
