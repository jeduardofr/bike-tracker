package com.biketracker.domain.model

import com.biketracker.BuildConfig
import com.biketracker.data.local.database.entity.TripDirection
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Places as building outlines (from local.properties, never committed: the repo is public).
 * Android's geofences only do circles, so those stay the early, coarse trigger; an outline decides
 * the exact moments: where a ride leaves, when it arrives.
 *
 * Only home has one. At the office the bike is parked ~185 m (a 3 min walk) from the building and
 * trips are ride-only, so the office stays the circle around the saved point, which covers the
 * bike; arrival there is the ride's end plus the walk (StopTripUseCase).
 *
 * GPS near and inside a building is off by 10-20 m, so "at" a place means inside its outline or
 * within [NEAR_M] of an edge.
 */
object Places {
    enum class Place { HOME, OFFICE }

    const val NEAR_M = 25.0

    private val outlines: Map<Place, List<Pair<Double, Double>>> = mapOf(
        Place.HOME to parse(BuildConfig.PLACE_HOME_POLYGON)
    ).filterValues { it.size >= 3 }

    private fun parse(s: String) = s.split(";").mapNotNull { p ->
        val (lat, lng) = p.split(",").map { it.trim().toDoubleOrNull() ?: return@mapNotNull null }.takeIf { it.size == 2 } ?: return@mapNotNull null
        lat to lng
    }

    fun has(place: Place) = place in outlines

    fun from(direction: TripDirection) = when (direction) {
        TripDirection.OFFICE_TO_HOME -> Place.OFFICE
        TripDirection.HOME_TO_OFFICE -> Place.HOME
        TripDirection.FREE -> null
    }

    fun to(direction: TripDirection) = when (direction) {
        TripDirection.HOME_TO_OFFICE -> Place.OFFICE
        TripDirection.OFFICE_TO_HOME -> Place.HOME
        TripDirection.FREE -> null
    }

    /** Meters from the outline: 0 inside, else to the nearest edge; null without an outline. */
    fun distance(place: Place, lat: Double, lng: Double): Double? {
        val ring = outlines[place] ?: return null
        // a local flat projection around the point: plenty accurate over a few hundred meters
        val mLat = 111_320.0
        val mLng = 111_320.0 * cos(Math.toRadians(lat))
        val xy = ring.map { (a, b) -> (b - lng) * mLng to (a - lat) * mLat }
        var inside = false
        var best = Double.MAX_VALUE
        for (i in xy.indices) {
            val (x1, y1) = xy[i]
            val (x2, y2) = xy[(i + 1) % xy.size]
            if ((y1 > 0) != (y2 > 0) && 0 < (x2 - x1) * (0 - y1) / (y2 - y1) + x1) inside = !inside
            best = minOf(best, segmentDistance(x1, y1, x2, y2))
        }
        return if (inside) 0.0 else best
    }

    fun isAt(place: Place, p: RoutePoint) = (distance(place, p.latitude, p.longitude) ?: Double.MAX_VALUE) <= NEAR_M

    /** Distance from the origin (0, 0) to the segment. */
    private fun segmentDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = dx * dx + dy * dy
        val t = if (len == 0.0) 0.0 else ((-x1 * dx - y1 * dy) / len).coerceIn(0.0, 1.0)
        return hypot(x1 + t * dx, y1 + t * dy)
    }
}
