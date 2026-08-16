package com.phoneproof.checks.sensors

/**
 * The bar each gesture has to clear, shared between the screen and the verdict.
 *
 * These live here, in one place, for a reason worth spelling out. The screen shows the buyer a live
 * meter filling as they tilt the phone, and the analysis afterwards decides whether the tilt was big
 * enough to conclude anything. **If those two used separate numbers, the app would be able to show a
 * full meter and then report "could not tell" for the same gesture** — which would look like a bug, and
 * would be one.
 *
 * So the screen asks these functions what to draw, the analysis asks them what to conclude, and a test
 * asserts that a full meter always implies a conclusive verdict.
 */
object SensorGesture {

    /**
     * Tilting a phone from flat to upright moves a whole 9.8 m/s² from one axis to another, so 4.0 is
     * less than half of the gesture the screen asks for.
     */
    const val TILT_TARGET: Double = 4.0

    /**
     * 0.35 rad/s is about 20°/s. A deliberate wrist turn is several times faster, while sensor noise on
     * a phone lying still never approaches it.
     */
    const val TURN_TARGET: Double = 0.35

    /** A palm has to make the light sensor dark, not merely make it change. See [lightWentDark]. */
    private const val COVERED_LUX = 8.0
    private const val COVERED_DROP = 15.0

    private const val LIGHT_ABSOLUTE_DROP = 20.0
    private const val LIGHT_DROP_RATIO = 2.0

    /** 0f to 1f, for the meter on screen. Reaching 1f is what makes the motion verdict conclusive. */
    fun tiltProgress(stats: TraceStats): Float =
        (stats.largestAxisSpan / TILT_TARGET).coerceIn(0.0, 1.0).toFloat()

    fun turnProgress(stats: TraceStats): Float =
        (stats.magnitudeMax / TURN_TARGET).coerceIn(0.0, 1.0).toFloat()

    fun tilted(stats: TraceStats): Boolean = stats.largestAxisSpan >= TILT_TARGET

    fun turned(stats: TraceStats): Boolean = stats.magnitudeMax >= TURN_TARGET

    /** Proximity reporting anything other than one constant distance. */
    fun proximityResponded(stats: TraceStats): Boolean =
        stats.count > 0 && stats.largestAxisSpan > 0.0

    /**
     * The light sensor noticed *something*, which is all being alive requires.
     *
     * Two rules rather than one because a shop can be either. Near a window a palm costs hundreds of
     * lux; in a dim back room the room itself is 15 lux, no absolute threshold could ever be met, and
     * the ratio is the whole test.
     */
    fun lightResponded(stats: TraceStats): Boolean = stats.count > 0 && (
        stats.magnitudeMax - stats.magnitudeMin >= LIGHT_ABSOLUTE_DROP ||
            stats.magnitudeMax >= stats.magnitudeMin.coerceAtLeast(0.5) * LIGHT_DROP_RATIO
        )

    /**
     * A stricter bar, used only when the light sensor is asked to testify against the proximity sensor.
     *
     * Being alive only needs [lightResponded]. Being *evidence that a palm was there* needs more,
     * because a shop with fluorescent tubes and people walking past will double a low reading on its
     * own — and on the looser rule that flicker would have been enough to call a perfectly good
     * proximity sensor dead. A palm does not make the reading vary, it makes it dark.
     */
    fun lightWentDark(stats: TraceStats): Boolean = stats.count > 0 &&
        stats.magnitudeMin <= COVERED_LUX &&
        stats.magnitudeMax >= stats.magnitudeMin + COVERED_DROP
}
