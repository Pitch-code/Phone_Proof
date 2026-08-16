package com.phoneproof.core.device

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.phoneproof.checks.radios.RadioKind
import com.phoneproof.checks.radios.RadioObservation
import com.phoneproof.core.diagnostics.Diagnostics

/** Both radios as they are right now. */
data class RadioSnapshot(
    val wifi: RadioObservation,
    val bluetooth: RadioObservation,
)

/**
 * Reads the state of the Wi-Fi and Bluetooth radios.
 *
 * ## Every API here was chosen to avoid a permission prompt
 *
 * Three of the obvious calls are traps, and all three are avoided:
 *
 *  - **`WifiManager.getScanResults()`** needs fine location, because a list of nearby networks is a location.
 *    Association is used as the evidence instead, which needs nothing. See `RadioCheck`.
 *  - **`BluetoothAdapter.ACTION_REQUEST_ENABLE`**, the tidy "turn it on for me" dialog, requires the
 *    `BLUETOOTH_CONNECT` runtime permission from API 31. A runtime prompt to save one tap is a bad trade for
 *    an app asking a stranger to trust it, so the buyer is sent to the system's own settings surface instead.
 *  - **`WifiManager.setWifiEnabled()`** has been a no-op since API 29 in any case, so there is no version of
 *    this where the app flips the switch itself.
 *
 * What remains needs only two normal, install-time permissions (`ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`)
 * and the legacy `BLUETOOTH` one on API 30 and below. None of them prompt.
 *
 * ## Why this polls instead of listening
 *
 * The event-driven version needs two protected system broadcasts, each of which has to be registered with an
 * exported flag that only exists from API 33, and getting it wrong fails at runtime on exactly the phones this
 * app targets. Reading the state is a handful of cheap synchronous calls, so the screen re-reads about once a
 * second and the whole class of receiver bugs disappears. A second of latency on a toggle nobody can flip
 * faster than that is not a real cost.
 */
class RadioProbe(private val context: Context) {

    fun snapshot(): RadioSnapshot = RadioSnapshot(wifi = readWifi(), bluetooth = readBluetooth())

    // ------------------------------------------------------------------------------------------- Wi-Fi

    private fun readWifi(): RadioObservation {
        val present = hasFeature("android.hardware.wifi")
        if (!present) {
            return RadioObservation(kind = RadioKind.WIFI, present = false)
        }

        val manager = runCatching {
            context.applicationContext.getSystemService(WifiManager::class.java)
        }.onFailure { Diagnostics.warn(TAG, "no WifiManager", it) }.getOrNull()

        val enabled = manager?.let {
            runCatching { it.isWifiEnabled }
                .onFailure { error -> Diagnostics.warn(TAG, "could not read the Wi-Fi state", error) }
                .getOrNull()
        }

        val capabilities = activeCapabilities()
        // Association is judged from the transport of the live network rather than from WifiInfo, whose
        // interesting fields are redacted without location from API 29 onwards.
        val associated = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        return RadioObservation(
            kind = RadioKind.WIFI,
            present = true,
            stateReadable = enabled != null,
            enabled = enabled == true,
            associated = associated,
            internetWorking = associated && capabilities.validated(),
            signalDbm = if (associated) capabilities?.let(::signalDbm) else null,
        )
    }

    private fun activeCapabilities(): NetworkCapabilities? = runCatching {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return null
        connectivity.getNetworkCapabilities(connectivity.activeNetwork ?: return null)
    }.onFailure { Diagnostics.warn(TAG, "could not read the active network", it) }.getOrNull()

    private fun NetworkCapabilities?.validated(): Boolean =
        this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true

    /**
     * Signal strength in dBm, or null.
     *
     * `getSignalStrength()` arrived in API 29 and this app supports 26, so on older phones there is simply no
     * answer — which is reported as no answer rather than as a zero that would read as terrible reception.
     */
    private fun signalDbm(capabilities: NetworkCapabilities): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val raw = runCatching { capabilities.signalStrength }.getOrNull() ?: return null
        // SIGNAL_STRENGTH_UNSPECIFIED is Int.MIN_VALUE. The range check also drops the placeholder values
        // some phones return, since a Wi-Fi reading outside this band is not a reading.
        return raw.takeIf { it in PLAUSIBLE_DBM }
    }

    // --------------------------------------------------------------------------------------- Bluetooth

    private fun readBluetooth(): RadioObservation {
        val present = hasFeature("android.hardware.bluetooth")
        if (!present) {
            return RadioObservation(kind = RadioKind.BLUETOOTH, present = false)
        }

        val adapter: BluetoothAdapter? = runCatching {
            context.applicationContext.getSystemService(BluetoothManager::class.java)?.adapter
        }.onFailure { Diagnostics.warn(TAG, "no BluetoothManager", it) }.getOrNull()

        if (adapter == null) {
            // The feature is declared but the adapter is missing, which is the platform contradicting
            // itself. Not readable rather than not present: the app will not claim a phone has no
            // Bluetooth chip on this evidence.
            return RadioObservation(kind = RadioKind.BLUETOOTH, present = true, stateReadable = false)
        }

        // isEnabled() carries @RequiresNoPermission from API 31 and needs only the legacy normal BLUETOOTH
        // permission below it, but it is wrapped anyway: a SecurityException here has to become "could not
        // read" rather than a crash or, worse, a reported fault.
        val enabled = runCatching { adapter.isEnabled }
            .onFailure { Diagnostics.warn(TAG, "could not read the Bluetooth state", it) }
            .getOrNull()

        return RadioObservation(
            kind = RadioKind.BLUETOOTH,
            present = true,
            stateReadable = enabled != null,
            enabled = enabled == true,
        )
    }

    // ------------------------------------------------------------------------------------------- shared

    private fun hasFeature(name: String): Boolean =
        runCatching { context.packageManager.hasSystemFeature(name) }
            .onFailure { Diagnostics.warn(TAG, "could not query feature $name", it) }
            .getOrDefault(false)

    /**
     * Where to send the buyer to flip a radio on.
     *
     * The Wi-Fi settings *panel* from API 29 is preferred because it slides over the app rather than
     * replacing it, so the buyer keeps their place in the test. Everything else falls back to the full
     * settings screen.
     */
    fun settingsIntent(kind: RadioKind): Intent {
        val intent = when (kind) {
            RadioKind.WIFI ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }
            RadioKind.BLUETOOTH -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        return intent
    }

    /** False when no activity would handle [settingsIntent], so the screen can hide a dead button. */
    fun canOpenSettings(kind: RadioKind): Boolean = runCatching {
        settingsIntent(kind).resolveActivity(context.packageManager) != null
    }.getOrDefault(false)

    private companion object {
        const val TAG = "RadioProbe"

        /** Wi-Fi RSSI lives between roughly -100 and -20 dBm; outside that it is a placeholder. */
        val PLAUSIBLE_DBM = -120..-10
    }
}
