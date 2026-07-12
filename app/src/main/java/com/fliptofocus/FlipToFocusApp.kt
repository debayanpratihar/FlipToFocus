package com.fliptofocus

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * Annotated with [HiltAndroidApp] so Hilt can generate the application-level dependency container.
 * On startup it installs a crash logger that records any uncaught exception so the next launch can
 * display it for easy reporting.
 */
@HiltAndroidApp
class FlipToFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    /**
     * Records any uncaught exception (stack trace + timestamp) to SharedPreferences, then delegates
     * to the platform's default handler. This does NOT swallow crashes - it only captures them so
     * [MainActivity] can display the trace on the next launch.
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

    companion object {
        const val CRASH_PREFS = "ftf_crash_report"
        const val KEY_TRACE = "trace"
        const val KEY_TIME = "time"
    }
}
