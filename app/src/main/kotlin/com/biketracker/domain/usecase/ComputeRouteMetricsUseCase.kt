package com.biketracker.domain.usecase

import com.biketracker.domain.model.RoutePoint
import javax.inject.Inject
import kotlin.math.*

data class RouteMetrics(val distanceMeters: Float, val averageSpeedKmh: Float)

class ComputeRouteMetricsUseCase @Inject constructor() {

    companion object {
        private const val MAX_ACCURACY_M = 50f
    }

    operator fun invoke(points: List<RoutePoint>): RouteMetrics {
        if (points.size < 2) return RouteMetrics(0f, 0f)

        // Filter out inaccurate points first
        val filtered = points.filter { it.accuracy <= MAX_ACCURACY_M || it.accuracy == 0f }
        if (filtered.size < 2) return RouteMetrics(0f, 0f)

        var totalDistance = 0f
        for (i in 1 until filtered.size) {
            totalDistance += haversineMeters(filtered[i - 1], filtered[i])
        }

        val durationSeconds = (filtered.last().timestamp - filtered.first().timestamp) / 1000.0
        val avgSpeedKmh = if (durationSeconds > 0) {
            ((totalDistance / durationSeconds) * 3.6).toFloat()
        } else 0f

        return RouteMetrics(totalDistance, avgSpeedKmh)
    }

    private fun haversineMeters(a: RoutePoint, b: RoutePoint): Float {
        val r = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return (2 * r * asin(sqrt(h))).toFloat()
    }
}
