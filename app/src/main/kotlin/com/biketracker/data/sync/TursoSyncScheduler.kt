package com.biketracker.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.biketracker.BuildConfig
import com.biketracker.worker.TursoDeleteTripWorker
import com.biketracker.worker.TursoSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TursoSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val isConfigured = BuildConfig.TURSO_DATABASE_URL.isNotBlank() &&
        BuildConfig.TURSO_AUTH_TOKEN.isNotBlank()

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Push unsynced trips as soon as network allows (called after a trip completes). */
    fun syncNow() {
        if (!isConfigured) return
        val request = OneTimeWorkRequestBuilder<TursoSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SYNC_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Safety net for trips whose immediate sync never went through. */
    fun schedulePeriodicSync() {
        if (!isConfigured) return
        val request = PeriodicWorkRequestBuilder<TursoSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_SYNC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun deleteRemoteTrip(uuid: String) {
        if (!isConfigured || uuid.isBlank()) return
        val request = OneTimeWorkRequestBuilder<TursoDeleteTripWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(TursoDeleteTripWorker.KEY_TRIP_UUID to uuid))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("$DELETE_WORK_PREFIX$uuid", ExistingWorkPolicy.KEEP, request)
    }

    companion object {
        private const val SYNC_WORK = "turso_sync"
        private const val PERIODIC_SYNC_WORK = "turso_sync_periodic"
        private const val DELETE_WORK_PREFIX = "turso_delete_"
    }
}
