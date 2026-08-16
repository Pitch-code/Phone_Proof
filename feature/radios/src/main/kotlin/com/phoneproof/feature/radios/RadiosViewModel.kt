package com.phoneproof.feature.radios

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.radios.RadioCheck
import com.phoneproof.checks.radios.RadioKind
import com.phoneproof.checks.radios.RadioObservation
import com.phoneproof.core.model.CheckOutcome
import com.phoneproof.core.device.RadioProbe
import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RadiosStage {
    /** Live. Both radios are being re-read about once a second while the buyer works on them. */
    WATCHING,

    DONE,
}

/**
 * One radio's row on the screen.
 *
 * @param visitedSettings whether the buyer has actually been sent to the system settings for this radio. The
 *   follow-up question is never asked before this is true, so nobody is quizzed out of nowhere.
 * @param enableClaim null until answered: whether the buyer says they flipped the switch. This is the only
 *   route to a negative verdict, so it is kept separate from anything the app measured itself.
 */
@Immutable
data class RadioPanel(
    val kind: RadioKind,
    val observation: RadioObservation,
    val visitedSettings: Boolean = false,
    val enableClaim: Boolean? = null,
    val canOpenSettings: Boolean = true,
) {
    /**
     * Whether to ask "did you switch it on?".
     *
     * Only after a trip to settings that left the radio off. If the buyer turned it on successfully the app
     * can see that for itself and says nothing — the question exists purely to separate a radio that refused
     * from a switch nobody touched.
     */
    val asking: Boolean
        get() = visitedSettings &&
            observation.present &&
            observation.stateReadable &&
            !observation.enabled &&
            enableClaim == null

    val result: CheckResult
        get() = RadioCheck.evaluate(observation.copy(enableAttempted = enableClaim == true))
}

@Immutable
data class RadiosUiState(
    val stage: RadiosStage = RadiosStage.WATCHING,
    val wifi: RadioPanel,
    val bluetooth: RadioPanel,
) {
    val panels: List<RadioPanel> get() = listOf(wifi, bluetooth)

    /** Nothing is blocked on these, but the button says something different once both are proved. */
    val allProved: Boolean get() = panels.all { it.result.outcome == CheckOutcome.PASS }

    val results: List<CheckResult> get() = panels.map { it.result }
}

/**
 * Watches both radios while the buyer switches them on.
 *
 * ## Why this polls
 *
 * See `RadioProbe`: the event-driven version needs two protected system broadcasts and an exported-receiver
 * flag that does not exist below API 33. Re-reading the state once a second is a few cheap synchronous
 * calls and removes that whole class of bug. The practical benefit is that returning from the settings panel
 * updates the screen on its own — the buyer flips Wi-Fi on, comes back, and the row has already gone green.
 *
 * ## Why finishing is a button
 *
 * The app cannot join a network for the buyer and cannot toggle a radio for them, so there is no moment it can
 * declare the test over. Both radios can also be legitimately untestable — a shop with no Wi-Fi to join — and
 * a screen that waited for a green tick would trap the buyer. So the results are always savable and the honest
 * "cannot tell" is one tap away.
 */
class RadiosViewModel(
    private val probe: RadioProbe,
) : ViewModel() {

    private val _uiState: MutableStateFlow<RadiosUiState>
    val uiState: StateFlow<RadiosUiState> get() = _uiState.asStateFlow()

    private var poller: Job? = null

    init {
        val snapshot = probe.snapshot()
        _uiState = MutableStateFlow(
            RadiosUiState(
                wifi = RadioPanel(
                    kind = RadioKind.WIFI,
                    observation = snapshot.wifi,
                    canOpenSettings = probe.canOpenSettings(RadioKind.WIFI),
                ),
                bluetooth = RadioPanel(
                    kind = RadioKind.BLUETOOTH,
                    observation = snapshot.bluetooth,
                    canOpenSettings = probe.canOpenSettings(RadioKind.BLUETOOTH),
                ),
            ),
        )
        watch()
    }

    private fun watch() {
        poller = viewModelScope.launch {
            while (isActive) {
                delay(POLL_MILLIS)
                if (_uiState.value.stage != RadiosStage.WATCHING) continue
                val snapshot = runCatching { probe.snapshot() }
                    .onFailure { Diagnostics.warn(TAG, "could not re-read the radios", it) }
                    .getOrNull() ?: continue
                _uiState.update {
                    it.copy(
                        wifi = it.wifi.observed(snapshot.wifi),
                        bluetooth = it.bluetooth.observed(snapshot.bluetooth),
                    )
                }
            }
        }
    }

    /**
     * Folds a fresh reading into a panel.
     *
     * A radio that has come on since the question was asked clears the claim, because the question no longer
     * applies — leaving a stale "yes I turned it on" behind would let a working radio carry a CAUTION.
     */
    private fun RadioPanel.observed(fresh: RadioObservation): RadioPanel = copy(
        observation = fresh,
        enableClaim = if (fresh.enabled) null else enableClaim,
    )

    /** Called as the buyer is sent to the system settings for [kind]. */
    fun markSettingsVisited(kind: RadioKind) {
        Diagnostics.info(TAG, "sent the buyer to settings for $kind")
        _uiState.update { state ->
            state.mapPanel(kind) { it.copy(visitedSettings = true) }
        }
    }

    /** The buyer's answer to "did you switch it on?". */
    fun answerEnableClaim(kind: RadioKind, turnedItOn: Boolean) {
        _uiState.update { state ->
            state.mapPanel(kind) { it.copy(enableClaim = turnedItOn) }
        }
    }

    fun finish() {
        val state = _uiState.value
        Diagnostics.info(
            TAG,
            "saving radios: " + state.results.joinToString(", ") { "${it.id}=${it.outcome}" },
        )
        _uiState.value = state.copy(stage = RadiosStage.DONE)
    }

    fun restart() {
        poller?.cancel()
        val snapshot = probe.snapshot()
        _uiState.update {
            RadiosUiState(
                wifi = it.wifi.copy(observation = snapshot.wifi, visitedSettings = false, enableClaim = null),
                bluetooth = it.bluetooth.copy(
                    observation = snapshot.bluetooth,
                    visitedSettings = false,
                    enableClaim = null,
                ),
            )
        }
        watch()
    }

    override fun onCleared() {
        poller?.cancel()
        super.onCleared()
    }

    private fun RadiosUiState.mapPanel(
        kind: RadioKind,
        transform: (RadioPanel) -> RadioPanel,
    ): RadiosUiState = when (kind) {
        RadioKind.WIFI -> copy(wifi = transform(wifi))
        RadioKind.BLUETOOTH -> copy(bluetooth = transform(bluetooth))
    }

    private companion object {
        const val TAG = "RadiosTest"

        /** Fast enough that returning from the settings panel feels instant, slow enough to cost nothing. */
        const val POLL_MILLIS = 1_000L
    }
}
