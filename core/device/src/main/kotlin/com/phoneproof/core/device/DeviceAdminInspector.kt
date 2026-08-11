package com.phoneproof.core.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.phoneproof.checks.emilock.AdminApp
import com.phoneproof.checks.emilock.DeviceAdminSnapshot
import com.phoneproof.core.diagnostics.DiagnosticsRecorder

/**
 * Asks the platform which apps hold administrative control over this device.
 *
 * Every call is defensive. OEM builds differ in what they expose, and a phone that has been tampered
 * with is exactly the phone most likely to behave oddly — which is also the phone this check matters
 * most on. A thrown exception must never look like a clean result.
 *
 * The critical subtlety, learned from a real device rather than from the documentation:
 * [DevicePolicyManager.getActiveAdmins] returns **null when there are no active administrators**.
 * That is a clean phone, not a failed query. Conflating the two made a healthy handset report
 * "can't tell", which is the single most useless thing this check could say to someone holding cash.
 */
class DeviceAdminInspector(
    private val context: Context,
    private val diagnostics: DiagnosticsRecorder? = null,
) {

    fun snapshot(): DeviceAdminSnapshot {
        val dpm = runCatching {
            context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        }.getOrNull()

        if (dpm == null) {
            diagnostics?.warn(TAG, "DevicePolicyManager unavailable on this device")
            return DeviceAdminSnapshot(queryFailed = true)
        }

        val query = runCatching { dpm.activeAdmins }
        if (query.isFailure) {
            diagnostics?.error(TAG, "activeAdmins threw", query.exceptionOrNull())
            return DeviceAdminSnapshot(queryFailed = true)
        }

        // null here means "none registered", not "could not ask".
        val components: List<ComponentName> = query.getOrNull() ?: emptyList()

        if (components.isEmpty()) {
            // Logged explicitly: this is the most common real-world outcome, and when it was silent
            // the diagnostics report came back with a single startup line and no explanation at all.
            diagnostics?.info(TAG, "no device admins registered — clean")
            return DeviceAdminSnapshot.from(emptyList())
        }

        val packageManager = context.packageManager
        val admins = components
            .map { it.packageName }
            .distinct()
            .map { packageName ->
                AdminApp(
                    packageName = packageName,
                    label = resolveLabel(packageManager, packageName),
                    isDeviceOwner = runCatching { dpm.isDeviceOwnerApp(packageName) }
                        .getOrDefault(false),
                    isProfileOwner = runCatching { dpm.isProfileOwnerApp(packageName) }
                        .getOrDefault(false),
                )
            }

        diagnostics?.info(
            TAG,
            "found ${admins.size} device admin(s): " + admins.joinToString { it.packageName },
        )
        return DeviceAdminSnapshot.from(admins)
    }

    /**
     * Returns null rather than a guess when the label cannot be read.
     *
     * The app declares a `<queries>` entry for `DEVICE_ADMIN_ENABLED` so administrator apps are
     * visible to it, which is what turns `com.microsoft.office.outlook` into `Outlook` on screen.
     * Without that, Android 11+ package-visibility rules hide the details and the raw package id is
     * all a buyer sees.
     */
    private fun resolveLabel(packageManager: PackageManager, packageName: String): String? =
        runCatching {
            packageManager.getApplicationInfo(packageName, 0)
                .loadLabel(packageManager)
                .toString()
                .takeIf { it.isNotBlank() && it != packageName }
        }.onFailure {
            diagnostics?.info(TAG, "label not visible for $packageName")
        }.getOrNull()

    private companion object {
        const val TAG = "DeviceAdminInspector"
    }
}
