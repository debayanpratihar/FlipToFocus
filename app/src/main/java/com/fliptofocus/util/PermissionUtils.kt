package com.fliptofocus.util

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Stateless helpers for querying and navigating to the two special-access
 * permissions this app depends on:
 *
 *  - Usage Access (PACKAGE_USAGE_STATS) - checked via [AppOpsManager] because it
 *    is an app-op, not a runtime permission.
 *  - Draw Over Other Apps (SYSTEM_ALERT_WINDOW) - checked via
 *    [Settings.canDrawOverlays].
 *
 * Neither of these can be requested with the standard runtime-permission dialog;
 * both are granted from a dedicated Settings screen, so this object also exposes
 * the intents that deep-link the user there.
 */
object PermissionUtils {

    /** True when the user has granted Usage Access to this package. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** True when the user has granted "Display over other apps" to this package. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Deep-links to the system Usage Access settings list. */
    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    /** Deep-links directly to this app's "Display over other apps" toggle. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + context.packageName)
        )
}
