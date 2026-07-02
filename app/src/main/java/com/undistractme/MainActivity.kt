package com.undistractme

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.undistractme.service.AppBlockerService
import com.undistractme.ui.navigation.AppNavigation
import com.undistractme.ui.theme.UnDistractMeTheme
import com.undistractme.util.Constants
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the whole Compose UI.
 *
 * Responsibilities:
 *  - Host [AppNavigation], which decides whether to open onboarding or home
 *    based on the current permission state.
 *  - Request the POST_NOTIFICATIONS runtime permission on API 33+ (the ongoing
 *    foreground-service notification needs it to be visible).
 *  - Expose start/stop helpers so screens can (re)start the blocking service
 *    through simple callbacks rather than touching the service class directly.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The result is intentionally not acted upon here: notifications are a
            // nice-to-have surface for the ongoing service, and blocking still
            // functions if the user declines. Re-prompting is avoided per policy.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        setContent {
            UnDistractMeTheme {
                AppNavigation(
                    startService = ::startBlockingService,
                    stopService = ::stopBlockingService
                )
            }
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /** Starts (or refreshes) the foreground blocking service. */
    fun startBlockingService() {
        val intent = Intent(this, AppBlockerService::class.java)
            .setAction(Constants.ACTION_START)
        ContextCompat.startForegroundService(this, intent)
    }

    /** Signals the blocking service to tear itself down. */
    fun stopBlockingService() {
        val intent = Intent(this, AppBlockerService::class.java)
            .setAction(Constants.ACTION_STOP)
        ContextCompat.startForegroundService(this, intent)
    }
}
