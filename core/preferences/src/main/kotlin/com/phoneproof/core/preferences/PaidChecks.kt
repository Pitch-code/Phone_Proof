package com.phoneproof.core.preferences

/**
 * Which individual checks the free trial does not include.
 *
 * ## The rule these were chosen by
 *
 * **Lock what a buyer wants to see. Never lock what protects them.**
 *
 * A locked row has to create a real wish to pay, and the honest way to do that is completeness: a list
 * with three rows greyed out is an itch. The dishonest way is fear — withholding the test that would have
 * caught an expensive fault, so the buyer pays out of worry. That second one is not available here, and
 * not only for ethical reasons: if someone buys a phone with a dead earpiece because this app hid the
 * earpiece test behind ₹99, the app has done more harm than not existing, and `monetisation.md` already
 * forbids it — "no result the app has already measured is hidden behind a price".
 *
 * So the three below share one property: **a buyer can establish the same thing with their own hands in
 * under a minute.** Locking them withholds convenience and a satisfying number, not protection.
 *
 * ### Locked, and why each one is safe to lock
 *
 *  - **Fingers at once.** Pure curiosity — how many points the digitiser tracks is a spec, not a fault.
 *    Dead patches, which *are* a fault and are miserable to live with, are found by the touch-response
 *    test, which stays free. Nobody buys a bad phone for want of knowing it tracks eight fingers.
 *  - **Wi-Fi and Bluetooth.** Ten seconds with the two toggles tells a buyer what this screen tells them.
 *    It is a satisfying tick on the list and almost no protection.
 *  - **Vibration.** A finger on the back of the phone settles it, and the check's own advice says exactly
 *    that: "trust your fingers, not this number". Withholding a measurement whose own copy defers to the
 *    buyer's hand costs the buyer nothing.
 *
 * ### Free, and why each one must stay free
 *
 * Every one of these finds something expensive that a buyer **cannot** find by hand in a shop:
 *
 *  - **Charging** — a loose socket charges fine while you watch and gives up overnight. The app's
 *    flagship "you could not have caught this yourself".
 *  - **Remote lock** — a lender who can brick the phone after payment. The worst outcome in the app.
 *  - **IMEI** — whether it is stolen. Not a fault, a crime someone else committed.
 *  - **Touch response** — dead strips, found by sweeping every cell.
 *  - **Dead pixels and burn-in** — expensive, and invisible until the right colour is on screen.
 *  - **Microphone, earpiece and speaker** — a phone that cannot take a call is not a phone.
 *  - **Cameras and flashlight** — among the first three things anyone buys a phone for.
 *  - **Sensors** — a dead proximity sensor ends calls against a cheek; nobody checks that in a shop.
 *  - **Storage speed** — recycled flash is full size and terrible quality, and is undetectable by hand.
 *  - **Volume buttons** — self-testable by pressing them, so locking it would add an itch without
 *    withholding anything, which is pettiness rather than a business model.
 *
 * Routes rather than titles, so a copy change cannot silently unlock or lock a screen.
 */
object PaidChecks {

    /**
     * What each locked check is worth, and how to get the same answer without paying.
     *
     * The wording lives here, next to the reasoning it has to stay consistent with, rather than in the
     * navigation graph where it started. Three reasons. A paywall's prose is the part most likely to drift
     * away from the decision behind it, and the two are now impossible to read apart. The `doItYourself`
     * line is a *rule*, not a nicety — it is the property these three were chosen by — so it belongs
     * somewhere a test can insist every locked check has one. And the locked set is now derived from these
     * keys, which makes locking a check without saying what it costs the buyer unrepresentable rather than
     * merely discouraged.
     */
    private val copy: Map<String, PaidCheckCopy> = mapOf(
        "multi-touch" to PaidCheckCopy(
            title = "Fingers at once",
            whatItFinds = "This counts how many fingers the screen can follow at the same time, and " +
                "shows a numbered ring under each one.",
            doItYourself = "Dead patches on the screen are the fault that actually costs you, and the " +
                "touch test finds those — it stays free.",
        ),
        "radios" to PaidCheckCopy(
            title = "Wi-Fi and Bluetooth",
            whatItFinds = "This watches both radios and records, in the report, that they switched on " +
                "and joined a network.",
            doItYourself = "You can settle this in ten seconds yourself: open the phone's settings, " +
                "turn both on, and join any network.",
        ),
        "vibration" to PaidCheckCopy(
            title = "Vibration",
            whatItFinds = "This measures the buzz with the accelerometer and puts a number on it, so " +
                "nobody has to be asked whether they felt something.",
            doItYourself = "Put a finger on the back of the phone and set a one-minute alarm. This " +
                "check's own advice says to trust your fingers over its number anyway.",
        ),
    )

