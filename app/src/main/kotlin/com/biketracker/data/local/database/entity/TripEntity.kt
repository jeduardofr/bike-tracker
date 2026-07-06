package com.biketracker.data.local.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TripDirection { HOME_TO_OFFICE, OFFICE_TO_HOME, FREE }

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceMeters: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val direction: TripDirection = TripDirection.FREE,
    val isCompleted: Boolean = false,
    // stable identity across devices; local autoincrement ids can't be used remotely
    @ColumnInfo(defaultValue = "") val uuid: String = UUID.randomUUID().toString(),
    val syncedAt: Long? = null
)
