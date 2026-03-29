package com.biketracker.domain.model

data class WeeklyStats(
    val weekStartDate: String,
    val days: List<DailyStats>,
    val totalDistanceMeters: Float,
    val totalTrips: Int
)
