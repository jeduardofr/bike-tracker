package com.biketracker.ui.screen.tracking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.R
import com.biketracker.service.TrackingState
import com.biketracker.ui.component.StatCard
import com.biketracker.ui.theme.StopRed
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*

@Composable
fun TrackingScreen(
    onStop: () -> Unit,
    viewModel: TrackingViewModel = hiltViewModel()
) {
    val trackingState by viewModel.trackingState.collectAsState()
    val currentLocation by viewModel.currentLocation.collectAsState()

    var hasBeenTracking by remember { mutableStateOf(false) }
    LaunchedEffect(trackingState) {
        if (trackingState is TrackingState.Tracking) hasBeenTracking = true
        if (hasBeenTracking && trackingState is TrackingState.Idle) onStop()
    }

    val trackingData = trackingState as? TrackingState.Tracking

    val cameraPositionState = rememberCameraPositionState()
    var isFollowing by remember { mutableStateOf(true) }
    var showStopDialog by remember { mutableStateOf(false) }

    // Detect user gesture panning
    LaunchedEffect(cameraPositionState.isMoving, cameraPositionState.cameraMoveStartedReason) {
        if (cameraPositionState.isMoving &&
            cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        ) {
            isFollowing = false
        }
    }

    // Center on current location initially
    LaunchedEffect(currentLocation) {
        if (isFollowing) {
            currentLocation?.let {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 16f)
            }
        }
    }

    // Follow latest GPS point only when isFollowing
    LaunchedEffect(trackingData?.points?.lastOrNull(), isFollowing) {
        if (isFollowing) {
            trackingData?.points?.lastOrNull()?.let {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val darkStyle = MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.map_style_dark)
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true, mapStyleOptions = darkStyle),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false)
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

        // Re-center FAB
        if (!isFollowing) {
            FloatingActionButton(
                onClick = {
                    isFollowing = true
                    trackingData?.points?.lastOrNull()?.let {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(LatLng(it.latitude, it.longitude), 16f)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Re-center", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Stats overlay
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (trackingData != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Distance", "%.1f".format(trackingData.distanceMeters / 1000f), "km", Modifier.weight(1f))
                        StatCard("Speed", "%.1f".format(trackingData.currentSpeedKmh), "km/h", Modifier.weight(1f))
                        StatCard("Time", formatElapsed(trackingData.elapsedSeconds), "", Modifier.weight(1f))
                    }
                } else {
                    Text(
                        "Waiting for GPS…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showStopDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = StopRed, contentColor = Color.White)
                ) {
                    Text("Stop Trip")
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Stop trip?") },
            text = { Text("Are you sure you want to end this trip?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.stopTrip()
                    showStopDialog = false
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
