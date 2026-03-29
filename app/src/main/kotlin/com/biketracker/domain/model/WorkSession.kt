package com.biketracker.domain.model

data class WorkSession(
    val id: Long = 0,
    val date: String,
    val arrivalTime: Long,
    val departureTime: Long? = null,
    val targetDepartureTime: Long
)
