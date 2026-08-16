package com.phoneproof.checks.vibration

import com.phoneproof.checks.sensors.SensorReading
import kotlin.math.sqrt

/**
 * How fast the acceleration on a phone is changing, averaged over a run of samples.
 *
 * ## Why not simply the amount of acceleration
 *
 * A vibrating phone does not feel more acceleration than a still one. Gravity is 9.8 m/s² either way, and it
 * dwarfs anything a coin-sized motor produces. What changes is how *quickly* the reading moves: a still
 * phone's samples sit almost on top of each other, while a buzzing one throws them around dozens of times a
 * second.
 *
 * ## Why the vector difference rather than the difference in magnitude
 *
 * A motor mostly changes the *direction* of the acceleration the phone feels, and a change of direction can
 * leave the magnitude almost untouched — the length of the vector stays near 9.8 while it wobbles. Measuring
 * only the magnitude would throw away most of the signal and, on an unlucky axis, all of it. The distance
 * between consecutive vectors keeps every bit of it.
 */
fun meanJerk(readings: List<SensorReading>): Double {
    if (readings.size < 2) return 0.0

    var total = 0.0
    for (index in 1 until readings.size) {
        val previous = readings[index - 1]
        val current = readings[index]
        val dx = (current.x - previous.x).toDouble()
        val dy = (current.y - previous.y).toDouble()
        val dz = (current.z - previous.z).toDouble()
        total += sqrt(dx * dx + dy * dy + dz * dz)
    }
    return total / (readings.size - 1)
}
