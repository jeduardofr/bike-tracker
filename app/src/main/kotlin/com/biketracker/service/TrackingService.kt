package com.biketracker.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.model.Places
import com.biketracker.domain.model.RoutePoint
import com.biketracker.domain.repository.SettingsRepository
import com.biketracker.domain.repository.TripRepository
import com.biketracker.domain.usecase.StopTripUseCase
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var fusedLocationClient: FusedLocationProviderClient
    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var trackingStateHolder: TrackingStateHolder
    @Inject lateinit var stopTripUseCase: StopTripUseCase
    @Inject lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Database writes (each point, then closing the trip) run here, in order, and are never
    // cancelled: serviceScope dies in onDestroy, which runs right after stopSelf() and used to kill
    // the closing write before it ran, leaving the trip open until something closed it hours later.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private var stopping = false
    // the main thread: location callbacks run there, so buffer hand-overs can't interleave with them
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Inject lateinit var startTripUseCase: com.biketracker.domain.usecase.StartTripUseCase
    private var currentTripId: Long = -1
    private var tripStartTime: Long = 0L
    private val collectedPoints = mutableListOf<RoutePoint>()
    private var destinationLatLng: LatLng? = null
    private var geofenceRadius: Float = 150f
    // the outline to arrive at (Places), when there is one; else the point + radius above
    private var destinationPlace: Places.Place? = null

    private lateinit var locationCallback: LocationCallback

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        /** "Start ride" on the 8:00 or leave-work notification (EXTRA_DIRECTION) */
        const val ACTION_CONFIRM = "ACTION_CONFIRM"
        /** "Not now" on those notifications */
        const val ACTION_DISMISS = "ACTION_DISMISS"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"
        const val EXTRA_DIRECTION = "EXTRA_DIRECTION"
        private const val TAG = "TrackingService"
    }

    override fun onCreate() {
        super.onCreate()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
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
                    writeScope.launch {
                        if (currentTripId != -1L) {
                            tripRepository.addRoutePoint(currentTripId, point)
                        }
                    }
                }

                val distanceMeters = computeDistance(collectedPoints)
                val speedKmh = if (collectedPoints.isNotEmpty()) collectedPoints.last().speedMps * 3.6f else 0f
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

                checkAutoStop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // a trip started from the app; if a watch is running, it keeps what it recorded
                val id = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                Log.d(TAG, "ACTION_START tripId=$id direction=${intent.getStringExtra(EXTRA_DIRECTION)}")
                becomeTrip(id, intent.getStringExtra(EXTRA_DIRECTION))
            }
            ACTION_CONFIRM -> {
                val direction = intent.getStringExtra(EXTRA_DIRECTION)?.let { runCatching { TripDirection.valueOf(it) }.getOrNull() }
                appNotificationManager.cancelRidePrompts()
                // started as a foreground service (getForegroundService): it must go foreground now
                startForeground(AppNotificationManager.NOTIF_ID_TRACKING, appNotificationManager.buildTrackingNotification(0f, 0f))
                if (currentTripId != -1L) return START_NOT_STICKY // already tracking
                if (direction == null) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                mainScope.launch {
                    val id = withContext(Dispatchers.IO) { startTripUseCase(direction).getOrNull() } ?: return@launch
                    becomeTrip(id, direction.name)
                }
            }
            ACTION_DISMISS -> {
                appNotificationManager.cancelRidePrompts()
                if (currentTripId == -1L) stopSelf()
            }
            ACTION_STOP -> {
                stopTracking()
            }
        }
        return START_STICKY
    }

    /** Starts tracking [tripId]. Main thread. */
    private fun becomeTrip(tripId: Long, directionName: String?) {
        stopping = false
        currentTripId = tripId
        collectedPoints.clear()
        tripStartTime = System.currentTimeMillis()
        loadDestination(directionName)
        startForeground(AppNotificationManager.NOTIF_ID_TRACKING, appNotificationManager.buildTrackingNotification(0f, 0f))
        requestLocationUpdates()
    }

    private fun loadDestination(directionName: String?) {
        Log.d(TAG, "loadDestination: directionName=$directionName")
        val direction = directionName?.let { runCatching { TripDirection.valueOf(it) }.getOrNull() }
        destinationPlace = direction?.let { Places.to(it) }?.takeIf { Places.has(it) }
        if (direction == null || direction == TripDirection.FREE) {
            Log.d(TAG, "loadDestination: no destination (direction=$direction)")
            destinationLatLng = null
            return
        }
        serviceScope.launch {
            val location = when (direction) {
                TripDirection.HOME_TO_OFFICE -> settingsRepository.officeLocation.firstOrNull()
                TripDirection.OFFICE_TO_HOME -> settingsRepository.homeLocation.firstOrNull()
                else -> null
            }
            destinationLatLng = location
            geofenceRadius = settingsRepository.geofenceRadiusMeters.firstOrNull() ?: 150f
            Log.d(TAG, "Destination loaded: $destinationLatLng, radius=$geofenceRadius")
        }
    }

    private fun checkAutoStop() {
        val lastPoint = collectedPoints.lastOrNull() ?: return
        destinationPlace?.let { place ->
            if (Places.isAt(place, lastPoint)) {
                Log.d(TAG, "Auto-stop: at the $place outline")
                stopTracking()
            }
            return
        }
        val dest = destinationLatLng ?: return
        val destPoint = RoutePoint(latitude = dest.latitude, longitude = dest.longitude, timestamp = 0L)
        val distToDest = haversineMeters(lastPoint, destPoint)
        if (collectedPoints.size % 10 == 0) {
            Log.d(TAG, "checkAutoStop: dist=${distToDest}m, radius=$geofenceRadius, dest=$dest")
        }
        if (distToDest < geofenceRadius) {
            Log.d(TAG, "Auto-stop: within ${distToDest}m of destination")
            stopTracking()
        }
    }

    private fun requestLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
            .setMinUpdateIntervalMillis(1_500L)
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
        if (stopping) return // auto-stop fires on every fix inside the radius until updates stop
        stopping = true
        fusedLocationClient.removeLocationUpdates(locationCallback)
        trackingStateHolder.emit(TrackingState.Idle)
        val tripId = currentTripId
        writeScope.launch {
            // after the queued point writes; if the service was restarted it no longer knows the
            // trip, so close whichever one is still open
            val id = if (tripId != -1L) tripId else tripRepository.getActiveTrip().firstOrNull()?.id
            if (id != null) stopTripUseCase(id)
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
