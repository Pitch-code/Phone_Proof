package com.phoneproof.core.preferences.passes

import com.phoneproof.core.preferences.Entitlement

/**
 * A pass granted to **this** phone by redeeming a code, and the moment it stops.
 *
 * The expiry is the product, not a limitation of it. A buyer is being asked to type a paid code onto a
 * stranger's handset, and the only reason that is reasonable is that nothing is left behind afterwards.
 * Making a pass permanent would be a small convenience bought with the promise the whole model rests on.
 *
 * Stored as an absolute instant rather than a duration plus a start, so nothing has to be ticking for it to
 * expire. The app can be killed, the phone rebooted, a week can pass — the answer is still a comparison.
 */
data class InspectionPass(
    /** The code that granted this, canonical form. Kept so the server can be told which pack was used. */
    val code: String,
    val expiresAtEpochMs: Long,
) {
    fun isActiveAt(nowEpochMs: Long): Boolean = nowEpochMs < expiresAtEpochMs

    /**
     * Whole hours left, rounded up, or zero once it has gone.
     *
     * Rounded up because "0 hours left" beside a screen that still works reads as a bug, and a buyer glancing
     * at this wants to know whether they have time for one more phone.
     */
    fun hoursLeftAt(nowEpochMs: Long): Int {
        val remaining = expiresAtEpochMs - nowEpochMs
        if (remaining <= 0) return 0
        return ((remaining + HOUR_MILLIS - 1) / HOUR_MILLIS).toInt()
    }

    companion object {
        const val HOUR_MILLIS: Long = 60L * 60L * 1000L

        /**
         * How long one pass lasts.
         *
         * A day, so that an inspection interrupted and resumed — the seller takes a call, they agree to meet
         * again after lunch — does not cost a second pass. Short enough that the phone does not stay unlocked
         * in a way the buyer would be uneasy about having caused.
         */
        const val DURATION_MILLIS: Long = 24L * HOUR_MILLIS
    }
}

/**
 * What this install can do, given what its Google account owns and whether a pass is running.
 *
 * The two routes to Premium are deliberately independent. An account entitlement is the buyer's **own**
 * phone; a pass is **someone else's**, for a day. Neither can be derived from the other and both are real, so
 * this function is the single place they meet — every screen asks it rather than reasoning about passes
 * itself, which is how "unlocked here but locked there" bugs are avoided.
 *
 * A lapsed pass returns exactly what no pass returns. There is no partial state and no grace: the moment it
 * expires the phone is as it was, which is the promise made to whoever owns it.
 */
fun effectiveEntitlement(
    accountEntitlement: Entitlement,
    pass: InspectionPass?,
    nowEpochMs: Long,
): Entitlement = when {
    // A paid account is never downgraded by the absence of a pass, or by holding an expired one.
    accountEntitlement != Entitlement.FREE -> accountEntitlement
    pass?.isActiveAt(nowEpochMs) == true -> Entitlement.PREMIUM
    else -> Entitlement.FREE
}
