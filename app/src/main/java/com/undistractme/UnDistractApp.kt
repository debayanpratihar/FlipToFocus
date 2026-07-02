package com.undistractme

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.undistractme.util.Constants
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Annotated with [HiltAndroidApp] so Hilt can generate the application-level
 * dependency container. On startup it registers the low-importance notification
 * channel used by the foreground blocking service (channels are required from
 * API 26, which is this app's minSdk).
 */
@HiltAndroidApp
class UnDistractApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    Constants.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows while a focus break is being enforced."
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
