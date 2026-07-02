package com.undistractme.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.undistractme.overlay.FocusOverlayScreen
import com.undistractme.sensor.ChallengeState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds/removes the full-screen focus overlay as a [WindowManager] window driven entirely from a
 * background service. Because there is no Activity, the hosting [ComposeView] is wired to a
 * lightweight owner that implements the four view-tree owners Compose needs
 * (Lifecycle / ViewModelStore / SavedStateRegistry / OnBackPressedDispatcher) so that state,
 * remembered values and BackHandler all work.
 *
 * Back handling: there is no Activity to forward the Back key into the Compose
 * [OnBackPressedDispatcher], and on API 33+ the platform can route Back through the window's
 * [OnBackInvokedDispatcher]. [BackHandlingComposeView] bridges both paths into the overlay's
 * own [OnBackPressedDispatcher] so the [FocusOverlayScreen] BackHandler reliably consumes Back
 * and it never leaks to the app behind the overlay.
 *
 * Compliance: the overlay is only ever shown over a user-selected distracting app. The window is
 * focusable (so Back can be consumed) but the platform Home / Recents affordances remain fully
 * functional, so the user can always leave. This is never used over Settings.
 */
@Singleton
class OverlayManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var composeView: ComposeView? = null
    private var overlayOwner: OverlayViewLifecycleOwner? = null

    val isShowing: Boolean
        get() = composeView != null

    /**
     * Displays the focus overlay bound to [stateFlow]. Safe to call repeatedly; a second call while
     * already showing is a no-op. All WindowManager interaction is guarded.
     */
    fun showOverlay(
        triggeringLabel: String,
        stateFlow: StateFlow<ChallengeState>,
        onEndEarly: () -> Unit
    ) {
        if (isShowing) return
        val owner = OverlayViewLifecycleOwner()
        try {
            owner.onStart()
            // Route hardware/gesture Back into the Compose dispatcher owned by this window so the
            // BackHandler inside FocusOverlayScreen fires (there is no Activity to do this for us).
            val view = BackHandlingComposeView(context) {
                owner.onBackPressedDispatcher.onBackPressed()
            }
            view.setViewTreeLifecycleOwner(owner)
            view.setViewTreeViewModelStoreOwner(owner)
            view.setViewTreeSavedStateRegistryOwner(owner)
            view.setViewTreeOnBackPressedDispatcherOwner(owner)
            view.setContent {
                val state by stateFlow.collectAsState()
                FocusOverlayScreen(
                    state = state,
                    triggeringAppLabel = triggeringLabel,
                    onEndEarly = onEndEarly
                )
            }
            windowManager.addView(view, buildLayoutParams())
            composeView = view
            overlayOwner = owner
        } catch (t: Throwable) {
            // If the window could not be added (e.g. permission revoked mid-session) fail safe and
            // never leave a half-attached view behind.
            runCatching { owner.onStop() }
            composeView = null
            overlayOwner = null
        }
    }

    /** Removes the overlay window if present. Guarded against double-remove / missing window. */
    fun hideOverlay() {
        val view = composeView
        val owner = overlayOwner
        composeView = null
        overlayOwner = null
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        runCatching { owner?.onStop() }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        // Focusable window (no FLAG_NOT_FOCUSABLE) so the ComposeView receives the back key and our
        // BackHandler can consume it. FLAG_NOT_TOUCH_MODAL keeps behaviour predictable; since the
        // window is MATCH_PARENT it captures all in-app touches. FLAG_KEEP_SCREEN_ON keeps the
        // screen awake for the duration of the challenge.
        val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    /**
     * [ComposeView] that guarantees the Back button/gesture is consumed by the overlay window
     * regardless of API level or whether the app has opted into the predictive-back
     * [OnBackInvokedDispatcher]:
     *
     *  - On API < 33 (and on API 33+ when predictive back is not enabled) Back arrives as a
     *    KEYCODE_BACK [KeyEvent]; [dispatchKeyEvent] intercepts it and forwards to [onBack].
     *  - On API 33+ with predictive back enabled, KEYCODE_BACK is not delivered as a key event, so
     *    an [OnBackInvokedCallback] is registered on the window's dispatcher instead.
     *
     * Exactly one of these paths is active for a given configuration, so [onBack] fires once.
     */
    private class BackHandlingComposeView(
        context: Context,
        private val onBack: () -> Unit
    ) : ComposeView(context) {

        // Typed as Any? so the class verifies on API < 33 where OnBackInvokedCallback is absent;
        // it is only ever created/touched inside an SDK_INT >= 33 guard.
        private var registeredBackCallback: Any? = null

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val dispatcher = findOnBackInvokedDispatcher()
                if (dispatcher != null) {
                    val callback = OnBackInvokedCallback { onBack() }
                    dispatcher.registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                        callback
                    )
                    registeredBackCallback = callback
                }
            }
        }

        override fun onDetachedFromWindow() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                (registeredBackCallback as? OnBackInvokedCallback)?.let { cb ->
                    findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb)
                }
                registeredBackCallback = null
            }
            super.onDetachedFromWindow()
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) {
                    onBack()
                }
                // Consume both DOWN and UP so Back never propagates to the window behind us.
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    /**
     * Minimal owner that satisfies Compose's view-tree owner requirements for a window that has no
     * backing Activity. It is driven straight to RESUMED when attached and to DESTROYED when the
     * overlay is removed.
     */
    private class OverlayViewLifecycleOwner :
        LifecycleOwner,
        ViewModelStoreOwner,
        SavedStateRegistryOwner,
        OnBackPressedDispatcherOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val store = ViewModelStore()
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val backDispatcher = OnBackPressedDispatcher()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val viewModelStore: ViewModelStore
            get() = store

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateRegistryController.savedStateRegistry

        override val onBackPressedDispatcher: OnBackPressedDispatcher
            get() = backDispatcher

        fun onStart() {
            // Restore must happen before the lifecycle reaches CREATED.
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onStop() {
            if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }
    }
}
