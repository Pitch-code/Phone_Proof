package com.phoneproof.feature.charging

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.device.ChargeAttempt
import com.phoneproof.checks.device.ChargeTrace
import com.phoneproof.checks.device.ChargingCheck
import com.phoneproof.checks.device.PlugType
import com.phoneproof.core.device.ChargeSample
import com.phoneproof.core.device.ChargingProbe
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val probe: ChargingProbe,
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
     */
    private suspend fun measure() {
        _uiState.update { it.copy(stage = ChargingStage.MEASURING, dropouts = 0) }

        val samples = mutableListOf<ChargeSample>()
        var recoveries = 0
        var wasPlugged = true

        val ticker = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            while (isActive) {
                val remaining = SAMPLE_SECONDS * 1000L - (System.currentTimeMillis() - startedAt)
                _uiState.update {
                    it.copy(secondsLeft = ((remaining + 999) / 1000).coerceAtLeast(0).toInt())
                }
                delay(250)
            }
        }

        withTimeoutOrNull(SAMPLE_SECONDS * 1000L) {
            probe.stream()
                .onEach { sample ->
                    samples += sample
                    if (sample.plugged && !wasPlugged) {
                        recoveries++
                        _uiState.update { it.copy(dropouts = recoveries) }
                    }
                    wasPlugged = sample.plugged
                    _uiState.update { it.copy(live = sample) }
                }
                .collect()
        }
        ticker.cancel()

        publish(samples, recoveries)
    }

    private fun publish(samples: List<ChargeSample>, dropouts: Int) {
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
            sampleSeconds = SAMPLE_SECONDS,
        )

        Diagnostics.info(
            TAG,
            "attempt=$attempt watts=${trace.watts} dropouts=$dropouts samples=${samples.size}",
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

    private companion object {
        const val TAG = "ChargingTest"

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
