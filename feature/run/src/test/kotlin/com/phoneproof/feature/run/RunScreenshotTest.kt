package com.phoneproof.feature.run

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.model.Confidence
import com.phoneproof.core.model.Measurement
import com.phoneproof.core.run.RunSession
import com.phoneproof.core.run.RunState
import com.phoneproof.core.run.RunVerdict
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the run at the points that matter.
 *
 * A half-finished run with one fault already found is the state this screen exists for, and it is
 * close to impossible to reach by hand on a development machine — it needs a real phone with a real
 * dead patch on it. Constructing the state directly is what makes it reviewable at all.
 *
 * The verdict is rendered at all four grades for the same reason the lock screen is: "walk away"
 * cannot be produced without a handset a lender still controls, so without this it would ship unseen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class RunScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    // ------------------------------------------------------------------ the checklist

    @Test
    fun the_intro_is_read_once_before_anything_starts() {
        renderChecklist("run-1-intro", RunState())
    }

    @Test
    fun a_run_partway_through_with_a_fault_already_found() {
        renderChecklist(
            "run-2-partway",
            state(
                results = mapOf(
                    "scan" to listOf(storagePass(), batteryUnknown()),
                    "touch" to listOf(touchFail()),
                ),
            ),
        )
    }

    @Test
    fun a_run_with_a_skipped_step() {
        renderChecklist(
            "run-3-skipped",
            state(
                results = mapOf("scan" to listOf(storagePass())),
                skipped = listOf("touch"),
            ),
        )
    }

    @Test
    fun everything_dealt_with_so_the_verdict_is_the_only_action_left() {
        renderChecklist(
            "run-4-finished",
            state(
                results = mapOf(
                    "scan" to listOf(storagePass()),
                    "touch" to listOf(touchFail()),
                    "screen-patterns" to listOf(displayPass()),
                    "audio" to listOf(microphonePass(), speakerCaution()),
                    "camera" to listOf(cameraPass()),
                    "imei" to listOf(imeiPass()),
                ),
                done = listOf("guide"),
                skipped = listOf("claims"),
            ),
        )
    }

    @Test
    fun the_checklist_in_light_mode() {
        // Light mode has produced two real bugs in this project already, both of them invisible in
        // dark. Every new screen gets rendered in both.
        renderChecklist(
            "run-5-partway-light",
            state(
                results = mapOf(
                    "scan" to listOf(storagePass(), batteryUnknown()),
                    "touch" to listOf(touchFail()),
                ),
            ),
            themeMode = ThemeMode.LIGHT,
        )
    }

    // ------------------------------------------------------------------ the verdict

    @Test
    fun nothing_found_and_everything_essential_measured() {
        renderVerdict(
            "run-6-verdict-clean",
            state(
                results = mapOf(
                    "scan" to listOf(storagePass()),
                    "touch" to listOf(touchPass()),
                    "screen-patterns" to listOf(displayPass()),
                    "audio" to listOf(microphonePass()),
                    "camera" to listOf(cameraPass()),
                ),
            ),
        )
    }

    @Test
    fun faults_worth_haggling_over_lead_with_what_to_say() {
        renderVerdict(
            "run-7-verdict-negotiate",
            negotiableRun(),
        )
    }

    /** The lower half, which `onRoot` cannot see: the problem cards and the untested list. */
    @Test
    fun the_evidence_below_the_verdict() {
        renderVerdict(
            "run-8-verdict-negotiate-scrolled",
            negotiableRun(),
            scrollTo = "Not tested",
        )
    }

    @Test
    fun a_lender_still_holding_the_phone_ends_it() {
        renderVerdict(
            "run-9-verdict-walk-away",
            state(
                results = mapOf(
                    "scan" to listOf(storagePass(), remoteLockFail()),
                    "touch" to listOf(touchPass()),
                    "screen-patterns" to listOf(displayPass()),
                    "audio" to listOf(microphonePass()),
                    "camera" to listOf(cameraPass()),
                ),
            ),
        )
    }

    @Test
    fun too_little_measured_to_say_anything() {
        renderVerdict(
            "run-10-verdict-incomplete",
            state(
                results = mapOf("scan" to listOf(storagePass())),
                skipped = listOf("touch", "audio"),
            ),
        )
    }

    @Test
    fun the_verdict_in_light_mode() {
        renderVerdict("run-11-verdict-negotiate-light", negotiableRun(), themeMode = ThemeMode.LIGHT)
    }

    // ------------------------------------------------------------------ plumbing

    private fun negotiableRun(): RunState = state(
        results = mapOf(
            "scan" to listOf(storagePass(), batteryCaution()),
            "touch" to listOf(touchFail()),
            "screen-patterns" to listOf(displayPass()),
            "audio" to listOf(microphonePass(), speakerCaution()),
            "camera" to listOf(cameraPass()),
        ),
        skipped = listOf("claims"),
    )

    private fun renderChecklist(
        name: String,
        state: RunState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                RunChecklistScreen(
                    state = state,
                    onStart = {},
                    onOpenStep = {},
                    onSkip = {},
                    onSeeVerdict = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    private fun renderVerdict(
        name: String,
        state: RunState,
        themeMode: ThemeMode = ThemeMode.DARK,
        scrollTo: String? = null,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                RunVerdictScreen(
                    verdict = RunVerdict.of(state),
                    deviceLabel = "realme RMX5110 · Android 16 (API 36)",
                    savedToReports = true,
                    onOpenStep = {},
                    onOpenReports = {},
                    onTestAnotherPhone = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // onRoot captures the visible screen only, so anything below the fold goes unreviewed unless
        // it is scrolled into view first. That has already let one broken layout through in this repo.
        scrollTo?.let { composeRule.onNodeWithText(it.uppercase()).performScrollTo() }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    private fun state(
        results: Map<String, List<CheckResult>> = emptyMap(),
        skipped: List<String> = emptyList(),
        done: List<String> = emptyList(),
    ): RunState {
        val session = RunSession(now = { 1_700_000_000_000L })
        session.start()
        results.forEach { (step, stepResults) -> session.record(step, stepResults) }
        done.forEach(session::markDone)
        skipped.forEach(session::skip)
        return session.state.value
    }

    // Results written in the app's own voice rather than as "test 1 failed", because these renders are
    // how the copy gets reviewed as well as the layout.

    private fun storagePass() = CheckResult(
        id = "hardware.storage",
        title = "Storage",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "128 GB, which matches what the phone claims",
        measurements = listOf(Measurement("Total", "128", "GB"), Measurement("Free", "41", "GB")),
    )

    private fun batteryUnknown() = CheckResult(
        id = "hardware.battery",
        title = "Battery health",
        outcome = CheckOutcome.UNKNOWN,
        confidence = Confidence.LOW,
        headline = "Android does not let any app read the real state of health",
    )

    private fun batteryCaution() = CheckResult(
        id = "hardware.battery",
        title = "Battery health",
        outcome = CheckOutcome.CAUTION,
        confidence = Confidence.MEDIUM,
        headline = "Charging at 31°C with the screen off, which is warmer than it should be",
        consequence = "A battery that runs warm at rest is usually near the end of its life, and a " +
            "replacement is 1,500 to 2,500 rupees fitted.",
        action = "Ask how old the battery is, and take 1,500 off if it has never been changed.",
        falsePositiveCauses = listOf(
            "The phone has been in a hot pocket or in the sun",
            "A fast charger was plugged in moments before",
        ),
    )

    private fun touchFail() = CheckResult(
        id = "screen.touch_coverage",
        title = "Touch response",
        outcome = CheckOutcome.FAIL,
        confidence = Confidence.HIGH,
        headline = "A patch near the bottom-right never responded",
        consequence = "That is where the space bar and the send button sit. You would fight this " +
            "every time you typed a message.",
        action = "Get 2,000 off for a digitiser, or walk away — this one does not get better.",
        measurements = listOf(
            Measurement("Cells reached", "498 of 512"),
            Measurement("Largest dead patch", "9", "cells"),
        ),
        falsePositiveCauses = listOf(
            "A screen protector lifting at the corner",
            "Wet or greasy fingers",
            "A notification banner covering the area mid-test",
        ),
    )

    private fun touchPass() = CheckResult(
        id = "screen.touch_coverage",
        title = "Touch response",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "Every part of the screen responded, edges included",
        measurements = listOf(Measurement("Cells reached", "512 of 512")),
    )

    private fun displayPass() = CheckResult(
        id = "screen.defects",
        title = "Dead pixels and burn-in",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.MEDIUM,
        headline = "Nothing reported on any of the six colour pages",
    )

    private fun microphonePass() = CheckResult(
        id = "hardware.microphone",
        title = "Microphone",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "Picked up sound at a healthy level across three seconds",
        measurements = listOf(Measurement("Peak", "-14", "dBFS")),
    )

    private fun speakerCaution() = CheckResult(
        id = "hardware.speaker",
        title = "Loudspeaker",
        outcome = CheckOutcome.CAUTION,
        confidence = Confidence.MEDIUM,
        headline = "You heard the tone but the microphone did not",
        consequence = "The speaker works, but quietly enough that the phone could not hear its own " +
            "tone — often a blocked grille or a tired driver.",
        action = "Play a video at full volume before you decide, and ask for 500 off.",
        falsePositiveCauses = listOf(
            "A noisy shop",
            "A finger or a case over the speaker grille",
            "Media volume turned down after the test started",
        ),
    )

    private fun cameraPass() = CheckResult(
        id = "hardware.camera",
        title = "Back camera",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "Produced a live, varying picture",
        measurements = listOf(Measurement("Frames read", "12")),
    )

    private fun imeiPass() = CheckResult(
        id = "security.imei_checksum",
        title = "IMEI checksum",
        outcome = CheckOutcome.PASS,
        confidence = Confidence.HIGH,
        headline = "The fifteen digits add up, so the number is at least a real IMEI",
    )

    private fun remoteLockFail() = CheckResult(
        id = "security.device_admin_lock",
        title = "Remote lock control",
        outcome = CheckOutcome.FAIL,
        confidence = Confidence.HIGH,
        headline = "Finance Lock is the device owner of this phone",
        consequence = "Whoever installed it can lock this handset from anywhere, weeks after you " +
            "have paid for it, and you would have no way to undo it.",
        action = "Do not buy this until the phone has been factory reset and this is gone.",
        falsePositiveCauses = listOf(
            "A company phone with legitimate management software",
            "A parental-control app the seller forgot about",
        ),
    )
}
