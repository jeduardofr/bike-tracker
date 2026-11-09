package com.biketracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biketracker.domain.repository.SettingsRepository
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var geofencingManager: GeofencingManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var leaveReminder: LeaveReminder

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            goAsync {
                geofencingManager.registerGeofences()
                leaveReminder.restore()
            }
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) return

        val transition = geofencingEvent.geofenceTransition
        val fenceIds = geofencingEvent.triggeringGeofences?.map { it.requestId } ?: return

        goAsync {
            when {
                transition == Geofence.GEOFENCE_TRANSITION_EXIT && fenceIds.contains(GeofencingManager.GEOFENCE_ID_HOME) -> {
                    val checklist = settingsRepository.packingChecklist.firstOrNull() ?: emptyList()
                    appNotificationManager.showPackingChecklistNotification(checklist)
                }
                transition == Geofence.GEOFENCE_TRANSITION_ENTER && fenceIds.contains(GeofencingManager.GEOFENCE_ID_OFFICE) -> {
                    // a fallback: the ride in usually sets the arrival first (StopTripUseCase)
                    leaveReminder.arrived(System.currentTimeMillis(), "geofence")
                }
                transition == Geofence.GEOFENCE_TRANSITION_EXIT && fenceIds.contains(GeofencingManager.GEOFENCE_ID_OFFICE) -> {
                    // no longer cancels the reminder: GPS jitter at the fence edge and lunch
                    // breaks exit too; starting the ride home is what cancels it
                    appNotificationManager.showStartHomeTripNotification()
                }
            }
        }
    }

    private fun goAsync(block: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                block()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
