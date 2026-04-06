package com.biketracker.ui.screen.weekly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.R
import com.biketracker.ui.component.StatCard
import com.biketracker.ui.theme.SurfaceDark
import com.biketracker.ui.util.buildSegments
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@Composable
fun WeeklyScreen(viewModel: WeeklyViewModel = hiltViewModel()) {
    val stats by viewModel.weeklyStats.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "This Week",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        val allPoints = stats?.days?.flatMap { day ->
            day.trips.flatMap { trip -> trip.routePoints.map { LatLng(it.latitude, it.longitude) } }
        } ?: emptyList()

        val cameraPositionState = rememberCameraPositionState {
            if (allPoints.isNotEmpty()) position = CameraPosition.fromLatLngZoom(allPoints.first(), 13f)
        }

        // Re-center when data loads
        LaunchedEffect(allPoints.firstOrNull()) {
            allPoints.firstOrNull()?.let {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 13f)
            }
        }

        val darkStyle = MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.map_style_dark)
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapStyleOptions = darkStyle)
        ) {
            stats?.days?.forEach { day ->
                day.trips.forEach { trip ->
                    if (trip.routePoints.size >= 2) {
                        val segments = buildSegments(trip.routePoints)
                        segments.forEach { segment ->
                            Polyline(
                                points = segment.points.map { LatLng(it.latitude, it.longitude) },
                                color = if (segment.isRiding) Color(0xFF2E7D32) else Color(0xFF7B1FA2),
                                width = 6f
                            )
                        }
                    }
                }
            }
        }

        stats?.let { ws ->
            val totalDuration = ws.days.sumOf { it.totalDurationSeconds }
            val avgSpeed = ws.days.filter { it.averageSpeedKmh > 0 }.let { activeDays ->
                if (activeDays.isNotEmpty()) activeDays.map { it.averageSpeedKmh }.average().toFloat() else 0f
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard("Distance", "%.1f".format(ws.totalDistanceMeters / 1000f), "km")
                        StatCard("Trips", ws.totalTrips.toString(), "rides")
                        StatCard("Time", formatDuration(totalDuration), "")
                        StatCard("Avg", "%.1f".format(avgSpeed), "km/h")
                    }
                }
            }

            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ws.days.filter { it.trips.isNotEmpty() }) { day ->
                    Card(
                        modifier = Modifier.width(120.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                formatDayLabel(day.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "%.1f km".format(day.totalDistanceMeters / 1000f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${day.trips.size} trip${if (day.trips.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatDuration(day.totalDurationSeconds),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "%dh %dm".format(h, m) else "%dm".format(m)
}

private fun formatDayLabel(isoDate: String): String {
    return try {
        val date = java.time.LocalDate.parse(isoDate)
        date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    } catch (_: Exception) {
        isoDate.takeLast(5)
    }
}
