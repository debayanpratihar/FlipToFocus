package com.fliptofocus.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.fliptofocus.domain.model.AppConfig
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import com.fliptofocus.domain.repository.FocusSessionRepository
import com.fliptofocus.sensor.SensorChallengeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-app detector and focus-challenge orchestrator, implemented as an
 * [AccessibilityService].
 *
 * Why accessibility (and not a polling foreground service): the system delivers a
 * [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] event whenever the foreground app changes, which
 * is both more reliable and dramatically cheaper than polling UsageStats, and it removes the
 * fragile Android 14 `specialUse` foreground-service start path that could crash on some devices.
 *
 * Privacy / Play compliance:
 *  - Only window-state-change events are handled; window content retrieval is disabled, so no
 *    on-screen text or data is ever read. Only the foreground package name is used.
 *  - The overlay is shown ONLY over apps the user explicitly chose to block, never over Settings
 *    or the launcher, and the user can always leave via Home/Recents.
 *  - A confirmed "End session early" control preserves user autonomy.
 *
 * Every callback is wrapped defensively so a transient failure can never crash the process.
 */
@AndroidEntryPoint
class FocusAccessibilityService : AccessibilityService() {

    @Inject lateinit var blockedAppRepository: BlockedAppRepository
    @Inject lateinit var appConfigRepository: AppConfigRepository
    @Inject lateinit var focusSessionRepository: FocusSessionRepository
    @Inject lateinit var overlayManager: OverlayManager
    @Inject lateinit var challengeManager: SensorChallengeManager

    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(job + Dispatchers.Main.immediate)

    // Cached repository state, refreshed via collected flows.
    @Volatile private var cachedConfig: AppConfig = AppConfig()
    @Volatile private var cachedEnabled: Set<String> = emptySet()
    @Volatile private var cachedLabels: Map<String, String> = emptyMap()

    // State machine.
    private var activeBlockedPkg: String? = null
    private var activeSessionId: Long? = null
    private var challengeSatisfiedPkg: String? = null
    private var completionJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching { applyServiceInfo() }
        observeRepositories()
    }

    private fun applyServiceInfo() {
        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    private fun observeRepositories() {
        appConfigRepository.observeConfig()
            .onEach { config ->
                cachedConfig = config
                // If the user turns blocking off mid-challenge, release the overlay immediately.
                if (!config.isBlockingEnabled && activeBlockedPkg != null) {
                    teardown(abandon = true)
                }
            }
            .catch { /* never crash the service on a repository error */ }
            .launchIn(serviceScope)

        blockedAppRepository.observeBlockedApps()
            .onEach { apps ->
                cachedLabels = apps.associate { it.packageName to it.appLabel }
                cachedEnabled = apps.asSequence()
                    .filter { it.isEnabled }
                    .map { it.packageName }
                    .toSet()
                // If the app being challenged was disabled/removed, release it.
                val active = activeBlockedPkg
                if (active != null && !cachedEnabled.contains(active)) {
                    teardown(abandon = true)
                }
            }
            .catch { /* never crash the service on a repository error */ }
            .launchIn(serviceScope)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            // A single bad event must never take down the detector.
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString()
        if (pkg.isNullOrBlank()) return
        // Ignore our own windows (including the overlay itself) so we don't fight ourselves.
        if (pkg == packageName) return

        val config = cachedConfig
        val enabled = cachedEnabled
        val isBlockedNow = config.isBlockingEnabled && enabled.contains(pkg)

        // A blocked app just came forward and it isn't already satisfied/active: challenge it.
        if (isBlockedNow && pkg != challengeSatisfiedPkg && pkg != activeBlockedPkg) {
            startChallengeFor(pkg)
            return
        }

        // The foreground moved somewhere other than the app we're actively challenging.
        if (pkg != activeBlockedPkg) {
            if (activeBlockedPkg != null) {
                // User left the challenged app (Home/Recents/another app): release the overlay.
                teardown(abandon = true)
            }
            // Once the user genuinely moves to a different app, clear the satisfied marker so
            // re-opening the blocked app requires the challenge again.
            if (pkg != challengeSatisfiedPkg) {
                challengeSatisfiedPkg = null
            }
        }
    }

    private fun startChallengeFor(pkg: String) {
        // Mark synchronously so rapid repeat events don't double-trigger.
        activeBlockedPkg = pkg
        val config = cachedConfig
        val durationMillis = config.challengeDurationMinutes.toLong() * 60_000L

        serviceScope.launch {
            val sessionId = runCatching {
                focusSessionRepository.startSession(pkg, durationMillis)
            }.getOrNull()

            // The user may have left while the session row was being created; roll back if so.
            if (activeBlockedPkg != pkg) {
                if (sessionId != null) runCatching { focusSessionRepository.abandonSession(sessionId) }
                return@launch
            }
            activeSessionId = sessionId

            runCatching {
                challengeManager.start(
                    type = config.challengeType,
                    durationMillis = durationMillis,
                    requireFaceDown = config.requireFaceDown,
                    motionTolerance = config.motionTolerance,
                    shakeTarget = config.shakeCount,
                    mathTotal = config.mathProblemCount
                )
            }
            runCatching {
                overlayManager.showOverlay(
                    triggeringLabel = cachedLabels[pkg] ?: pkg,
                    stateFlow = challengeManager.state,
                    onEndEarly = { endEarly() },
                    onMathAnswer = { answer -> runCatching { challengeManager.submitMathAnswer(answer) } }
                )
            }
            observeCompletion(pkg)
        }
    }

    private fun observeCompletion(pkg: String) {
        completionJob?.cancel()
        completionJob = challengeManager.state
            .onEach { state ->
                if (state.isComplete && activeBlockedPkg == pkg) {
                    onChallengeCompleted(pkg)
                }
            }
            .catch { }
            .launchIn(serviceScope)
    }

    private fun onChallengeCompleted(pkg: String) {
        val id = activeSessionId
        activeSessionId = null
        activeBlockedPkg = null
        challengeSatisfiedPkg = pkg
        completionJob?.cancel()
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        if (id != null) {
            serviceScope.launch { runCatching { focusSessionRepository.completeSession(id) } }
        }
    }

    /** Invoked from the overlay's confirmed "End session early" action. */
    private fun endEarly() {
        val pkg = activeBlockedPkg
        val id = activeSessionId
        activeSessionId = null
        activeBlockedPkg = null
        if (pkg != null) challengeSatisfiedPkg = pkg
        completionJob?.cancel()
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        if (id != null) {
            serviceScope.launch { runCatching { focusSessionRepository.abandonSession(id) } }
        }
    }

    private fun teardown(abandon: Boolean) {
        val id = activeSessionId
        activeSessionId = null
        activeBlockedPkg = null
        completionJob?.cancel()
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        if (abandon && id != null) {
            serviceScope.launch { runCatching { focusSessionRepository.abandonSession(id) } }
        }
    }

    override fun onInterrupt() {
        // Required override; nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        runCatching { job.cancel() }
        super.onDestroy()
    }
}
