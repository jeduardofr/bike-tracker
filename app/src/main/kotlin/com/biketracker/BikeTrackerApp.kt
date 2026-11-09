package com.biketracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltAndroidApp
class BikeTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appNotificationManager: com.biketracker.service.AppNotificationManager

    @Inject
    lateinit var tursoSyncScheduler: com.biketracker.data.sync.TursoSyncScheduler

    @Inject
    lateinit var tripRepository: com.biketracker.domain.repository.TripRepository

    @Inject
    lateinit var geofencingManager: com.biketracker.service.GeofencingManager

    @Inject
    lateinit var leaveReminder: com.biketracker.service.LeaveReminder


    @Inject
    lateinit var morningPrompt: com.biketracker.service.MorningPrompt

    override fun onCreate() {
        super.onCreate()
        appNotificationManager.createNotificationChannels()
        tursoSyncScheduler.schedulePeriodicSync()
        // catch up on anything that missed its post-trip sync
        tursoSyncScheduler.syncNow()
        // the old 8 h WorkManager countdown, replaced by LeaveReminder's alarm
        androidx.work.WorkManager.getInstance(this).cancelUniqueWork("work_countdown")
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            // trips closed hours late before end times came from the last GPS point
            tripRepository.repairEndTimes()
            // geofences vanish when location is switched off or Play services resets; re-adding
            // them is cheap (inside the office, its ENTER just confirms today's earliest arrival)
            geofencingManager.registerGeofences()
            leaveReminder.restore()
            morningPrompt.schedule()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
