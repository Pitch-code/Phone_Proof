package com.phoneproof.feature.charging

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.device.ChargeAttempt
import com.phoneproof.checks.device.ChargeTrace
import com.phoneproof.checks.device.ChargingCheck
import com.phoneproof.checks.device.PlugType
import com.phoneproof.core.device.ChargeSample
import com.phoneproof.core.device.ChargeSource
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ChargingStage {
    /** Waiting for a cable. The app cannot make this happen, so it waits and says so. */
    WAITING,

    /** Plugged in and being watched. */
    MEASURING,

    DONE,
}

@Immutable
data class ChargingUiState(
    val stage: ChargingStage = ChargingStage.WAITING,
    val live: ChargeSample? = null,
    val secondsLeft: Int = 0,
    val dropouts: Int = 0,
    val result: CheckResult? = null,
) {
    val plugged: Boolean get() = live?.plugged == true
    val percent: Int get() = live?.percent ?: 0
    val watts: Double?
        get() = live?.let { sample ->
            sample.currentMilliamps?.let { (sample.voltageMillivolts / 1000.0) * (it / 1000.0) }
        }
}

/**
 * Waits for a cable, then watches what happens.
 *
 * The unusual thing about this test is that the app cannot start it. Every other check runs on demand; this
 * one needs a charger plugged into someone else's phone, which may not exist in the room. So waiting is a
 * first-class state with its own screen, and giving up is a button rather than a timeout — a buyer who has no
 * charger to hand should get an honest "not tested" rather than sit watching a countdown.
 */
