package com.callflow.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF0F172A)
val Indigo = Color(0xFF6366F1)
val IndigoSoft = Color(0xFFEEF0FF)
val Emerald = Color(0xFF10B981)
val WarmBackground = Color(0xFFF8FAFC)
val Slate = Color(0xFF64748B)
val Border = Color(0xFFE8EAF0)

private val CallFlowColors = lightColorScheme(
    primary = Indigo, onPrimary = Color.White, primaryContainer = IndigoSoft, onPrimaryContainer = Navy,
    secondary = Navy, onSecondary = Color.White, tertiary = Emerald, background = WarmBackground,
    onBackground = Navy, surface = Color.White, onSurface = Navy, surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Slate, outline = Border, error = Color(0xFFEF4444),
)

private val CallFlowTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 31.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun CallFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CallFlowColors, typography = CallFlowTypography, content = content)
}
