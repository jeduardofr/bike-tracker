package com.biketracker.ui.screen.weekly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.R
import com.biketracker.ui.component.StatCard
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
            modifier = Modifier.padding(16.dp)
        )

        val allPoints = stats?.days?.flatMap { day ->
            day.trips.flatMap { trip -> trip.routePoints.map { LatLng(it.latitude, it.longitude) } }
        } ?: emptyList()

        val cameraPositionState = rememberCameraPositionState {
            if (allPoints.isNotEmpty()) position = CameraPosition.fromLatLngZoom(allPoints.first(), 13f)
        }

        val darkStyle = MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.map_style_dark)
        GoogleMap(
            modifier = Modifier.fillMaxWidth().height(280.dp),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapStyleOptions = darkStyle)
        ) {
            val colors = listOf(Color.Blue, Color.Red, Color.Green, Color.Magenta, Color.Cyan, Color.Yellow, Color(0xFFFF6600))
            stats?.days?.forEachIndexed { idx, day ->
                day.trips.forEach { trip ->
                    val pts = trip.routePoints.map { LatLng(it.latitude, it.longitude) }
                    if (pts.size >= 2) Polyline(points = pts, color = colors[idx % colors.size], width = 6f)
                }
            }
        }

        stats?.let { ws ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Total", "%.1f".format(ws.totalDistanceMeters / 1000f), "km", Modifier.weight(1f))
                StatCard("Trips", ws.totalTrips.toString(), "rides", Modifier.weight(1f))
            }

            LazyRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ws.days) { day ->
                    Card(modifier = Modifier.width(100.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(day.date.takeLast(5), style = MaterialTheme.typography.labelSmall)
                            Text("%.1f km".format(day.totalDistanceMeters / 1000f), style = MaterialTheme.typography.bodyMedium)
                            Text("${day.trips.size} trips", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
