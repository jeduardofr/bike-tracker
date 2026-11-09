package com.biketracker.domain.repository

import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.model.RoutePoint
import com.biketracker.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    suspend fun startTrip(direction: TripDirection): Long
    suspend fun addRoutePoint(tripId: Long, point: RoutePoint)
    /** Closes a trip; it ends at its last GPS point (now, only if it has none). */
    suspend fun stopTrip(tripId: Long, distanceMeters: Float, averageSpeedKmh: Float)
    /** Recomputed distance/speed for a finished trip, without touching when it ended. */
    suspend fun updateMetrics(tripId: Long, distanceMeters: Float, averageSpeedKmh: Float)
    /** Finished trips whose end is long after their last GPS point get that point as their end. */
    suspend fun repairEndTimes(): Int
    fun getActiveTrip(): Flow<Trip?>
    fun getTripsInRange(from: Long, to: Long): Flow<List<Trip>>
    suspend fun getTripWithRoute(tripId: Long): Trip?
    fun getAllTrips(): Flow<List<Trip>>
    suspend fun getTripsWithRoutesInRange(from: Long, to: Long): List<Trip>
    suspend fun deleteTrip(tripId: Long)
}
