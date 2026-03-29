package com.biketracker.ui.screen.settings

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.domain.repository.SettingsRepository
import com.biketracker.service.GeofencingManager
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LocationPickerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val geofencingManager: GeofencingManager
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<Address>>(emptyList())
    val searchResults: StateFlow<List<Address>> = _searchResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    fun searchAddress(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = withContext(Dispatchers.IO) {
                runCatching { geocode(query) }.getOrDefault(emptyList())
            }
            _isSearching.value = false
        }
    }

    fun clearResults() {
        _searchResults.value = emptyList()
    }

    @Suppress("DEPRECATION")
    private suspend fun geocode(query: String): List<Address> {
        val geocoder = Geocoder(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withContext(Dispatchers.Main) {
                kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 5) { results ->
                        cont.resume(results, null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                geocoder.getFromLocationName(query, 5) ?: emptyList()
            }
        }
    }

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
