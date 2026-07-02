package com.fliptofocus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.fliptofocus.util.Constants
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Annotated with [HiltAndroidApp] so Hilt can generate the application-level dependency container.
 * On startup it (1) installs a crash logger that records any uncaught exception so the next launch
 * can show it for reporting, and (2) registers the low-importance notification channel used by the
 * foreground blocking service.
 */
@HiltAndroidApp
class FlipToFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        createNotificationChannel()
    }

    /**
     * Records any uncaught exception (stack trace + timestamp) to SharedPreferences, then delegates
     * to the platform's default handler. This does NOT swallow crashes - it only captures them so
     * [MainActivity] can display the trace on the next launch for easy reporting.
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TRACE, throwable.stackTraceToString())
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .commit()
            }
            previous?.uncaughtException(thread, throwable)
        }
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

    companion object {
        const val CRASH_PREFS = "ftf_crash_report"
        const val KEY_TRACE = "trace"
        const val KEY_TIME = "time"
    }
}
