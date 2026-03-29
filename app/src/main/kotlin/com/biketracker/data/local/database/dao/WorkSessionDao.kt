package com.biketracker.data.local.database.dao

import androidx.room.*
import com.biketracker.data.local.database.entity.WorkSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(session: WorkSessionEntity): Long

    @Update
    suspend fun update(session: WorkSessionEntity)

    @Query("SELECT * FROM work_sessions WHERE date = :date LIMIT 1")
    suspend fun getSessionForDate(date: String): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions ORDER BY date DESC LIMIT 30")
    fun getRecentSessions(): Flow<List<WorkSessionEntity>>
}
