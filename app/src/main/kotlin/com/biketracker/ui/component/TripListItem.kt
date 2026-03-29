package com.biketracker.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.biketracker.domain.model.Trip
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@Composable
fun TripListItem(trip: Trip, onClick: () -> Unit) {
    val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val startFormatted = dateFormat.format(Date(trip.startTime))
    val durationMin = trip.endTime?.let { ((it - trip.startTime) / 60_000).toInt() } ?: 0

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text("${trip.direction.name.replace("_", " → ")}") },
        supportingContent = { Text("$startFormatted · ${durationMin} min · ${"%.1f".format(trip.distanceMeters / 1000f)} km") },
        trailingContent = { Text("${"%.1f".format(trip.averageSpeedKmh)} km/h") }
    )
}
