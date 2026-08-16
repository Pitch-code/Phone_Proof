package com.phoneproof.feature.sensortest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.sensors.SensorCheck
import com.phoneproof.checks.sensors.SensorGesture
import com.phoneproof.checks.sensors.SensorKind
import com.phoneproof.checks.sensors.SensorLiveness
import com.phoneproof.checks.sensors.SensorReading
import com.phoneproof.checks.sensors.SensorTrace
import com.phoneproof.checks.sensors.TraceStats
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.sensors.SensorEventUpdate
import com.phoneproof.core.sensors.SensorProbe
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class SensorPhase {
    READY,

    /** Tilt and turn: accelerometer, gyroscope and compass. */
    MOTION,

    /** A palm over the top of the screen: proximity and light. */
    COVER,

    DONE,
}

data class SensorTestUiState(
    val phase: SensorPhase = SensorPhase.READY,
    val available: Set<SensorKind> = emptySet(),
    val secondsLeft: Int = 0,
    /** 0f to 1f. Filling these is what turns "could not tell" into an answer. */
    val tiltProgress: Float = 0f,
    val turnProgress: Float = 0f,
    val proximityFelt: Boolean = false,
    val lightWentDark: Boolean = false,
    val gestureComplete: Boolean = false,
    val results: List<CheckResult> = emptyList(),
) {
    val hasGyroscope: Boolean get() = SensorKind.GYROSCOPE in available
    val hasProximity: Boolean get() = SensorKind.PROXIMITY in available
    val hasLight: Boolean get() = SensorKind.LIGHT in available
}

/**
 * Runs the two gestures, then judges the whole lot at once.
 *
 * Judging at the end rather than per phase is not a detail: the analysis works by letting sensors vouch
 * for each other, and the accelerometer that proves the phone was turned is recorded in the first phase
 * while the proximity sensor it helps exonerate is recorded in the second. Analysing each phase alone
 * would throw away exactly the cross-references the verdicts are built on.
 */
class SensorTestViewModel(private val probe: SensorProbe) : ViewModel() {

    private val _uiState = MutableStateFlow(SensorTestUiState(available = probe.available()))
    val uiState: StateFlow<SensorTestUiState> = _uiState.asStateFlow()

    /** Kept across phases, because the verdicts are cross-referenced. */
    private val traces = mutableListOf<SensorTrace>()

    private var job: Job? = null

    fun start() {
        job?.cancel()
        traces.clear()
        _uiState.update {
            SensorTestUiState(available = it.available, phase = SensorPhase.MOTION)
        }
        job = viewModelScope.launch {
            runMotionPhase()
            runCoverPhase()
            finish()
        }
    }

    fun restart() = start()

    override fun onCleared() {
        // The flow unsubscribes in awaitClose, but cancelling explicitly means a 50 Hz subscription
        // cannot outlive the screen even for the moment it takes the collector to wind down. This is a
        // stranger's phone and their battery.
        job?.cancel()
        super.onCleared()
    }

    private suspend fun runMotionPhase() {
        _uiState.update { it.copy(phase = SensorPhase.MOTION, gestureComplete = false) }
        val kinds = setOf(
            SensorKind.ACCELEROMETER,
            SensorKind.GYROSCOPE,
            SensorKind.MAGNETOMETER,
        ).intersect(_uiState.value.available)

        collect(kinds, MOTION_MILLIS) { readings ->
            val tilt = SensorGesture.tiltProgress(stats(readings, SensorKind.ACCELEROMETER))
            val turn = SensorGesture.turnProgress(stats(readings, SensorKind.GYROSCOPE))
            // A phone with no gyroscope must not be held at the tilt screen waiting for a turn meter
            // that can never fill. Absence is the inventory check's business, not this one's.
            val done = tilt >= 1f && (turn >= 1f || SensorKind.GYROSCOPE !in kinds)
            _uiState.update {
                it.copy(tiltProgress = tilt, turnProgress = turn, gestureComplete = done)
            }
            done
        }
    }

    private suspend fun runCoverPhase() {
        _uiState.update { it.copy(phase = SensorPhase.COVER, gestureComplete = false) }
        val kinds = setOf(SensorKind.PROXIMITY, SensorKind.LIGHT)
            .intersect(_uiState.value.available)

        if (kinds.isEmpty()) return

        collect(kinds, COVER_MILLIS) { readings ->
            val proximity = SensorGesture.proximityResponded(stats(readings, SensorKind.PROXIMITY))
            val dark = SensorGesture.lightWentDark(stats(readings, SensorKind.LIGHT))
            val done = (proximity || SensorKind.PROXIMITY !in kinds) &&
                (dark || SensorKind.LIGHT !in kinds)
            _uiState.update {
                it.copy(proximityFelt = proximity, lightWentDark = dark, gestureComplete = done)
            }
            done
        }
    }

