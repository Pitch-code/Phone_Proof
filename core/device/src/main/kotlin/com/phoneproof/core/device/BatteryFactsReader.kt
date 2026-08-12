package com.phoneproof.core.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.phoneproof.checks.device.BatteryFacts
import com.phoneproof.checks.device.BatteryHealth
import com.phoneproof.core.diagnostics.DiagnosticsRecorder

/**
 * Reads the battery, from the two places Android keeps it.
 *
 * `ACTION_BATTERY_CHANGED` is a sticky broadcast: registering a null receiver returns the last
 * value immediately without ever receiving a callback, so this stays synchronous and needs no
 * permission and no unregister.
 *
 * Cycle count is read as an extra with a sentinel default rather than gated on `SDK_INT >= 34`.
 * The version check would be misleading in both directions: several OEMs populated this extra
 * before API 34, and plenty of API 34+ phones still do not report it because it depends on the fuel
 * gauge. Asking whether the value is actually there is the honest test.
 */
class BatteryFactsReader(
    private val context: Context,
    private val diagnostics: DiagnosticsRecorder,
) {

    fun read(): BatteryFacts {
        val intent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.onFailure {
            diagnostics.error(TAG, "battery broadcast unavailable", it)
        }.getOrNull()

        val manager = runCatching {
            context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        }.getOrNull()

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val chargePercent = if (level >= 0 && scale > 0) {
            (level * 100) / scale
        } else {
            null
        }

        val cycles = intent
            ?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, UNREPORTED)
            ?.takeIf { it > 0 }

        val chargeCounter = manager
            ?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            // Documented to return Long.MIN_VALUE when unsupported, and some devices return 0.
            ?.takeIf { it > 0L && it != Long.MIN_VALUE }

        val tenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, UNREPORTED) ?: UNREPORTED
        val temperatureC = tenthsC.takeIf { it != UNREPORTED }?.let { it / 10f }

        val voltageMv = intent
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, UNREPORTED)
            ?.takeIf { it > 0 }

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, UNREPORTED) ?: UNREPORTED

        val facts = BatteryFacts(
            cycleCount = cycles,
            health = healthFrom(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, UNREPORTED)),
            chargePercent = chargePercent,
            chargeCounterMicroAh = chargeCounter,
            temperatureC = temperatureC,
            voltageMv = voltageMv,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            // Defaults to true when the extra is missing: a phone running this app has a battery,
            // and claiming otherwise would turn a missing extra into "no battery found".
            present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true) ?: true,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )

        diagnostics.info(
            TAG,
            "battery: cycles=${facts.cycleCount ?: "n/a"} · health=${facts.health} · " +
                "charge=${facts.chargePercent ?: "n/a"}% · counter=${facts.chargeCounterMicroAh ?: "n/a"}µAh · " +
                "temp=${facts.temperatureC ?: "n/a"}C · full≈${facts.estimatedFullChargeMah ?: "n/a"}mAh",
        )
        return facts
    }

    private fun healthFrom(raw: Int?): BatteryHealth = when (raw) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
        else -> BatteryHealth.UNKNOWN
    }

    private companion object {
        const val TAG = "BatteryFactsReader"

        /** Sentinel for "the extra was not present". -1 is a legal temperature, 0 a legal voltage. */
        const val UNREPORTED = Int.MIN_VALUE
    }
}
