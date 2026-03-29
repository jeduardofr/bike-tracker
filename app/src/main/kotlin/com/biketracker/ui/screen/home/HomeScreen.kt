package com.biketracker.ui.screen.home

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.service.TrackingState
import com.biketracker.ui.component.StatCard
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onStartTracking: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val trackingState by viewModel.trackingState.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()

    // Required for GPS tracking (foreground only — background location is for geofencing, handled in Settings)
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Required on Android 13+ for the foreground service notification
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    var pendingDirection by remember { mutableStateOf<TripDirection?>(null) }

    // Once the required permissions are granted, start the trip that was waiting
    val readyToTrack = locationPermission.status.isGranted &&
            (notificationPermission == null || notificationPermission.status.isGranted)

    LaunchedEffect(readyToTrack) {
        if (readyToTrack) {
            pendingDirection?.let { direction ->
                viewModel.startTrip(direction)
                onStartTracking()
                pendingDirection = null
            }
        }
    }

    fun requestPermissionsAndStart(direction: TripDirection) {
        pendingDirection = direction
        when {
            !locationPermission.status.isGranted ->
                locationPermission.launchPermissionRequest()
            notificationPermission != null && !notificationPermission.status.isGranted ->
                notificationPermission.launchPermissionRequest()
            else -> {
                viewModel.startTrip(direction)
                onStartTracking()
                pendingDirection = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bike Tracker", style = MaterialTheme.typography.headlineMedium)

        when (val state = trackingState) {
            is TrackingState.Tracking -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tracking…", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("Distance", "%.1f".format(state.distanceMeters / 1000f), "km", modifier = Modifier.weight(1f))
                            StatCard("Speed", "%.1f".format(state.currentSpeedKmh), "km/h", modifier = Modifier.weight(1f))
                            StatCard("Time", formatSeconds(state.elapsedSeconds), "", modifier = Modifier.weight(1f))
                        }
                        Button(
                            onClick = { viewModel.stopTrip() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stop Trip")
                        }
                    }
                }
            }
            TrackingState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Start a trip", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TripDirection.entries.forEach { direction ->
                                OutlinedButton(
                                    onClick = { requestPermissionsAndStart(direction) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        when (direction) {
                                            TripDirection.HOME_TO_OFFICE -> "Home"
                                            TripDirection.OFFICE_TO_HOME -> "Office"
                                            TripDirection.FREE -> "Free"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        dailyStats?.let { stats ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Distance", "%.1f".format(stats.totalDistanceMeters / 1000f), "km", modifier = Modifier.weight(1f))
                        StatCard("Trips", stats.trips.size.toString(), "rides", modifier = Modifier.weight(1f))
                        StatCard("Time", formatSeconds(stats.totalDurationSeconds), "", modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun formatSeconds(seconds: Long): String {
    val m = seconds / 60
    return if (m < 60) m.toString() else "%d:%02d".format(m / 60, m % 60)
}
