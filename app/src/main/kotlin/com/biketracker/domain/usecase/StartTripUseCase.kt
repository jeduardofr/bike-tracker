package com.biketracker.domain.usecase

import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.TripRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(direction: TripDirection): Result<Long> {
        val activeTrip = tripRepository.getActiveTrip().firstOrNull()
        if (activeTrip != null) {
            return Result.failure(IllegalStateException("A trip is already in progress"))
        }
        return Result.success(tripRepository.startTrip(direction))
    }
}
