package com.phoneproof.checks.vibration

import com.phoneproof.checks.sensors.SensorReading
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The jerk a run of samples reached, taken at a high percentile rather than averaged.
 *
 * ## Why the mean was the wrong statistic
 *
 * A vibrating phone oscillates. Averaging the distance between consecutive samples therefore averages the
 * fast-moving part of each swing together with the moment it changes direction and is briefly barely moving
 * at all — so the mean sits well below what the motor actually achieved. On a real handset, resting at
 * 0.01 m/s² and buzzing on a hard desk, the mean came out at 0.04 while the check demanded 0.35.
 *
 * A high percentile keeps the peaks and still ignores a single freak sample, which a plain maximum would
 * not: one bump of the table, or one dropped sample from the sensor, would otherwise decide the verdict.
 *
 * The same statistic is used for the resting baseline as for the buzz, so the two are comparable. Taking a
 * mean at rest and a peak while buzzing would inflate the ratio for free.
 */
fun jerkPercentile(readings: List<SensorReading>, fraction: Double = 0.9): Double {
    if (readings.size < 2) return 0.0

    val jerks = ArrayList<Double>(readings.size - 1)
    for (index in 1 until readings.size) {
        val previous = readings[index - 1]
        val current = readings[index]
        val dx = (current.x - previous.x).toDouble()
        val dy = (current.y - previous.y).toDouble()
        val dz = (current.z - previous.z).toDouble()
        jerks += sqrt(dx * dx + dy * dy + dz * dz)
    }
    jerks.sort()

    // Nearest-rank, clamped. With two samples there is one jerk and every percentile is that one value,
    // which is the honest answer rather than an interpolation between a number and nothing.
    val rank = (fraction * (jerks.size - 1)).roundToInt().coerceIn(0, jerks.size - 1)
    return jerks[rank]
}
