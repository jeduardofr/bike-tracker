package com.biketracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = BikeGreen,
    onPrimary = Color.White,
    primaryContainer = BikeGreenDark,
    onPrimaryContainer = BikeGreenLight,
    secondary = BikeOrange,
    background = BackgroundBlack,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceDarker,
    onSurfaceVariant = OnSurfaceMuted
)

@Composable
fun BikeTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
