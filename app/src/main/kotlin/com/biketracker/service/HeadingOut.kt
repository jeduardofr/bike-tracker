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
import androidx.core.content.ContextCompat
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.TripRepository
import com.biketracker.domain.usecase.StartTripUseCase
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
 * Notices you heading out on the bike and starts tracking the ride by itself.
 *
 * Two signals have to agree within [WINDOW_MS]: leaving the office (or home) geofence, and Play
 * services' activity recognition saying you're cycling. Either alone misfires: the fence on GPS
 * jitter or a walk to lunch, cycling on any ride. They arrive in either order (the fence's EXIT
 * often lags), so each one checks the other. At most one start per direction per day, none
 * while a trip is being tracked. The tracking notification has a Discard button for a wrong guess.
 *
 * Starting a location foreground service from the background is allowed here: geofence and
 * activity-transition broadcasts are exemptions, and the app holds background location.
 */
@Singleton
class HeadingOut @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val startTrip: StartTripUseCase
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
        check(at)
    }

    suspend fun cycling(started: Boolean, at: Long) {
        prefs.edit().putLong("cycling_since", if (started) at else 0L).apply()
        if (started) check(at)
    }

    private suspend fun check(now: Long) {
        val cyclingSince = prefs.getLong("cycling_since", 0L)
        if (cyclingSince == 0L) return
        if (tripRepository.getActiveTrip().firstOrNull() != null) return
        for ((place, direction) in listOf(PLACE_OFFICE to TripDirection.OFFICE_TO_HOME, PLACE_HOME to TripDirection.HOME_TO_OFFICE)) {
            val exit = prefs.getLong("exit_$place", 0L)
            if (exit == 0L || now - exit > WINDOW_MS || kotlin.math.abs(cyclingSince - exit) > WINDOW_MS) continue
            val key = "started_${direction.name}"
            val today = LocalDate.now().toString()
            if (prefs.getString(key, null) == today) continue
            prefs.edit().putString(key, today).apply()
            Log.d(TAG, "heading out: left $place at $exit, cycling since $cyclingSince")
            startTrip(direction).onSuccess { id ->
                ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).apply {
                    action = TrackingService.ACTION_START
                    putExtra(TrackingService.EXTRA_TRIP_ID, id)
                    putExtra(TrackingService.EXTRA_DIRECTION, direction.name)
                    putExtra(TrackingService.EXTRA_AUTO, true)
                })
            }
            return
        }
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
