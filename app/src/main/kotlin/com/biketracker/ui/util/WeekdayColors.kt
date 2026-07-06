package com.biketracker.ui.util

import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate

// One vivid hue per commute weekday, spread across the wheel so the
// polylines stay tellable-apart on the dark map style
fun weekdayColor(day: DayOfWeek): Color = when (day) {
    DayOfWeek.MONDAY -> Color(0xFF00C853)    // vivid green
    DayOfWeek.TUESDAY -> Color(0xFF448AFF)   // strong blue
    DayOfWeek.WEDNESDAY -> Color(0xFFFFD600) // bright yellow
    DayOfWeek.THURSDAY -> Color(0xFFE040FB)  // magenta
    DayOfWeek.FRIDAY -> Color(0xFF00E5FF)    // cyan
    else -> Color(0xFF9E9E9E)                // weekend fallback
}

fun weekdayColor(isoDate: String): Color = try {
    weekdayColor(LocalDate.parse(isoDate).dayOfWeek)
} catch (_: Exception) {
    Color(0xFF9E9E9E)
}
