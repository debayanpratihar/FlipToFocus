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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-app detector and focus-challenge orchestrator, implemented as an
 * [AccessibilityService].
 *
 * Detection is BOTH event-driven and self-healing:
 *  - [onAccessibilityEvent] reacts immediately to window-state changes.
 *  - A short polling loop re-reads the current foreground package (via [rootInActiveWindow])
 *    every [POLL_MS] and re-evaluates. This closes two gaps that pure event handling misses:
 *      1. A blocked app that was ALREADY open when blocking was enabled (no new event fires).
 *      2. Returning to a blocked app from Recents, where some devices don't emit a fresh event.
 *    The result: the focus overlay reliably (re)appears and cannot be bypassed via Recents.
 *
 * Privacy / Play compliance: only the foreground package NAME is ever used; on-screen content is
 * never inspected or stored. The overlay appears only over user-selected apps, never over Settings
 * or the launcher, and the user can always leave via Home/Recents.
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

    @Volatile private var cachedConfig: AppConfig = AppConfig()
    @Volatile private var cachedEnabled: Set<String> = emptySet()
    @Volatile private var cachedLabels: Map<String, String> = emptyMap()

    private var activeBlockedPkg: String? = null
    private var activeSessionId: Long? = null
    private var challengeSatisfiedPkg: String? = null
    @Volatile private var lastForegroundPkg: String? = null

    private var completionJob: Job? = null
    private var pollJob: Job? = null
    private var pendingTeardownJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        runCatching { applyServiceInfo() }
        observeRepositories()
        startForegroundPolling()
    }

    private fun applyServiceInfo() {
        val info = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    private fun observeRepositories() {
        appConfigRepository.observeConfig()
            .onEach { config ->
                cachedConfig = config
                if (!config.isBlockingEnabled && activeBlockedPkg != null) {
                    teardown(abandon = true)
                }
            }
            .catch { }
            .launchIn(serviceScope)

        blockedAppRepository.observeBlockedApps()
            .onEach { apps ->
                cachedLabels = apps.associate { it.packageName to it.appLabel }
                cachedEnabled = apps.asSequence()
                    .filter { it.isEnabled }
                    .map { it.packageName }
                    .toSet()
                val active = activeBlockedPkg
                if (active != null && !cachedEnabled.contains(active)) {
                    teardown(abandon = true)
                }
                // Re-check the current foreground now that the blocklist is known (fixes the
                // "already open when blocking was enabled" gap).
                evaluate(lastForegroundPkg)
            }
            .catch { }
            .launchIn(serviceScope)
    }

    private fun startForegroundPolling() {
        pollJob?.cancel()
        pollJob = serviceScope.launch {
            while (isActive) {
                delay(POLL_MS)
                val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull()
                    ?: lastForegroundPkg
                if (pkg != null) {
                    lastForegroundPkg = pkg
                    runCatching { evaluate(pkg) }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val pkg = event.packageName?.toString()
                if (!pkg.isNullOrBlank()) {
                    lastForegroundPkg = pkg
                    evaluate(pkg)
                }
            }
        } catch (t: Throwable) {
            // A single bad event must never take down the detector.
        }
    }

    /**
     * Central self-healing state machine. Safe to call repeatedly from events and the poller.
     */
    private fun evaluate(pkg: String?) {
        if (pkg.isNullOrBlank() || pkg == packageName) return

        val config = cachedConfig
        val blockedUnsatisfied = config.isBlockingEnabled &&
            cachedEnabled.contains(pkg) &&
            pkg != challengeSatisfiedPkg

        if (blockedUnsatisfied) {
            // Returning to the blocked app cancels any pending teardown so the overlay never
            // flickers away and back (e.g. a quick Recents peek).
            pendingTeardownJob?.cancel()
            pendingTeardownJob = null
            // Already actively challenging this exact app with the overlay up? Leave the timer be.
            if (activeBlockedPkg == pkg && overlayManager.isShowing) return
            startChallengeFor(pkg)
        } else {
            // Launcher / Recents / a satisfied app / blocking-off. Release any active challenge,
            // but debounced so brief transitions don't tear the overlay down.
            if (activeBlockedPkg != null && activeBlockedPkg != pkg) {
                scheduleTeardown()
            }
            // Moving to any genuinely different app clears the satisfied marker, so re-opening a
            // blocked app always requires the challenge again.
            if (pkg != challengeSatisfiedPkg) {
                challengeSatisfiedPkg = null
            }
        }
    }

    /**
     * Removes the overlay only if the user stays away from the blocked app for
     * [TEARDOWN_GRACE_MS]. Eliminates flicker from transient transitions such as opening Recents
     * and immediately returning.
     */
    private fun scheduleTeardown() {
        if (pendingTeardownJob?.isActive == true) return
        pendingTeardownJob = serviceScope.launch {
            delay(TEARDOWN_GRACE_MS)
            teardown(abandon = true)
        }
    }

    private fun startChallengeFor(pkg: String) {
        activeBlockedPkg = pkg
        val config = cachedConfig
        val factor = config.difficulty.factor
        val durationMillis = (config.challengeDurationMinutes.toLong() * 60_000L * factor)
            .toLong().coerceAtLeast(30_000L)
        val shakeTarget = (config.shakeCount * factor).toInt().coerceAtLeast(3)
        val mathTotal = (config.mathProblemCount * factor).toInt().coerceAtLeast(1)
        // Harder difficulty = stricter motion (lower tolerance).
        val motionTolerance = (config.motionTolerance / factor).coerceIn(0f, 3f)

        serviceScope.launch {
            val sessionId = runCatching {
                focusSessionRepository.startSession(pkg, durationMillis)
            }.getOrNull()

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
                    motionTolerance = motionTolerance,
                    shakeTarget = shakeTarget,
                    mathTotal = mathTotal
                )
            }
            runCatching {
                overlayManager.showOverlay(
                    triggeringLabel = cachedLabels[pkg] ?: pkg,
                    stateFlow = challengeManager.state,
                    onEndEarly = { endEarly() },
                    onMathAnswer = { answer -> runCatching { challengeManager.submitMathAnswer(answer) } },
                    onLeaveToHome = { leaveToHome() }
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

    /** Confirmed "End session early": grants access and abandons the session. */
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

    /**
     * "Use another app": leaves to the launcher WITHOUT unlocking the blocked app, so the user can
     * open something else while a timed/math challenge is pending. The blocked app stays blocked
     * and will challenge again when reopened.
     */
    private fun leaveToHome() {
        teardown(abandon = true)
        runCatching { performGlobalAction(GLOBAL_ACTION_HOME) }
    }

    private fun teardown(abandon: Boolean) {
        pendingTeardownJob = null
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
        pollJob?.cancel()
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        pollJob?.cancel()
        runCatching { challengeManager.stop() }
        runCatching { overlayManager.hideOverlay() }
        runCatching { job.cancel() }
        super.onDestroy()
    }

    private companion object {
        const val POLL_MS = 600L
        const val TEARDOWN_GRACE_MS = 700L
    }
}
