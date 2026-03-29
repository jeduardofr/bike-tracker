package com.biketracker.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.domain.model.Trip
import com.biketracker.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    val trips = tripRepository.getAllTrips().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Trip>()
    )

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch { tripRepository.deleteTrip(tripId) }
    }
}
