package com.biketracker.domain.usecase

import com.biketracker.domain.model.WeeklyStats
import com.biketracker.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

class GetWeeklyStatsUseCase @Inject constructor(
    private val tripRepository: TripRepository,
    private val getDailyStats: GetDailyStatsUseCase
) {
    operator fun invoke(weekOf: LocalDate = LocalDate.now()): Flow<WeeklyStats> {
        val monday = weekOf.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val zoneId = ZoneId.systemDefault()
        val from = monday.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val to = monday.plusWeeks(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return tripRepository.getTripsInRange(from, to).map { allTrips ->
            val days = (0L..6L).map { offset ->
                val day = monday.plusDays(offset)
                val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
                val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                val dayTrips = allTrips.filter { it.startTime in dayStart until dayEnd }
                val totalDist = dayTrips.sumOf { it.distanceMeters.toDouble() }.toFloat()
                val totalDur = dayTrips.sumOf { t ->
                    if (t.endTime != null) ((t.endTime - t.startTime) / 1000L) else 0L
                }
                val avgSpd = if (dayTrips.isNotEmpty()) dayTrips.map { it.averageSpeedKmh }.average().toFloat() else 0f
                com.biketracker.domain.model.DailyStats(day.toString(), dayTrips, totalDist, totalDur, avgSpd)
            }
            WeeklyStats(
                weekStartDate = monday.toString(),
                days = days,
                totalDistanceMeters = allTrips.sumOf { it.distanceMeters.toDouble() }.toFloat(),
                totalTrips = allTrips.size
            )
        }
    }
}
