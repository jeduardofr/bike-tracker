package com.biketracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = BikeGreen,
    onPrimary = Color.White,
    primaryContainer = BikeGreenLight,
    secondary = BikeOrange,
    background = SurfaceLight,
    surface = Color.White,
    onBackground = OnSurface,
    onSurface = OnSurface
)

private val DarkColors = darkColorScheme(
    primary = BikeGreenLight,
    onPrimary = Color.Black,
    secondary = BikeOrange
)

@Composable
fun BikeTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
