package com.biketracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BikeTrackerApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appNotificationManager: com.biketracker.service.AppNotificationManager

    @Inject
    lateinit var tursoSyncScheduler: com.biketracker.data.sync.TursoSyncScheduler

    override fun onCreate() {
        super.onCreate()
        appNotificationManager.createNotificationChannels()
        tursoSyncScheduler.schedulePeriodicSync()
        // catch up on anything that missed its post-trip sync
        tursoSyncScheduler.syncNow()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
