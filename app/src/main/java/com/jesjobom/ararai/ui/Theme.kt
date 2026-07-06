package com.jesjobom.ararai.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArarAiColorScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF5BD6A3),
    onPrimary = Color(0xFF052116),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF241A00),
    tertiary = Color(0xFF86B7FF),
    background = Color(0xFF101418),
    surface = Color(0xFF171C21),
    surfaceVariant = Color(0xFF252C33),
    onBackground = Color(0xFFE5E9ED),
    onSurface = Color(0xFFE5E9ED),
    onSurfaceVariant = Color(0xFFBAC3CC),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF330000),
)

@Composable
fun ArarAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArarAiColorScheme,
        content = content,
    )
}
