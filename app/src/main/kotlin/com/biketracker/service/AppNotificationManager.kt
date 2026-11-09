package com.biketracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.biketracker.ui.MainActivity
import com.biketracker.data.local.database.entity.TripDirection
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_TRACKING = "tracking"
        const val CHANNEL_GEOFENCE = "geofence"
        const val CHANNEL_WORK_TIMER = "work_timer"
        const val NOTIF_ID_TRACKING = 1001
        const val NOTIF_ID_PACKING = 1002
        const val NOTIF_ID_WORK_DONE = 1003
        const val NOTIF_ID_START_HOME = 1004
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createNotificationChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_TRACKING, "Route Tracking", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while actively tracking a bike route"
            },
            NotificationChannel(CHANNEL_GEOFENCE, "Location Alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Packing reminders and trip prompts based on your location"
            },
            NotificationChannel(CHANNEL_WORK_TIMER, "Work Timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when it's time to leave work"
            }
        )
        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun buildTrackingNotification(distanceMeters: Float, speedKmh: Float): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_TRACKING)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Tracking route…")
            .setContentText("%.1f km · %.1f km/h".format(distanceMeters / 1000f, speedKmh))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun serviceIntent(action: String, code: Int, direction: TripDirection? = null): PendingIntent {
        val intent = Intent(context, TrackingService::class.java).setAction(action)
            .apply { direction?.let { putExtra(TrackingService.EXTRA_DIRECTION, it.name) } }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        // "Start ride" may have to bring the service up from nothing (the morning prompt)
        return if (action == TrackingService.ACTION_CONFIRM) PendingIntent.getForegroundService(context, code, intent, flags)
        else PendingIntent.getService(context, code, intent, flags)
    }

    /** "Heading to the office?" at 8:00 (MorningPrompt): Start ride starts the trip, nothing else does. */
    fun showMorningPrompt() {
        val start = serviceIntent(TrackingService.ACTION_CONFIRM, 6, TripDirection.HOME_TO_OFFICE)
        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Heading to the office?")
            .setContentText("Start ride to track it.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(start)
            .addAction(android.R.drawable.ic_media_play, "Start ride", start)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Not now", serviceIntent(TrackingService.ACTION_DISMISS, 7))
            .setAutoCancel(true)
            .setTimeoutAfter(2 * 3_600_000L) // stale by mid-morning
            .build()
        manager.notify(NOTIF_ID_START_HOME, notification)
    }

    fun showPackingChecklistNotification(items: List<String> = emptyList()) {
        val style = if (items.isNotEmpty()) {
            NotificationCompat.InboxStyle().also { s ->
                items.forEach { s.addLine("• $it") }
            }
        } else null

        val builder = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to leave!")
            .setContentText("Remember to pack everything before heading out.")
            .setAutoCancel(true)
        if (style != null) builder.setStyle(style)
        manager.notify(NOTIF_ID_PACKING, builder.build())
    }

    fun showWorkCountdownCompleteNotification(arrivalTime: Long) {
        val since = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(arrivalTime))
        val hours = (System.currentTimeMillis() - arrivalTime) / 3_600_000.0
        val start = serviceIntent(TrackingService.ACTION_CONFIRM, 8, TripDirection.OFFICE_TO_HOME)
        val notification = NotificationCompat.Builder(context, CHANNEL_WORK_TIMER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Time to head home!")
            .setContentText("At the office since $since (%.1f h). Start ride when you're on the bike.".format(hours))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(start)
            .addAction(android.R.drawable.ic_media_play, "Start ride", start)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Not now", serviceIntent(TrackingService.ACTION_DISMISS, 9))
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID_WORK_DONE, notification)
    }

    /** Both ride prompts (8:00 and leave-work), once a ride starts or one is dismissed. */
    fun cancelRidePrompts() {
        manager.cancel(NOTIF_ID_START_HOME)
        manager.cancel(NOTIF_ID_WORK_DONE)
    }
}
