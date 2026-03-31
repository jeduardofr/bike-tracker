package com.biketracker.ui.screen.settings

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

@Composable
fun SettingsScreen(
    onPickLocation: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val homeLocation by viewModel.homeLocation.collectAsState()
    val officeLocation by viewModel.officeLocation.collectAsState()
    val workHours by viewModel.workHours.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val checklist by viewModel.packingChecklist.collectAsState()
    var newItem by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineMedium) }

        // Locations
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Locations", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { onPickLocation("home") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (homeLocation != null) "Home: ${"%.4f".format(homeLocation!!.latitude)}, ${"%.4f".format(homeLocation!!.longitude)}" else "Set Home Location")
                    }
                    OutlinedButton(onClick = { onPickLocation("office") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (officeLocation != null) "Office: ${"%.4f".format(officeLocation!!.latitude)}, ${"%.4f".format(officeLocation!!.longitude)}" else "Set Office Location")
                    }
                }
            }
        }

        // Work hours
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Work Hours: ${workHours.toInt()}h", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = workHours,
                        onValueChange = { viewModel.setWorkHours(it) },
                        valueRange = 1f..12f,
                        steps = 10
                    )
                }
            }
        }

        // Geofence radius
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Geofence Radius: ${geofenceRadius.toInt()}m", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = geofenceRadius,
                        onValueChange = { viewModel.setGeofenceRadius(it) },
                        valueRange = 10f..100f,
                        steps = 8
                    )
                }
            }
        }

        // Packing checklist
        item {
            Text("Packing Checklist", style = MaterialTheme.typography.titleMedium)
        }
        items(checklist) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item, modifier = Modifier.weight(1f))
                IconButton(onClick = { viewModel.removePackingItem(item) }) {
                    Icon(Icons.Default.Delete, "Remove")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    label = { Text("Add item") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    viewModel.addPackingItem(newItem)
                    newItem = ""
                }) {
                    Text("Add")
                }
            }
        }
    }
}
