package com.biketracker.ui.screen.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.service.TrackingState
import com.biketracker.ui.component.StatCard

@Composable
fun HomeScreen(
    onStartTracking: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val trackingState by viewModel.trackingState.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Bike Tracker", style = MaterialTheme.typography.headlineMedium)

        // Active trip card
        when (val state = trackingState) {
            is TrackingState.Tracking -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tracking…", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatCard("Distance", "%.1f".format(state.distanceMeters / 1000f), "km", modifier = Modifier.weight(1f))
                            StatCard("Speed", "%.1f".format(state.currentSpeedKmh), "km/h", modifier = Modifier.weight(1f))
                            StatCard("Time", formatSeconds(state.elapsedSeconds), "min", modifier = Modifier.weight(1f))
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
                // Start trip section
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Start a trip", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TripDirection.entries.forEach { direction ->
                                OutlinedButton(
                                    onClick = {
                                        viewModel.startTrip(direction)
                                        onStartTracking()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(direction.name.take(4), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Daily stats
        dailyStats?.let { stats ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Distance", "%.1f".format(stats.totalDistanceMeters / 1000f), "km", modifier = Modifier.weight(1f))
                        StatCard("Trips", stats.trips.size.toString(), "rides", modifier = Modifier.weight(1f))
                        StatCard("Time", formatSeconds(stats.totalDurationSeconds), "min", modifier = Modifier.weight(1f))
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
