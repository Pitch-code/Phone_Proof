package com.phoneproof.core.designsystem.theme

import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * The gate in front of every looping animation in the app.
 *
 * Worth a direct test because the alternative is asserting the absence of motion in a screenshot, which a
 * single rendered frame cannot show. If this helper silently returned true forever, all five loops would keep
 * running for someone who had switched animation off and nothing would fail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AnimationsEnabledTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun readInComposition(): Boolean {
        var seen by mutableStateOf<Boolean?>(null)
        composeRule.setContent { seen = rememberAnimationsEnabled() }
        composeRule.waitForIdle()
        return requireNotNull(seen) { "rememberAnimationsEnabled did not produce a value" }
    }

    @Test
    fun animation_is_on_by_default() {
        assertThat(readInComposition()).isTrue()
    }

    @Test
    fun a_scale_of_zero_means_the_owner_has_switched_animation_off() {
        // What Android's "remove animations" accessibility switch actually writes.
        ShadowSettings.setAnimatorDurationScale(0f)

        assertThat(readInComposition()).isFalse()
    }

    @Test
    fun a_normal_scale_leaves_animation_on() {
        ShadowSettings.setAnimatorDurationScale(1f)

        assertThat(readInComposition()).isTrue()
    }

    @Test
    fun a_slowed_down_scale_still_counts_as_on() {
        // Developers and some users set 0.5x or 2x rather than off. Only zero means "no motion", so
        // anything above it must not be read as a request for stillness.
        ShadowSettings.setAnimatorDurationScale(2f)

        assertThat(readInComposition()).isTrue()
    }

    @Test
    fun the_setting_this_reads_is_the_one_android_documents() {
        // Guards a plausible mistake: TRANSITION_ANIMATION_SCALE and WINDOW_ANIMATION_SCALE are siblings of
        // this key and control different things. Reading the wrong one would look like it worked.
        assertThat(Settings.Global.ANIMATOR_DURATION_SCALE).isEqualTo("animator_duration_scale")
    }
}
