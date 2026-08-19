package com.callflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(primary = Color(0xFF195C46), secondary = Color(0xFF4C635A), surface = Color(0xFFF8FAF7))
private val DarkColors = darkColorScheme(primary = Color(0xFF8BD5B7), secondary = Color(0xFFB3CCC1))

@Composable
fun CallFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
