package com.fliptofocus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.fliptofocus.MainActivity
import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.domain.repository.FocusSessionRepository
import com.fliptofocus.sensor.SensorChallengeManager
import com.fliptofocus.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that watches the current foreground app and, when a user-selected distracting
 * app is opened, forces the offline sensor challenge via an overlay before that app is usable.
 *
 * Compliance guarantees enforced here:
 *  - The challenge is only triggered for apps the user explicitly enabled in the blocklist.
 *  - [com.android.settings] is NEVER a trigger, and if the user reaches Settings any active
 *    overlay is torn down so we never draw over Settings.
 *  - Leaving the blocked app (Home / Recents) tears down the overlay and abandons the session,
 *    so the user can always leave.
 *  - "End session early" (surfaced by the overlay) abandons the session and grants access,
 *    preserving user autonomy.
 */
@AndroidEntryPoint
class AppBlockerService : LifecycleService() {

    @Inject lateinit var blockedAppRepository: BlockedAppRepository
    @Inject lateinit var appConfigRepository: AppConfigRepository
    @Inject lateinit var focusSessionRepository: FocusSessionRepository
    @Inject lateinit var foregroundAppMonitor: ForegroundAppMonitor
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var sensorChallengeManager: SensorChallengeManager

    // Cached repository state, refreshed via collected flows.
    @Volatile private var cachedConfig: AppConfig = AppConfig()
    @Volatile private var cachedEnabledPackages: Set<String> = emptySet()
    @Volatile private var cachedLabels: Map<String, String> = emptyMap()

    // State machine.
    private var activeBlockedPkg: String? = null
    private var activeSessionId: Long? = null
    private var challengeSatisfiedPkg: String? = null

    private var monitoringJob: Job? = null
    private var sensorStateJob: Job? = null

    private val isMonitoring: Boolean
        get() = monitoringJob?.isActive == true

    private val ownPackage: String
        get() = packageName

