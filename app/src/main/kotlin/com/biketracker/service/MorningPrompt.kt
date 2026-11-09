package com.biketracker.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.biketracker.data.local.database.entity.TripDirection
import com.biketracker.domain.repository.TripRepository
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Heading to the office? Start ride" at 8:00 on office days (Mon-Thu; Fridays are from home).
 * Only a question: the trip starts when it's answered. Skipped when a ride is being tracked or
 * today's ride in already exists. An exact alarm, re-armed for the next office day each time.
 */
@Singleton
class MorningPrompt @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository,
    private val notifications: AppNotificationManager
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun schedule() {
        val zone = ZoneId.systemDefault()
        var at = ZonedDateTime.of(LocalDate.now(zone), AT, zone)
        while (at.toInstant().toEpochMilli() <= System.currentTimeMillis() || at.dayOfWeek !in OFFICE_DAYS) at = at.plusDays(1)
        val ms = at.toInstant().toEpochMilli()
        val pi = PendingIntent.getBroadcast(
            context, 0, Intent(context, MorningPromptReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    suspend fun fire() {
        schedule() // the next office day first, whatever happens below
        if (tripRepository.getActiveTrip().firstOrNull() != null) return
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val today = tripRepository.getTripsInRange(dayStart, System.currentTimeMillis() + 1).firstOrNull().orEmpty()
        if (today.any { it.direction == TripDirection.HOME_TO_OFFICE }) return
        notifications.showMorningPrompt()
    }

    private companion object {
        val AT: LocalTime = LocalTime.of(8, 0)
        val OFFICE_DAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
    }
}

@AndroidEntryPoint
class MorningPromptReceiver : BroadcastReceiver() {
    @Inject lateinit var morningPrompt: MorningPrompt

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                morningPrompt.fire()
            } finally {
                pending.finish()
            }
        }
    }
}
