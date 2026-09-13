package com.phoneproof.checks.device

import com.google.common.truth.Truth.assertThat
import com.phoneproof.checks.device.BiometricSensorCheck.BiometricAvailability
import com.phoneproof.core.model.CheckOutcome
import org.junit.Test

/**
 * The verdicts for the fingerprint / face reader.
 *
 * The line this holds: the buyer cannot authenticate with the seller's finger, so the check must never
 * turn that inability into a fault. A phone is only ever *passed* when the platform itself says the reader
 * is set up and ready; everything else is a caution or an honest "can't tell", never a FAIL.
 */
class BiometricSensorCheckTest {

    @Test
    fun `set up and ready is a pass`() {
        val result = BiometricSensorCheck.evaluate(BiometricAvailability.READY)
        assertThat(result.outcome).isEqualTo(CheckOutcome.PASS)
    }

    @Test
    fun `nothing enrolled cannot be proven, so it is unknown not a fault`() {
        val result = BiometricSensorCheck.evaluate(BiometricAvailability.NOT_ENROLLED)
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
        // It has to tell the buyer how to get an answer rather than shrug.
        assertThat(result.action).isNotNull()
        assertThat(result.action).contains("add a fingerprint")
    }

    @Test
    fun `present but unavailable is a caution`() {
        val result = BiometricSensorCheck.evaluate(BiometricAvailability.UNAVAILABLE)
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `needing a security update is a caution`() {
        val result = BiometricSensorCheck.evaluate(BiometricAvailability.UPDATE_REQUIRED)
        assertThat(result.outcome).isEqualTo(CheckOutcome.CAUTION)
    }

    @Test
    fun `no hardware is unknown, because absence and a dead sensor look identical from here`() {
        val result = BiometricSensorCheck.evaluate(BiometricAvailability.NO_HARDWARE)
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `an unreadable state says so`() {
        val result = BiometricSensorCheck.evaluate(BiometricAvailability.UNKNOWN_STATUS)
        assertThat(result.outcome).isEqualTo(CheckOutcome.UNKNOWN)
    }

    @Test
    fun `no state ever fails the phone`() {
        // The core promise: the buyer's inability to authenticate as the seller must never become a defect.
        BiometricAvailability.entries.forEach { availability ->
            assertThat(BiometricSensorCheck.evaluate(availability).outcome)
                .isNotEqualTo(CheckOutcome.FAIL)
        }
    }

    @Test
    fun `every check carries the biometric id and title`() {
        BiometricAvailability.entries.forEach { availability ->
            val result = BiometricSensorCheck.evaluate(availability)
            assertThat(result.id).isEqualTo(BiometricSensorCheck.CHECK_ID)
            assertThat(result.title).isEqualTo("Fingerprint & face unlock")
        }
    }
}
