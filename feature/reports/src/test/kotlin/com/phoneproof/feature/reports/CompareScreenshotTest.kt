package com.phoneproof.feature.reports

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.reports.SavedReport
import com.phoneproof.core.reports.compareReports
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CompareScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun result(id: String, title: String, outcome: CheckOutcome) = when (outcome) {
        CheckOutcome.PASS, CheckOutcome.UNKNOWN -> CheckResult(
            id = id,
            title = title,
            outcome = outcome,
            confidence = Confidence.HIGH,
            headline = "Nothing wrong here.",
        )
        else -> CheckResult(
            id = id,
            title = title,
            outcome = outcome,
            confidence = Confidence.HIGH,
            headline = "Something is wrong here.",
            consequence = "It will cost you.",
            action = "Take it off the price.",
            falsePositiveCauses = listOf("Could be wrong."),
        )
    }

    private fun report(device: String, results: List<CheckResult>) = SavedReport(
        id = device,
        createdAtEpochMs = 1_000,
        deviceLabel = device,
        androidLabel = "Android 16 (API 36)",
        results = results,
    )

    private fun render(name: String, left: SavedReport, right: SavedReport) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                CompareScreen(
                    comparison = compareReports(left, right),
                    candidates = emptyList(),
                    onPick = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun one_phone_clearly_better() {
        render(
            "compare-1-clear-winner",
            report(
                "realme RMX5110",
                listOf(
                    result("hardware.battery", "Battery", CheckOutcome.PASS),
                    result("screen.defects", "Dead pixels", CheckOutcome.PASS),
                    result("security.root", "Root", CheckOutcome.PASS),
                ),
            ),
            report(
                "Redmi Note 12",
                listOf(
                    result("hardware.battery", "Battery", CheckOutcome.CAUTION),
                    result("screen.defects", "Dead pixels", CheckOutcome.FAIL),
                    result("security.root", "Root", CheckOutcome.PASS),
                ),
            ),
        )
    }

    @Test
    fun a_split_decision_refuses_to_recommend() {
        // The render that matters most: the app must not pick when each phone is better in places.
        render(
            "compare-2-split",
            report(
                "realme RMX5110",
                listOf(
                    result("hardware.battery", "Battery", CheckOutcome.PASS),
                    result("screen.defects", "Dead pixels", CheckOutcome.FAIL),
                ),
            ),
            report(
                "Redmi Note 12",
                listOf(
                    result("hardware.battery", "Battery", CheckOutcome.FAIL),
                    result("screen.defects", "Dead pixels", CheckOutcome.PASS),
                ),
            ),
        )
    }

    @Test
    fun a_check_only_one_phone_was_tested_for() {
        // "Not tested" must not read as a pass, which is what a blank cell would do.
        render(
            "compare-3-not-tested",
            report(
                "realme RMX5110",
                listOf(
                    result("hardware.battery", "Battery", CheckOutcome.PASS),
                    result("screen.defects", "Dead pixels", CheckOutcome.PASS),
                ),
            ),
            report("Redmi Note 12", listOf(result("hardware.battery", "Battery", CheckOutcome.PASS))),
        )
    }

    @Test
    fun nothing_to_compare_yet() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                CompareScreen(
                    comparison = null,
                    candidates = listOf(report("realme RMX5110", emptyList())),
                    onPick = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/compare-4-need-two.png")
    }
}
