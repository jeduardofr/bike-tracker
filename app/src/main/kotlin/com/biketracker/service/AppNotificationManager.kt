package com.biketracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.biketracker.ui.MainActivity
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
            .setContentIntent(openAppIntent)
            .build()
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

    fun showWorkCountdownCompleteNotification() {
        val notification = NotificationCompat.Builder(context, CHANNEL_WORK_TIMER)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Time to head home!")
            .setContentText("You've been at work for 8 hours.")
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIF_ID_WORK_DONE, notification)
    }

    fun showStartHomeTripNotification() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_START_HOME_TRIP"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_GEOFENCE)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Back home?")
            .setContentText("Tap to start tracking your ride home.")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(NOTIF_ID_START_HOME, notification)
    }
}
