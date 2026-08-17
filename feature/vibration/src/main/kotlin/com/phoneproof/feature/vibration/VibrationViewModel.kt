package com.phoneproof.feature.vibration

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.sensors.SensorKind
import com.phoneproof.checks.sensors.SensorReading
import com.phoneproof.checks.vibration.VibrationAttempt
import com.phoneproof.checks.vibration.VibrationCheck
import com.phoneproof.checks.vibration.VibrationTrace
import com.phoneproof.checks.vibration.jerkPercentile
import com.phoneproof.core.device.BuzzResult
import com.phoneproof.core.device.VibrationDriver
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import com.phoneproof.core.sensors.SensorEventUpdate
import com.phoneproof.core.sensors.SensorProbe
import com.phoneproof.core.sensors.SensorRate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class VibrationStage {
    READY,

    /** Watching a phone that is supposed to be sitting still. */
    RESTING,

    /** The motor is running and the accelerometer is watching. */
    BUZZING,

    DONE,
}

@Immutable
data class VibrationUiState(
    val stage: VibrationStage = VibrationStage.READY,
    val hasMotor: Boolean = true,
    val hasAccelerometer: Boolean = true,
    /** Live movement, so the buyer can see for themselves whether they are holding still enough. */
    val liveJerk: Double = 0.0,
    val restingJerk: Double = 0.0,
    val activeJerk: Double = 0.0,
    val result: CheckResult? = null,
) {
    /** Whether the phone is currently still enough for the baseline to be worth anything. */
    val stillEnough: Boolean get() = liveJerk <= VibrationCheck.TOO_RESTLESS

    val canStart: Boolean get() = hasMotor && hasAccelerometer
}

/**
 * Takes a quiet baseline, runs the motor, and compares.
 *
 * The baseline is the part that makes this a measurement rather than a guess. Without it there is no way to
 * tell a buzzing phone from one being carried down a flight of stairs — and with it, the same number that
 * proves the motor ran also proves the phone was still beforehand, which is what stops the app reporting a
 * bus journey as a working vibration motor.
 */
class VibrationViewModel(
    private val probe: SensorProbe,
    private val driver: VibrationDriver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VibrationUiState(
            hasMotor = driver.hasMotor(),
            hasAccelerometer = SensorKind.ACCELEROMETER in probe.available(),
        ),
    )
    val uiState: StateFlow<VibrationUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    fun start() {
        job?.cancel()
        val state = _uiState.value

        // Neither of these needs the motor run to be answered, so they are answered before anything buzzes.
        if (!state.hasMotor) {
            publish(VibrationTrace(VibrationAttempt.NO_MOTOR, hasAmplitudeControl = false))
            return
        }
        if (!state.hasAccelerometer) {
            publish(
                VibrationTrace(
                    attempt = VibrationAttempt.NO_ACCELEROMETER,
                    hasAmplitudeControl = driver.hasAmplitudeControl(),
                ),
            )
            return
        }

        _uiState.update {
            it.copy(
                stage = VibrationStage.RESTING,
                liveJerk = 0.0,
                restingJerk = 0.0,
                activeJerk = 0.0,
                result = null,
            )
        }

        job = viewModelScope.launch {
            val resting = jerkPercentile(record(RESTING_MILLIS))

            _uiState.update { it.copy(stage = VibrationStage.BUZZING, restingJerk = resting) }

            // The two failures are kept apart deliberately: one is this app's bug and one may be the
            // phone's. Flattening them is what once had a working handset told to check Do Not Disturb.
            val started = driver.buzz(BUZZ_MILLIS)
            if (started != BuzzResult.ACCEPTED) {
                publish(
                    VibrationTrace(
                        attempt = when (started) {
                            BuzzResult.NOT_PERMITTED -> VibrationAttempt.NOT_PERMITTED
                            else -> VibrationAttempt.REFUSED
                        },
                        restingJerk = resting,
                        hasAmplitudeControl = driver.hasAmplitudeControl(),
                    ),
                )
                return@launch
            }

            // The window is the buzz and nothing after it. It used to run 300 ms longer so the whole
            // buzz was certainly inside it, which meant nearly a third of the samples were of a still
            // phone — and being a mean, that pulled the figure down by about as much. A percentile does
            // not need the padding: a few quiet samples at either edge no longer decide anything.
            val active = jerkPercentile(record(BUZZ_MILLIS))

            Diagnostics.info(TAG, "resting=$resting active=$active")
            publish(
                VibrationTrace(
                    attempt = VibrationAttempt.MEASURED,
                    restingJerk = resting,
                    activeJerk = active,
                    requestedMillis = BUZZ_MILLIS,
                    hasAmplitudeControl = driver.hasAmplitudeControl(),
                ),
            )
        }
    }

    fun restart() = start()

    override fun onCleared() {
        job?.cancel()
        // Backing out mid-buzz must not leave a stranger's phone humming in their hand.
        driver.cancel()
        super.onCleared()
    }

    /** Collects accelerometer readings for [millis], updating the live figure as they arrive. */
    private suspend fun record(millis: Long): List<SensorReading> {
        val readings = mutableListOf<SensorReading>()

        withTimeoutOrNull(millis) {
            probe.stream(setOf(SensorKind.ACCELEROMETER))
                .onEach { update ->
                    if (update is SensorEventUpdate.Sample) {
                        readings += update.reading
                        // A short trailing window rather than everything so far, so the live number reacts
                        // to what the buyer is doing now instead of being anchored by the first second.
                        _uiState.update {
                            it.copy(liveJerk = jerkPercentile(readings.takeLast(LIVE_WINDOW)))
                        }
                    }
                }
                .collect()
        }
        return readings
    }

    private fun publish(trace: VibrationTrace) {
        _uiState.update {
            it.copy(
                stage = VibrationStage.DONE,
                restingJerk = trace.restingJerk,
                activeJerk = trace.activeJerk,
                result = VibrationCheck.evaluate(trace),
            )
        }
    }

    private companion object {
        const val TAG = "VibrationTest"

        /** Long enough for a hand to settle, short enough that nobody wonders what is happening. */
        const val RESTING_MILLIS = 1_500L

        /** A buzz a person plainly feels, and well over a hundred accelerometer samples of it. */
        const val BUZZ_MILLIS = 700L

        /** Samples behind the live figure: about a fifth of a second at 50 Hz. */
        /**
         * Samples behind the live figure.
         *
         * Raised with the sampling rate: at four times the samples per second, ten of them covered a
         * quarter of the time they used to and the live number became twitchy.
         */
        const val LIVE_WINDOW = 40
    }
}
