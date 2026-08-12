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
import com.phoneproof.feature.claims.ClaimsRoute
import com.phoneproof.feature.guide.GuideRoute
import com.phoneproof.feature.home.HomeCheck
import com.phoneproof.feature.home.HomeScreen
import com.phoneproof.feature.reports.CompareRoute
import com.phoneproof.feature.reports.ReportDetailRoute
import com.phoneproof.feature.reports.ReportsRoute
import com.phoneproof.feature.scan.ScanRoute
import com.phoneproof.feature.screentest.ScreenTestRoute
import com.phoneproof.feature.settings.SettingsRoute
import com.phoneproof.feature.touchgrid.TouchGridRoute

private object Routes {
    const val HOME = "home"
    const val TOUCH = "touch"
    const val LOCK = "lock"
    const val SCAN = "scan"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
    const val REPORTS = "reports"
    const val SCREEN_PATTERNS = "screen-patterns"
    const val GUIDE = "guide"
    const val COMPARE = "compare"
    const val CLAIMS = "claims"
    const val REPORT_DETAIL = "reports/{reportId}"

    fun reportDetail(id: String): String = "reports/$id"
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
                    HomeCheck(
                        title = "Dead pixels and burn-in",
                        subtitle = "Plain colours that make screen faults obvious",
                        onClick = { navController.navigate(Routes.SCREEN_PATTERNS) },
                    ),
                    HomeCheck(
                        title = "Claimed against measured",
                        subtitle = "Is it the phone you were promised?",
                        onClick = { navController.navigate(Routes.CLAIMS) },
                    ),
                ),
                // The instant scan is the closest thing to the full guided run that exists, and
                // it already includes the remote-lock check, so the primary action opens it.
                onStartFullTest = { navController.navigate(Routes.SCAN) },
                // The guide is no longer one of the checks: it has its own heading on Home, because
                // it is advice for the buyer's hands rather than something the phone measures.
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.CLAIMS) {
            ClaimsRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.GUIDE) {
            GuideRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.SCREEN_PATTERNS) {
            ScreenTestRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.REPORTS) {
            ReportsRoute(
                onOpenReport = { id -> navController.navigate(Routes.reportDetail(id)) },
                onCompare = { navController.navigate(Routes.COMPARE) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.COMPARE) {
            CompareRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.REPORT_DETAIL) { entry ->
            ReportDetailRoute(
                reportId = entry.arguments?.getString("reportId").orEmpty(),
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
                // Only a debug build can switch tiers by hand. Read here rather than inside the
                // feature module so a release build has no code path to the switcher at all.
                showTestingControls = BuildConfig.DEBUG,
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
