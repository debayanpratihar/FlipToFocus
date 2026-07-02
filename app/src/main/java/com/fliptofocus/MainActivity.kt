package com.fliptofocus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
 *  - Host [AppNavigation] (onboarding vs. home based on permission state).
 *  - Request POST_NOTIFICATIONS on API 33+ for the ongoing service notification.
 *  - Re-arm the blocking service when the app is opened (foreground-safe).
 *  - If the previous run crashed, surface the captured stack trace and skip auto-starting the
 *    service this launch so the app cannot get stuck in a crash loop.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appConfigRepository: AppConfigRepository

    private var recentlyCrashed = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Notifications are a nice-to-have surface for the ongoing service; blocking still
            // works if declined. Not re-prompted, per policy.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(FlipToFocusApp.CRASH_PREFS, Context.MODE_PRIVATE)
        val crashTrace = prefs.getString(FlipToFocusApp.KEY_TRACE, null)
        val crashAgeMs = System.currentTimeMillis() - prefs.getLong(FlipToFocusApp.KEY_TIME, 0L)
        // Only treat it as "recent" (and thus break the loop) if it happened moments ago.
        recentlyCrashed = crashTrace != null && crashAgeMs in 0..RECENT_CRASH_WINDOW_MS

        maybeRequestNotificationPermission()
        setContent {
            FlipToFocusTheme {
                var trace by remember { mutableStateOf(crashTrace) }
                AppNavigation(
                    startService = ::startBlockingService,
                    stopService = ::stopBlockingService
                )
                trace?.let { captured ->
                    CrashReportDialog(
                        trace = captured,
                        onDismiss = {
                            prefs.edit().clear().apply()
                            trace = null
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reArmBlockingIfEnabled()
    }

    /**
     * Resumes protection when the app is opened, if blocking is enabled and permissions are
     * present. Skipped right after a crash so the app can open and show the report instead of
     * immediately restarting the (possibly crashing) service.
     */
    private fun reArmBlockingIfEnabled() {
        if (recentlyCrashed) return
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

    /** Starts (or refreshes) the foreground blocking service. Never throws. */
    fun startBlockingService() {
        runCatching {
            val intent = Intent(this, AppBlockerService::class.java)
                .setAction(Constants.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    /** Signals the blocking service to tear itself down. Never throws. */
    fun stopBlockingService() {
        runCatching {
            val intent = Intent(this, AppBlockerService::class.java)
                .setAction(Constants.ACTION_STOP)
            ContextCompat.startForegroundService(this, intent)
        }
    }

    private companion object {
        const val RECENT_CRASH_WINDOW_MS = 60_000L
    }
}

@Composable
private fun CrashReportDialog(trace: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("FlipToFocus hit an error") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "The last run crashed. Please tap Copy and send this to the developer:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = trace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { clipboard.setText(AnnotatedString(trace)) }) {
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
