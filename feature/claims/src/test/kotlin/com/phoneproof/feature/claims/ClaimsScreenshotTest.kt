package com.phoneproof.feature.claims

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.device.ClaimedSpecs
import com.phoneproof.checks.device.ClaimedSpecsCheck
import com.phoneproof.checks.device.DeviceFacts
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.model.CheckResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ClaimsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun facts(storage: Long, ram: Long) = DeviceFacts(
        manufacturer = "realme",
        brand = "realme",
        model = "RMX5110",
        device = "RE6440L1",
        hardware = "mt6878",
        sdkInt = 36,
        androidRelease = "16",
        totalStorageBytes = storage,
        totalRamBytes = ram,
    )

    private fun render(
        name: String,
        storage: String = "",
        ram: String = "",
        model: String = "",
        result: CheckResult? = null,
    ) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                ClaimsScreen(
                    storage = storage,
                    ram = ram,
                    model = model,
                    result = result,
                    onStorageChanged = {},
                    onRamChanged = {},
                    onModelChanged = {},
                    onCompare = {},
                    onReset = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun the_empty_form() {
        render("claims-1-form")
    }

    @Test
    fun an_honest_phone_matches() {
        // The realme's real readings against an honest 128 GB / 8 GB claim. This has to pass, or the
        // check would accuse every phone: 109.7 GB usable is only 86% of a marketed 128 GB.
        render(
            "claims-2-matches",
            storage = "128",
            ram = "8",
            model = "realme P4 5G",
            result = ClaimedSpecsCheck.evaluate(
                ClaimedSpecs(storageGb = 128, ramGb = 8, modelName = "realme P4 5G"),
                facts(storage = 109_678_919_680, ram = 7_900_000_000),
            ),
        )
    }

    @Test
    fun a_phone_with_less_than_promised() {
        // The fraud: sold as 128 GB, holds 32.
        render(
            "claims-3-short",
            storage = "128",
            ram = "8",
            result = ClaimedSpecsCheck.evaluate(
                ClaimedSpecs(storageGb = 128, ramGb = 8),
                facts(storage = 30_000_000_000, ram = 7_900_000_000),
            ),
        )
    }
}
