package com.fliptofocus.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.TextUtils
import com.fliptofocus.service.FocusAccessibilityService

/**
 * Stateless helpers for querying and navigating to the two special-access permissions this app
 * depends on:
 *
 *  - The Accessibility service (foreground-app detection) - checked against the system's list of
 *    enabled accessibility services.
 *  - Draw Over Other Apps (SYSTEM_ALERT_WINDOW) - checked via [Settings.canDrawOverlays].
 *
 * Both are granted from dedicated Settings screens, so this object also exposes the deep-link
 * intents. Every OS call is wrapped defensively so a query can never crash the caller.
 */
object PermissionUtils {

    /** True when FlipToFocus's [FocusAccessibilityService] is enabled by the user in system settings. */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, FocusAccessibilityService::class.java)
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull()
        if (enabled.isNullOrEmpty()) return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component != null && component == expected) return true
        }
        // Fallback for OEM formatting differences.
        return enabled.contains(expected.flattenToString(), ignoreCase = true) ||
            enabled.contains(expected.flattenToShortString(), ignoreCase = true)
    }

    /** True when the user has granted "Display over other apps" to this package. */
    fun canDrawOverlays(context: Context): Boolean =
        runCatching { Settings.canDrawOverlays(context) }.getOrDefault(false)

    /** Deep-links to the system Accessibility settings list. */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Deep-links directly to this app's "Display over other apps" toggle. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
}
