package com.biketracker.domain.usecase

import com.biketracker.domain.repository.TripRepository
import javax.inject.Inject

class StopTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val computeMetrics: ComputeRouteMetricsUseCase
) {
    suspend operator fun invoke(tripId: Long): Result<Unit> {
        val trip = tripRepository.getTripWithRoute(tripId)
            ?: return Result.failure(IllegalStateException("Trip not found: $tripId"))
        val metrics = computeMetrics(trip.routePoints)
        tripRepository.stopTrip(tripId, metrics.distanceMeters, metrics.averageSpeedKmh)
        return Result.success(Unit)
    }
}
