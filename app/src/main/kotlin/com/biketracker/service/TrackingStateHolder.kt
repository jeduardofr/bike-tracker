package com.biketracker.service

import com.biketracker.domain.model.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed class TrackingState {
    object Idle : TrackingState()
    data class Tracking(
        val tripId: Long,
        val distanceMeters: Float,
        val currentSpeedKmh: Float,
        val elapsedSeconds: Long,
        val points: List<RoutePoint>
    ) : TrackingState()
}

@Singleton
class TrackingStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<TrackingState>(TrackingState.Idle)
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    fun emit(state: TrackingState) {
        _state.value = state
    }

    /** The direction HeadingOut is watching for (GPS buffering, no trip yet), or null. */
    @Volatile var watching: com.biketracker.data.local.database.entity.TripDirection? = null
    /** Whether this watch already asked "Heading home?" */
    @Volatile var prompted: Boolean = false
}
