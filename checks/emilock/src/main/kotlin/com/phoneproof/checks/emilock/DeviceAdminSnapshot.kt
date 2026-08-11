package com.phoneproof.checks.emilock

/**
 * One app that holds administrative control over the device.
 *
 * @param label the human-readable app name, or null when it could not be resolved. On Android 11+
 *   an app cannot read another package's details without declaring visibility for it, so a null
 *   label is normal and expected — the package name is reported instead. Guessing a friendly name
 *   would be worse than showing the raw identifier.
 */
data class AdminApp(
    val packageName: String,
    val label: String? = null,
    val isDeviceOwner: Boolean = false,
    val isProfileOwner: Boolean = false,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }

    /** What to show a buyer: the app name if known, otherwise the package. */
    val displayName: String get() = label?.takeIf { it.isNotBlank() } ?: packageName
}

/**
 * What the device reported about administrative control.
 *
 * @param queryFailed true when the platform could not be interrogated at all. That is reported as
 *   `CAN'T TELL` rather than as a clean result, because "we could not check" and "nothing found"
 *   must never look the same to someone about to hand over money.
 */
data class DeviceAdminSnapshot(
    val admins: List<AdminApp> = emptyList(),
    val queryFailed: Boolean = false,
) {
    companion object {
        /**
         * Builds a snapshot from a platform result where **null means the call failed** and an
         * empty list means the platform answered and found nothing.
         *
         * This distinction exists as its own named function because getting it backwards is
         * exactly what happened on a real device: `DevicePolicyManager.getActiveAdmins()` returns
         * null when there are no administrators, that null was treated as a failed query, and a
         * perfectly clean phone reported "can't tell" instead of passing. A user checking a phone
         * they were about to buy got no answer at all.
         */
        fun from(admins: List<AdminApp>?): DeviceAdminSnapshot =
            if (admins == null) {
                DeviceAdminSnapshot(queryFailed = true)
            } else {
                DeviceAdminSnapshot(admins = admins)
            }
    }

    val deviceOwners: List<AdminApp> get() = admins.filter { it.isDeviceOwner }
    val profileOwners: List<AdminApp> get() = admins.filter { it.isProfileOwner }

    /** Admins that hold neither ownership role — a plain device-administrator registration. */
    val plainAdmins: List<AdminApp>
        get() = admins.filter { !it.isDeviceOwner && !it.isProfileOwner }
}
