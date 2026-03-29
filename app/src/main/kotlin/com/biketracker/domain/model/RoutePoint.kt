package com.biketracker.domain.model

data class RoutePoint(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedMps: Float = 0f,
    val timestamp: Long,
    val accuracy: Float = 0f
)
