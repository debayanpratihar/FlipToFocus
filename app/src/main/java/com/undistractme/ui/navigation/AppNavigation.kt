package com.undistractme.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.undistractme.ui.blocklist.BlocklistScreen
import com.undistractme.ui.home.HomeScreen
import com.undistractme.ui.onboarding.OnboardingScreen
import com.undistractme.ui.settings.SettingsScreen
import com.undistractme.util.PermissionUtils

/** Canonical navigation route names. */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val BLOCKLIST = "blocklist"
    const val SETTINGS = "settings"
}

/**
 * Top-level navigation graph.
 *
 * The start destination is decided from the current permission state: if either
 * Usage Access or the overlay permission is missing, the user is routed through
 * onboarding first; otherwise they land on home. The [startService] /
 * [stopService] callbacks are threaded down to the screens that toggle blocking
 * so the UI never references the service class directly.
 *
 * Call sites here match the screen declarations exactly: every screen takes the
 * shared [NavController] (and performs its own popBackStack / navigate), and
 * HomeScreen additionally receives [onBlockingEnabledChanged], which we bridge to
 * the service start/stop callbacks so toggling the master switch actually
 * starts or stops [com.undistractme.service.AppBlockerService].
 */
@Composable
fun AppNavigation(
    startService: () -> Unit,
    stopService: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val startDestination = remember {
        val ready = PermissionUtils.hasUsageAccess(context) &&
            PermissionUtils.canDrawOverlays(context)
        if (ready) Routes.HOME else Routes.ONBOARDING
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    startService()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                navController = navController,
                onBlockingEnabledChanged = { enabled ->
                    if (enabled) startService() else stopService()
                }
            )
        }
        composable(Routes.BLOCKLIST) {
            BlocklistScreen(navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
    }
}
