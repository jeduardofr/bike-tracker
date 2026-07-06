package com.biketracker.ui.screen.weekly

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.R
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.model.RoutePoint
import com.biketracker.domain.model.WeeklyStats
import com.biketracker.ui.component.StatCard
import com.biketracker.ui.util.buildSegments
import com.biketracker.ui.util.weekdayColor
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
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

        val ws = stats
        if (ws == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            WeeklyContent(ws)
        }
    }
}

@Composable
private fun WeeklyContent(ws: WeeklyStats) {
    // segment + downsample once per data emission, not on every recomposition
    val overlays = remember(ws) {
        ws.days.flatMap { day ->
            val dayColor = weekdayColor(day.date)
            day.trips.filter { it.routePoints.size >= 2 }.flatMap { trip ->
                buildSegments(downsampleForOverview(trip.routePoints)).map { segment ->
                    MapOverlay(
                        color = dayColor,
                        isRiding = segment.isRiding,
                        points = segment.points.map { LatLng(it.latitude, it.longitude) }
                    )
                }
            }
        }
    }
    val firstPoint = overlays.firstOrNull()?.points?.firstOrNull()

    val cameraPositionState = rememberCameraPositionState {
        if (firstPoint != null) position = CameraPosition.fromLatLngZoom(firstPoint, 13f)
    }
    LaunchedEffect(firstPoint) {
        firstPoint?.let { cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 13f) }
    }

    val context = LocalContext.current
    val mapProperties = remember {
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_style_dark))
    }

    GoogleMap(
        modifier = Modifier.fillMaxWidth().height(280.dp),
        cameraPositionState = cameraPositionState,
        properties = mapProperties
    ) {
        overlays.forEach { overlay ->
            // color = weekday, dashed = walking
            Polyline(
                points = overlay.points,
                color = overlay.color,
                pattern = if (overlay.isRiding) null else listOf(Dash(18f), Gap(12f)),
                width = 8f
            )
        }
    }

    val totalDuration = ws.days.sumOf { it.totalDurationSeconds }
    val avgSpeed = ws.days.filter { it.averageSpeedKmh > 0 }.let { activeDays ->
        if (activeDays.isNotEmpty()) activeDays.map { it.averageSpeedKmh }.average().toFloat() else 0f
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            // 2×2 grid so the stats get breathing room
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard("Distance", "%.1f".format(ws.totalDistanceMeters / 1000f), "km", Modifier.weight(1f))
                StatCard("Trips", ws.totalTrips.toString(), "rides", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard("Time", formatDuration(totalDuration), "", Modifier.weight(1f))
                StatCard("Avg", "%.1f".format(avgSpeed), "km/h", Modifier.weight(1f))
            }
        }
    }

    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ws.days.filter { it.trips.isNotEmpty() }) { day ->
            Card(
                modifier = Modifier.width(132.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatDayLabel(day.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = weekdayColor(day.date)
                        )
                        // one dot per trip in the day's color; filled = to office, outlined = to home
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val dayColor = weekdayColor(day.date)
                            day.trips.sortedBy { it.startTime }.take(6).forEach { trip ->
                                val dotModifier = when (trip.direction) {
                                    TripDirection.OFFICE_TO_HOME ->
                                        Modifier.size(11.dp).border(2.dp, dayColor, CircleShape)
                                    TripDirection.FREE ->
                                        Modifier.size(11.dp).background(dayColor.copy(alpha = 0.5f), CircleShape)
                                    else ->
                                        Modifier.size(11.dp).background(dayColor, CircleShape)
                                }
                                Box(modifier = dotModifier)
                            }
                        }
                    }
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

private data class MapOverlay(val color: Color, val isRiding: Boolean, val points: List<LatLng>)

// The 280dp overview map doesn't need full GPS resolution; cap points per trip
private fun downsampleForOverview(points: List<RoutePoint>, maxPoints: Int = 400): List<RoutePoint> {
    if (points.size <= maxPoints) return points
    val step = (points.size + maxPoints - 1) / maxPoints
    return points.filterIndexed { index, _ -> index % step == 0 || index == points.lastIndex }
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
