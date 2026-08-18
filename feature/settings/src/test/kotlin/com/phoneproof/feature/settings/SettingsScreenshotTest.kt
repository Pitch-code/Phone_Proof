package com.phoneproof.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.phoneproof.core.designsystem.theme.PhoneProofTheme
import com.phoneproof.core.designsystem.theme.ThemeMode
import com.phoneproof.core.preferences.Entitlement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SettingsScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val outputDir: String =
        System.getProperty("phoneproof.screenshotDir") ?: "build/screenshots"

    /**
     * A made-up version, on purpose, and not updated when the real one changes.
     *
     * These renders would otherwise churn on every release for no review value. Kept obviously fake so that
     * a reviewer reading "0.1.0" here does not conclude the version wiring has regressed - the real version
     * comes from version.properties and is asserted against BuildConfig by AppVersionTest.
     */
    private val FIXTURE_VERSION = "0.1.0"

    private fun render(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            PhoneProofTheme(themeMode = themeMode) {
                SettingsScreen(
                    state = SettingsUiState(
                        themeMode = themeMode,
                        versionName = FIXTURE_VERSION,
                        versionCode = 1L,
                        billingAvailable = false,
                        // Set, because a free-trial user always has a count — the route computes it
                        // for every FREE entitlement. Leaving it null rendered "Active on this
                        // device", which is the paid-tier wording and a state nobody will ever see.
                        freeScansLeft = Entitlement.FREE_SCAN_LIMIT,
                    ),
                    onThemeSelected = {},
                    onOpenPrivacyPolicy = {},
                    onShareApp = {},
                    onOpenDiagnostics = {},
                    onChoosePlan = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/$name.png")
    }

    @Test
    fun settings_dark() {
        render("settings-1-dark", ThemeMode.DARK)
    }

    @Test
    fun settings_light() {
        // The reason this test exists: light mode was previously broken by construction. Material
        // component colours switched while every custom surface, border and outcome colour stayed
        // dark, so "light" rendered as a dark app with pale buttons. A render is the only way to
        // catch that, because it compiles perfectly either way.
        render("settings-2-light", ThemeMode.LIGHT)
    }

    // The two shots above are cut off by the viewport at the Shop card, so everything below it —
    // version, build, privacy policy, share, and the link to Diagnostics — was shipping unrendered
    // by any test. A tall viewport is used rather than scrolling because it captures the whole
    // column in one image, which is what makes a missing or misworded row visible.
    @Test
    @Config(qualifiers = "w411dp-h2400dp-xhdpi")
    fun settings_full_column() {
        render("settings-3-full", ThemeMode.DARK)
    }

    @Test
    @Config(qualifiers = "w411dp-h3000dp-xhdpi")
    fun a_payment_still_settling() {
        // UPI and net-banking sit pending for minutes, which is ordinary here rather than an edge case.
        //
        // Rendered because the copy is the whole point of the state: someone who has just paid and sees
        // "Free trial" concludes the payment failed, and the usual response to that is to pay again. This
        // is the screen that has to stop them, so the wording needs looking at rather than compiling.
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.LIGHT) {
                SettingsScreen(
                    state = SettingsUiState(
                        themeMode = ThemeMode.LIGHT,
                        versionName = FIXTURE_VERSION,
                        versionCode = 1,
                        billingAvailable = true,
                        entitlement = Entitlement.FREE,
                        freeScansLeft = 2,
                        playPrices = mapOf(PremiumPlan.PREMIUM.productId to "₹99.00"),
                        pendingProductIds = listOf(PremiumPlan.PREMIUM.productId),
                    ),
                    onThemeSelected = {},
                    onOpenPrivacyPolicy = {},
                    onShareApp = {},
                    onOpenDiagnostics = {},
                    onChoosePlan = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/settings-5-payment-pending.png")
    }

    /**
     * The Shop tier's branding fields and the debug tier switcher.
     *
     * These shipped with no render at all: every test above leaves `entitlement` at FREE, so two whole
     * sections — five interactive controls between them — had never been drawn, let alone looked at.
     *
     * The switcher now appears because these tests run against the debug variant, which compiles the
     * `src/debug` copy of `TierOverride`. A release build compiles the `src/release` copy, which draws
     * nothing — so this render is proof the debug affordance exists, not that it ships.
     */
    @Test
    @Config(qualifiers = "w411dp-h3000dp-xhdpi")
    fun settings_with_shop_branding_and_testing_controls() {
        composeRule.setContent {
            PhoneProofTheme(themeMode = ThemeMode.DARK) {
                SettingsScreen(
                    state = SettingsUiState(
                        themeMode = ThemeMode.DARK,
                        versionName = FIXTURE_VERSION,
                        versionCode = 1,
                        billingAvailable = false,
                        entitlement = Entitlement.SHOP,
                        // Set so the render shows what a Shop customer actually sees on their own
                        // tier: "Active on this device", not "Unavailable".
                        ownedPlan = PremiumPlan.SHOP,
                        shopName = "Krishna Mobiles",
                        shopContact = "98765 43210 · MG Road",
                        shopLogoPath = "/files/branding/shop-logo.png",
                    ),
                    onThemeSelected = {},
                    onOpenPrivacyPolicy = {},
                    onShareApp = {},
                    onOpenDiagnostics = {},
                    onChoosePlan = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composeRule.onRoot().captureRoboImage("$outputDir/settings-4-shop.png")
    }
}
