package com.fliptofocus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.service.AppBlockerService
import com.fliptofocus.ui.navigation.AppNavigation
import com.fliptofocus.ui.theme.FlipToFocusTheme
import com.fliptofocus.util.Constants
import com.fliptofocus.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    @Inject
    lateinit var appConfigRepository: AppConfigRepository

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
            FlipToFocusTheme {
                AppNavigation(
                    startService = ::startBlockingService,
                    stopService = ::stopBlockingService
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reArmBlockingIfEnabled()
    }

    /**
     * Resumes protection whenever the user opens the app: if blocking is enabled and both
     * permissions are present, (re)start the service. Android can refuse to restart a foreground
     * service from the background, so this foreground-triggered restart is the reliable way to
     * bring blocking back after the system stopped the service. It never starts while blocking is
     * off, so the persistent notification is never shown misleadingly.
     */
    private fun reArmBlockingIfEnabled() {
        lifecycleScope.launch {
            val config = runCatching { appConfigRepository.getConfig() }.getOrNull() ?: return@launch
            val permitted = PermissionUtils.hasUsageAccess(this@MainActivity) &&
                PermissionUtils.canDrawOverlays(this@MainActivity)
            if (config.isBlockingEnabled && permitted) {
                startBlockingService()
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
