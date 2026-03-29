package com.biketracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.biketracker.data.local.database.dao.WorkSessionDao
import com.biketracker.data.local.database.entity.WorkSessionEntity
import com.biketracker.domain.repository.SettingsRepository
import com.biketracker.worker.WorkCountdownWorker
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var appNotificationManager: AppNotificationManager
    @Inject lateinit var geofencingManager: GeofencingManager
    @Inject lateinit var workSessionDao: WorkSessionDao
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            goAsync {
                geofencingManager.registerGeofences()
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
                    handleOfficeEnter(context)
                }
                transition == Geofence.GEOFENCE_TRANSITION_EXIT && fenceIds.contains(GeofencingManager.GEOFENCE_ID_OFFICE) -> {
                    handleOfficeExit(context)
                }
            }
        }
    }

    private suspend fun handleOfficeEnter(context: Context) {
        val now = System.currentTimeMillis()
        val workHours = settingsRepository.workHours.firstOrNull() ?: 8f
        val targetDeparture = now + (workHours * 3600 * 1000).toLong()
        val today = LocalDate.now().toString()

        workSessionDao.insertOrReplace(
            WorkSessionEntity(
                date = today,
                arrivalTime = now,
                targetDepartureTime = targetDeparture
            )
        )

        val workRequest = OneTimeWorkRequestBuilder<WorkCountdownWorker>()
            .setInitialDelay(workHours.toLong(), TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("work_countdown", ExistingWorkPolicy.REPLACE, workRequest)
    }

    private fun handleOfficeExit(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("work_countdown")
        appNotificationManager.showStartHomeTripNotification()
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
