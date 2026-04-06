package com.biketracker.data.local.database.dao

import androidx.room.*
import com.biketracker.data.local.database.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getTripById(id: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE startTime >= :from AND startTime < :to AND isCompleted = 1 ORDER BY startTime DESC")
    fun getTripsInRange(from: Long, to: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE isCompleted = 0 LIMIT 1")
    fun getActiveTrip(): Flow<TripEntity?>

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE startTime >= :from AND startTime < :to AND isCompleted = 1 ORDER BY startTime DESC")
    suspend fun getTripsInRangeOnce(from: Long, to: Long): List<TripEntity>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)
}
