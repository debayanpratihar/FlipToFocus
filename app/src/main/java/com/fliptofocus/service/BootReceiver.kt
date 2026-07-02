package com.fliptofocus.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.fliptofocus.data.local.FlipToFocusDatabase
import com.fliptofocus.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts the [AppBlockerService] after a reboot, but only if the user had blocking enabled.
 *
 * Kept intentionally minimal and safe: it reads the persisted config with a lightweight Room
 * instance under [goAsync], and starts the service only when a config row exists and blocking is
 * enabled. It never re-arms blocking that the user turned off, and does nothing else.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var database: FlipToFocusDatabase? = null
            try {
                database = Room.databaseBuilder(
                    appContext,
                    FlipToFocusDatabase::class.java,
                    "fliptofocus.db"
                ).fallbackToDestructiveMigration().build()

                val config = database.appConfigDao().get()
                if (config != null && config.isBlockingEnabled) {
                    val serviceIntent = Intent(appContext, AppBlockerService::class.java).apply {
                        action = Constants.ACTION_START
                    }
                    appContext.startForegroundService(serviceIntent)
                }
            } catch (t: Throwable) {
                // Boot restore is best-effort; never crash on failure.
            } finally {
                runCatching { database?.close() }
                pendingResult.finish()
            }
        }
    }
}
