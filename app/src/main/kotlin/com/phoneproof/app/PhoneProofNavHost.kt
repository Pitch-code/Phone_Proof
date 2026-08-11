package com.phoneproof.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.phoneproof.feature.diagnostics.DiagnosticsRoute
import com.phoneproof.feature.emilock.EmiLockRoute
import com.phoneproof.feature.home.HomeCheck
import com.phoneproof.feature.home.HomeScreen
import com.phoneproof.feature.scan.ScanRoute
import com.phoneproof.feature.settings.SettingsRoute
import com.phoneproof.feature.touchgrid.TouchGridRoute

private object Routes {
    const val HOME = "home"
    const val TOUCH = "touch"
    const val LOCK = "lock"
    const val SCAN = "scan"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
}

/**
 * Navigation wiring, and nothing else. All UI lives in feature modules so that this file never
 * becomes the place where screens quietly accumulate.
 */
@Composable
fun PhoneProofNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                checks = listOf(
                    HomeCheck(
                        title = "Instant scan",
                        subtitle = "Software, storage, sensors and screen — no waiting",
                        onClick = { navController.navigate(Routes.SCAN) },
                    ),
                    HomeCheck(
                        title = "Remote lock control",
                        subtitle = "Can a lender brick this phone after you pay?",
                        onClick = { navController.navigate(Routes.LOCK) },
                    ),
                    HomeCheck(
                        title = "Touch response",
                        subtitle = "Find dead patches on the screen",
                        onClick = { navController.navigate(Routes.TOUCH) },
                    ),
                ),
                // The instant scan is the closest thing to the full guided run that exists, and
                // it already includes the remote-lock check, so the primary action opens it.
                onStartFullTest = { navController.navigate(Routes.SCAN) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.TOUCH) {
            TouchGridRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.SCAN) {
            ScanRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.LOCK) {
            EmiLockRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.SETTINGS) {
            SettingsRoute(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.DIAGNOSTICS) {
            DiagnosticsRoute(
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
