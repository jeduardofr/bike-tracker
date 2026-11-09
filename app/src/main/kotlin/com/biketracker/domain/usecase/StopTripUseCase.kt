package com.biketracker.domain.usecase

import android.location.Location
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.SettingsRepository
import com.biketracker.domain.repository.TripRepository
import com.biketracker.service.LeaveReminder
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class StopTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val computeMetrics: ComputeRouteMetricsUseCase,
    private val settingsRepository: SettingsRepository,
    private val leaveReminder: LeaveReminder
) {
    suspend operator fun invoke(tripId: Long): Result<Unit> {
        val trip = tripRepository.getTripWithRoute(tripId)
            ?: return Result.failure(IllegalStateException("Trip not found: $tripId"))
        val metrics = computeMetrics(trip.routePoints)
        tripRepository.stopTrip(tripId, metrics.distanceMeters, metrics.averageSpeedKmh)

        // a ride in that ends at the office (where the bike is parked) is the most reliable arrival
        // time there is, plus the walk from the bike to the building
        val last = trip.routePoints.lastOrNull()
        val office = settingsRepository.officeLocation.firstOrNull()
        if (trip.direction == TripDirection.HOME_TO_OFFICE && last != null && office != null) {
            val radius = settingsRepository.geofenceRadiusMeters.firstOrNull() ?: 150f
            val d = FloatArray(1)
            Location.distanceBetween(last.latitude, last.longitude, office.latitude, office.longitude, d)
            if (d[0] <= maxOf(radius * 2, 300f)) leaveReminder.arrived(last.timestamp + WALK_TO_OFFICE_MS, "ride")
        }
        return Result.success(Unit)
    }

    private companion object {
        const val WALK_TO_OFFICE_MS = 3 * 60_000L
    }
}
