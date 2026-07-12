package com.fliptofocus.service

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
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
import com.fliptofocus.overlay.FocusOverlayScreen
import com.fliptofocus.sensor.ChallengeState
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
 * Back handling: [ComposeView] is `final` and cannot be subclassed, so the overlay's root is a
 * [BackHandlingOverlayLayout] (a [FrameLayout]) that hosts a plain [ComposeView] child and bridges
 * Back into the overlay's own [OnBackPressedDispatcher]:
 *
 *  - On API < 33 (and API 33+ without predictive back) Back arrives as a KEYCODE_BACK [KeyEvent];
 *    [FrameLayout.dispatchKeyEvent] intercepts and consumes it.
 *  - On API 33+ with predictive back, an [OnBackInvokedCallback] on the window's dispatcher fires.
 *
 * Either way Back is consumed by the overlay window and never leaks to the app behind it.
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

    // The root view actually attached to the WindowManager (a FrameLayout hosting a ComposeView).
    private var overlayView: View? = null
    private var overlayOwner: OverlayViewLifecycleOwner? = null

    val isShowing: Boolean
        get() = overlayView != null

    /**
     * Displays the focus overlay bound to [stateFlow]. Safe to call repeatedly; a second call while
     * already showing is a no-op. All WindowManager interaction is guarded.
     */
    fun showOverlay(
        triggeringLabel: String,
        stateFlow: StateFlow<ChallengeState>,
        onEndEarly: () -> Unit,
        onMathAnswer: (Int) -> Unit = {},
        onLeaveToHome: () -> Unit = {}
    ) {
        if (isShowing) return
        val owner = OverlayViewLifecycleOwner()
        try {
            owner.onStart()

            // Root container that consumes Back. Route hardware/gesture Back into the Compose
            // dispatcher owned by this window so the BackHandler inside FocusOverlayScreen fires
            // (there is no Activity to do this for us); if nothing is registered, Back is still
            // consumed at the view level and cannot reach the app behind the overlay.
            val root = BackHandlingOverlayLayout(context) {
                owner.onBackPressedDispatcher.onBackPressed()
            }
            // Owners are set on the root; the child ComposeView resolves them by walking up the tree.
            root.setViewTreeLifecycleOwner(owner)
            root.setViewTreeViewModelStoreOwner(owner)
            root.setViewTreeSavedStateRegistryOwner(owner)
            root.setViewTreeOnBackPressedDispatcherOwner(owner)

            val composeView = ComposeView(context).apply {
                setContent {
                    val state by stateFlow.collectAsState()
                    FocusOverlayScreen(
                        state = state,
                        triggeringAppLabel = triggeringLabel,
                        onEndEarly = onEndEarly,
                        onMathAnswer = onMathAnswer,
                        onLeaveToHome = onLeaveToHome
                    )
                }
            }
            root.addView(
                composeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            windowManager.addView(root, buildLayoutParams())
            overlayView = root
            overlayOwner = owner
        } catch (t: Throwable) {
            // If the window could not be added (e.g. permission revoked mid-session) fail safe and
            // never leave a half-attached view behind.
            runCatching { owner.onStop() }
            overlayView = null
            overlayOwner = null
        }
    }

    /** Removes the overlay window if present. Guarded against double-remove / missing window. */
    fun hideOverlay() {
        val view = overlayView
        val owner = overlayOwner
        overlayView = null
        overlayOwner = null
        if (view != null) {
            runCatching { windowManager.removeView(view) }
        }
        runCatching { owner?.onStop() }
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        // Focusable window (no FLAG_NOT_FOCUSABLE) so the overlay receives the Back key and can
        // consume it. FLAG_NOT_TOUCH_MODAL keeps behaviour predictable; since the window is
        // MATCH_PARENT it captures all in-app touches. FLAG_KEEP_SCREEN_ON keeps the screen awake
        // for the duration of the challenge.
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
     * [FrameLayout] root for the overlay that guarantees the Back button/gesture is consumed by the
     * overlay window regardless of API level or predictive-back opt-in. It hosts a plain
     * [ComposeView] child (Compose's [ComposeView] is final and cannot be subclassed directly).
     */
    private class BackHandlingOverlayLayout(
        context: Context,
        private val onBack: () -> Unit
    ) : FrameLayout(context) {

        // Typed as Any? so the class verifies on API < 33 where OnBackInvokedCallback is absent;
        // it is only ever created/touched inside an SDK_INT >= 33 guard.
        private var registeredBackCallback: Any? = null

        init {
            // Must be focusable to receive the Back key on all API levels.
            isFocusable = true
            isFocusableInTouchMode = true
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            requestFocus()
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
