package com.phoneproof.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.phoneproof.checks.sensors.SensorKind
import com.phoneproof.checks.sensors.SensorReading
import com.phoneproof.core.diagnostics.Diagnostics
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** What arrives while the buyer is handling the phone. */
sealed interface SensorEventUpdate {

    /**
     * Which sensors the platform actually agreed to send readings for, emitted before any of them.
     *
     * The reason this exists rather than being inferred from which sensors turn up: a sensor that was
     * never subscribed to and a sensor that was subscribed to and stayed silent look identical in a
     * list of samples, and one of those is a broken phone while the other is a failure in this app.
     * Reporting the second as the first would be an accusation against someone's handset for a bug of
     * ours.
     */
    data class Subscribed(val kinds: Set<SensorKind>) : SensorEventUpdate

    data class Sample(val kind: SensorKind, val reading: SensorReading) : SensorEventUpdate
}

/**
 * Reads the sensors, and asks for no permissions to do it.
 *
 * Worth stating plainly because it is unusual in this app: motion and environment sensors are not
 * permission-gated below 200 Hz. [SensorManager.SENSOR_DELAY_GAME] is around 50 Hz, so nothing here
 * needs `HIGH_SAMPLING_RATE_SENSORS`, and nothing needs to be declared in the manifest. A screen that
 * asks a stranger for no permission at all is the cheapest trust this app will ever buy.
 *
 * `SENSOR_DELAY_GAME` rather than `SENSOR_DELAY_UI` because the test has to catch the peak of a wrist
 * turn. At the UI rate — about 16 Hz — a quick flick of the wrist can fall between two samples, and a
 * missed peak reads as a gyroscope that did not respond.
 */
class SensorProbe(private val context: Context) {

    private val manager: SensorManager? =
        runCatching { context.getSystemService(SensorManager::class.java) }
            .onFailure { Diagnostics.error(TAG, "no sensor manager", it) }
            .getOrNull()

    /** Which of the five this handset claims to have at all. */
    fun available(): Set<SensorKind> {
        val sensorManager = manager ?: return emptySet()
        return SensorKind.entries
            .filter { kind ->
                runCatching { sensorManager.getDefaultSensor(androidType(kind)) != null }
                    .getOrDefault(false)
            }
            .toSet()
    }

    /**
     * Streams readings from [kinds] until the collector stops.
     *
     * A flow rather than a "record for three seconds and hand back a list" call, because the screen
     * needs to show the buyer that their tilting is registering *while* they do it. Without that
     * feedback the commonest outcome of this test is "cannot tell" — the buyer waggles the phone
     * politely, never reaches the threshold, and gets nothing for their trouble.
     *
     * Listeners are unregistered in [awaitClose], which matters more than it looks: a sensor
     * subscription left alive at 50 Hz after the buyer has navigated away drains the seller's battery
     * and is exactly the kind of thing that gets an app pulled from review.
     */
    fun stream(kinds: Set<SensorKind>): Flow<SensorEventUpdate> = callbackFlow {
        val sensorManager = manager
        if (sensorManager == null) {
            trySend(SensorEventUpdate.Subscribed(emptySet()))
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val kind = event?.sensor?.type?.let(::kindOf) ?: return
                val values = event.values ?: return
                trySend(
                    SensorEventUpdate.Sample(
                        kind = kind,
                        reading = SensorReading(
                            // Single-axis sensors fill only the first slot, so the rest are read
                            // defensively rather than assumed — a short values array here would
                            // otherwise crash the screen on some OEM driver.
                            x = values.getOrElse(0) { 0f },
                            y = values.getOrElse(1) { 0f },
                            z = values.getOrElse(2) { 0f },
                        ),
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val subscribed = kinds.filter { kind ->
            runCatching {
                val sensor = sensorManager.getDefaultSensor(androidType(kind))
                sensor != null && sensorManager.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
            }.onFailure {
                Diagnostics.error(TAG, "could not subscribe to $kind", it)
            }.getOrDefault(false)
        }.toSet()

        Diagnostics.info(TAG, "subscribed to ${subscribed.size} of ${kinds.size} sensors")
        trySend(SensorEventUpdate.Subscribed(subscribed))

        awaitClose {
            runCatching { sensorManager.unregisterListener(listener) }
                .onFailure { Diagnostics.error(TAG, "could not unregister sensor listener", it) }
        }
    }

    private fun androidType(kind: SensorKind): Int = when (kind) {
        // TYPE_ACCELEROMETER, deliberately, and not TYPE_LINEAR_ACCELERATION. The whole calibration
        // check rests on gravity being in the reading; linear acceleration is the one with gravity
        // subtracted out, which would leave nothing to compare against a known constant.
        SensorKind.ACCELEROMETER -> Sensor.TYPE_ACCELEROMETER
        SensorKind.GYROSCOPE -> Sensor.TYPE_GYROSCOPE
        SensorKind.MAGNETOMETER -> Sensor.TYPE_MAGNETIC_FIELD
        SensorKind.PROXIMITY -> Sensor.TYPE_PROXIMITY
        SensorKind.LIGHT -> Sensor.TYPE_LIGHT
    }

    private fun kindOf(type: Int): SensorKind? = when (type) {
        Sensor.TYPE_ACCELEROMETER -> SensorKind.ACCELEROMETER
        Sensor.TYPE_GYROSCOPE -> SensorKind.GYROSCOPE
        Sensor.TYPE_MAGNETIC_FIELD -> SensorKind.MAGNETOMETER
        Sensor.TYPE_PROXIMITY -> SensorKind.PROXIMITY
        Sensor.TYPE_LIGHT -> SensorKind.LIGHT
        else -> null
    }

    private companion object {
        const val TAG = "SensorProbe"
    }
}
