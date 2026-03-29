package com.biketracker.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "work_sessions")
data class WorkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val arrivalTime: Long,
    val departureTime: Long? = null,
    val targetDepartureTime: Long
)