    /**
     * Subscribes, accumulates, and stops when the gesture is done or the clock runs out.
     *
     * Ending early once [onSample] reports the gesture complete is what makes this feel like a test
     * rather than a wait. The floor of [MINIMUM_MILLIS] is there because the analysis needs a run of
     * samples to work with — a single lucky reading that crosses the threshold is not evidence, and
     * stopping on it would also rob the compass of the turning it needs.
     *
     * When a sensor is dead the gesture never completes and the phase runs its full length, which is
     * the right way round: the failing case is exactly the one where all the evidence is wanted.
     */
    private suspend fun collect(
        kinds: Set<SensorKind>,
        durationMillis: Long,
        onSample: (Map<SensorKind, List<SensorReading>>) -> Boolean,
    ) {
        val collected: MutableMap<SensorKind, MutableList<SensorReading>> =
            kinds.associateWith { mutableListOf<SensorReading>() }.toMutableMap()
        var subscribed: Set<SensorKind> = emptySet()
        val startedAt = System.currentTimeMillis()

        val ticker = viewModelScope.launch {
            // Its own coroutine so the countdown keeps moving even when not one sample arrives, which
            // is precisely the situation on the phone this test exists to catch.
            while (isActive) {
                val remaining = durationMillis - (System.currentTimeMillis() - startedAt)
                _uiState.update {
                    it.copy(secondsLeft = ((remaining + 999) / 1000).coerceAtLeast(0).toInt())
                }
                delay(TICK_MILLIS)
            }
        }

        var complete = false

        // onEach then takeWhile, rather than a `return` out of collect. A return from inside a collect
        // lambda leaves the lambda and nothing else — the flow keeps running and the sensors stay
        // subscribed at 50 Hz. takeWhile actually completes the flow, which unsubscribes.
        withTimeoutOrNull(durationMillis) {
            probe.stream(kinds)
                .onEach { update ->
                    when (update) {
                        is SensorEventUpdate.Subscribed -> subscribed = update.kinds
                        is SensorEventUpdate.Sample -> {
                            collected[update.kind]?.add(update.reading)
                            val elapsed = System.currentTimeMillis() - startedAt
                            if (onSample(collected) && elapsed >= MINIMUM_MILLIS) complete = true
                        }
                    }
                }
                .takeWhile { !complete }
                .collect()
        }

        ticker.cancel()
        // Paused after unsubscribing, not before: the buyer gets to see the meter full, and the sensors
        // are already switched off while they do.
        if (complete) delay(COMPLETION_PAUSE_MILLIS)
        traces += kinds.map {
            SensorTrace(
                kind = it,
                readings = collected[it].orEmpty().toList(),
                registered = it in subscribed,
            )
        }
        Diagnostics.info(
            TAG,
            "phase done: " + kinds.joinToString { "$it=${collected[it].orEmpty().size}" },
        )
    }

    private fun finish() {
        val findings = SensorLiveness.analyse(traces, _uiState.value.available)
        Diagnostics.info(TAG, "findings: " + findings.joinToString { "${it.kind}=${it.state}" })
        _uiState.update {
            it.copy(
                phase = SensorPhase.DONE,
                secondsLeft = 0,
                results = SensorCheck.results(findings),
            )
        }
    }

    private fun stats(
        readings: Map<SensorKind, List<SensorReading>>,
        kind: SensorKind,
    ): TraceStats = TraceStats.of(readings[kind].orEmpty())

    private companion object {
        const val TAG = "SensorTest"

        /** Long enough to tilt a phone over and turn it back without being rushed. */
        const val MOTION_MILLIS = 8_000L
        /**
         * 25 seconds, up from six.
         *
         * Reported from a real phone: six seconds is not long enough to read the instruction, work out
         * where the sensor is on an unfamiliar handset, and get a palm flat over it. And it costs nothing
         * to be generous, because the phase still ends the moment both indicators light — the full 25
         * seconds only elapses when something is actually wrong, which is exactly when all the evidence
         * is wanted.
         */
        const val COVER_MILLIS = 25_000L

        /** No phase ends before this, however quickly a threshold is crossed. */
        const val MINIMUM_MILLIS = 1_500L
        const val COMPLETION_PAUSE_MILLIS = 600L
        const val TICK_MILLIS = 100L
    }
}
