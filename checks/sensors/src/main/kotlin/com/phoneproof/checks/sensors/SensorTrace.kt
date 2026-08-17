package com.phoneproof.checks.sensors

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The five sensors worth testing on a used phone, and the only ones this module knows about.
 *
 * [axes] is not decoration. Light and proximity report a single number — lux, and centimetres — and
 * Android says nothing about what a driver may put in the remaining slots of the event. Some OEM drivers
 * publish raw or colour-temperature channels there, so reading them as if they were part of a vector
 * produces a "lux" figure that is partly not lux. Recording how many slots actually mean something is
 * what stops that happening; see [SensorReading.of].
 */
enum class SensorKind(val axes: Int) {
    ACCELEROMETER(3),
    GYROSCOPE(3),
    MAGNETOMETER(3),
    PROXIMITY(1),
    LIGHT(1),
}

/**
 * One sample.
 *
 * Single-axis sensors — proximity in centimetres, light in lux — use [x] and leave the rest at zero.
 * A plain class rather than a `FloatArray` because a data class holding an array has an `equals` that
 * compares references, which makes every test that compares traces quietly meaningless.
 */
data class SensorReading(val x: Float, val y: Float = 0f, val z: Float = 0f) {
    val magnitude: Double get() = sqrt((x.toDouble() * x + y.toDouble() * y + z.toDouble() * z))

    companion object {
        /**
         * Builds a reading from a platform event, keeping only the slots that mean something for [kind].
         *
         * This exists because of a real bug. A light sensor's lux is `values[0]` and nothing else, but
         * every slot was being copied in and [magnitude] squares all three — so on a driver that
         * publishes an extra channel, the number compared against the "is it dark" threshold was
         * inflated by data that was never lux. The palm indicator could then never be satisfied, and
         * the lux shown in the report was wrong too.
         *
         * Dropping the surplus slots at the boundary, where the platform data arrives, means every
         * calculation downstream is right by construction instead of having to remember this.
         */
        fun of(kind: SensorKind, x: Float, y: Float, z: Float): SensorReading =
            if (kind.axes == 1) SensorReading(x) else SensorReading(x, y, z)
    }
}

/** Everything one sensor delivered during one phase of the test. */
data class SensorTrace(
    val kind: SensorKind,
    val readings: List<SensorReading>,
    /**
     * Whether the app succeeded in subscribing to the sensor at all.
     *
     * Kept apart from an empty [readings] list because the two mean opposite things. A sensor that
     * accepted the subscription and then said nothing for three seconds is broken. A sensor the system
     * refused to subscribe to tells us nothing about the hardware — it is the app that failed, or the
     * platform that declined, and reporting that as a fault in someone's phone would be a lie.
     */
    val registered: Boolean = true,
) {
    val stats: TraceStats get() = TraceStats.of(readings)
}

/**
 * What a trace amounts to.
 *
 * [largestAxisSpan] is the important one and the reason magnitude alone will not do. Tilting a phone
 * from flat to upright barely changes the *size* of the acceleration it feels — gravity is still
 * gravity — while swinging a whole 9.8 from one axis to another. A liveness test that watched
 * magnitude would call a perfectly good accelerometer motionless.
 */
data class TraceStats(
    val count: Int,
    val magnitudeMean: Double,
    val magnitudeMin: Double,
    val magnitudeMax: Double,
    /** The biggest peak-to-peak swing of any single axis. */
    val largestAxisSpan: Double,
    /**
     * How many different samples arrived.
     *
     * One, across three seconds of handling, is a sensor whose driver has latched. It is not the same
     * failure as silence and it is invisible to any check that only asks whether readings arrived.
     */
    val distinctValues: Int,
) {
    val isEmpty: Boolean get() = count == 0

    companion object {
        fun of(readings: List<SensorReading>): TraceStats {
            if (readings.isEmpty()) {
                return TraceStats(0, 0.0, 0.0, 0.0, 0.0, 0)
            }
            val magnitudes = readings.map { it.magnitude }
            val spans = listOf(
                span(readings.map { it.x }),
                span(readings.map { it.y }),
                span(readings.map { it.z }),
            )
            return TraceStats(
                count = readings.size,
                magnitudeMean = magnitudes.average(),
                magnitudeMin = magnitudes.min(),
                magnitudeMax = magnitudes.max(),
                largestAxisSpan = spans.max(),
                distinctValues = readings.distinct().size,
            )
        }

        private fun span(values: List<Float>): Double =
            abs(values.max().toDouble() - values.min().toDouble())
    }
}
