package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.device.DeviceFacts.Companion.SENSOR_TYPE_ACCELEROMETER
import com.phoneproof.checks.device.DeviceFacts.Companion.SENSOR_TYPE_GYROSCOPE
import com.phoneproof.checks.device.DeviceFacts.Companion.SENSOR_TYPE_LIGHT
import com.phoneproof.checks.device.DeviceFacts.Companion.SENSOR_TYPE_MAGNETIC_FIELD
import com.phoneproof.checks.device.DeviceFacts.Companion.SENSOR_TYPE_PROXIMITY
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

/** A realistic baseline: the realme handset this was first tested on. */
private fun facts(
    manufacturer: String = "realme",
    brand: String = "realme",
    model: String = "RMX5110",
    device: String = "RMX5110",
    hardware: String = "mt6789",
    fingerprint: String? = "realme/RMX5110/RMX5110:16/UP1A/S123:user/release-keys",
    buildTags: String? = "release-keys",
    securityPatch: String? = "2026-07-05",
    totalStorage: Long? = 118_000_000_000L,
    freeStorage: Long? = 60_000_000_000L,
    widthPx: Int? = 1080,
    heightPx: Int? = 2392,
    currentHz: Float? = 120f,
    supportedHz: List<Float> = listOf(60f, 90f, 120f),
    sensorTypes: List<Int> = listOf(
        SENSOR_TYPE_ACCELEROMETER, SENSOR_TYPE_GYROSCOPE, SENSOR_TYPE_MAGNETIC_FIELD,
        SENSOR_TYPE_PROXIMITY, SENSOR_TYPE_LIGHT,
    ),
    sensorsReadable: Boolean = true,
) = DeviceFacts(
    manufacturer = manufacturer, brand = brand, model = model, device = device,
    hardware = hardware, socModel = "MT6789", fingerprint = fingerprint, buildTags = buildTags,
    sdkInt = 36, androidRelease = "16", securityPatch = securityPatch,
    totalStorageBytes = totalStorage, freeStorageBytes = freeStorage,
    widthPx = widthPx, heightPx = heightPx, densityDpi = 480,
    currentRefreshRateHz = currentHz, supportedRefreshRatesHz = supportedHz,
    sensors = sensorTypes.map { SensorFact(it, "sensor$it", "vendor") },
    sensorsReadable = sensorsReadable,
)

/** 2026-08-11, matching the patch dates used above. */
private const val TODAY = 20_676L

class SecurityPatchCheckTest {

    @Test
    fun `date parsing handles a normal patch string`() {
        assertThat(SecurityPatchCheck.parsePatchDate("2026-07-05")).isNotNull()
        assertThat(SecurityPatchCheck.parsePatchDate("1970-01-01")).isNull() // year out of range
        assertThat(SecurityPatchCheck.parsePatchDate("2026-13-01")).isNull()
        assertThat(SecurityPatchCheck.parsePatchDate("2026-07")).isNull()
        assertThat(SecurityPatchCheck.parsePatchDate("garbage")).isNull()
    }

    @Test
    fun `epoch conversion agrees with a known date`() {
        // 2026-08-11 is 20676 days after 1970-01-01.
        assertThat(SecurityPatchCheck.parsePatchDate("2026-08-11")).isEqualTo(TODAY)
        assertThat(SecurityPatchCheck.parsePatchDate("1970-01-02")).isNull()
        assertThat(SecurityPatchCheck.parsePatchDate("2000-01-01")).isEqualTo(10_957L)
    }

