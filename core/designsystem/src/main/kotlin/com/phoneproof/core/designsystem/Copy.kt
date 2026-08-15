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


/**
 * The scan allowance running out, worded so it survives the allowance changing.
 *
 * This used to read "You have used **both** free scans", with the number two written into the English
 * while the number itself came from `Entitlement.FREE_SCAN_LIMIT`. Raise the trial to three and the
 * title keeps saying "both" — the app would be stating a limit it is not enforcing, on the screen
 * where a buyer is being asked to pay. The same word was in the explanation ("and both are done") and
 * in Settings ("Active — both scans used").
 *
 * Functions taking the limit rather than constants, because these are the only two strings in the app
 * that have to agree with a number. The limit is not read from `Entitlement` here: `core:preferences`
 * depends on this module, so this module cannot depend on it back. The caller passes it in, which has
 * the useful side effect that `LockedFeatureScreenshotTest` can render the two-scan case while still
 * using the real wording — it previously retyped this copy with a literal `2`, which is exactly how
 * the rendered paywall and the shipped paywall drift apart.
 */
fun scanAllowanceUsedUpTitle(scanLimit: Int): String = "You have used all $scanLimit free scans"

/** @see scanAllowanceUsedUpTitle */
fun scanAllowanceUsedUpExplanation(scanLimit: Int): String =
    "The free trial covers $scanLimit full scans of a phone, and they are all used. Nothing is " +
        "wrong with this phone or with the app — the trial has simply ended."

/** What paying changes, for the scan allowance. No number in it, so it needs no argument. */
const val SCAN_ALLOWANCE_UNLOCK: String =
    "Scan as many phones as you like, keep every report instead of the last two, save a report as a " +
        "PDF, and compare two phones side by side."
