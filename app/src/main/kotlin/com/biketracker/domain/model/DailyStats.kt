package com.biketracker.domain.model

data class DailyStats(
    val date: String,
    val trips: List<Trip>,
    val totalDistanceMeters: Float,
    val totalDurationSeconds: Long,
    val averageSpeedKmh: Float
)