    /**
     * Navigation routes of the checks the free trial leaves out.
     *
     * Derived from [copy] rather than listed separately, so a locked check always has wording explaining
     * itself. Two lists would eventually disagree, and the way they would disagree is the bad way: a row
     * marked Premium opening onto a paywall with nothing written on it.
     */
    val routes: Set<String> = copy.keys

    /**
     * Routes the free trial also excludes, but whose paywall is drawn inside their own feature module.
     *
     * These predate the three above and are gated on `hasAdvisoryTools` rather than `hasPremiumExtras`:
     * they are advice, not measurement, which is a distinction worth keeping even though both flags
     * currently mean the same thing.
     *
     * They are listed here for one reason: **the row marker has to tell the truth about all of them.**
     * Marking three rows Premium teaches a buyer that an unmarked row is free, and "Claimed against
     * measured" was sitting in the same list, locked, unmarked — so the marker would have taught a rule the
     * app then broke. A surprise paywall is bad; a surprise paywall directly under a screen that has been
     * carefully labelling its paywalls is worse.
     */
    val advisoryRoutes: Set<String> = setOf(
        "claims",
        "guide",
    )

    /** What to say on the paywall for [route], or null if the trial includes it. */
    fun copyFor(route: String): PaidCheckCopy? = copy[route]

    /** Whether [route] needs a paid tier. */
    fun requiresPremium(route: String): Boolean = route in routes

    /**
     * Whether [route] should be marked as paid in a list of checks, for someone holding [entitlement].
     *
     * Covers both families, because a buyer reading the list does not know or care which module happens to
     * draw the paywall. Each is asked against the capability that actually gates it rather than against a
     * single "is paid" flag — those two capabilities coincide today, and writing that coincidence into the
     * code is how they would come apart without anyone noticing.
     */
    fun isLocked(route: String, entitlement: Entitlement): Boolean = when {
        route in routes -> !entitlement.hasPremiumExtras
        route in advisoryRoutes -> !entitlement.hasAdvisoryTools
        else -> false
    }

    /**
     * Whether [entitlement] may open [route].
     *
     * Everything else in the app is reachable on the free trial, subject only to the scan allowance.
     */
    fun isUnlocked(route: String, entitlement: Entitlement): Boolean =
        !requiresPremium(route) || entitlement.hasPremiumExtras
}

/**
 * The wording shown when a locked check is opened on the free trial.
 *
 * Both sentences are required by the type, because a paywall missing either one is a specific kind of bad.
 * Without [whatItFinds] it asks for money without saying what for. Without [doItYourself] it stops being a
 * limit and becomes a hostage.
 */
data class PaidCheckCopy(
    /** The check's name, as the buyer saw it on the list they tapped. */
    val title: String,
    /** What this check measures, in the buyer's terms — they are deciding whether to pay for it. */
    val whatItFinds: String,
    /**
     * How to get the same answer without paying.
     *
     * Every locked check has one; that is the rule they were chosen by. Printing it costs a sale
     * occasionally, and is the whole difference between a limit and a hostage.
     */
    val doItYourself: String,
) {
    /**
     * The two sentences as the paywall shows them.
     *
     * Assembled here rather than at the call site so the screen, and any test reading it, are looking at
     * one string rather than two copies of an idea about how it is put together.
     */
    val explanation: String
        get() = "$whatItFinds\n\nThe free trial leaves this one out. $doItYourself"

    /**
     * What paying actually buys, which has to be more than the screen being unblocked.
     *
     * Shared across all three, because it is a property of the tier rather than of the check.
     */
    val whatUnlockingGives: String
        get() = "Premium unlocks this and the other two measured checks the trial leaves out, keeps " +
            "every report instead of the last two, and adds PDF export and side-by-side comparison."
}
