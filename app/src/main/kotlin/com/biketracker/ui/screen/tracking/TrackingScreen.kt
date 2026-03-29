package com.biketracker.ui.screen.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.service.TrackingState
import com.biketracker.ui.component.StatCard
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun TrackingScreen(
    onStop: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val trackingState by viewModel.trackingState.collectAsState()

    val state = trackingState
    if (state is TrackingState.Idle) {
        onStop()
        return
    }

    val trackingData = state as? TrackingState.Tracking

    val cameraPositionState = rememberCameraPositionState {
        if (trackingData?.points?.isNotEmpty() == true) {
            val last = trackingData.points.last()
            position = CameraPosition.fromLatLngZoom(LatLng(last.latitude, last.longitude), 16f)
        }
    }

    LaunchedEffect(trackingData?.points?.lastOrNull()) {
        trackingData?.points?.lastOrNull()?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            trackingData?.points?.let { points ->
                if (points.size >= 2) {
                    Polyline(
                        points = points.map { LatLng(it.latitude, it.longitude) },
                        color = Color(0xFF2E7D32),
                        width = 8f
                    )
                }
            }
        }

        // Stats overlay
        trackingData?.let { data ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Distance", "%.1f".format(data.distanceMeters / 1000f), "km", Modifier.weight(1f))
                        StatCard("Speed", "%.1f".format(data.currentSpeedKmh), "km/h", Modifier.weight(1f))
                        StatCard("Time", formatElapsed(data.elapsedSeconds), "", Modifier.weight(1f))
                    }
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Stop Trip")
                    }
                }
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
