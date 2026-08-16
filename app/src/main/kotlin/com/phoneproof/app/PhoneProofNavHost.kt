package com.phoneproof.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.phoneproof.core.preferences.Entitlement
import com.phoneproof.core.preferences.SettingsRepository
import com.phoneproof.feature.diagnostics.DiagnosticsRoute
import com.phoneproof.feature.emilock.EmiLockRoute
import com.phoneproof.feature.audiotest.AudioTestRoute
import com.phoneproof.feature.claims.ClaimsRoute
import com.phoneproof.feature.guide.GuideRoute
import com.phoneproof.feature.home.HomeCheck
import com.phoneproof.feature.home.HomeScreen
import com.phoneproof.feature.imei.ImeiRoute
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
    const val IMEI = "imei"
    const val AUDIO = "audio"
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
    val context = LocalContext.current
    val settings = remember(context) { SettingsRepository(context) }
    val entitlement by settings.entitlement.collectAsStateWithLifecycle(Entitlement.FREE)
    val scansUsed by settings.scansUsed.collectAsStateWithLifecycle(0)

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
                        title = "Microphone and speaker",
                        subtitle = "Measured with a test tone, not just played back",
                        onClick = { navController.navigate(Routes.AUDIO) },
                    ),
                    HomeCheck(
                        title = "Claimed against measured",
                        subtitle = "Is it the phone you were promised?",
                        onClick = { navController.navigate(Routes.CLAIMS) },
                    ),
                    // Listed with the checks even though the buyer types the number, because from
                    // their side it is the same kind of task: find out something about this handset
                    // before paying. The screen itself is candid that Android will not supply it.
                    HomeCheck(
                        title = "IMEI and the stolen-phone register",
                        subtitle = "Check the number, then check it against CEIR",
                        onClick = { navController.navigate(Routes.IMEI) },
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
                // null for a paid tier, so no counter is shown at all rather than a limit that does
                // not apply. coerceAtLeast guards a stored count above the limit, which would
                // otherwise render as a negative number of scans left.
                freeScansLeft = if (entitlement.hasUnlimitedScans) {
                    null
                } else {
                    (Entitlement.FREE_SCAN_LIMIT - scansUsed).coerceAtLeast(0)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.CLAIMS) {
            ClaimsRoute(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.AUDIO) {
            AudioTestRoute(modifier = Modifier.fillMaxSize())
        }

        // No entitlement gate. This is a measurement of the handset in front of the buyer rather than
        // advice or a comparison, so by the rule in monetisation.md it sits outside the paywall.
        composable(Routes.IMEI) {
            ImeiRoute(modifier = Modifier.fillMaxSize())
        }

        composable(Routes.GUIDE) {
            GuideRoute(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
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
            ScanRoute(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
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
