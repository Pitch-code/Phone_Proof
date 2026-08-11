package com.phoneproof.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween

/**
 * Motion specifications.
 *
 * Motion in this app only ever confirms an action or reveals a result. There is no decorative
 * animation, and — importantly — **no looping or infinite animation anywhere**.
 *
 * That is a correctness constraint rather than a matter of taste. The app measures battery
 * discharge under a load it controls; a continuously animating surface is an uncontrolled load and
 * would corrupt the app's own measurement. An infinite animation is a bug in this codebase.
 *
 * Only the specs that are actually called live here. The full set — standard transitions, staggered
 * result rows, the verdict overshoot — is written down in `.kiro/steering/design-system.md` and gets
 * added to this file alongside the screen that needs it. Carrying unused animation specs would be
 * speculative code, and it hides which motion the app really uses.
 */
object PhoneProofMotion {

    /**
     * One-shot emphasis on uncovered cells once a test finishes. Never repeated: it settles and
     * stops, so nothing is animating while a measurement could be running.
     */
    fun <T> singlePulse(): AnimationSpec<T> = tween(durationMillis = 320)
}
