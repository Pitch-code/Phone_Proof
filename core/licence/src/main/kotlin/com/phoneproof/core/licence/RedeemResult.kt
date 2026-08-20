package com.phoneproof.core.licence

import com.phoneproof.core.preferences.passes.InspectionPass

/**
 * What happened when a code was redeemed.
 *
 * Every case exists because the screen has to say something different, and the differences matter more here
 * than almost anywhere else in the app: the person reading it is standing in front of a seller who wants
 * their phone back. "It didn't work" is not an acceptable answer when the real answer is "you mistyped one
 * character" or "you have no signal, move to the door".
 *
 * A sealed type rather than a nullable pass plus an error string, so that adding a case forces every screen
 * to decide what to do about it rather than falling into a generic branch.
 */
sealed interface RedeemResult {

    /** The pass is live on this phone. */
    data class Granted(
        val pass: InspectionPass,
        val passesLeft: Int,
        /**
         * True when this phone already had a running pass under this code.
         *
         * Worth telling the buyer, because it is the difference between "that cost you one" and "that cost
         * you nothing" — and being charged for reopening an app you closed by accident is the kind of small
         * unfairness people remember.
         */
        val alreadyActive: Boolean,
    ) : RedeemResult

    /**
     * Not a code this system could have issued. **Decided locally**, before any request.
     *
     * Separate from [Unknown] because the advice differs: this one means look at what you typed, and it is
     * answered instantly without waiting on a network that might also be failing.
     */
    data object Malformed : RedeemResult

    /** Well formed, but the server has never heard of it. A code from somewhere else, or a real typo. */
    data object Unknown : RedeemResult

    /** The pack is spent. Not an error — the buyer used what they bought. */
    data class Exhausted(val passesLeft: Int = 0) : RedeemResult

    /**
     * The server could not be reached.
     *
     * The expected failure of this whole design, and the one worth wording carefully: a shop with no signal.
     * It is emphatically not the buyer's mistake and the screen must not imply the code is bad.
     */
    data object Offline : RedeemResult

    /** Reached, but it answered with something unusable. Nothing the buyer can act on. */
    data object ServerProblem : RedeemResult
}
