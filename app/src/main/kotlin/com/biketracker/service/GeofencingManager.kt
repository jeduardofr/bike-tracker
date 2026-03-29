package com.biketracker.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.biketracker.domain.repository.SettingsRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeofencingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geofencingClient: GeofencingClient,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val GEOFENCE_ID_HOME = "GEOFENCE_HOME"
        const val GEOFENCE_ID_OFFICE = "GEOFENCE_OFFICE"
        const val GEOFENCE_ACTION = "com.biketracker.GEOFENCE_EVENT"
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = GEOFENCE_ACTION
        }
        PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    suspend fun registerGeofences() {
        val home = settingsRepository.homeLocation.firstOrNull()
        val office = settingsRepository.officeLocation.firstOrNull()
        val radius = settingsRepository.geofenceRadiusMeters.firstOrNull() ?: 150f

        val geofences = mutableListOf<Geofence>()

        if (home != null) {
            geofences += Geofence.Builder()
                .setRequestId(GEOFENCE_ID_HOME)
                .setCircularRegion(home.latitude, home.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        if (office != null) {
            geofences += Geofence.Builder()
                .setRequestId(GEOFENCE_ID_OFFICE)
                .setCircularRegion(office.latitude, office.longitude, radius)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .build()
        }

        if (geofences.isEmpty()) return

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()

        try {
            geofencingClient.addGeofences(request, geofencePendingIntent).await()
        } catch (e: SecurityException) {
            // Background location permission not granted
        }
    }

    suspend fun unregisterGeofences() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent).await()
        } catch (_: Exception) {}
    }
}
