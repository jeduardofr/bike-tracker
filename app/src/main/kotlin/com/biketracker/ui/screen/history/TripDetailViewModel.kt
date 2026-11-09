package com.biketracker.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biketracker.domain.model.Trip
import com.biketracker.domain.repository.TripRepository
import com.biketracker.domain.usecase.ComputeRouteMetricsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val computeMetrics: ComputeRouteMetricsUseCase
) : ViewModel() {

    private val _trip = MutableStateFlow<Trip?>(null)
    val trip: StateFlow<Trip?> = _trip

    fun load(tripId: Long) {
        viewModelScope.launch {
            val t = tripRepository.getTripWithRoute(tripId) ?: return@launch
            val metrics = computeMetrics(t.routePoints)
            val needsUpdate = t.distanceMeters == 0f ||
                    kotlin.math.abs(t.distanceMeters - metrics.distanceMeters) > 10f
            // only finished trips, and only their metrics: viewing a trip must never end it
            if (needsUpdate && t.isCompleted && t.routePoints.size >= 2) {
                tripRepository.updateMetrics(tripId, metrics.distanceMeters, metrics.averageSpeedKmh)
                _trip.value = t.copy(
                    distanceMeters = metrics.distanceMeters,
                    averageSpeedKmh = metrics.averageSpeedKmh
                )
            } else {
                _trip.value = t
            }
        }
    }
}
