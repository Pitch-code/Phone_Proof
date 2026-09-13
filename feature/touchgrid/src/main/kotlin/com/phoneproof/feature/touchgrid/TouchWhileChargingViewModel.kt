package com.phoneproof.feature.touchgrid

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.touch.GhostTouchCheck
import com.phoneproof.checks.touch.TouchWhileChargingCheck
import com.phoneproof.core.device.ChargeSource
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChargeTouchStage {
    /** No charger connected. The app cannot make one appear, so it waits and says so. */
    NEEDS_CHARGER,

    /** A charger is connected and the watch can begin. */
    READY,

    /** Plugged in and being watched. */
    WATCHING,

    DONE,
}

@Immutable
data class ChargeTouchUiState(
    val stage: ChargeTouchStage = ChargeTouchStage.NEEDS_CHARGER,
    val plugged: Boolean = false,
    val watchedMillis: Long = 0,
    /** Touches nobody made, clustered the same way the verdict clusters them, for a live count. */
    val contactCount: Int = 0,
    val result: CheckResult? = null,
) {
    val remainingSeconds: Int
        get() = ((TouchWhileChargingCheck.FULL_WATCH_MILLIS - watchedMillis).coerceAtLeast(0) / 1000).toInt()

    val watchedLongEnough: Boolean
        get() = watchedMillis >= TouchWhileChargingCheck.MINIMUM_USEFUL_MILLIS
}

/**
 * Watches an untouched screen while a charger is connected.
 *
 * Two clocks meet here. Plug state is event-driven — the charge stream tells the ViewModel the instant a
 * cable goes in or comes out — while the watch itself is time-driven, ticked from the screen with real
 * timestamps so the ViewModel stays free of Android and a test can run a full watch in an instant.
 *
 * The rule that earns this check its keep: if the charger is pulled out mid-watch, the watch is void, not a
 * pass. Half a watch on the charger and half off measures neither, so [TouchWhileChargingCheck] is handed
 * `chargerConnectedThroughout = false` and reports UNKNOWN — the same honesty the charging test learned the
 * hard way about a cable that leaves partway through.
 */
class TouchWhileChargingViewModel(
    private val source: ChargeSource,
) : ViewModel() {

    private val startPlugged = source.snapshot()?.plugged == true

    private val _uiState = MutableStateFlow(
        ChargeTouchUiState(
            stage = if (startPlugged) ChargeTouchStage.READY else ChargeTouchStage.NEEDS_CHARGER,
            plugged = startPlugged,
        ),
    )
    val uiState: StateFlow<ChargeTouchUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null
    private var startMillis = 0L
    private val contacts = mutableListOf<GhostTouchCheck.Contact>()

    init {
        watchTheCable()
    }

    private fun watchTheCable() {
        streamJob = viewModelScope.launch {
            source.stream()
                .onEach { sample -> onPlugChanged(sample.plugged) }
                .collect()
        }
    }

    private fun onPlugChanged(plugged: Boolean) {
        _uiState.update { state ->
            when {
                // The cable left mid-watch. The watch is void — settle it as inconclusive rather than let a
                // half-charging watch masquerade as a result. Handled below, outside the update, so the
                // evaluation is not run inside a state reducer.
                state.stage == ChargeTouchStage.WATCHING && !plugged -> state.copy(plugged = false)

                // Not watching: just reflect the cable, and move between waiting and ready accordingly.
                state.stage == ChargeTouchStage.NEEDS_CHARGER && plugged ->
                    state.copy(stage = ChargeTouchStage.READY, plugged = true)

                state.stage == ChargeTouchStage.READY && !plugged ->
                    state.copy(stage = ChargeTouchStage.NEEDS_CHARGER, plugged = false)

                else -> state.copy(plugged = plugged)
            }
        }
        if (_uiState.value.stage == ChargeTouchStage.WATCHING && !plugged) {
            finish(chargerConnectedThroughout = false)
        }
    }

    /** Begins a watch at [nowMillis]. Only meaningful with a charger connected. */
    fun start(nowMillis: Long) {
        if (!_uiState.value.plugged) return
        startMillis = nowMillis
        contacts.clear()
        _uiState.update {
            it.copy(stage = ChargeTouchStage.WATCHING, watchedMillis = 0, contactCount = 0, result = null)
        }
    }

    fun onContact(nowMillis: Long, xFraction: Float, yFraction: Float) {
        if (_uiState.value.stage != ChargeTouchStage.WATCHING) return
        contacts += GhostTouchCheck.Contact(
            atMillis = nowMillis - startMillis,
            xFraction = xFraction.coerceIn(0f, 1f),
            yFraction = yFraction.coerceIn(0f, 1f),
        )
        val events = GhostTouchCheck.distinctEvents(contacts).size
        _uiState.update { it.copy(contactCount = events) }
    }

    fun tick(nowMillis: Long) {
        if (_uiState.value.stage != ChargeTouchStage.WATCHING) return
        val watched = nowMillis - startMillis
        _uiState.update { it.copy(watchedMillis = watched) }
        if (watched >= TouchWhileChargingCheck.FULL_WATCH_MILLIS) finish(chargerConnectedThroughout = true)
    }

    /** Ends the watch early while still plugged in, because the seller wants the phone back. */
    fun stopEarly() {
        if (_uiState.value.stage == ChargeTouchStage.WATCHING) finish(chargerConnectedThroughout = true)
    }

    private fun finish(chargerConnectedThroughout: Boolean) {
        val watched = _uiState.value.watchedMillis
        _uiState.update {
            it.copy(
                stage = ChargeTouchStage.DONE,
                result = TouchWhileChargingCheck.evaluate(
                    TouchWhileChargingCheck.Watch(
                        watchedMillis = watched,
                        contacts = contacts.toList(),
                        chargerConnectedThroughout = chargerConnectedThroughout,
                    ),
                ),
            )
        }
    }

    fun restart() {
        contacts.clear()
        val plugged = _uiState.value.plugged
        _uiState.update {
            ChargeTouchUiState(
                stage = if (plugged) ChargeTouchStage.READY else ChargeTouchStage.NEEDS_CHARGER,
                plugged = plugged,
            )
        }
    }

    override fun onCleared() {
        streamJob?.cancel()
        super.onCleared()
    }
}
