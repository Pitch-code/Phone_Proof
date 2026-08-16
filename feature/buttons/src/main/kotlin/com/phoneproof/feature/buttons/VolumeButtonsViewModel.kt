package com.phoneproof.feature.buttons

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phoneproof.checks.buttons.ButtonObservation
import com.phoneproof.checks.buttons.PressedBoth
import com.phoneproof.checks.buttons.VolumeButtonCheck
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Which of the two side keys an event came from. */
enum class VolumeKey { UP, DOWN }

enum class VolumeStage {
    /** Listening for key presses. */
    WAITING,

    /** One button worked and the other did not, so the buyer is being asked whether they pressed it. */
    ASKING,

    DONE,
}

@Immutable
data class VolumeButtonsUiState(
    val stage: VolumeStage = VolumeStage.WAITING,
    val up: ButtonObservation = ButtonObservation(),
    val down: ButtonObservation = ButtonObservation(),
    val result: CheckResult? = null,
) {
    val bothHeard: Boolean get() = up.everPressed && down.everPressed
    val anythingHeard: Boolean get() = up.everPressed || down.everPressed
    val anyKeyHeldNow: Boolean get() = up.stillDown || down.stillDown

    /** The button still waiting to be heard from, or null once both have been. */
    val missing: VolumeKey?
        get() = when {
            !up.everPressed -> VolumeKey.UP
            !down.everPressed -> VolumeKey.DOWN
            else -> null
        }
}

/**
 * Watches the volume keys and decides when there is enough to judge.
 *
 * The one piece of real machinery here is the hold timer. A key that is *pressed* produces a down and an up
 * a moment later; a key that is physically **jammed** produces a down and nothing else, ever. Waiting for an
 * up that will never arrive would leave the screen watching an empty room, so a ticker measures how long
 * anything has been held and the check treats a long-enough hold as a jam.
 */
class VolumeButtonsViewModel(
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VolumeButtonsUiState())
    val uiState: StateFlow<VolumeButtonsUiState> = _uiState.asStateFlow()

    private var downAt = mutableMapOf<VolumeKey, Long>()
    private var ticker: Job? = null

    fun onKeyDown(key: VolumeKey) {
        if (_uiState.value.stage != VolumeStage.WAITING) return
        // Android repeats a held key. Only the first down starts the clock, or a jam would look like a
        // rapid series of presses instead of one long hold.
        if (downAt.containsKey(key)) return

        downAt[key] = now()
        update(key) { it.copy(presses = it.presses + 1) }
        startTicker()
    }

    fun onKeyUp(key: VolumeKey) {
        if (_uiState.value.stage != VolumeStage.WAITING) return
        val pressedAt = downAt.remove(key) ?: return
        val held = now() - pressedAt

        update(key) {
            it.copy(
                releases = it.releases + 1,
                longestHoldMillis = maxOf(it.longestHoldMillis, held),
            )
        }
        if (downAt.isEmpty()) stopTicker()
        settleIfPossible()
    }

    /**
     * Ends the test and judges what was seen.
     *
     * Goes to the question only when one button was heard and the other was not — the single case where the
     * buyer knows something the app cannot. Asking in any other situation would be asking them to
     * second-guess a finding.
     */
    fun finish() {
        stopTicker()
        val state = _uiState.value
        val oneSided = state.anythingHeard && !state.bothHeard

        _uiState.update {
            it.copy(
                stage = if (oneSided) VolumeStage.ASKING else VolumeStage.DONE,
                result = if (oneSided) {
                    null
                } else {
                    VolumeButtonCheck.evaluate(it.up, it.down)
                },
            )
        }
    }

    fun answerPressedBoth(pressedBoth: Boolean) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                stage = VolumeStage.DONE,
                result = VolumeButtonCheck.evaluate(
                    up = state.up,
                    down = state.down,
                    pressedBoth = if (pressedBoth) PressedBoth.YES else PressedBoth.NO,
                ),
            )
        }
    }

    fun restart() {
        stopTicker()
        downAt = mutableMapOf()
        _uiState.value = VolumeButtonsUiState()
    }

    override fun onCleared() {
        stopTicker()
        super.onCleared()
    }

    /**
     * Finishes on its own once there is nothing left to learn.
     *
     * Both keys heard and released is a complete pass, and making the buyer tap a button to be told so is
     * pointless ceremony. A jam is equally conclusive, and the ticker calls this once the hold is long
     * enough — otherwise the screen would sit there waiting for a release that is never coming.
     */
    private fun settleIfPossible() {
        val state = _uiState.value
        if (state.stage != VolumeStage.WAITING) return

        val jammed = state.up.stillDown && state.up.longestHoldMillis >= STUCK ||
            state.down.stillDown && state.down.longestHoldMillis >= STUCK
        val complete = state.bothHeard && !state.anyKeyHeldNow

        if (jammed || complete) {
            stopTicker()
            _uiState.update {
                it.copy(stage = VolumeStage.DONE, result = VolumeButtonCheck.evaluate(it.up, it.down))
            }
        }
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive && downAt.isNotEmpty()) {
                val moment = now()
                downAt.forEach { (key, pressedAt) ->
                    val held = moment - pressedAt
                    update(key) { it.copy(longestHoldMillis = maxOf(it.longestHoldMillis, held)) }
                }
                settleIfPossible()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private inline fun update(key: VolumeKey, change: (ButtonObservation) -> ButtonObservation) {
        _uiState.update { state ->
            when (key) {
                VolumeKey.UP -> state.copy(up = change(state.up))
                VolumeKey.DOWN -> state.copy(down = change(state.down))
            }
        }
    }

    private companion object {
        val STUCK = VolumeButtonCheck.STUCK_HOLD_MILLIS
        const val TICK_MILLIS = 200L
    }
}
