package com.phoneproof.core.device

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageManager
import com.phoneproof.checks.emilock.AdminApp
import com.phoneproof.checks.emilock.DeviceAdminSnapshot
import com.phoneproof.core.diagnostics.DiagnosticsRecorder

/**
 * Asks the platform which apps hold administrative control over this device.
 *
 * Every call is defensive. OEM builds differ in what they expose here, and a phone that has been
 * tampered with is exactly the phone most likely to behave oddly — which is also exactly the phone
 * this check matters most on. A thrown exception must never look like a clean result, so any failure
 * produces `queryFailed = true` and the check reports "can't tell" rather than "nothing found".
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

        val active = runCatching { dpm.activeAdmins }
            .onFailure { diagnostics?.error(TAG, "activeAdmins threw", it) }
            .getOrNull()

        if (active == null) {
            return DeviceAdminSnapshot(queryFailed = true)
        }

        val packageManager = context.packageManager
        val admins = active
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

        diagnostics?.info(TAG, "found ${admins.size} device admin(s)")
        return DeviceAdminSnapshot(admins = admins)
    }

    /**
     * Returns null rather than a guess when the label cannot be read.
     *
     * On Android 11+ an app cannot see another package's details without declaring visibility for
     * it, so this legitimately fails most of the time. The evaluator falls back to showing the
     * package name, which is less friendly but true.
     */
    private fun resolveLabel(packageManager: PackageManager, packageName: String): String? =
        runCatching {
            packageManager.getApplicationInfo(packageName, 0)
                .loadLabel(packageManager)
                .toString()
                .takeIf { it.isNotBlank() && it != packageName }
        }.getOrNull()

    private companion object {
        const val TAG = "DeviceAdminInspector"
    }
}
