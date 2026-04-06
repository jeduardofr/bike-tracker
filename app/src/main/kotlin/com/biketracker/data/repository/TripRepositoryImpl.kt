package com.biketracker.data.repository

import com.biketracker.data.local.database.dao.RoutePointDao
import com.biketracker.data.local.database.dao.TripDao
import com.biketracker.data.local.database.entity.RoutePointEntity
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.data.local.database.entity.TripEntity
import com.biketracker.domain.model.RoutePoint
import com.biketracker.domain.model.Trip
import com.biketracker.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    private val routePointDao: RoutePointDao
) : TripRepository {

    override suspend fun startTrip(direction: TripDirection): Long {
        val entity = TripEntity(
            startTime = System.currentTimeMillis(),
            direction = direction
        )
        return tripDao.insertTrip(entity)
    }

    override suspend fun addRoutePoint(tripId: Long, point: RoutePoint) {
        routePointDao.insertPoint(point.toEntity(tripId))
    }

    override suspend fun stopTrip(tripId: Long, distanceMeters: Float, averageSpeedKmh: Float) {
        val trip = tripDao.getTripById(tripId) ?: return
        tripDao.updateTrip(
            trip.copy(
                endTime = System.currentTimeMillis(),
                distanceMeters = distanceMeters,
                averageSpeedKmh = averageSpeedKmh,
                isCompleted = true
            )
        )
    }

    override fun getActiveTrip(): Flow<Trip?> =
        tripDao.getActiveTrip().map { it?.toDomain() }

    override fun getTripsInRange(from: Long, to: Long): Flow<List<Trip>> =
        tripDao.getTripsInRange(from, to).map { list -> list.map { it.toDomain() } }

    override suspend fun getTripWithRoute(tripId: Long): Trip? {
        val trip = tripDao.getTripById(tripId) ?: return null
        val points = routePointDao.getPointsForTripOnce(tripId)
        return trip.toDomain(points.map { it.toDomain() })
    }

    override fun getAllTrips(): Flow<List<Trip>> =
        tripDao.getAllTrips().map { list -> list.map { it.toDomain() } }

    override suspend fun getTripsWithRoutesInRange(from: Long, to: Long): List<Trip> {
        val trips = tripDao.getTripsInRangeOnce(from, to)
        return trips.map { trip ->
            val points = routePointDao.getPointsForTripOnce(trip.id)
            trip.toDomain(points.map { it.toDomain() })
        }
    }

    override suspend fun deleteTrip(tripId: Long) = tripDao.deleteTrip(tripId)

    private fun TripEntity.toDomain(points: List<RoutePoint> = emptyList()) = Trip(
        id = id, startTime = startTime, endTime = endTime,
        distanceMeters = distanceMeters, averageSpeedKmh = averageSpeedKmh,
        direction = direction, isCompleted = isCompleted, routePoints = points
    )

    private fun RoutePointEntity.toDomain() = RoutePoint(
        id = id, latitude = latitude, longitude = longitude,
        altitude = altitude, speedMps = speedMps, timestamp = timestamp, accuracy = accuracy
    )

    private fun RoutePoint.toEntity(tripId: Long) = RoutePointEntity(
        tripId = tripId, latitude = latitude, longitude = longitude,
        altitude = altitude, speedMps = speedMps, timestamp = timestamp, accuracy = accuracy
    )
}
