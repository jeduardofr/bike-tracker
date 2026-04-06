package com.biketracker.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.R
import com.biketracker.ui.component.StatCard
import com.biketracker.ui.util.buildSegments
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val routePoints = t.routePoints
            val cameraPositionState = rememberCameraPositionState {
                if (routePoints.isNotEmpty()) {
                    position = CameraPosition.fromLatLngZoom(
                        LatLng(routePoints.first().latitude, routePoints.first().longitude), 14f
                    )
                }
            }

            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                val darkStyle = MapStyleOptions.loadRawResourceStyle(LocalContext.current, R.raw.map_style_dark)
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(mapStyleOptions = darkStyle)
                ) {
                    if (routePoints.size >= 2) {
                        val segments = buildSegments(routePoints)
                        segments.forEach { segment ->
                            Polyline(
                                points = segment.points.map { LatLng(it.latitude, it.longitude) },
                                color = if (segment.isRiding) Color(0xFF2E7D32) else Color(0xFF7B1FA2),
                                width = 8f
                            )
                        }
                    }

                    // GPS sample dots at high zoom — sampled to max ~60 points
                    val zoom = cameraPositionState.position.zoom
                    if (zoom > 15f && routePoints.isNotEmpty()) {
                        val step = maxOf(1, routePoints.size / 60)
                        routePoints.filterIndexed { i, _ -> i % step == 0 }.forEach { point ->
                            Circle(
                                center = LatLng(point.latitude, point.longitude),
                                radius = 3.0,
                                fillColor = Color.White.copy(alpha = 0.7f),
                                strokeColor = Color.White,
                                strokeWidth = 1f
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val durationMin = t.endTime?.let { ((it - t.startTime) / 60_000).toInt() } ?: 0
                    StatCard("Distance", "%.1f".format(t.distanceMeters / 1000f), "km", Modifier.weight(1f))
                    StatCard("Duration", durationMin.toString(), "min", Modifier.weight(1f))
                    StatCard("Avg Speed", "%.1f".format(t.averageSpeedKmh), "km/h", Modifier.weight(1f))
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFmt = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard("Date", dateFmt.format(Date(t.startTime)), "", Modifier.weight(1f))
                    StatCard("Departed", timeFmt.format(Date(t.startTime)), "", Modifier.weight(1f))
                    StatCard(
                        "Arrived",
                        t.endTime?.let { timeFmt.format(Date(it)) } ?: "—",
                        "",
                        Modifier.weight(1f)
                    )
                }
            }
        } ?: Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
