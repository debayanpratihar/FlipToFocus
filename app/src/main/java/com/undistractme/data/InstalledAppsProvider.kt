package com.undistractme.data

import android.content.Context
import android.content.Intent
import com.undistractme.domain.model.BlockedApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Enumerates the launchable apps installed on the device.
 *
 * Uses [android.content.pm.PackageManager.queryIntentActivities] with the
 * MAIN/LAUNCHER intent (matching the manifest <queries> element) so the app can
 * see only user-launchable apps â€” this is the Google Play compliant alternative
 * to the QUERY_ALL_PACKAGES permission, which this app deliberately does not use.
 *
 * The query and label loading run on [Dispatchers.IO] because resolving and
 * loading labels for every launcher entry can be slow on cold caches.
 */
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun getLaunchableApps(): List<BlockedApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = context.packageName

        pm.queryIntentActivities(intent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == ownPackage) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() }
                    ?: pkg
                BlockedApp(packageName = pkg, appLabel = label, isEnabled = true)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appLabel.lowercase() }
            .toList()
    }
}
