package com.phoneproof.app

import android.app.Application

/**
 * No dependency-injection framework, no analytics, no crash reporter, no ad SDK yet.
 *
 * Each of those arrives with the feature that needs it. The ad SDK in particular changes the
 * app's data-safety declaration and forces the privacy wording on screen to soften, so it lands
 * with the monetisation work and not before.
 */
class PhoneProofApplication : Application()
