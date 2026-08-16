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
import com.phoneproof.core.run.RunSession
import com.phoneproof.feature.diagnostics.DiagnosticsRoute
import com.phoneproof.feature.emilock.EmiLockRoute
import com.phoneproof.feature.audiotest.AudioTestRoute
import com.phoneproof.feature.cameratest.CameraTestRoute
import com.phoneproof.feature.claims.ClaimsRoute
import com.phoneproof.feature.guide.GuideRoute
import com.phoneproof.feature.home.HomeCatalogue
import com.phoneproof.feature.home.HomeCheck
import com.phoneproof.feature.home.HomeScreen
import com.phoneproof.feature.imei.ImeiRoute
import com.phoneproof.feature.reports.CompareRoute
import com.phoneproof.feature.reports.ReportDetailRoute
import com.phoneproof.feature.reports.ReportsRoute
import com.phoneproof.feature.run.RunRoute
import com.phoneproof.feature.run.RunVerdictRoute
import com.phoneproof.feature.scan.ScanRoute
import com.phoneproof.feature.sensortest.SensorTestRoute
import com.phoneproof.feature.screentest.ScreenTestRoute
import com.phoneproof.feature.settings.SettingsRoute
import com.phoneproof.feature.touchgrid.TouchGridRoute

/**
 * Every destination in the app.
 *
 * `internal` rather than private so that [RunPlanRoutesTest] can assert that each step of the guided
 * run names a route that actually exists. The run stores its steps as route strings — which is what
 * lets the checklist navigate without a second lookup table to keep in sync — and the cost of that is
 * that a typo would produce a step no screen can reach. The test is what makes it safe.
 */
internal object Routes {
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
    const val CAMERA = "camera"
    const val SENSORS = "sensors"
    const val RUN = "run"
    const val VERDICT = "verdict"
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

    // One session for the whole app session, held here because this is the only place composed for the
    // entire time a run is in progress — every individual test screen comes and goes.
    //
    // Deliberately not persisted. A run is one phone in one shop; reviving a half-finished one days
    // later would risk the worst bug this product could have, which is findings from one handset shown
    // against another. What survives is the saved report, which records the phone it was taken on.
    val runSession = remember { RunSession() }
    val runState by runSession.state.collectAsStateWithLifecycle()

    val entitlement by settings.entitlement.collectAsStateWithLifecycle(Entitlement.FREE)
    val scansUsed by settings.scansUsed.collectAsStateWithLifecycle(0)

    // Every test screen below hands its findings to the session. While no run is in progress the
    // session ignores them, so a buyer opening one check from Home leaves no trace; while one is live,
    // a check opened from Home rather than from the checklist still counts, because it is the same
    // phone either way. This is the whole of the coupling: no feature module knows the run exists.
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier.fillMaxSize(),
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                // Built from HomeCatalogue rather than written out here, so the screenshot test can
                // render the same list. It used to be spelled out in both places and the test's copy fell
                // five entries behind without anything failing.
                checks = HomeCatalogue.map { entry ->
                    HomeCheck(
                        title = entry.title,
                        subtitle = entry.subtitle,
                        onClick = { navController.navigate(entry.route) },
                    )
                },
                onStartFullTest = { navController.navigate(Routes.RUN) },
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
                onResults = { runSession.record(Routes.CLAIMS, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.AUDIO) {
            AudioTestRoute(
                onResults = { runSession.record(Routes.AUDIO, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.CAMERA) {
            CameraTestRoute(
                onResults = { runSession.record(Routes.CAMERA, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // No entitlement gate and no permission gate: it measures the handset in front of the buyer,
        // and Android asks for nothing to read these sensors below 200 Hz.
        composable(Routes.SENSORS) {
            SensorTestRoute(
                onResults = { runSession.record(Routes.SENSORS, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The guided run. Step ids are these route names, which is what lets the checklist navigate
        // without a lookup table; RunPlanRoutesTest asserts every one of them resolves.
        composable(Routes.RUN) {
            RunRoute(
                session = runSession,
                onOpenStep = { route -> navController.navigate(route) },
                onSeeVerdict = { navController.navigate(Routes.VERDICT) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.VERDICT) {
            RunVerdictRoute(
                session = runSession,
                onOpenStep = { route -> navController.navigate(route) },
                onOpenReports = { navController.navigate(Routes.REPORTS) },
                // Back to Home rather than to a fresh checklist: someone who has finished with one
                // phone is usually done, and the ones who are not are standing in front of the next
                // handset and will tap the big button again anyway.
                onTestAnotherPhone = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // No entitlement gate. This is a measurement of the handset in front of the buyer rather than
        // advice or a comparison, so by the rule in monetisation.md it sits outside the paywall.
        composable(Routes.IMEI) {
            ImeiRoute(
                onResults = { runSession.record(Routes.IMEI, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.GUIDE) {
            GuideRoute(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.SCREEN_PATTERNS) {
            ScreenTestRoute(
                onResults = { runSession.record(Routes.SCREEN_PATTERNS, it) },
                modifier = Modifier.fillMaxSize(),
            )
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
            TouchGridRoute(
                onResults = { runSession.record(Routes.TOUCH, it) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        composable(Routes.SCAN) {
            ScanRoute(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onResults = { runSession.record(Routes.SCAN, it) },
                // During a run the run writes one report covering every step, so the scan must not
                // also write its own — on the free tier, which keeps two, that would let a single
                // run evict the previous phone from the history.
                saveOwnReport = !runState.active,
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
