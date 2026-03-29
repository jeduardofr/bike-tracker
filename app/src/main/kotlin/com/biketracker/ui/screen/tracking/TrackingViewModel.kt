package com.biketracker.ui.screen.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.service.TrackingState
import com.biketracker.service.TrackingStateHolder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class TrackingViewModel @Inject constructor(
    trackingStateHolder: TrackingStateHolder,
    private val fusedLocationClient: FusedLocationProviderClient
) : ViewModel() {

    val trackingState = trackingStateHolder.state.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), TrackingState.Idle
    )

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    val currentLocation: StateFlow<LatLng?> = _currentLocation

    init {
        viewModelScope.launch {
            try {
                val location = fusedLocationClient.lastLocation.await()
                if (location != null) {
                    _currentLocation.value = LatLng(location.latitude, location.longitude)
                }
            } catch (_: SecurityException) {}
        }
    }
}
