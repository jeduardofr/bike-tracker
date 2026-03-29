package com.biketracker.data.local.database.dao

import androidx.room.*
import com.biketracker.data.local.database.entity.RoutePointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutePointDao {

    @Insert
    suspend fun insertPoint(point: RoutePointEntity): Long

    @Insert
    suspend fun insertPoints(points: List<RoutePointEntity>)

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun getPointsForTrip(tripId: Long): Flow<List<RoutePointEntity>>

    @Query("SELECT * FROM route_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getPointsForTripOnce(tripId: Long): List<RoutePointEntity>
}
