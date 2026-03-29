package com.biketracker.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun LocationPickerScreen(
    type: String,
    onSaved: () -> Unit,
    viewModel: LocationPickerViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
    var selectedLabel by remember { mutableStateOf<String?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.6961, -103.3047), 13f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng ->
                selectedLatLng = latLng
                selectedLabel = null
                viewModel.clearResults()
            }
        ) {
            selectedLatLng?.let {
                Marker(
                    state = MarkerState(position = it),
                    title = selectedLabel ?: type.replaceFirstChar { c -> c.uppercase() }
                )
            }
        }

        // Search bar + results overlay
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search address…") },
                trailingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            viewModel.searchAddress(searchQuery)
                        }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            if (searchResults.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(searchResults) { address ->
                            val label = address.getAddressLine(0) ?: "${address.latitude}, ${address.longitude}"
                            ListItem(
                                headlineContent = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier.clickable {
                                    val latLng = LatLng(address.latitude, address.longitude)
                                    selectedLatLng = latLng
                                    selectedLabel = label
                                    searchQuery = label
                                    viewModel.clearResults()
                                    keyboardController?.hide()
                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 16f)
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Save button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Button(
                onClick = {
                    selectedLatLng?.let {
                        viewModel.saveLocation(type, it)
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedLatLng != null
            ) {
                Text("Save ${type.replaceFirstChar { c -> c.uppercase() }} Location")
            }
        }
    }
}
