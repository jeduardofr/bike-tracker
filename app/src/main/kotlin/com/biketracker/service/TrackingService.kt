package com.biketracker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.biketracker.domain.model.RoutePoint
import com.biketracker.domain.repository.TripRepository
import com.biketracker.domain.usecase.StopTripUseCase
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var trackingStateHolder: TrackingStateHolder
    @Inject lateinit var stopTripUseCase: StopTripUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentTripId: Long = -1
    private var tripStartTime: Long = 0L
    private val collectedPoints = mutableListOf<RoutePoint>()

    private lateinit var locationCallback: LocationCallback

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"
        private const val TAG = "TrackingService"
    }

    override fun onCreate() {
        super.onCreate()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // lastLocation is deprecated and can return null on Android 12+; use locations list
                val locations = result.locations
                Log.d(TAG, "onLocationResult: ${locations.size} location(s)")
                if (locations.isEmpty()) return

                for (location in locations) {
                    val point = RoutePoint(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = location.altitude,
                        speedMps = location.speed,
                        timestamp = location.time,
                        accuracy = location.accuracy
                    )
                    collectedPoints.add(point)
                    serviceScope.launch {
                        if (currentTripId != -1L) {
                            tripRepository.addRoutePoint(currentTripId, point)
                        }
                    }
                }

                val distanceMeters = computeDistance(collectedPoints)
                val speedKmh = (locations.last().speed * 3.6f)
                val elapsed = (System.currentTimeMillis() - tripStartTime) / 1000L
                trackingStateHolder.emit(
                    TrackingState.Tracking(
                        tripId = currentTripId,
                        distanceMeters = distanceMeters,
                        currentSpeedKmh = speedKmh,
                        elapsedSeconds = elapsed,
                        points = collectedPoints.toList()
                    )
                )
                val notification = appNotificationManager.buildTrackingNotification(distanceMeters, speedKmh)
                startForeground(AppNotificationManager.NOTIF_ID_TRACKING, notification)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentTripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                tripStartTime = System.currentTimeMillis()
                collectedPoints.clear()
                Log.d(TAG, "ACTION_START tripId=$currentTripId")
                val notification = appNotificationManager.buildTrackingNotification(0f, 0f)
                startForeground(AppNotificationManager.NOTIF_ID_TRACKING, notification)
                requestLocationUpdates()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(2_000L)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            Log.d(TAG, "requestLocationUpdates registered successfully")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException requesting location updates", e)
            stopSelf()
        }
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        serviceScope.launch {
            if (currentTripId != -1L) {
                stopTripUseCase(currentTripId)
            }
            trackingStateHolder.emit(TrackingState.Idle)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun computeDistance(points: List<RoutePoint>): Float {
        if (points.size < 2) return 0f
        var total = 0f
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1], points[i])
        }
        return total
    }

    private fun haversineMeters(a: RoutePoint, b: RoutePoint): Float {
        val r = 6_371_000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(lat1) * kotlin.math.cos(lat2) *
                kotlin.math.sin(dLng / 2).let { it * it }
        return (2 * r * kotlin.math.asin(kotlin.math.sqrt(h))).toFloat()
    }
}
