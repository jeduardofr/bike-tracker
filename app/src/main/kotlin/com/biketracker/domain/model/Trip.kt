package com.biketracker.domain.model

import com.biketracker.data.local.database.entity.TripDirection

data class Trip(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val distanceMeters: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val direction: TripDirection = TripDirection.FREE,
    val isCompleted: Boolean = false,
    val routePoints: List<RoutePoint> = emptyList()
)
