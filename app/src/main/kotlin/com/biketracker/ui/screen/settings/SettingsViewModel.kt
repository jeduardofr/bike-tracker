package com.biketracker.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.domain.repository.SettingsRepository
import com.biketracker.service.GeofencingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val geofencingManager: GeofencingManager
) : ViewModel() {

    val homeLocation = settingsRepository.homeLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val officeLocation = settingsRepository.officeLocation.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val workHours = settingsRepository.workHours.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8f)
    val geofenceRadius = settingsRepository.geofenceRadiusMeters.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 150f)
    val packingChecklist = settingsRepository.packingChecklist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setWorkHours(hours: Float) {
        viewModelScope.launch { settingsRepository.setWorkHours(hours) }
    }

    fun setGeofenceRadius(radius: Float) {
        viewModelScope.launch {
            settingsRepository.setGeofenceRadiusMeters(radius)
            geofencingManager.registerGeofences()
        }
    }

    fun addPackingItem(item: String) {
        viewModelScope.launch {
            val current = packingChecklist.value.toMutableList()
            if (item.isNotBlank() && !current.contains(item)) {
                current.add(item)
                settingsRepository.setPackingChecklist(current)
            }
        }
    }

    fun removePackingItem(item: String) {
        viewModelScope.launch {
            val current = packingChecklist.value.toMutableList()
            current.remove(item)
            settingsRepository.setPackingChecklist(current)
        }
    }
}
