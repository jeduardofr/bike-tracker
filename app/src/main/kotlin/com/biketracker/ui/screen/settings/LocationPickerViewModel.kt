package com.biketracker.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.domain.repository.SettingsRepository
import com.biketracker.service.GeofencingManager
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geofencingManager: GeofencingManager
) : ViewModel() {

    fun saveLocation(type: String, latLng: LatLng) {
        viewModelScope.launch {
            when (type) {
                "home" -> settingsRepository.setHomeLocation(latLng.latitude, latLng.longitude)
                "office" -> settingsRepository.setOfficeLocation(latLng.latitude, latLng.longitude)
            }
            geofencingManager.registerGeofences()
        }
    }
}
