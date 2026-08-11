package com.phoneproof.checks.device

/** One sensor the platform reported as present. */
data class SensorFact(
    val type: Int,
    val name: String,
    val vendor: String?,
)

/**
 * Everything the platform said about itself, measured at runtime.
 *
 * Deliberately a plain data class with no Android types, so every judgement made from it is
 * testable in milliseconds and none of it depends on a per-model lookup table. Nullable fields are
 * genuinely unavailable on some devices or API levels — never filled with a placeholder, because a
 * guessed value that looks measured is worse than an honest gap.
 */
data class DeviceFacts(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val hardware: String,
    val socModel: String? = null,
    val fingerprint: String? = null,
    val buildTags: String? = null,

    val sdkInt: Int,
    val androidRelease: String,
    /** `Build.VERSION.SECURITY_PATCH`, e.g. "2026-07-05". Blank on some builds. */
    val securityPatch: String? = null,

    val totalRamBytes: Long? = null,
    val totalStorageBytes: Long? = null,
    val freeStorageBytes: Long? = null,

    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val densityDpi: Int? = null,
    val currentRefreshRateHz: Float? = null,
    val supportedRefreshRatesHz: List<Float> = emptyList(),

    val sensors: List<SensorFact> = emptyList(),
    /** Null when the sensor list itself could not be read, which is different from "none". */
    val sensorsReadable: Boolean = true,

    val abis: List<String> = emptyList(),
) {
    val hasGyroscope: Boolean get() = sensors.any { it.type == SENSOR_TYPE_GYROSCOPE }
    val hasAccelerometer: Boolean get() = sensors.any { it.type == SENSOR_TYPE_ACCELEROMETER }
    val hasMagnetometer: Boolean get() = sensors.any { it.type == SENSOR_TYPE_MAGNETIC_FIELD }
    val hasProximity: Boolean get() = sensors.any { it.type == SENSOR_TYPE_PROXIMITY }
    val hasLight: Boolean get() = sensors.any { it.type == SENSOR_TYPE_LIGHT }

    val maxRefreshRateHz: Float?
        get() = supportedRefreshRatesHz.maxOrNull() ?: currentRefreshRateHz

    companion object {
        // Mirrors android.hardware.Sensor constants. Duplicated as plain ints so this module can
        // stay free of Android imports; the values are frozen platform API and cannot change.
        const val SENSOR_TYPE_ACCELEROMETER = 1
        const val SENSOR_TYPE_MAGNETIC_FIELD = 2
        const val SENSOR_TYPE_GYROSCOPE = 4
        const val SENSOR_TYPE_LIGHT = 5
        const val SENSOR_TYPE_PROXIMITY = 8
    }
}
