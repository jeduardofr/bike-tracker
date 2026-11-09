package com.biketracker.domain.usecase

import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.TripRepository
import com.biketracker.service.LeaveReminder
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class StartTripUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val stopTrip: StopTripUseCase,
    private val leaveReminder: LeaveReminder,
    private val notifications: com.biketracker.service.AppNotificationManager
) {
    suspend operator fun invoke(direction: TripDirection): Result<Long> {
        // close any trip left open by a crashed/interrupted session, with its real distance and
        // end (its last GPS point), not zeros and "now"
        val activeTrip = tripRepository.getActiveTrip().firstOrNull()
        if (activeTrip != null) stopTrip(activeTrip.id)
        // heading home: today's "time to leave" reminder is no longer needed
        if (direction == TripDirection.OFFICE_TO_HOME) leaveReminder.departed(System.currentTimeMillis())
        notifications.cancelRidePrompts() // started by hand: the prompt is moot
        return Result.success(tripRepository.startTrip(direction))
    }
}
