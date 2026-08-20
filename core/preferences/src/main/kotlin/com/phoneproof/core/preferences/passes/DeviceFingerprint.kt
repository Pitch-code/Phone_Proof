package com.phoneproof.core.preferences.passes

import android.content.Context
import android.provider.Settings
import com.phoneproof.core.diagnostics.Diagnostics
import java.security.MessageDigest

/**
 * How the licence server recognises "this same phone again" without ever learning which phone it is.
 *
 * The server needs exactly one thing: to tell whether a redeem is a fresh inspection or the buyer reopening
 * the app on a handset that already has a live pass, because the second must not cost a second inspection.
 * It needs nothing else, and this is built so it *can* learn nothing else.
 *
 * ## Why the code is the salt
 *
 * The hash is over the **pass code and the device id together**. That single decision is what makes the
 * whole scheme private rather than merely obscured:
 *
 *  - Under one code, the same phone hashes the same way every time — so the 24-hour rule works.
 *  - Under a *different* code, the same phone hashes to something unrelated — so the server cannot tell
 *    that two codes were used on one handset, cannot count how many phones a person inspected, and cannot
 *    follow a phone from one buyer to another.
 *
 * A plain hash of the device id would have been simpler and would have quietly built a graph of every
 * handset this app has ever run on. The phones being identified belong to **sellers**, who never agreed to
 * anything and will never know the request happened. That is the reason for the salt, and the reason it is
 * not negotiable.
 *
 * ## What is hashed
 *
 * `ANDROID_ID`, which needs no permission, is stable across reboots, and is already scoped per app-signing
 * key — so it is not a cross-app identifier to begin with. It changes on factory reset, which is correct
 * here: a wiped phone genuinely is a different inspection.
 */
object DeviceFingerprint {

    /**
     * The value sent to the server. Pure, so it can be tested without a phone.
     *
     * SHA-256, hex, full length. Not truncated: the only reason to shorten it would be to save bytes on a
     * request that happens once per inspection, and a shorter hash makes collisions between two handsets
     * thinkable — which would hand one buyer's pass to a stranger.
     */
    fun hash(code: String, deviceId: String): String {
        // The separator matters. Without it, code "AB" + id "CD" and code "ABC" + id "D" would produce the
        // same hash, and a colon cannot appear in either value.
        val salted = "$code:$deviceId"
        val digest = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * The fingerprint of *this* phone for [code].
     *
     * Falls back to a constant when the id cannot be read. That is deliberate and the trade-off is worth
     * stating: the phone loses the "reopening does not cost a pass" protection, so the worst case is a buyer
     * spending one extra inspection. Refusing to redeem at all would be worse — it would strand somebody in
     * front of a seller with a code they paid for and no way to use it.
     */
    fun of(context: Context, code: String): String {
        val deviceId = runCatching {
            @Suppress("HardwareIds")
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.onFailure {
            Diagnostics.warn(TAG, "could not read the device id; the 24-hour rule may not apply", it)
        }.getOrNull()

        return hash(code, deviceId?.takeIf { it.isNotBlank() } ?: UNKNOWN_DEVICE)
    }

    /** Used when the phone will not say who it is. Salted like any other value, so it leaks nothing. */
    internal const val UNKNOWN_DEVICE = "unknown-device"

    private const val TAG = "DeviceFingerprint"
}
