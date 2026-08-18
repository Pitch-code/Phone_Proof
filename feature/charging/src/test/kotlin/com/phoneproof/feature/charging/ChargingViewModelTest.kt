package com.phoneproof.feature.charging

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.device.PlugType
import com.phoneproof.core.device.ChargeSample
import com.phoneproof.core.device.ChargeSource
import com.phoneproof.core.model.CheckOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * What the charging test does when the cable moves.
 *
 * This file exists because of a bug found on a handset: the buyer unplugged the charger mid-measurement and
 * the countdown carried on to the end. There was no unit test for this state machine at all — only screenshot
 * renders of states handed to it ready-made — so nothing in the project could have noticed either that
 * symptom or the worse one behind it, which was a confident wattage published for seconds when nothing was
 * connected.
 *
 * Every test here runs in virtual time. That is only possible because the tick loop counts its own ticks
 * rather than reading the wall clock, so these twenty-second measurements take no real time and cannot go
 * flaky on a slow machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChargingViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    /** Replays one value so the second collector opened by `measure()` sees the current state. */
    private val samples = MutableSharedFlow<ChargeSample>(replay = 1, extraBufferCapacity = 64)

    /**
     * Never suspends.
     *
     * `emit` on a shared flow waits for slow collectors, and under a `StandardTestDispatcher` no collector
     * has run yet — so a suspending emit deadlocks the test rather than failing it. The buffer plus
     * `tryEmit` keeps these tests describing the charger, not the scheduler.
     */
    private fun MutableSharedFlow<ChargeSample>.emitNow(sample: ChargeSample) {
        check(tryEmit(sample)) { "the test buffer is full" }
    }

    private val source = object : ChargeSource {
        override fun snapshot(): ChargeSample? = samples.replayCache.lastOrNull()
        override fun stream(): Flow<ChargeSample> = samples
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sample(plugged: Boolean, charging: Boolean = plugged) = ChargeSample(
        plugType = if (plugged) PlugType.AC else PlugType.NONE,
        charging = charging,
        percent = 46,
        voltageMillivolts = 4_180,
        currentMilliamps = if (charging) 2_900 else null,
        temperatureCelsius = 31.2,
    )

    @Test
    fun unplugging_mid_test_stops_the_countdown_and_goes_back_to_waiting() = runTest(dispatcher) {
        val viewModel = ChargingViewModel(source)
        samples.emitNow(sample(plugged = true))
        runCurrent()

        assertThat(viewModel.uiState.value.stage).isEqualTo(ChargingStage.MEASURING)

        advanceTimeBy(5_000)
        samples.emitNow(sample(plugged = false))
        // Past the grace period, so this is a cable that has been taken out rather than a socket blinking.
        advanceTimeBy(4_000)
        runCurrent()

        val state = viewModel.uiState.value
        // The reported symptom: this used to still be MEASURING, counting down to zero.
        assertThat(state.stage).isEqualTo(ChargingStage.WAITING)
        // And the worse one: no verdict at all, rather than a wattage measured against thin air.
        assertThat(state.result).isNull()
    }

    @Test
    fun going_back_to_waiting_leaves_nothing_recorded_against_the_phone() = runTest(dispatcher) {
        val viewModel = ChargingViewModel(source)
        samples.emitNow(sample(plugged = true))
        runCurrent()
        advanceTimeBy(2_000)

        samples.emitNow(sample(plugged = false))
        advanceTimeBy(4_000)
        runCurrent()

        // Pulling the cable is not a fault, so the dropout counter must not carry a number into the next
        // attempt. Otherwise a buyer who knocked the cable once would be told the socket is loose.
        assertThat(viewModel.uiState.value.dropouts).isEqualTo(0)
        assertThat(viewModel.uiState.value.secondsLeft).isEqualTo(0)
    }

    @Test
    fun plugging_back_in_starts_a_fresh_measurement() = runTest(dispatcher) {
        val viewModel = ChargingViewModel(source)
        samples.emitNow(sample(plugged = true))
        runCurrent()
        advanceTimeBy(2_000)

        samples.emitNow(sample(plugged = false))
        advanceTimeBy(4_000)
        runCurrent()
        assertThat(viewModel.uiState.value.stage).isEqualTo(ChargingStage.WAITING)

        // The prompt on the screen says the test starts again on its own, so it has to.
        samples.emitNow(sample(plugged = true))
        runCurrent()

        assertThat(viewModel.uiState.value.stage).isEqualTo(ChargingStage.MEASURING)
        assertThat(viewModel.uiState.value.secondsLeft).isEqualTo(20)
    }

    @Test
    fun a_brief_dropout_that_comes_back_is_counted_and_does_not_abort() = runTest(dispatcher) {
        val viewModel = ChargingViewModel(source)
        samples.emitNow(sample(plugged = true))
        runCurrent()
        advanceTimeBy(3_000)

        // A loose socket lets go for a moment and recovers on its own. Well inside the grace period, so the
        // measurement has to survive it — this is the finding the whole check exists to make.
        samples.emitNow(sample(plugged = false))
        advanceTimeBy(500)
        samples.emitNow(sample(plugged = true))
        advanceTimeBy(500)
        runCurrent()

        assertThat(viewModel.uiState.value.stage).isEqualTo(ChargingStage.MEASURING)
        assertThat(viewModel.uiState.value.dropouts).isEqualTo(1)
    }

    @Test
    fun unplugging_after_a_dropout_publishes_the_finding_instead_of_discarding_it() =
        runTest(dispatcher) {
            val viewModel = ChargingViewModel(source)
            samples.emitNow(sample(plugged = true))
            runCurrent()
            advanceTimeBy(2_000)

            // The socket lets go and recovers: a real fault, now on record.
            samples.emitNow(sample(plugged = false))
            advanceTimeBy(500)
            samples.emitNow(sample(plugged = true))
            advanceTimeBy(1_000)
            runCurrent()
            assertThat(viewModel.uiState.value.dropouts).isEqualTo(1)

            // Then the buyer takes the cable out. The remaining seconds are worth less than what was already
            // found, so this must not throw the finding away by going back to WAITING.
            samples.emitNow(sample(plugged = false))
            advanceTimeBy(4_000)
            runCurrent()

            val state = viewModel.uiState.value
            assertThat(state.stage).isEqualTo(ChargingStage.DONE)
            assertThat(state.result).isNotNull()
            // A loose socket outranks the speed, so this is the verdict a buyer needs to see.
            assertThat(state.result!!.outcome).isEqualTo(CheckOutcome.CAUTION)
        }

    @Test
    fun a_full_twenty_seconds_plugged_in_produces_a_verdict() = runTest(dispatcher) {
        val viewModel = ChargingViewModel(source)
        samples.emitNow(sample(plugged = true))
        runCurrent()

        advanceTimeBy(21_000)
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.stage).isEqualTo(ChargingStage.DONE)
        assertThat(state.result).isNotNull()
        assertThat(state.secondsLeft).isEqualTo(0)
    }

    @Test
    fun giving_up_without_a_charger_reports_not_tested_rather_than_a_pass() = runTest(dispatcher) {
        val viewModel = ChargingViewModel(source)
        samples.emitNow(sample(plugged = false))
        runCurrent()

        viewModel.giveUp()
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.stage).isEqualTo(ChargingStage.DONE)
        // Not a fault and not a pass. The phone was never asked the question.
        assertThat(state.result!!.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }
}
