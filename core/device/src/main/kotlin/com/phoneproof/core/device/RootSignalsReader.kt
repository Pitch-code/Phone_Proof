package com.phoneproof.core.device

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.phoneproof.checks.device.RootSignals
import com.phoneproof.core.diagnostics.DiagnosticsRecorder
import java.io.File

/**
 * Looks for the traces root leaves behind.
 *
 * Three independent probes, because each is individually defeatable: root managers hide from package
 * queries, `su` can be renamed, and neither says anything about the bootloader. Verified-boot state
 * is the hardest to fake from userspace, so it acts as the tie-breaker.
 *
 * Nothing here attempts to *gain* root or run a privileged command. It reads file existence, asks
 * the package manager about a short declared list, and reads one public system property.
 */
class RootSignalsReader(
    private val context: Context,
    private val diagnostics: DiagnosticsRecorder? = null,
) {

    fun read(): RootSignals {
        val su = runCatching { SU_PATHS.filter { File(it).exists() } }
            .onFailure { diagnostics?.info(TAG, "su path scan blocked") }

        val managers = runCatching { installedRootManagers() }
            .onFailure { diagnostics?.info(TAG, "root manager query blocked") }

        val bootState = runCatching { systemProperty(VERIFIED_BOOT_STATE) }
            .onFailure { diagnostics?.info(TAG, "verifiedbootstate unreadable") }
            .getOrNull()

        // Only a total failure counts as unreadable. A blocked probe alongside a working one still
        // yields a usable answer, and reporting "can't tell" then would throw away real evidence.
        val readable = su.isSuccess || managers.isSuccess || bootState != null

        val signals = RootSignals(
            suBinaryPaths = su.getOrNull().orEmpty(),
            rootManagerPackages = managers.getOrNull().orEmpty(),
            verifiedBootState = bootState,
            testKeysBuild = runCatching { Build.TAGS?.contains("test-keys") == true }
                .getOrDefault(false),
            readable = readable,
        )

        diagnostics?.info(
            TAG,
            "root: su=${signals.suBinaryPaths.size} managers=${signals.rootManagerPackages.size} " +
                "vbstate=${signals.verifiedBootState ?: "?"} testKeys=${signals.testKeysBuild}",
        )
        return signals
    }

    private fun installedRootManagers(): List<String> {
        val packageManager = context.packageManager
        return ROOT_MANAGER_PACKAGES.filter { packageName ->
            runCatching {
                packageManager.getPackageInfo(packageName, 0)
                true
            }.getOrDefault(false)
        }
    }

    /**
     * Reads a system property with `getprop`.
     *
     * Reflection into `SystemProperties` is the usual shortcut, but it is a non-SDK interface and is
     * blocked on modern Android, so it would silently return nothing on exactly the devices this
     * needs to work on. `getprop` is an ordinary read-only binary shipped with the platform.
     */
    private fun systemProperty(key: String): String? {
        val process = ProcessBuilder("getprop", key)
            .redirectErrorStream(true)
            .start()
        return try {
            val value = process.inputStream.bufferedReader().use { it.readLine() }?.trim()
            value?.takeIf { it.isNotEmpty() }
        } finally {
            process.destroy()
        }
    }

    private companion object {
        const val TAG = "RootSignalsReader"
        const val VERIFIED_BOOT_STATE = "ro.boot.verifiedbootstate"

        /** The standard locations a superuser binary is installed to. */
        val SU_PATHS = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/system/sbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/vendor/bin/su",
            "/odm/bin/su",
            "/data/local/su",
            "/data/local/bin/su",
            "/data/local/xbin/su",
            "/system/bin/failsafe/su",
            "/system/sd/xbin/su",
        )

        /**
         * Declared in the manifest's `<queries>` so these can be seen without
         * `QUERY_ALL_PACKAGES`. A short, specific list rather than blanket visibility.
         */
        val ROOT_MANAGER_PACKAGES = listOf(
            "com.topjohnwu.magisk",
            "io.github.huskydg.magisk",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "com.koushikdutta.superuser",
            "me.weishu.kernelsu",
        )
    }
}
