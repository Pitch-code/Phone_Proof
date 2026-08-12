package com.phoneproof.feature.screentest

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.device.ScreenFinding
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

/**
 * Plain JVM tests. The ViewModel holds no Android dependency, so the sequencing that decides whether
 * a run counts as complete is testable without Robolectric.
 */
class ScreenTestViewModelTest {

    private fun viewModel() = ScreenTestViewModel()

    @Test
    fun `starts on the intro so the brightness warning is read first`() {
        assertThat(viewModel().uiState.value.phase).isEqualTo(ScreenTestPhase.INTRO)
    }

    @Test
    fun `starting shows the first pattern with nothing viewed yet`() {
        val subject = viewModel()
        subject.onStart()

        assertThat(subject.uiState.value.phase).isEqualTo(ScreenTestPhase.PATTERN)
        assertThat(subject.uiState.value.index).isEqualTo(0)
        assertThat(subject.uiState.value.viewed).isEqualTo(0)
    }

    @Test
    fun `each tap counts one pattern as viewed`() {
        val subject = viewModel()
        subject.onStart()
        subject.onPatternSeen()
        subject.onPatternSeen()

        assertThat(subject.uiState.value.viewed).isEqualTo(2)
        assertThat(subject.uiState.value.index).isEqualTo(2)
    }

    @Test
    fun `tapping through every pattern reaches the question with a full count`() {
        val subject = viewModel()
        subject.onStart()
        val total = subject.uiState.value.total
        repeat(total) { subject.onPatternSeen() }

        assertThat(subject.uiState.value.phase).isEqualTo(ScreenTestPhase.QUESTION)
        assertThat(subject.uiState.value.viewed).isEqualTo(total)
    }

    @Test
    fun `the count never runs past the number of patterns`() {
        val subject = viewModel()
        subject.onStart()
        val total = subject.uiState.value.total
        repeat(total + 5) { subject.onPatternSeen() }

        assertThat(subject.uiState.value.viewed).isEqualTo(total)
    }

    @Test
    fun `a full run answered clean is a pass`() {
        val subject = viewModel()
        subject.onStart()
        repeat(subject.uiState.value.total) { subject.onPatternSeen() }
        subject.onAnswer(ScreenFinding.NOTHING)

        assertThat(subject.uiState.value.phase).isEqualTo(ScreenTestPhase.FINISHED)
        assertThat(subject.uiState.value.result?.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `stopping early keeps the partial count, so a clean answer is not a pass`() {
        // The point of tracking viewed separately from index. Someone who bails after one pattern
        // and answers "nothing" must not be handed a clean bill of health.
        val subject = viewModel()
        subject.onStart()
        subject.onPatternSeen()
        subject.onStopEarly()
        subject.onAnswer(ScreenFinding.NOTHING)

        assertThat(subject.uiState.value.result?.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `stopping early to report a fault still records the fault`() {
        val subject = viewModel()
        subject.onStart()
        subject.onPatternSeen()
        subject.onStopEarly()
        subject.onAnswer(ScreenFinding.LARGE_PATCHES)

        assertThat(subject.uiState.value.result?.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `stopping early goes straight to the question`() {
        val subject = viewModel()
        subject.onStart()
        subject.onStopEarly()

        assertThat(subject.uiState.value.phase).isEqualTo(ScreenTestPhase.QUESTION)
    }

    @Test
    fun `taps are ignored once the patterns are done`() {
        // Guards against a late tap landing after the question appears and inflating the count.
        val subject = viewModel()
        subject.onStart()
        subject.onStopEarly()
        val before = subject.uiState.value.viewed

        subject.onPatternSeen()

        assertThat(subject.uiState.value.viewed).isEqualTo(before)
        assertThat(subject.uiState.value.phase).isEqualTo(ScreenTestPhase.QUESTION)
    }

    @Test
    fun `retesting clears the previous answer completely`() {
        val subject = viewModel()
        subject.onStart()
        repeat(subject.uiState.value.total) { subject.onPatternSeen() }
        subject.onAnswer(ScreenFinding.SMALL_DOTS)
        subject.onRetest()

        val state = subject.uiState.value
        assertThat(state.phase).isEqualTo(ScreenTestPhase.INTRO)
        assertThat(state.result).isNull()
        assertThat(state.viewed).isEqualTo(0)
        assertThat(state.index).isEqualTo(0)
    }

    @Test
    fun `every pattern is a different colour`() {
        // A duplicated colour would silently reduce the test's coverage of the subpixels.
        val colours = DefaultPatterns.map { it.colour }

        assertThat(colours).containsNoDuplicates()
    }

    @Test
    fun `the patterns cover black, white and each subpixel`() {
        // Each fault needs at least one pattern on which it is unmissable: dead pixels on white,
        // stuck pixels on black, and a single dead subpixel on its own primary.
        val names = DefaultPatterns.map { it.name }

        assertThat(names).containsAtLeast("White", "Black", "Red", "Green", "Blue")
    }

    @Test
    fun `every pattern says what to look for`() {
        DefaultPatterns.forEach { pattern ->
            assertThat(pattern.lookFor).isNotEmpty()
        }
    }
}