class ChargingViewModel(
    private val probe: ChargeSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChargingUiState(live = probe.snapshot()))
    val uiState: StateFlow<ChargingUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        watchForCable()
    }

    /** Ends the test without a charger, honestly. */
    fun giveUp() {
        job?.cancel()
        _uiState.update {
            it.copy(
                stage = ChargingStage.DONE,
                result = ChargingCheck.evaluate(
                    ChargeTrace(
                        attempt = ChargeAttempt.NOT_PLUGGED,
                        batteryPercent = it.percent,
                        temperatureCelsius = it.live?.temperatureCelsius,
                    ),
                ),
            )
        }
    }

    fun restart() {
        job?.cancel()
        _uiState.value = ChargingUiState(live = probe.snapshot())
        watchForCable()
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private fun watchForCable() {
        job = viewModelScope.launch {
            probe.stream()
                .onEach { sample ->
                    _uiState.update { it.copy(live = sample) }
                    if (sample.plugged && _uiState.value.stage == ChargingStage.WAITING) {
                        measure()
                    }
                }
                .collect()
        }
    }

    /**
     * Watches a connected charger for [SAMPLE_SECONDS], counting the times it lets go.
     *
     * A dropout is counted as a **recovery**, not as a disconnection, and that distinction is the whole
     * reliability of this check. A buyer pulling the cable out when they think the test is over would
     * otherwise be recorded as a loose socket. A genuine loose port drops and comes back on its own; a
     * deliberate unplug never comes back.
     *
     * ## Why the tick loop owns the clock
     *
     * This used to be a fixed `withTimeoutOrNull(20s)` racing a countdown derived from
     * `System.currentTimeMillis()`, and **neither one looked at whether the charger was still there.** Pull
     * the cable out and the countdown ran to the end regardless — which is the bug a buyer reported, and the
     * smaller half of it. The larger half was what happened next: the window published a confident
     * `MEASURED` verdict with a wattage averaged over seconds when nothing was connected at all.
     *
     * So the loop below is the only clock, and it is the thing that decides when to stop. It reads
     * [ChargingUiState] rather than local variables because the sample collector runs in a separate
     * coroutine, and a `StateFlow` read is safe across both where a captured `var` would not be.
     *
     * Counting ticks rather than reading the wall clock also makes the whole rule testable in virtual time,
     * and immune to the clock being changed underneath it mid-measurement.
     */
    private suspend fun measure() = coroutineScope {
        _uiState.update {
            it.copy(
                stage = ChargingStage.MEASURING,
                dropouts = 0,
                secondsLeft = SAMPLE_SECONDS,
            )
        }

        val samples = mutableListOf<ChargeSample>()
        var wasPlugged = true
        val stop = CompletableDeferred<StopReason>()

        val collector = launch {
            probe.stream()
                .onEach { sample ->
                    samples += sample
                    if (sample.plugged && !wasPlugged) {
                        _uiState.update { it.copy(dropouts = it.dropouts + 1) }
                    }
                    wasPlugged = sample.plugged
                    _uiState.update { it.copy(live = sample) }
                }
                .collect()
        }

        val ticker = launch {
            var elapsedMillis = 0L
            var unpluggedMillis = 0L

            while (isActive) {
                delay(TICK_MILLIS)
                elapsedMillis += TICK_MILLIS

                val state = _uiState.value
                unpluggedMillis = if (state.plugged) 0L else unpluggedMillis + TICK_MILLIS

                val remaining = SAMPLE_SECONDS * 1000L - elapsedMillis
                _uiState.update {
                    it.copy(secondsLeft = ((remaining + 999) / 1000).coerceAtLeast(0).toInt())
                }

                if (unpluggedMillis >= CABLE_GONE_MILLIS) {
                    // Two very different situations, told apart by whether anything was found before the
                    // cable went. Both are "the charger is not there any more"; only one of them is a
                    // finding, and discarding the other would be inventing one.
                    stop.complete(
                        if (state.dropouts > 0) {
                            StopReason.ENOUGH_ALREADY
                        } else {
                            StopReason.CABLE_REMOVED
                        },
                    )
                    return@launch
                }

                if (elapsedMillis >= SAMPLE_SECONDS * 1000L) {
                    stop.complete(StopReason.FINISHED)
                    return@launch
                }
            }
        }

        val reason = stop.await()
        ticker.cancel()
        collector.cancel()

        when (reason) {
            // Back to waiting rather than to a verdict, and deliberately not to "not tested" either. The
            // cable coming out is nearly always someone knocking it or deciding to stop, and WAITING is the
            // state that says so: the "please connect the charger" prompt reappears on its own, and plugging
            // back in starts a clean measurement. The honest "not tested" is still one tap away on the
            // give-up button, where it belongs — chosen by the buyer rather than assumed for them.
            StopReason.CABLE_REMOVED -> _uiState.update {
                it.copy(stage = ChargingStage.WAITING, secondsLeft = 0, dropouts = 0)
            }

            // The socket already let go and came back at least once. That is the finding this check exists
            // for, and it is worth more than the remaining seconds — so it is published rather than thrown
            // away because the cable came out afterwards.
            StopReason.ENOUGH_ALREADY,
            StopReason.FINISHED,
            -> publish(samples, _uiState.value.dropouts, elapsedSecondsOf(reason))
        }
    }

    /**
     * How long was actually watched, in whole seconds.
     *
     * Reported rather than assumed, because a measurement stopped early is not a twenty-second one and the
     * verdict says "in twenty seconds" out loud. Claiming the full window for a window that was cut short
     * would be a small lie in the one sentence a buyer repeats to the seller.
     */
    private fun elapsedSecondsOf(reason: StopReason): Int = when (reason) {
        StopReason.FINISHED -> SAMPLE_SECONDS
        else -> (SAMPLE_SECONDS - _uiState.value.secondsLeft).coerceAtLeast(1)
    }

    private fun publish(samples: List<ChargeSample>, dropouts: Int, watchedSeconds: Int) {
        val last = samples.lastOrNull() ?: _uiState.value.live
        if (last == null) {
            giveUp()
            return
        }

        // Averaged over the samples that reported anything, because a single instantaneous reading swings
        // wildly — the charge controller adjusts constantly and any one moment is not representative.
        val currents = samples.mapNotNull { it.currentMilliamps }.filter { it > 0 }
        val voltages = samples.map { it.voltageMillivolts }.filter { it > 0 }
        val everCharging = samples.any { it.charging }

        val attempt = when {
            last.percent >= FULL_PERCENT -> ChargeAttempt.BATTERY_FULL
            !everCharging -> ChargeAttempt.PLUGGED_NOT_CHARGING
            else -> ChargeAttempt.MEASURED
        }

        val trace = ChargeTrace(
            attempt = attempt,
            plugType = samples.firstOrNull { it.plugged }?.plugType ?: last.plugType,
            batteryPercent = last.percent,
            voltageMillivolts = voltages.averageOrZero(),
            currentMilliamps = currents.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            temperatureCelsius = last.temperatureCelsius,
            dropouts = dropouts,
            sampleSeconds = watchedSeconds,
        )

        Diagnostics.info(
            TAG,
            "attempt=$attempt watts=${trace.watts} dropouts=$dropouts " +
                "samples=${samples.size} watched=${watchedSeconds}s",
        )
        _uiState.update {
            it.copy(
                stage = ChargingStage.DONE,
                secondsLeft = 0,
                result = ChargingCheck.evaluate(trace),
            )
        }
    }

    private fun List<Int>.averageOrZero(): Int = if (isEmpty()) 0 else average().toInt()

    /** Why a measurement stopped, which decides whether there is anything worth publishing. */
    private enum class StopReason {
        /** The full window elapsed. */
        FINISHED,

        /** The charger went and stayed gone, with nothing found before it did. */
        CABLE_REMOVED,

        /** The charger went, but the socket had already let go at least once. That is the finding. */
        ENOUGH_ALREADY,
    }

    private companion object {
        const val TAG = "ChargingTest"

        /** How often the clock is read and the countdown redrawn. */
        const val TICK_MILLIS = 250L

        /**
         * How long the charger must be absent before the test accepts that it is gone.
         *
         * Three seconds, and the number is a compromise between the two things being told apart. A loose
         * socket lets go for a fraction of a second and comes back; anything still absent after three
         * seconds is a cable that has been taken out. Too short and a bad socket would be read as a buyer
         * giving up, which loses the finding; too long and someone who has already unplugged sits watching a
         * countdown for a charger that is in their other hand.
         */
        const val CABLE_GONE_MILLIS = 3_000L

        /**
         * Twenty seconds.
         *
         * Long enough that a loose socket has a fair chance to let go — which is the finding worth having —
         * and short enough that a buyer holding a phone in a shop will actually wait for it.
         */
        const val SAMPLE_SECONDS = 20

        /** At or above this there is nowhere for the energy to go, so speed cannot be measured. */
        const val FULL_PERCENT = 99
    }
}
