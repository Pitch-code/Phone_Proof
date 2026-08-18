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

    /** Navigation routes of the checks the free trial leaves out. */
    val routes: Set<String> = setOf(
        "multi-touch",
        "radios",
        "vibration",
    )

    /** Whether [route] needs a paid tier. */
    fun requiresPremium(route: String): Boolean = route in routes

    /**
     * Whether [entitlement] may open [route].
     *
     * Everything else in the app is reachable on the free trial, subject only to the scan allowance.
     */
    fun isUnlocked(route: String, entitlement: Entitlement): Boolean =
        !requiresPremium(route) || entitlement.hasPremiumExtras
}
