package com.phoneproof.core.device

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.view.WindowManager
import com.phoneproof.checks.device.DeviceFacts
import com.phoneproof.checks.device.SensorFact
import com.phoneproof.core.diagnostics.DiagnosticsRecorder

/**
 * Reads what the platform says about itself.
 *
 * Every field is gathered independently and defensively. One OEM returning something unexpected
 * must degrade that single fact to null rather than losing the whole scan — an all-or-nothing read
 * would mean the least reliable phone produces the least information, which is backwards.
 *
 * Nothing here decides anything. The verdicts live in `checks:device` as pure functions.
 */
class DeviceFactsReader(
    private val context: Context,
    private val diagnostics: DiagnosticsRecorder? = null,
) {

    fun read(): DeviceFacts {
        val sensorsResult = runCatching { readSensors() }
            .onFailure { diagnostics?.error(TAG, "sensor list failed", it) }

        val facts = DeviceFacts(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            socModel = socModel(),
            fingerprint = field { Build.FINGERPRINT },
            buildTags = field { Build.TAGS },
            sdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            securityPatch = field { Build.VERSION.SECURITY_PATCH },
            totalRamBytes = totalRam(),
            totalStorageBytes = storage { it.blockCountLong * it.blockSizeLong },
            freeStorageBytes = storage { it.availableBlocksLong * it.blockSizeLong },
            widthPx = metric { it.first },
            heightPx = metric { it.second },
            densityDpi = runCatching { context.resources.displayMetrics.densityDpi }.getOrNull(),
            currentRefreshRateHz = refreshRate(),
            supportedRefreshRatesHz = supportedRefreshRates(),
            sensors = sensorsResult.getOrNull().orEmpty(),
            sensorsReadable = sensorsResult.isSuccess,
            abis = runCatching { Build.SUPPORTED_ABIS.toList() }.getOrDefault(emptyList()),
        )

        diagnostics?.info(
            TAG,
            "facts: ${facts.manufacturer} ${facts.model} · patch=${facts.securityPatch ?: "?"} · " +
                "sensors=${facts.sensors.size} · gyro=${facts.hasGyroscope} · " +
                "storage=${facts.totalStorageBytes ?: -1} · maxHz=${facts.maxRefreshRateHz ?: -1f}",
        )
        return facts
    }

    private inline fun field(read: () -> String?): String? =
        runCatching { read()?.takeIf { it.isNotBlank() } }.getOrNull()

    private fun socModel(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            field { Build.SOC_MODEL }
        } else {
            null
        }

    private fun totalRam(): Long? = runCatching {
        // /proc/meminfo is the only route that does not need a running ActivityManager query and
        // it reports the real installed total rather than what is currently available.
        java.io.File("/proc/meminfo").useLines { lines ->
            lines.firstOrNull { it.startsWith("MemTotal") }
                ?.filter { it.isDigit() }
                ?.toLongOrNull()
                ?.times(1024L)
        }
    }.onFailure { diagnostics?.info(TAG, "MemTotal unreadable") }.getOrNull()

    private inline fun storage(read: (StatFs) -> Long): Long? = runCatching {
        read(StatFs(Environment.getDataDirectory().absolutePath))
    }.getOrNull()

    private inline fun metric(pick: (Pair<Int, Int>) -> Int): Int? = runCatching {
        val m = context.resources.displayMetrics
        pick(m.widthPixels to m.heightPixels)
    }.getOrNull()

    private fun refreshRate(): Float? = runCatching {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.refreshRate
    }.getOrNull()

    private fun supportedRefreshRates(): List<Float> = runCatching {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.supportedModes.map { it.refreshRate }.distinct()
    }.getOrDefault(emptyList())

    private fun readSensors(): List<SensorFact> {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return manager.getSensorList(android.hardware.Sensor.TYPE_ALL).map {
            SensorFact(type = it.type, name = it.name.orEmpty(), vendor = it.vendor)
        }
    }

    private companion object {
        const val TAG = "DeviceFactsReader"
    }
}
