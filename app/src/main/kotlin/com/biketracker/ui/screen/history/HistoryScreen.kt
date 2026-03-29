package com.biketracker.ui.screen.history

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
import com.biketracker.ui.component.TripListItem

@OptIn(ExperimentalMaterial3Api::class)
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
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No trips yet. Start your first ride!")
            }
        } else {
            LazyColumn {
                items(trips, key = { it.id }) { trip ->
                    val swipeState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteTrip(trip.id)
                                true
                            } else false
                        }
                    )
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
                        TripListItem(trip = trip, onClick = { onTripClick(trip.id) })
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
