package com.biketracker.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biketracker.data.local.database.dao.RoutePointDao
import com.biketracker.data.local.database.dao.TripDao
import com.biketracker.data.local.database.entity.RoutePointEntity
import com.biketracker.data.local.database.entity.TripEntity
import com.biketracker.data.remote.TursoClient
import com.biketracker.data.remote.TursoStatement
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pushes completed, not-yet-synced trips (with their route points) to Turso. */
@HiltWorker
class TursoSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val tripDao: TripDao,
    private val routePointDao: RoutePointDao,
    private val tursoClient: TursoClient
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!tursoClient.isConfigured) return@withContext Result.success()

        val unsynced = tripDao.getUnsyncedTrips()
        for (trip in unsynced) {
            try {
                val points = routePointDao.getPointsForTripOnce(trip.id)
                tursoClient.execute(buildStatements(trip, points))
                tripDao.markSynced(trip.id, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed for trip ${trip.uuid}, will retry", e)
                return@withContext Result.retry()
            }
        }
        Result.success()
    }

    private fun buildStatements(trip: TripEntity, points: List<RoutePointEntity>): List<TursoStatement> {
        val statements = mutableListOf(
            TursoStatement(
                """
                INSERT INTO trips (uuid, start_time, end_time, distance_meters, average_speed_kmh, direction, is_completed)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    end_time = excluded.end_time,
                    distance_meters = excluded.distance_meters,
                    average_speed_kmh = excluded.average_speed_kmh,
                    direction = excluded.direction,
                    is_completed = excluded.is_completed
                """.trimIndent(),
                listOf(
                    trip.uuid, trip.startTime, trip.endTime, trip.distanceMeters,
                    trip.averageSpeedKmh, trip.direction.name, trip.isCompleted
                )
            ),
            // re-syncing a trip replaces its points instead of duplicating them
            TursoStatement("DELETE FROM route_points WHERE trip_uuid = ?", listOf(trip.uuid))
        )
        points.chunked(POINTS_PER_STATEMENT).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "(?, ?, ?, ?, ?, ?, ?)" }
            statements += TursoStatement(
                "INSERT INTO route_points (trip_uuid, latitude, longitude, altitude, speed_mps, timestamp, accuracy) VALUES $placeholders",
                chunk.flatMap {
                    listOf<Any?>(
                        trip.uuid, it.latitude, it.longitude, it.altitude,
                        it.speedMps, it.timestamp, it.accuracy
                    )
                }
            )
        }
        return statements
    }

    companion object {
        private const val TAG = "TursoSyncWorker"
        private const val POINTS_PER_STATEMENT = 100
    }
}
