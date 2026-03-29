package com.biketracker.domain.usecase

import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.TripRepository
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    suspend operator fun invoke(direction: TripDirection): Result<Long> {
        // Abandon any stuck incomplete trip from a previous crashed/interrupted session
        val activeTrip = tripRepository.getActiveTrip().firstOrNull()
        if (activeTrip != null) {
            tripRepository.stopTrip(activeTrip.id, 0f, 0f)
        }
        return Result.success(tripRepository.startTrip(direction))
    }
}
