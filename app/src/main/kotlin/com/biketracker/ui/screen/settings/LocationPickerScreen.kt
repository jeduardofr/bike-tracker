package com.biketracker.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var selectedLatLng by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(37.4221, -122.0841), 12f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            onMapClick = { latLng -> selectedLatLng = latLng }
        ) {
            selectedLatLng?.let {
                Marker(state = MarkerState(position = it), title = type.replaceFirstChar { c -> c.uppercase() })
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Tap the map to set your ${type} location",
                style = MaterialTheme.typography.bodyMedium
            )
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
