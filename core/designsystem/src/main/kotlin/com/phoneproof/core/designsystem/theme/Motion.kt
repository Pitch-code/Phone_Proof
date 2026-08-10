package com.phoneproof.core.designsystem.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Motion specifications.
 *
 * Motion in this app only ever confirms an action or reveals a result. There is no decorative
 * animation, and — importantly — **no looping or infinite animation anywhere**.
 *
 * That is a correctness constraint rather than a matter of taste. The app measures battery
 * discharge under a load it controls; a continuously animating surface is an uncontrolled load
 * and would corrupt the app's own measurement. An infinite animation is a bug in this codebase.
 */
object PhoneProofMotion {

    /** Default for screen and content transitions. Settles quickly with a barely-there ease. */
    fun <T> standard(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = 380f,
    )

    /**
     * Coverage-grid cell fill. Deliberately a short linear tween rather than a spring: the fill
     * must read as mechanical and immediate, like a sensor latching, not as something playful.
     */
    fun <T> cellFill(): FiniteAnimationSpec<T> = tween(
        durationMillis = 90,
        easing = LinearEasing,
    )

    /** Per-row delay when a list of results reveals itself. */
    const val RESULT_ROW_STAGGER_MS: Int = 40

    fun <T> resultRow(index: Int): FiniteAnimationSpec<T> = tween(
        durationMillis = 220,
        delayMillis = index * RESULT_ROW_STAGGER_MS,
    )

    /**
     * The single expressive moment in the app: the verdict landing. Lower damping gives a slight
     * overshoot. Used once per inspection, which is exactly why it registers.
     */
    fun <T> verdict(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.60f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** One-shot emphasis pulse on uncovered cells at the end of the test. Never repeated. */
    fun <T> singlePulse(): AnimationSpec<T> = tween(durationMillis = 320)
}
