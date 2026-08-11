package com.phoneproof.feature.emilock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.checks.emilock.AdminApp
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
 * Renders every outcome of the lock check.
 *
 * This is where the stateless-screen decision pays off: a device-owner failure cannot be reproduced
 * on a normal handset, so without rendering from a constructed state the most important screen in
 * the app would go unreviewed until someone happened to buy a locked phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class EmiLockScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    private fun render(name: String, snapshot: DeviceAdminSnapshot) {
        val result = EmiLockEvaluator.evaluate(snapshot)
        composeRule.setContent {
            PhoneProofTheme(darkTheme = true) {
                EmiLockScreen(
                    result = result,
                    onRecheck = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun clean_phone_passes() {
        render("emilock-1-pass", DeviceAdminSnapshot())
    }

    @Test
    fun device_owner_is_the_expensive_one() {
        render(
            "emilock-2-device-owner",
            DeviceAdminSnapshot(
                listOf(AdminApp("com.example.financelock", "Finance Lock", isDeviceOwner = true)),
            ),
        )
    }

    @Test
    fun plain_admin_is_a_caution() {
        render(
            "emilock-3-caution",
            DeviceAdminSnapshot(listOf(AdminApp("com.example.guard", "Mobile Guard"))),
        )
    }

    @Test
    fun unreadable_platform_says_so() {
        render("emilock-4-cant-tell", DeviceAdminSnapshot(queryFailed = true))
    }
}
