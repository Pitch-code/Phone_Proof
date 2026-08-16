package com.phoneproof.checks.sensors

/**
 * Synthetic traces that stand in for real handling.
 *
 * Written as physics rather than as arbitrary numbers, so a threshold change shows up here as a test
 * that fails for a reason someone can reason about — "a phone tilted through 90° no longer counts as
 * movement" — rather than as a magic constant that needs nudging.
 */

/** Gravity, 9.81 m/s², resting on the z axis with the phone flat and a little sensor noise. */
internal fun accelerometerAtRest(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.ACCELEROMETER,
    readings = List(samples) { i ->
        val jitter = if (i % 2 == 0) 0.01f else -0.01f
        SensorReading(x = jitter, y = jitter, z = 9.81f + jitter)
    },
)

/** Flat, then tilted onto its side: gravity migrates from z to x without changing size. */
internal fun accelerometerTilted(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.ACCELEROMETER,
    readings = List(samples) { i ->
        val fraction = i.toFloat() / (samples - 1)
        val angle = fraction * (Math.PI / 2).toFloat()
        SensorReading(
            x = 9.81f * kotlin.math.sin(angle),
            y = 0.02f,
            z = 9.81f * kotlin.math.cos(angle),
        )
    },
)

/** The identical sample, over and over. A driver that has latched. */
internal fun accelerometerLatched(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.ACCELEROMETER,
    readings = List(samples) { SensorReading(x = 0f, y = 0f, z = 9.81f) },
)

/** Reporting zeros, which is what a disconnected sensor does. */
internal fun allZeroes(kind: SensorKind, samples: Int = 60): SensorTrace = SensorTrace(
    kind = kind,
    readings = List(samples) { SensorReading(0f, 0f, 0f) },
)

/** Registered, then said nothing for the whole three seconds. */
internal fun silent(kind: SensorKind): SensorTrace = SensorTrace(kind, readings = emptyList())

/** The subscription itself was refused, so the hardware is not on trial. */
internal fun unsubscribed(kind: SensorKind): SensorTrace =
    SensorTrace(kind, readings = emptyList(), registered = false)

/** A wrist turn: peaks well past the 0.35 rad/s the check asks for. */
internal fun gyroscopeTurning(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.GYROSCOPE,
    readings = List(samples) { i ->
        SensorReading(x = 0.01f, y = 0.01f, z = 1.4f * kotlin.math.sin(i * 0.2f))
    },
)

/** Sitting on a table: noise only, nothing that could be mistaken for a turn. */
internal fun gyroscopeAtRest(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.GYROSCOPE,
    readings = List(samples) { i ->
        val jitter = if (i % 2 == 0) 0.004f else -0.004f
        SensorReading(x = jitter, y = jitter, z = jitter)
    },
)

/** Earth's field at a middling latitude, about 48 µT. */
internal fun magnetometerNormal(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.MAGNETOMETER,
    readings = List(samples) { i ->
        val drift = i * 0.02f
        SensorReading(x = 22f + drift, y = -14f, z = 40f)
    },
)

/** A magnet on the back of the phone: still working, reading three times the planet. */
internal fun magnetometerSwamped(samples: Int = 60): SensorTrace = SensorTrace(
    kind = SensorKind.MAGNETOMETER,
    readings = List(samples) { i -> SensorReading(x = 150f + i * 0.1f, y = 90f, z = 120f) },
)

/** Uncovered, then a palm: 5 cm to 0 and back. */
internal fun proximityCovered(): SensorTrace = SensorTrace(
    kind = SensorKind.PROXIMITY,
    readings = List(20) { SensorReading(5f) } +
        List(20) { SensorReading(0f) } +
        List(20) { SensorReading(5f) },
)

/** Never changed. Whether that is a fault depends entirely on the light sensor. */
internal fun proximityUnchanged(): SensorTrace = SensorTrace(
    kind = SensorKind.PROXIMITY,
    readings = List(60) { SensorReading(5f) },
)

/** A lit shop, then a palm over the sensor: 320 lux down to 4. */
internal fun lightCovered(): SensorTrace = SensorTrace(
    kind = SensorKind.LIGHT,
    readings = List(20) { SensorReading(320f) } +
        List(20) { SensorReading(4f) } +
        List(20) { SensorReading(318f) },
)

/** A dim back room, 14 lux down to 3. Too small an absolute drop to pass on that alone. */
internal fun lightCoveredInADimRoom(): SensorTrace = SensorTrace(
    kind = SensorKind.LIGHT,
    readings = List(30) { SensorReading(14f) } + List(30) { SensorReading(3f) },
)

internal fun lightUnchanged(lux: Float = 300f): SensorTrace = SensorTrace(
    kind = SensorKind.LIGHT,
    readings = List(60) { SensorReading(lux) },
)

internal val everySensor: Set<SensorKind> = SensorKind.entries.toSet()

internal fun List<SensorFinding>.stateOf(kind: SensorKind): SensorState =
    first { it.kind == kind }.state
