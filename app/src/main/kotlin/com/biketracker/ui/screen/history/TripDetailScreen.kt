package com.biketracker.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.ui.component.StatCard
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripDetailScreen(
    tripId: Long,
    onBack: () -> Unit,
    viewModel: TripDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(tripId) { viewModel.load(tripId) }
    val trip by viewModel.trip.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trip Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        trip?.let { t ->
            val points = t.routePoints.map { LatLng(it.latitude, it.longitude) }
            val cameraPositionState = rememberCameraPositionState {
                if (points.isNotEmpty()) {
                    position = CameraPosition.fromLatLngZoom(points.first(), 14f)
                }
            }

            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    cameraPositionState = cameraPositionState
                ) {
                    if (points.size >= 2) {
                        Polyline(points = points, color = Color(0xFF2E7D32), width = 8f)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val durationMin = t.endTime?.let { ((it - t.startTime) / 60_000).toInt() } ?: 0
                    StatCard("Distance", "%.1f".format(t.distanceMeters / 1000f), "km", Modifier.weight(1f))
                    StatCard("Duration", durationMin.toString(), "min", Modifier.weight(1f))
                    StatCard("Avg Speed", "%.1f".format(t.averageSpeedKmh), "km/h", Modifier.weight(1f))
                }
            }
        } ?: Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
