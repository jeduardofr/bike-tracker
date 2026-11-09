package com.biketracker.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.location.Location
import com.biketracker.domain.repository.SettingsRepository
import com.google.android.gms.location.FusedLocationProviderClient
import java.time.ZoneId
import androidx.core.content.ContextCompat
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.TripRepository
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notices you heading out on the bike and asks before saving a ride, without losing its start.
 *
 * Leaving the office (or home) geofence starts a watch: TrackingService records GPS into memory
 * only. When Play services' activity recognition says you're cycling, "Heading home?" asks; "Start
 * ride" turns the watch into a trip that begins where you left, so the minutes it took to be sure
 * aren't lost. "Not now", no answer, or no cycling within [TrackingService.WATCH_MS] (a walk to
 * lunch) drops the watch; nothing was written, so nothing is left behind.
 *
 * Cycling can be detected before the fence's EXIT arrives (it often lags): then a watch starts
 * right away if the last known position is near the place, and asks at once.
 *
 * Starting the location foreground service from these broadcasts is allowed: geofence and
 * activity-transition events are background-start exemptions, and the app holds background location.
 */
@Singleton
class HeadingOut @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val trackingState: TrackingStateHolder,
    private val notifications: AppNotificationManager,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    private val prefs = context.getSharedPreferences("heading_out", Context.MODE_PRIVATE)

    fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Cycling start/stop updates; safe to call on every launch and after boot. */
    @SuppressLint("MissingPermission")
    suspend fun register() {
        if (!hasPermission()) return
        val transitions = listOf(ActivityTransition.ACTIVITY_TRANSITION_ENTER, ActivityTransition.ACTIVITY_TRANSITION_EXIT).map {
            ActivityTransition.Builder().setActivityType(DetectedActivity.ON_BICYCLE).setActivityTransition(it).build()
        }
        try {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent())
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "activity transitions not registered", e)
        }
    }

    suspend fun leftFence(place: String, at: Long) {
        prefs.edit().putLong("exit_$place", at).apply()
        val direction = directionFrom(place)
        if (!worthWatching(direction)) return
        watch(direction)
        if (prefs.getLong("cycling_since", 0L) > at - WINDOW_MS) ask(direction, at)
    }

    suspend fun cycling(started: Boolean, at: Long) {
        prefs.edit().putLong("cycling_since", if (started) at else 0L).apply()
        if (!started) return
        trackingState.watching?.let { return ask(it, exitTime(it) ?: at) }
        // no watch: the fence's EXIT may not have arrived yet; are we just leaving a place?
        val place = nearPlace() ?: return
        val direction = directionFrom(place)
        if (!worthWatching(direction)) return
        watch(direction)
        ask(direction, at)
    }

    private fun directionFrom(place: String) = if (place == PLACE_OFFICE) TripDirection.OFFICE_TO_HOME else TripDirection.HOME_TO_OFFICE

    private fun exitTime(direction: TripDirection): Long? {
        val place = if (direction == TripDirection.OFFICE_TO_HOME) PLACE_OFFICE else PLACE_HOME
        return prefs.getLong("exit_$place", 0L).takeIf { it > System.currentTimeMillis() - WINDOW_MS }
    }

    /** Not while tracking or already watching, and not once that ride is done today. */
    private suspend fun worthWatching(direction: TripDirection): Boolean {
        if (trackingState.watching != null || tripRepository.getActiveTrip().firstOrNull() != null) return false
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = tripRepository.getTripsInRange(dayStart, System.currentTimeMillis() + 1).firstOrNull().orEmpty()
        return today.none { it.direction == direction }
    }

    private fun watch(direction: TripDirection) {
        Log.d(TAG, "watching for $direction")
        ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).apply {
            action = TrackingService.ACTION_WATCH
            putExtra(TrackingService.EXTRA_DIRECTION, direction.name)
        })
    }

    private fun ask(direction: TripDirection, leftAt: Long) {
        if (trackingState.prompted) return
        trackingState.prompted = true
        notifications.showHeadingOutNotification(direction, leftAt)
    }

    @SuppressLint("MissingPermission")
    private suspend fun nearPlace(): String? {
        val here = try {
            fusedLocationClient.lastLocation.await()
        } catch (e: SecurityException) {
            null
        } ?: return null
        val radius = maxOf(settingsRepository.geofenceRadiusMeters.firstOrNull() ?: 150f, 150f) * 4
        for ((place, at) in listOf(PLACE_OFFICE to settingsRepository.officeLocation.firstOrNull(), PLACE_HOME to settingsRepository.homeLocation.firstOrNull())) {
            at ?: continue
            val d = FloatArray(1)
            Location.distanceBetween(here.latitude, here.longitude, at.latitude, at.longitude, d)
            if (d[0] <= radius) return place
        }
        return null
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, ActivityTransitionReceiver::class.java),
        // mutable: Play services fills in the transition result
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    companion object {
        const val PLACE_HOME = "home"
        const val PLACE_OFFICE = "office"
        private const val WINDOW_MS = 20 * 60_000L
        private const val TAG = "HeadingOut"
    }
}

@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {
    @Inject lateinit var headingOut: HeadingOut

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty()
            .filter { it.activityType == DetectedActivity.ON_BICYCLE }
        if (events.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                for (e in events) {
                    headingOut.cycling(e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER, System.currentTimeMillis())
                }
            } finally {
                pending.finish()
            }
        }
    }
}
