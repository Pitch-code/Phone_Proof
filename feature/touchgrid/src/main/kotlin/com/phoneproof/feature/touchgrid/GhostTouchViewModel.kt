package com.phoneproof.feature.touchgrid

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import com.phoneproof.checks.touch.GhostTouchCheck
import com.phoneproof.core.model.CheckResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GhostTouchStage {
    /** Explaining, before the watch has started. */
    READY,

    /** The screen is being watched and must be left alone. */
    WATCHING,

    /**
     * The watch found touches, so the buyer is being asked the one question that changes the verdict:
     * were their hands actually off the phone? See [GhostTouchCheck.Watch.confirmedHandsOff].
     */
    ASKING,

    DONE,
}

@Immutable
data class GhostTouchUiState(
    val stage: GhostTouchStage = GhostTouchStage.READY,
    /** How long the screen has been watched so far, so the countdown can be shown. */
    val watchedMillis: Long = 0,
    /** Touches nobody made, clustered the same way the verdict clusters them, for a live count. */
    val contactCount: Int = 0,
    val result: CheckResult? = null,
) {
    /** Seconds still to watch, floored at zero, for the countdown. */
    val remainingSeconds: Int
        get() = ((GhostTouchCheck.FULL_WATCH_MILLIS - watchedMillis).coerceAtLeast(0) / 1000).toInt()

    /** True once the watch has run long enough that a quiet result would mean something. */
    val watchedLongEnough: Boolean
        get() = watchedMillis >= GhostTouchCheck.MINIMUM_USEFUL_MILLIS
}

/**
 * Watches an untouched screen and reports what arrived.
 *
 * The whole design is a watch, not a test, because nothing can provoke a ghost touch on demand — so the
 * work here is only bookkeeping: start a clock, collect the stray contacts the screen reports while nobody
 * is meant to be touching it, and hand the lot to [GhostTouchCheck] to judge. Every judgement lives in that
 * pure check; this class holds no opinion about what a finding means.
 *
 * Time is passed in rather than read from a clock, so the ViewModel needs no `Context` and no
 * `System.currentTimeMillis`, and a test can drive a whole 30-second watch in an instant. The screen owns
 * the real clock.
 */
class GhostTouchViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GhostTouchUiState())
    val uiState: StateFlow<GhostTouchUiState> = _uiState.asStateFlow()

    private var startMillis = 0L
    private val contacts = mutableListOf<GhostTouchCheck.Contact>()

    /** Begins a fresh watch at [nowMillis], discarding anything from a previous run. */
    fun start(nowMillis: Long) {
        startMillis = nowMillis
        contacts.clear()
        _uiState.value = GhostTouchUiState(stage = GhostTouchStage.WATCHING)
    }

    /**
     * Records a touch that arrived while the screen was supposed to be untouched.
     *
     * Fractions rather than pixels, so the geometry means the same on any panel and a cluster can be
     * described as "the top left" rather than in coordinates a buyer cannot check. Ignored unless a watch
     * is actually running, so a stray event as the screen changes cannot pollute a result.
     */
    fun onContact(nowMillis: Long, xFraction: Float, yFraction: Float) {
        if (_uiState.value.stage != GhostTouchStage.WATCHING) return
        contacts += GhostTouchCheck.Contact(
            atMillis = nowMillis - startMillis,
            xFraction = xFraction.coerceIn(0f, 1f),
            yFraction = yFraction.coerceIn(0f, 1f),
        )
        // The live count uses the same clustering as the verdict, so the number the buyer watches climb is
        // the number the verdict will state — a flurry from one bad spot counts as one, in both places.
        val events = GhostTouchCheck.distinctEvents(contacts).size
        _uiState.update { it.copy(contactCount = events) }
    }

    /** Advances the clock. Ends the watch on its own once the full duration has elapsed. */
    fun tick(nowMillis: Long) {
        if (_uiState.value.stage != GhostTouchStage.WATCHING) return
        val watched = nowMillis - startMillis
        _uiState.update { it.copy(watchedMillis = watched) }
        if (watched >= GhostTouchCheck.FULL_WATCH_MILLIS) finish(nowMillis)
    }

    /** Ends the watch early, because the seller wants the phone back. */
    fun stopEarly(nowMillis: Long) {
        if (_uiState.value.stage == GhostTouchStage.WATCHING) finish(nowMillis)
    }

    private fun finish(nowMillis: Long) {
        val watched = nowMillis - startMillis
        val events = GhostTouchCheck.distinctEvents(contacts)

        // The hands-off question only changes the verdict when there is a finding to explain away. With
        // nothing found — or too short a watch to trust — the answer cannot matter, so skip straight to the
        // result rather than asking a question with no consequence.
        if (events.isEmpty() || watched < GhostTouchCheck.MINIMUM_USEFUL_MILLIS) {
            _uiState.update {
                it.copy(
                    stage = GhostTouchStage.DONE,
                    watchedMillis = watched,
                    result = GhostTouchCheck.evaluate(
                        GhostTouchCheck.Watch(watchedMillis = watched, contacts = contacts.toList()),
                    ),
                )
            }
        } else {
            _uiState.update { it.copy(stage = GhostTouchStage.ASKING, watchedMillis = watched) }
        }
    }

    /**
     * Settles a watch that found something, once the buyer has said whether they were touching it.
     *
     * `confirmed == true` (hands were off) yields a FAIL; `false` yields a CAUTION, because a resting thumb
     * produces exactly this evidence. The check enforces that difference — it forbids a low-confidence FAIL
     * outright — so this only has to pass the answer through.
     */
    fun answerHandsOff(confirmed: Boolean) {
        val watched = _uiState.value.watchedMillis
        _uiState.update {
            it.copy(
                stage = GhostTouchStage.DONE,
                result = GhostTouchCheck.evaluate(
                    GhostTouchCheck.Watch(
                        watchedMillis = watched,
                        contacts = contacts.toList(),
                        confirmedHandsOff = confirmed,
                    ),
                ),
            )
        }
    }

    fun restart() {
        contacts.clear()
        _uiState.value = GhostTouchUiState()
    }
}
