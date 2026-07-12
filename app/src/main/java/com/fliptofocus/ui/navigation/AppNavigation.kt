package com.fliptofocus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fliptofocus.ui.blocklist.BlocklistScreen
import com.fliptofocus.ui.home.HomeScreen
import com.fliptofocus.ui.onboarding.OnboardingScreen
import com.fliptofocus.ui.settings.SettingsScreen
import com.fliptofocus.util.PermissionUtils

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
 * Start destination is decided from the current permission state: if the accessibility service is
 * not enabled or the overlay permission is missing, the user goes through onboarding first;
 * otherwise they land on home. Blocking itself is driven purely by persisted config that the
 * accessibility service observes, so no screen needs to start/stop a service.
 */
@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val startDestination = remember {
        val ready = PermissionUtils.isAccessibilityServiceEnabled(context) &&
            PermissionUtils.canDrawOverlays(context)
        if (ready) Routes.HOME else Routes.ONBOARDING
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(navController = navController)
        }
        composable(Routes.BLOCKLIST) {
            BlocklistScreen(navController = navController)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(navController = navController)
        }
    }
}
