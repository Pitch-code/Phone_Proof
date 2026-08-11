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
import com.phoneproof.feature.touchgrid.TouchGridRoute

private object Routes {
    const val HOME = "home"
    const val TOUCH = "touch"
    const val LOCK = "lock"
    const val DIAGNOSTICS = "diagnostics"
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
                // Only two checks exist, so the primary action opens the one that catches the most
                // expensive problem rather than pretending a full guided run is ready.
                onStartFullTest = { navController.navigate(Routes.LOCK) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.TOUCH) {
            TouchGridRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.LOCK) {
            EmiLockRoute(modifier = Modifier.fillMaxSize())
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
