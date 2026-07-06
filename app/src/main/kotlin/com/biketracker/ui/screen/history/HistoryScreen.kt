package com.biketracker.ui.screen.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.domain.model.Trip
import com.biketracker.ui.component.TripListItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onTripClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val trips by viewModel.trips.collectAsState()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (trips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No trips yet. Start your first ride!")
            }
        } else {
            val zoneId = ZoneId.systemDefault()
            val grouped = remember(trips) {
                trips.groupBy { Instant.ofEpochMilli(it.startTime).atZone(zoneId).toLocalDate() }
            }
            LazyColumn {
                grouped.forEach { (date, dayTrips) ->
                    stickyHeader(key = date.toString()) {
                        DayHeader(date)
                    }
                    items(dayTrips, key = { it.id }) { trip ->
                        TripRow(
                            trip = trip,
                            onClick = { onTripClick(trip.id) },
                            onDelete = { viewModel.deleteTrip(trip.id) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate) {
    Text(
        text = formatDayHeader(date),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun formatDayHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> {
            val pattern = if (date.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // never settle into the dismissed state — snap back and let the dialog decide
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteConfirm = true
            }
            false
        }
    )

    if (showDeleteConfirm) {
        val timeFmt = java.text.SimpleDateFormat("EEE, MMM d · HH:mm", Locale.getDefault())
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete trip?") },
            text = {
                Text(
                    "${timeFmt.format(java.util.Date(trip.startTime))} · " +
                        "%.1f km".format(trip.distanceMeters / 1000f) +
                        "\n\nThis removes the trip from this phone and the synced database. It can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        TripListItem(trip = trip, onClick = onClick)
    }
}
