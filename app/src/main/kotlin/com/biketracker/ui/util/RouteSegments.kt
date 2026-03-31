package com.biketracker.ui.util

import com.biketracker.domain.model.RoutePoint

data class RouteSegment(val points: List<RoutePoint>, val isRiding: Boolean)

fun buildSegments(points: List<RoutePoint>, speedThresholdMps: Float = 1.94f): List<RouteSegment> {
    if (points.size < 2) return emptyList()

    val segments = mutableListOf<RouteSegment>()
    var currentPoints = mutableListOf(points[0])
    var currentIsRiding = points[0].speedMps > speedThresholdMps

    for (i in 1 until points.size) {
        val isRiding = points[i].speedMps > speedThresholdMps
        if (isRiding != currentIsRiding) {
            currentPoints.add(points[i]) // overlap for visual continuity
            segments.add(RouteSegment(currentPoints.toList(), currentIsRiding))
            currentPoints = mutableListOf(points[i])
            currentIsRiding = isRiding
        } else {
            currentPoints.add(points[i])
        }
    }
    if (currentPoints.size >= 2) {
        segments.add(RouteSegment(currentPoints.toList(), currentIsRiding))
    }
    return segments
}
