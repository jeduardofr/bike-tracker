package com.biketracker.domain.usecase

import com.biketracker.domain.model.DailyStats
import com.biketracker.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class GetDailyStatsUseCase @Inject constructor(
    private val tripRepository: TripRepository
) {
    operator fun invoke(date: LocalDate = LocalDate.now()): Flow<DailyStats> {
        val zoneId = ZoneId.systemDefault()
        val from = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return tripRepository.getTripsInRange(from, to).map { trips ->
            val totalDistance = trips.sumOf { it.distanceMeters.toDouble() }.toFloat()
            val totalDuration = trips.sumOf { t ->
                if (t.endTime != null) ((t.endTime - t.startTime) / 1000L) else 0L
            }
            val avgSpeed = if (trips.isNotEmpty()) trips.map { it.averageSpeedKmh }.average().toFloat() else 0f
            DailyStats(
                date = date.toString(),
                trips = trips,
                totalDistanceMeters = totalDistance,
                totalDurationSeconds = totalDuration,
                averageSpeedKmh = avgSpeed
            )
        }
    }
}
