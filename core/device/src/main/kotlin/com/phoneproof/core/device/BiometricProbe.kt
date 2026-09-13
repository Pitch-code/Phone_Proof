package com.phoneproof.core.device

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import com.phoneproof.checks.device.BiometricSensorCheck.BiometricAvailability
import com.phoneproof.core.diagnostics.Diagnostics

/**
 * Reads the state of the fingerprint / face unlock subsystem.
 *
 * This is the whole of the Android-specific part of the biometric check: turn `canAuthenticate`'s result
 * codes into the one enum [BiometricSensorCheck][com.phoneproof.checks.device.BiometricSensorCheck] reasons
 * about. No prompt, no authentication, no permission — the app runs on the seller's phone and cannot (and
 * must not) try to authenticate as them. It only asks the platform what it already knows.
 *
 * `BIOMETRIC_WEAK` rather than `BIOMETRIC_STRONG`, because the question is "does the reader work", not "is
 * it strong enough to gate a bank app". Many phones ship face unlock that only clears the weak bar, and
 * asking for STRONG would report those as having no biometric at all — the opposite of the truth.
 *
 * The AndroidX `BiometricManager` is used so the answer is the same shape on every API level from 26 up,
 * rather than branching between the platform manager (29+) and `FingerprintManager` below it.
 */
class BiometricProbe(private val context: Context) {

    fun read(): BiometricAvailability = runCatching {
        when (BiometricManager.from(context).canAuthenticate(Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.UPDATE_REQUIRED
            // STATUS_UNKNOWN, or any code a future library adds: report it as unreadable rather than
            // guessing it into a pass or a fault.
            else -> BiometricAvailability.UNKNOWN_STATUS
        }
    }.onFailure {
        Diagnostics.error(TAG, "could not read the biometric state", it)
    }.getOrDefault(BiometricAvailability.UNKNOWN_STATUS)

    private companion object {
        const val TAG = "BiometricProbe"
    }
}
