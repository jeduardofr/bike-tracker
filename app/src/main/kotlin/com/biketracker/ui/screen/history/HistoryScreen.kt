package com.biketracker.ui.screen.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biketracker.ui.component.TripListItem

@Composable
fun HistoryScreen(
    onTripClick: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val trips by viewModel.trips.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (trips.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No trips yet. Start your first ride!")
            }
        } else {
            LazyColumn {
                items(trips, key = { it.id }) { trip ->
                    TripListItem(trip = trip, onClick = { onTripClick(trip.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}
