package com.phoneproof.core.designsystem

/**
 * Copy that more than one screen has to say identically.
 *
 * Not a general string table. Nothing belongs here because it is user-visible — almost all copy in
 * this app is deliberately inline next to the layout it is written for, where it can be read in
 * context. A string earns a place in this file only when **several screens must agree** and a
 * disagreement between them would be a bug the compiler cannot catch.
 *
 * Both entries below qualify because both had already drifted.
 */

/**
 * The name of the manual-checks feature, wherever it is named.
 *
 * Four screens name this feature: Home, its own heading, the paywall guarding it, and the tier
 * comparison in Settings. As four hand-typed literals they had already drifted — two other screens
 * referred to it in prose as "the by-hand guide", a name it never had on screen — and a feature
 * called something different in each place reads as several features, which on a paywall reads as
 * a bait and switch.
 *
 * Quote it when it appears inside a sentence rather than as a heading; it is a phrase, not a noun.
 */
const val MANUAL_CHECKS_TITLE: String = "Eight things only you can check"

/**
 * Why an advisory screen is locked, in the words both advisory screens must use.
 *
 * This says the free trial is the reason, which is the honest answer: the gate is
 * `Entitlement.hasAdvisoryTools`, which is purely the tier. It is **not** the scan allowance, and
 * this sentence must never be rewritten to say the scans have run out — a buyer meets this screen
 * on a fresh install with both scans untouched, and a paywall that misstates its own cause is the
 * one thing a trust-focused app cannot afford.
 *
 * Shared rather than written twice because [com.phoneproof.core.designsystem.component.LockedFeature]
 * exists to make every locked case sound the same, and two hand-typed copies of the reason defeat
 * the component that was built to prevent exactly that.
 */
const val ADVISORY_TRIAL_EXCLUSION: String =
    "The free trial leaves this screen out. Nothing has run out and nothing is wrong with this " +
        "phone — the trial measures everything the app can measure, and this screen gives advice " +
        "rather than taking a measurement."
