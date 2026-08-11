package com.phoneproof.feature.scan

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.device.BuildIntegrityCheck
import com.phoneproof.checks.device.DeviceFacts
import com.phoneproof.checks.device.DisplayCheck
import com.phoneproof.checks.device.SecurityPatchCheck
import com.phoneproof.checks.device.SensorFact
import com.phoneproof.checks.device.SensorInventoryCheck
import com.phoneproof.checks.device.StorageCheck
import com.phoneproof.checks.emilock.DeviceAdminSnapshot
import com.phoneproof.checks.emilock.EmiLockEvaluator
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the scan screen from constructed facts.
 *
 * Two states matter: a clean phone, and a phone with several genuine problems. The second is the
 * one that can never be produced on demand from real hardware — nobody has a counterfeit handset to
 * hand — so without rendering it from data the most important screen in the app would go unreviewed
 * until a buyer met one in a market.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScanScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    /** 2026-08-11, so the patch ages in these fixtures are stable forever. */
    private val today = 20_676L

    private fun sensors(vararg types: Int) = types.map { SensorFact(it, "s$it", "vendor") }

    private val healthy = DeviceFacts(
        manufacturer = "realme", brand = "realme", model = "RMX5110", device = "RMX5110",
        hardware = "mt6789", socModel = "MT6789",
        fingerprint = "realme/RMX5110/RMX5110:16/UP1A/S123:user/release-keys",
        buildTags = "release-keys",
        sdkInt = 36, androidRelease = "16", securityPatch = "2026-07-05",
        totalStorageBytes = 118_000_000_000L, freeStorageBytes = 60_000_000_000L,
        widthPx = 1080, heightPx = 2392, densityDpi = 480,
        currentRefreshRateHz = 120f, supportedRefreshRatesHz = listOf(60f, 90f, 120f),
        sensors = sensors(1, 2, 4, 5, 8),
    )

    private val suspicious = DeviceFacts(
        manufacturer = "realme", brand = "realme", model = "Galaxy S26 Ultra", device = "SM-S948B",
        hardware = "mt6739", socModel = "MT6739",
        // Model edited to look like a flagship, fingerprint left behind: a cloned phone.
        fingerprint = "realme/RMX1911/RMX1911:11/RKQ1/456:user/release-keys",
        buildTags = "release-keys",
        sdkInt = 30, androidRelease = "11", securityPatch = "2023-09-01",
        totalStorageBytes = 40_000_000_000L, freeStorageBytes = 1_200_000_000L,
        widthPx = 720, heightPx = 1600, densityDpi = 320,
        currentRefreshRateHz = 60f, supportedRefreshRatesHz = listOf(60f, 120f),
        sensors = sensors(1, 2, 5),
    )

    private fun finishedState(facts: DeviceFacts, admins: DeviceAdminSnapshot): ScanUiState {
        val results = listOf(
            EmiLockEvaluator.evaluate(admins),
            BuildIntegrityCheck.evaluate(facts),
            SecurityPatchCheck.evaluate(facts, today),
            StorageCheck.evaluate(facts),
            SensorInventoryCheck.evaluate(facts),
            DisplayCheck.evaluate(facts),
        )
        return ScanUiState(
            steps = results.map {
                ScanStep(id = it.id, label = it.title, state = StepState.DONE, result = it)
            },
            finished = true,
        )
    }

    private fun render(name: String, state: ScanUiState) {
        composeRule.setContent {
            PhoneProofTheme(darkTheme = true) {
                ScanScreen(
                    state = state,
                    onRescan = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun a_clean_phone() {
        render("scan-1-clean", finishedState(healthy, DeviceAdminSnapshot.from(emptyList())))
    }

    @Test
    fun a_phone_with_real_problems() {
        render("scan-2-problems", finishedState(suspicious, DeviceAdminSnapshot.from(emptyList())))
    }

    @Test
    fun mid_scan_with_two_done_one_running_and_three_waiting() {
        // The state that only exists for about two seconds on a real device, and therefore the one
        // most likely to ship wrong without a render of it.
        val done = listOf(
            EmiLockEvaluator.evaluate(DeviceAdminSnapshot.from(emptyList())),
            BuildIntegrityCheck.evaluate(healthy),
        )
        val steps = done.map {
            ScanStep(id = it.id, label = it.title, state = StepState.DONE, result = it)
        } + listOf(
            ScanStep(SecurityPatchCheck.CHECK_ID, "Reading the security patch date", StepState.RUNNING),
            ScanStep(StorageCheck.CHECK_ID, "Measuring storage"),
            ScanStep(SensorInventoryCheck.CHECK_ID, "Counting sensors"),
            ScanStep(DisplayCheck.CHECK_ID, "Testing the display"),
        )
        render("scan-3-running", ScanUiState(steps = steps, finished = false))
    }

    @Test
    fun the_very_first_frame_before_anything_has_run() {
        val steps = listOf(
            ScanStep(EmiLockEvaluator.CHECK_ID, "Checking for remote lock control", StepState.RUNNING),
            ScanStep(BuildIntegrityCheck.CHECK_ID, "Verifying the software is genuine"),
            ScanStep(SecurityPatchCheck.CHECK_ID, "Reading the security patch date"),
            ScanStep(StorageCheck.CHECK_ID, "Measuring storage"),
            ScanStep(SensorInventoryCheck.CHECK_ID, "Counting sensors"),
            ScanStep(DisplayCheck.CHECK_ID, "Testing the display"),
        )
        render("scan-4-starting", ScanUiState(steps = steps, finished = false))
    }
}
