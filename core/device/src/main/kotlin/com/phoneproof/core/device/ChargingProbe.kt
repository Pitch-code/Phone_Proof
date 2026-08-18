package com.phoneproof.core.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.phoneproof.checks.device.PlugType
import com.phoneproof.core.diagnostics.Diagnostics
import kotlin.math.abs
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** One reading of what the charger and battery are doing. */
data class ChargeSample(
    val plugType: PlugType,
    val charging: Boolean,
    val percent: Int,
    val voltageMillivolts: Int,
    val currentMilliamps: Int?,
    val temperatureCelsius: Double?,
) {
    val plugged: Boolean get() = plugType != PlugType.NONE
}

/**
 * Where charger readings come from.
 *
 * Extracted so the charging state machine can be tested without a phone, a charger, or a human to pull the
 * cable out. That is not a theoretical benefit: the rule about what happens when the cable is removed
 * mid-measurement was wrong for the entire life of the check, and it was wrong precisely because there was
 * no way to write a test that would have said so.
 */
interface ChargeSource {

    /** The state right now, or null if the phone will not say. */
    fun snapshot(): ChargeSample?

    /** Every change until the collector stops, starting with the state as it is now. */
    fun stream(): Flow<ChargeSample>
}

/**
 * Watches the charger.
 *
 * `ACTION_BATTERY_CHANGED` is a sticky broadcast, so the current state is available immediately by
 * registering a null receiver — no waiting for the next change. The flow then keeps a receiver alive for the
 * length of the test, which is how a charger disappearing gets noticed at all.
 *
 * No permission for any of this.
 */
class ChargingProbe(private val context: Context) : ChargeSource {

    private val batteryManager: BatteryManager? =
        runCatching { context.getSystemService(BatteryManager::class.java) }.getOrNull()

    /** The state right now, from the sticky broadcast. */
    override fun snapshot(): ChargeSample? = runCatching {
        @Suppress("UnspecifiedRegisterReceiverFlag")
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        intent?.let(::read)
    }.onFailure { Diagnostics.error(TAG, "could not read the battery state", it) }.getOrNull()

    override fun stream(): Flow<ChargeSample> = callbackFlow {
        snapshot()?.let { trySend(it) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { trySend(read(it)) }
            }
        }

        runCatching {
            ContextCompat.registerExportedReceiver(
                context,
                receiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
        }.onFailure {
            Diagnostics.error(TAG, "could not register for battery changes", it)
            close()
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
                .onFailure { Diagnostics.warn(TAG, "could not unregister battery receiver", it) }
        }
    }

    private fun read(intent: Intent): ChargeSample {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val tenthsOfDegree = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)

        return ChargeSample(
            plugType = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
                BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
                0 -> PlugType.NONE
                else -> PlugType.OTHER
            },
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING,
            percent = if (level >= 0 && scale > 0) level * 100 / scale else 0,
            voltageMillivolts = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            currentMilliamps = currentMilliamps(),
            temperatureCelsius = tenthsOfDegree
                .takeIf { it != Int.MIN_VALUE }
                ?.let { it / 10.0 },
        )
    }

    /**
     * Charging current in milliamps, or null if the phone will not say.
     *
     * Two pieces of real-world untidiness handled here, both of which would otherwise produce nonsense:
     *
     *  - **The units are supposed to be microamps and often are not.** A number of manufacturers report
     *    milliamps from this property instead. So the magnitude decides: anything past 100,000 is microamps,
     *    anything smaller was already milliamps. A phone drawing 100 A does not exist, and neither does one
     *    charging at 0.1 mA.
     *  - **The sign is not standardised.** Some report charging as positive, some as negative. The absolute
     *    value is taken and the direction comes from the charging status, which is reliable.
     */
    private fun currentMilliamps(): Int? {
        val manager = batteryManager ?: return null
        val raw = runCatching {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull() ?: return null

        // Integer.MIN_VALUE and zero are both "no answer" here rather than "no current".
        if (raw == Int.MIN_VALUE || raw == 0) return null

        val magnitude = abs(raw.toLong())
        val milliamps = if (magnitude > MICROAMP_THRESHOLD) magnitude / 1000 else magnitude
        return milliamps.toInt().takeIf { it in 1..MAX_PLAUSIBLE_MILLIAMPS }
    }

    private companion object {
        const val TAG = "ChargingProbe"

        /** Past this the value must have been microamps: no phone charges at 100 A. */
        const val MICROAMP_THRESHOLD = 100_000L

        /** 20 A would be 80 W at 4 V, well beyond any phone. Anything above is a broken reading. */
        const val MAX_PLAUSIBLE_MILLIAMPS = 20_000
    }
}

/**
 * Registering a receiver in a way that satisfies every supported API level.
 *
 * `ACTION_BATTERY_CHANGED` is a protected system broadcast, so on Android 14 and later it has to be declared
 * exported or the registration is rejected outright — and the flag does not exist before Android 13. Getting
 * this wrong fails at runtime on exactly the phones this app is aimed at.
 */
private object ContextCompat {
    fun registerExportedReceiver(
        context: Context,
        receiver: BroadcastReceiver,
        filter: IntentFilter,
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }
}
