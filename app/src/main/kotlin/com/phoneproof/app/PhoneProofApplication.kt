package com.phoneproof.app

import android.app.Application
import android.os.Build
import com.phoneproof.core.diagnostics.Diagnostics

/**
 * No dependency-injection framework, no analytics, no crash reporter, no ad SDK yet. Each of those
 * arrives with the feature that needs it. The ad SDK in particular changes the app's data-safety
 * declaration and forces the on-screen privacy wording to soften, so it lands with the monetisation
 * work and not before.
 *
 * The one thing installed here is the diagnostics crash handler, and it goes in first — before
 * anything else can fail — because a tool for reporting that the app broke is worthless if it only
 * works while the app is healthy.
 */
class PhoneProofApplication : Application() {

    override fun onCreate() {
        Diagnostics.installCrashHandler()
        super.onCreate()

        Diagnostics.info(
            TAG,
            "start ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on " +
                "${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}",
        )
    }

    private companion object {
        const val TAG = "app"
    }
}
