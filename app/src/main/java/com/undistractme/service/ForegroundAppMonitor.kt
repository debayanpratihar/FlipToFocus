package com.undistractme.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the package currently in the foreground using UsageStatsManager events.
 *
 * This deliberately uses [UsageStatsManager.queryEvents] over a small rolling window and returns
 * the package of the most-recent MOVE_TO_FOREGROUND (ACTIVITY_RESUMED) event. It does NOT perform
 * any aggregation and never inspects package contents beyond the foreground event, keeping the
 * work cheap enough to run on the service poll interval.
 */
@Singleton
class ForegroundAppMonitor @Inject constructor(
    private val usageStatsManager: UsageStatsManager
) {

    /**
     * @return the package name of the app most recently moved to the foreground within the last
     * [lookbackMs] milliseconds, or null if no foreground event was observed in that window.
     */
    fun currentForegroundPackage(lookbackMs: Long = 10_000L): String? {
        val end = System.currentTimeMillis()
        val begin = end - lookbackMs
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var latestPackage: String? = null
        var latestTimestamp = Long.MIN_VALUE
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // MOVE_TO_FOREGROUND is equivalent to ACTIVITY_RESUMED.
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestPackage = event.packageName
                }
            }
        }
        return latestPackage
    }
}
