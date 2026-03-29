package com.biketracker.ui.screen.home

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.service.TrackingState
import com.biketracker.ui.component.StatCard
import com.biketracker.ui.component.SummaryCard
import com.biketracker.ui.theme.BikeGreen
import com.biketracker.ui.theme.StopRed
import com.biketracker.ui.theme.SurfaceDark
import com.google.accompanist.permissions.*

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    onStartTracking: () -> Unit,
    onGoToHistory: () -> Unit = {},
    onGoToWeekly: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val trackingState by viewModel.trackingState.collectAsState()
    val dailyStats by viewModel.dailyStats.collectAsState()
    val allTimeStats by viewModel.allTimeStats.collectAsState()

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    var pendingDirection by remember { mutableStateOf<TripDirection?>(null) }

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
            !locationPermission.status.isGranted -> locationPermission.launchPermissionRequest()
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
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Bike Tracker",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        when (val state = trackingState) {
            is TrackingState.Tracking -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Tracking…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatCard("Distance", "%.1f".format(state.distanceMeters / 1000f), "km")
                            StatCard("Speed", "%.1f".format(state.currentSpeedKmh), "km/h")
                            StatCard("Time", formatSeconds(state.elapsedSeconds), "")
                        }
                        Button(
                            onClick = { viewModel.stopTrip() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = StopRed, contentColor = Color.White)
                        ) {
                            Text("Stop Trip")
                        }
                    }
                }
            }

            TrackingState.Idle -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Start a trip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TripDirection.entries.forEach { direction ->
                                Button(
                                    onClick = { requestPermissionsAndStart(direction) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        when (direction) {
                                            TripDirection.HOME_TO_OFFICE -> "Home"
                                            TripDirection.OFFICE_TO_HOME -> "Office"
                                            TripDirection.FREE -> "Free"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        dailyStats?.let { stats ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatCard("Distance", "%.1f".format(stats.totalDistanceMeters / 1000f), "km")
                        StatCard("Trips", stats.trips.size.toString(), "rides")
                        StatCard("Time", formatSeconds(stats.totalDurationSeconds), "")
                    }
                }
            }
        }

        allTimeStats?.let { stats ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryCard(
                    icon = Icons.Default.LocationOn,
                    label = "Total Distance",
                    value = "%.1f km".format(stats.totalDistanceKm),
                    containerColor = BikeGreen,
                    onClick = onGoToWeekly,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    icon = Icons.Default.DateRange,
                    label = "Total Trips",
                    value = stats.totalTrips.toString(),
                    containerColor = SurfaceDark,
                    onClick = onGoToHistory,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatSeconds(seconds: Long): String {
    val m = seconds / 60
    return if (m < 60) m.toString() else "%d:%02d".format(m / 60, m % 60)
}