    @Volatile private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        runCatching { ensureNotificationChannel() }
        foregroundStarted = enterForeground()
        if (!foregroundStarted) {
            // Android 12+ can refuse a foreground-service start that originates from the background.
            // Bail out cleanly instead of crashing the process; blocking is re-armed the next time
            // the user opens the app (from the foreground, where the start is always allowed).
            stopSelf()
            return
        }
        runCatching { startCaches() }
    }

    /** Enters the foreground, returning false if the platform refused (never throws). */
    private fun enterForeground(): Boolean = try {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                Constants.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(Constants.FOREGROUND_NOTIFICATION_ID, notification)
        }
        true
    } catch (t: Throwable) {
        false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!foregroundStarted) {
            // We never entered the foreground (see onCreate); do not run the monitor and let the
            // service stop rather than restart into a crash-again loop.
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            Constants.ACTION_START -> startMonitoring()
            Constants.ACTION_STOP -> stopBlocking()
            // A null intent (or one without our action) means START_STICKY recreated us after a
            // low-memory kill. Re-arm monitoring so the persistent notification never lies about
            // blocking being active while nothing is actually inspected.
            else -> reArmAfterStickyRestart()
        }
        // NOT sticky: do not let the system resurrect this service in the background, where an
        // Android 14 foreground-service start can fail with an uncatchable async exception and
        // crash-loop. Blocking is re-armed from MainActivity.onResume when the app is opened.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        monitoringJob?.cancel()
        sensorStateJob?.cancel()
        sensorChallengeManager.stop()
        overlayManager.hideOverlay()
        activeBlockedPkg = null
        activeSessionId = null
        challengeSatisfiedPkg = null
        super.onDestroy()
    }

    // --- Cached state -------------------------------------------------------------------------

    private fun startCaches() {
        appConfigRepository.observeConfig()
            .onEach { cachedConfig = it }
            .catch { /* never let a repository error crash the service */ }
            .launchIn(lifecycleScope)
        blockedAppRepository.observeBlockedApps()
            .onEach { apps ->
                cachedLabels = apps.associate { it.packageName to it.appLabel }
                cachedEnabledPackages = apps.asSequence()
                    .filter { it.isEnabled }
                    .map { it.packageName }
                    .toSet()
            }
            .catch { /* never let a repository error crash the service */ }
            .launchIn(lifecycleScope)
    }

    // --- Monitoring loop ----------------------------------------------------------------------

    /**
     * Restores monitoring after a sticky restart. The cached config flow may not have emitted yet,
     * so read the persisted [AppConfig] once and only re-arm when the user still has blocking
     * enabled. [startMonitoring] is idempotent via the [isMonitoring] guard.
     */
    private fun reArmAfterStickyRestart() {
        lifecycleScope.launch {
            val enabled = runCatching {
                appConfigRepository.getConfig().isBlockingEnabled
            }.getOrDefault(cachedConfig.isBlockingEnabled)
            if (enabled) {
                startMonitoring()
            }
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        monitoringJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    tick()
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    // Never let a transient failure (e.g. a Room read) kill the monitor.
                }
                delay(Constants.POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopBlocking() {
        // Abandon any in-progress challenge cleanly before we stop, then leave the foreground.
        lifecycleScope.launch {
            teardownActiveChallenge(abandon = true)
            challengeSatisfiedPkg = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun tick() {
        val fg = withContext(Dispatchers.Default) {
            foregroundAppMonitor.currentForegroundPackage()
        } ?: return

        val config = cachedConfig
        val enabled = cachedEnabledPackages
        val isSettings = fg == SETTINGS_PACKAGE

        val shouldStart = !isSettings &&
            config.isBlockingEnabled &&
            enabled.contains(fg) &&
            fg != challengeSatisfiedPkg &&
            activeBlockedPkg != fg

        if (shouldStart) {
            startChallengeFor(fg)
            return
        }

        // If we are not sitting on the app we are actively challenging, ensure the overlay is gone.
        // This also covers Settings: we never start there, and if the user reaches it any overlay
        // is removed so we do not draw over Settings.
        if (fg != activeBlockedPkg) {
            handleLeftBlockedApp(fg)
        }
    }

    // --- State transitions --------------------------------------------------------------------

    private fun startChallengeFor(pkg: String) {
        // Mark synchronously so subsequent ticks do not re-trigger while we set up.
        activeBlockedPkg = pkg
        val config = cachedConfig
        val durationMillis = config.challengeDurationMinutes.toLong() * 60_000L
        lifecycleScope.launch {
            val sessionId = focusSessionRepository.startSession(pkg, durationMillis)
            // The user may have left while the session was being created; roll back if so.
            if (activeBlockedPkg != pkg) {
                focusSessionRepository.abandonSession(sessionId)
                return@launch
            }
            activeSessionId = sessionId
            sensorChallengeManager.start(
                type = config.challengeType,
                durationMillis = durationMillis,
                requireFaceDown = config.requireFaceDown,
                motionTolerance = config.motionTolerance,
                shakeTarget = config.shakeCount,
                mathTotal = config.mathProblemCount
            )
            overlayManager.showOverlay(
                triggeringLabel = labelFor(pkg),
                stateFlow = sensorChallengeManager.state,
                onEndEarly = { endEarly() },
                onMathAnswer = { sensorChallengeManager.submitMathAnswer(it) }
            )
            observeChallengeCompletion(pkg)
        }
    }

    private fun observeChallengeCompletion(pkg: String) {
        sensorStateJob?.cancel()
        sensorStateJob = sensorChallengeManager.state
            .onEach { state ->
                if (state.isComplete) {
                    handleChallengeCompleted(pkg)
                }
            }
            .launchIn(lifecycleScope)
    }

    private suspend fun handleChallengeCompleted(pkg: String) {
        val id = activeSessionId ?: return
        // Clear state first so the reset emission from stop() and any stray tick are no-ops.
        activeSessionId = null
        activeBlockedPkg = null
        challengeSatisfiedPkg = pkg
        focusSessionRepository.completeSession(id)
        sensorChallengeManager.stop()
        overlayManager.hideOverlay()
    }

    /** Invoked from the overlay's confirmed "End session early" action. */
    private fun endEarly() {
        val pkg = activeBlockedPkg
        val id = activeSessionId
        activeSessionId = null
        activeBlockedPkg = null
        // Ending early grants access to preserve user autonomy; the app is treated as satisfied
        // until the user leaves it.
        if (pkg != null) challengeSatisfiedPkg = pkg
        sensorStateJob?.cancel()
        sensorChallengeManager.stop()
        overlayManager.hideOverlay()
        if (id != null) {
            lifecycleScope.launch { focusSessionRepository.abandonSession(id) }
        }
    }

    private fun handleLeftBlockedApp(fg: String) {
        val activePkg = activeBlockedPkg
        if (activePkg != null) {
            val id = activeSessionId
            // Clear synchronously to prevent repeated abandon calls from back-to-back ticks.
            activeSessionId = null
            activeBlockedPkg = null
            sensorStateJob?.cancel()
            sensorChallengeManager.stop()
            overlayManager.hideOverlay()
            if (id != null) {
                lifecycleScope.launch { focusSessionRepository.abandonSession(id) }
            }
        }
        // Once the user moves to a genuinely different app (not the satisfied app, not our own UI),
        // clear the satisfied marker so re-opening the blocked app requires the challenge again.
        if (fg != challengeSatisfiedPkg && fg != ownPackage) {
            challengeSatisfiedPkg = null
        }
    }

    private suspend fun teardownActiveChallenge(abandon: Boolean) {
        monitoringJob?.cancel()
        monitoringJob = null
        val id = activeSessionId
        activeSessionId = null
        activeBlockedPkg = null
        sensorStateJob?.cancel()
        sensorChallengeManager.stop()
        overlayManager.hideOverlay()
        if (abandon && id != null) {
            focusSessionRepository.abandonSession(id)
        }
    }

    private fun labelFor(pkg: String): String = cachedLabels[pkg] ?: pkg

    // --- Notification -------------------------------------------------------------------------

    private fun ensureNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                Constants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification while focus blocking is active."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Focus blocking is active")
            .setContentText("Watching for distracting apps. Tap to open FlipToFocus.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
