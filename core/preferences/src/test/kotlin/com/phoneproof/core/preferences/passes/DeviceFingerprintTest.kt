package com.phoneproof.core.preferences.passes

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The privacy property, asserted rather than described.
 *
 * The salt is the whole design. Without a test, "hash the id with the code" is one refactor away from "hash
 * the id" — which would work identically, pass every other test, and quietly build a record of every
 * handset this app has ever run on. Those handsets belong to sellers who never agreed to anything.
 */
class DeviceFingerprintTest {

    private val phoneA = "a1b2c3d4e5f60718"
    private val phoneB = "ffeeddccbbaa9988"
    private val codeOne = "N6WEDKZE"
    private val codeTwo = "0G23456R"

    @Test
    fun the_same_phone_under_the_same_code_is_recognisable() {
        // What makes the 24-hour rule work: reopening the app must not cost a second inspection.
        assertThat(DeviceFingerprint.hash(codeOne, phoneA))
            .isEqualTo(DeviceFingerprint.hash(codeOne, phoneA))
    }

    @Test
    fun the_same_phone_under_a_different_code_is_unrecognisable() {
        // The property that stops the server building a picture of which phones a person inspected. If this
        // ever fails, the salt has been dropped and the scheme has become surveillance that happens to work.
        assertThat(DeviceFingerprint.hash(codeOne, phoneA))
            .isNotEqualTo(DeviceFingerprint.hash(codeTwo, phoneA))
    }

    @Test
    fun two_phones_under_one_code_are_told_apart() {
        // Needed for the opposite reason: two different handsets on one pack must each cost an inspection.
        assertThat(DeviceFingerprint.hash(codeOne, phoneA))
            .isNotEqualTo(DeviceFingerprint.hash(codeOne, phoneB))
    }

    @Test
    fun the_separator_stops_the_boundary_being_ambiguous() {
        // Without a separator, code "AB" + id "CD" and code "ABC" + id "D" are the same bytes, and two
        // unrelated phones would share a fingerprint — handing one buyer's pass to a stranger.
        assertThat(DeviceFingerprint.hash("AB", "CD"))
            .isNotEqualTo(DeviceFingerprint.hash("ABC", "D"))
    }

    @Test
    fun the_device_id_cannot_be_read_back_out_of_it() {
        // Not proof of irreversibility — that is SHA-256's job — but it does catch the obvious mistake of
        // sending the id alongside the hash, or of "hashing" by concatenating.
        val fingerprint = DeviceFingerprint.hash(codeOne, phoneA)

        assertThat(fingerprint).doesNotContain(phoneA)
        assertThat(fingerprint).doesNotContain(codeOne)
    }

    @Test
    fun it_is_a_full_length_hex_digest() {
        // The server requires at least 16 characters. Full length rather than truncated: the only saving
        // would be a few bytes on a request that happens once per inspection, and a shorter hash makes a
        // collision between two handsets thinkable.
        val fingerprint = DeviceFingerprint.hash(codeOne, phoneA)

        assertThat(fingerprint).hasLength(64)
        assertThat(fingerprint).matches("[0-9a-f]{64}")
    }

    @Test
    fun a_phone_that_will_not_identify_itself_still_produces_a_fingerprint() {
        // The fallback path. A buyer standing in front of a seller must be able to redeem the code they paid
        // for; losing the reopening protection is a far smaller harm than refusing outright.
        val fallback = DeviceFingerprint.hash(codeOne, DeviceFingerprint.UNKNOWN_DEVICE)

        assertThat(fallback).hasLength(64)
        // And it is still salted, so it is not a shared constant across every unidentifiable phone.
        assertThat(fallback).isNotEqualTo(DeviceFingerprint.hash(codeTwo, DeviceFingerprint.UNKNOWN_DEVICE))
    }
}
