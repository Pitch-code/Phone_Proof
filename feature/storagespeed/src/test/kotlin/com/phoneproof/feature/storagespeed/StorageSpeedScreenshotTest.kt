package com.phoneproof.feature.storagespeed

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.device.StorageSpeedAttempt
import com.phoneproof.checks.device.StorageSpeedCheck
import com.phoneproof.checks.device.StorageSpeedTrace
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class StorageSpeedScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private val fourMb = 4L * 1024 * 1024
    private val plentyFree = 30L * 1024 * 1024 * 1024

    private fun trace(
        chunkMillis: Long,
        stalls: List<Long> = emptyList(),
        matched: Boolean = true,
    ): StorageSpeedTrace {
        val chunks = List(16) { chunkMillis } + stalls
        return StorageSpeedTrace(
            attempt = StorageSpeedAttempt.MEASURED,
            bytesWritten = fourMb * chunks.size,
            writeMillis = chunks.sum(),
            readMillis = chunks.sum() / 3,
            chunkBytes = fourMb,
            chunkMillis = chunks,
            readBackMatched = matched,
            freeBytes = plentyFree,
        )
    }

    @Test
    fun before_it_runs_it_says_what_it_cannot_prove() {
        // The most important thing on this screen. "Fake storage" means a chip claiming 256 GB and holding
        // 32, and a buyer will assume that is what this checks — so the note appears before the test, not
        // only after it.
        render("storagespeed-1-ready", StorageSpeedUiState(freeBytes = plentyFree))
    }

    @Test
    fun a_phone_too_full_to_test_is_told_so_rather_than_filled() {
        render(
            "storagespeed-2-too-full",
            StorageSpeedUiState(freeBytes = 150L * 1024 * 1024),
        )
    }

    @Test
    fun writing() {
        render(
            "storagespeed-3-writing",
            StorageSpeedUiState(
                stage = StorageSpeedStage.RUNNING,
                progress = 0.32f,
                freeBytes = plentyFree,
            ),
        )
    }

    @Test
    fun reading_it_back() {
        render(
            "storagespeed-4-reading",
            StorageSpeedUiState(
                stage = StorageSpeedStage.RUNNING,
                progress = 0.78f,
                freeBytes = plentyFree,
            ),
        )
    }

    @Test
    fun healthy_flash() {
        render("storagespeed-5-pass", done(trace(chunkMillis = 40L)))
    }

    @Test
    fun very_slow_flash_which_is_what_recycled_chips_look_like() {
        render("storagespeed-6-slow", done(trace(chunkMillis = 500L)))
    }

    @Test
    fun a_stall_the_average_would_have_hidden() {
        // Respectable average, phone freezes for two seconds while saving a photo. No benchmark reports this.
        render("storagespeed-7-stall", done(trace(chunkMillis = 40L, stalls = listOf(2_000L, 1_800L))))
    }

    @Test
    fun bytes_that_came_back_wrong() {
        // The one flat failure in the check, and the only verdict in the app whose action says the rest of
        // the report cannot be trusted either.
        render("storagespeed-8-corruption", done(trace(chunkMillis = 40L, matched = false)))
    }

    @Test
    fun a_test_that_could_not_run() {
        render(
            "storagespeed-9-failed",
            done(StorageSpeedTrace(StorageSpeedAttempt.FAILED, freeBytes = plentyFree)),
        )
    }

    @Test
    fun ready_in_light_mode() {
        render(
            "storagespeed-10-ready-light",
            StorageSpeedUiState(freeBytes = plentyFree),
            ThemeMode.LIGHT,
        )
    }

    private fun done(trace: StorageSpeedTrace) = StorageSpeedUiState(
        stage = StorageSpeedStage.DONE,
        progress = 1f,
        freeBytes = trace.freeBytes,
        result = StorageSpeedCheck.evaluate(trace),
    )

    private fun render(
        name: String,
        state: StorageSpeedUiState,
        themeMode: ThemeMode = ThemeMode.DARK,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                StorageSpeedScreen(
                    state = state,
                    onStart = {},
                    onRestart = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }
}