    @Test
    fun `a recent patch passes`() {
        val result = SecurityPatchCheck.evaluate(facts(securityPatch = "2026-07-05"), TODAY)
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a patch older than six months is a caution`() {
        val result = SecurityPatchCheck.evaluate(facts(securityPatch = "2025-11-01"), TODAY)
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.action).contains("update")
    }

    @Test
    fun `a patch older than eighteen months is a failure`() {
        val result = SecurityPatchCheck.evaluate(facts(securityPatch = "2024-01-05"), TODAY)
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.consequence).contains("never be fixed")
    }

    @Test
    fun `a missing patch date is CAN'T TELL not a failure`() {
        assertThat(SecurityPatchCheck.evaluate(facts(securityPatch = null), TODAY).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(SecurityPatchCheck.evaluate(facts(securityPatch = "  "), TODAY).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `a future patch date is flagged rather than treated as very current`() {
        val result = SecurityPatchCheck.evaluate(facts(securityPatch = "2027-01-01"), TODAY)
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("future")
    }
}

class BuildIntegrityCheckTest {

    @Test
    fun `release keys with a consistent fingerprint passes`() {
        val result = BuildIntegrityCheck.evaluate(facts())
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `test keys is a hard failure`() {
        val result = BuildIntegrityCheck.evaluate(facts(buildTags = "test-keys"))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
        assertThat(result.consequence).contains("faked")
    }

    @Test
    fun `a fingerprint naming a different device is a caution`() {
        // The signature of a cloned phone: MODEL edited, fingerprint left behind.
        val result = BuildIntegrityCheck.evaluate(
            facts(
                model = "Galaxy S26 Ultra",
                device = "SM-S948B",
                fingerprint = "realme/RMX1911/RMX1911:11/RKQ1/123:user/release-keys",
            ),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.consequence).contains("cloned")
    }

    @Test
    fun `an unreadable fingerprint passes with lower confidence rather than failing`() {
        val result = BuildIntegrityCheck.evaluate(facts(fingerprint = null))
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("not readable")
    }
}

class StorageCheckTest {

    @Test
    fun `a plausible 128GB phone passes`() {
        assertThat(StorageCheck.evaluate(facts()).outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `storage far below its tier is a caution`() {
        // Claims to be in the 128GB class but only holds 40GB.
        val result = StorageCheck.evaluate(facts(totalStorage = 40_000_000_000L))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.consequence).contains("smaller than advertised")
    }

    @Test
    fun `a nearly full phone is flagged so it can be reset before testing`() {
        val result = StorageCheck.evaluate(facts(freeStorage = 900_000_000L))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.action).contains("factory reset")
    }

    @Test
    fun `unreadable storage is CAN'T TELL`() {
        assertThat(StorageCheck.evaluate(facts(totalStorage = null)).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
        assertThat(StorageCheck.evaluate(facts(totalStorage = 0L)).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
    }
}

class SensorInventoryCheckTest {

    @Test
    fun `a full sensor set passes`() {
        assertThat(SensorInventoryCheck.evaluate(facts()).outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a missing gyroscope is a caution with a gaming consequence`() {
        val result = SensorInventoryCheck.evaluate(
            facts(
                sensorTypes = listOf(
                    SENSOR_TYPE_ACCELEROMETER, SENSOR_TYPE_MAGNETIC_FIELD,
                    SENSOR_TYPE_PROXIMITY, SENSOR_TYPE_LIGHT,
                ),
            ),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("gyroscope")
        assertThat(result.consequence).contains("games")
    }

    @Test
    fun `a missing proximity sensor is a caution about calls`() {
        val result = SensorInventoryCheck.evaluate(
            facts(
                sensorTypes = listOf(
                    SENSOR_TYPE_ACCELEROMETER, SENSOR_TYPE_GYROSCOPE,
                    SENSOR_TYPE_MAGNETIC_FIELD, SENSOR_TYPE_LIGHT,
                ),
            ),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.consequence).contains("ear")
    }

    @Test
    fun `no accelerometer at all is a failure, since every phone has one`() {
        val result = SensorInventoryCheck.evaluate(facts(sensorTypes = listOf(SENSOR_TYPE_LIGHT)))
        assertThat(result.outcome).isEqualTo(CheckOutcome.FAIL)
    }

    @Test
    fun `an unreadable sensor list is CAN'T TELL, not a missing-sensor failure`() {
        val result = SensorInventoryCheck.evaluate(
            facts(sensorTypes = emptyList(), sensorsReadable = false),
        )
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }
}

class DisplayCheckTest {

    @Test
    fun `a high refresh screen running at its maximum passes`() {
        assertThat(DisplayCheck.evaluate(facts()).outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `a capped high refresh screen is a caution with a settings fix`() {
        val result = DisplayCheck.evaluate(facts(currentHz = 60f, supportedHz = listOf(60f, 120f)))
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
        assertThat(result.headline).contains("120")
        assertThat(result.action).contains("Refresh rate")
    }

    @Test
    fun `a plain 60Hz screen passes without complaint`() {
        val result = DisplayCheck.evaluate(facts(currentHz = 60f, supportedHz = listOf(60f)))
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `unreadable display details are CAN'T TELL`() {
        assertThat(DisplayCheck.evaluate(facts(widthPx = null)).outcome)
            .isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `max refresh falls back to the current rate when modes are unavailable`() {
        val result = DisplayCheck.evaluate(facts(supportedHz = emptyList(), currentHz = 90f))
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
        assertThat(result.headline).contains("90")
    }
}
