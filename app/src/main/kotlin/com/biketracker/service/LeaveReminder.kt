package com.biketracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.biketracker.data.local.database.dao.WorkSessionDao
import com.biketracker.data.local.database.entity.WorkSessionEntity
import com.biketracker.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Time to head home", [workHours] after arriving at the office.
 *
 * Arrival comes from the ride in (the last GPS point of a trip to the office that ends there),
 * with the office geofence's ENTER as a fallback for days without a tracked ride. The earliest
 * arrival of the day wins, so a geofence that fires late, GPS jitter at the fence edge or coming
 * back from lunch never pushes the reminder later. Starting the ride home cancels it.
 *
 * It's an exact alarm (USE_EXACT_ALARM, granted to this sideloaded app): WorkManager's 8 h delay
 * could be deferred for hours once the app sat unused in a low standby bucket.
 */
@Singleton
class LeaveReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workSessionDao: WorkSessionDao,
    private val settingsRepository: SettingsRepository,
    private val notifications: AppNotificationManager
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun dateOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    suspend fun arrived(at: Long, source: String) {
        val date = dateOf(at)
        val existing = workSessionDao.getSessionForDate(date)
        if (existing != null && (existing.departureTime != null || existing.arrivalTime <= at)) {
            // already arrived earlier today (or already left): keep it, just make sure it's armed
            if (existing.departureTime == null) schedule(existing.targetDepartureTime)
            return
        }
        val hours = settingsRepository.workHours.firstOrNull() ?: 8f
        val target = at + (hours * 3_600_000).toLong()
        val session = WorkSessionEntity(id = existing?.id ?: 0, date = date, arrivalTime = at, targetDepartureTime = target)
        if (existing == null) workSessionDao.insertOrReplace(session) else workSessionDao.update(session)
        Log.d(TAG, "arrived at $at via $source, reminder at $target")
        schedule(target)
    }

    suspend fun departed(at: Long) {
        val session = workSessionDao.getSessionForDate(dateOf(at)) ?: return
        if (session.departureTime == null) workSessionDao.update(session.copy(departureTime = at))
        alarms.cancel(pendingIntent())
    }

    /** After a reboot (alarms are wiped) or an app start: re-arms today's reminder if it's still ahead. */
    suspend fun restore() {
        val session = workSessionDao.getSessionForDate(LocalDate.now().toString()) ?: return
        if (session.departureTime == null && session.targetDepartureTime > System.currentTimeMillis()) {
            schedule(session.targetDepartureTime)
        }
    }

    /** Called by the alarm: notifies unless the ride home has already started. */
    suspend fun fire() {
        val session = workSessionDao.getSessionForDate(LocalDate.now().toString()) ?: return
        if (session.departureTime != null) return
        notifications.showWorkCountdownCompleteNotification(session.arrivalTime)
    }

    private fun schedule(at: Long) {
        // a late arrival record (e.g. a trip closed hours later) shouldn't fire a stale reminder
        if (at < System.currentTimeMillis() - 30 * 60_000L) return
        val pi = pendingIntent()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, LeaveReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object {
        const val TAG = "LeaveReminder"
    }
}

@AndroidEntryPoint
class LeaveReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var leaveReminder: LeaveReminder

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                leaveReminder.fire()
            } finally {
                pending.finish()
            }
        }
    }
}
