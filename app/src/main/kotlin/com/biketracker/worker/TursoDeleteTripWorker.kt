package com.biketracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biketracker.data.remote.TursoClient
import com.biketracker.data.remote.TursoStatement
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Deletes a trip (and its route points) from Turso after it was deleted locally. */
@HiltWorker
class TursoDeleteTripWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tursoClient: TursoClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!tursoClient.isConfigured) return@withContext Result.success()
        val uuid = inputData.getString(KEY_TRIP_UUID) ?: return@withContext Result.success()

        try {
            tursoClient.execute(
                listOf(
                    TursoStatement("DELETE FROM route_points WHERE trip_uuid = ?", listOf(uuid)),
                    TursoStatement("DELETE FROM trips WHERE uuid = ?", listOf(uuid))
                )
            )
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Remote delete failed for trip $uuid, will retry", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_TRIP_UUID = "trip_uuid"
        private const val TAG = "TursoDeleteTripWorker"
    }
}
