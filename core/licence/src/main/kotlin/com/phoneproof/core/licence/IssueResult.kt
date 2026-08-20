package com.phoneproof.core.licence

/**
 * What happened when a Play purchase was exchanged for a pass code.
 *
 * This runs on the buyer's **own** phone, straight after they paid. That makes the failure cases unusually
 * serious: money has already changed hands, and anything other than a code in their hands feels like theft
 * even when it is a five-second network blip.
 *
 * So no case here is fatal. [Deferred] exists because the correct answer to "we could not reach the server
 * just now" is *not* an apology — it is "your purchase is safe, ask again", which the app can do
 * automatically because `/issue` is idempotent: the same receipt always returns the same code.
 */
sealed interface IssueResult {

    /**
     * The code, ready to be shown and written down.
     *
     * [reissued] is true when the server had already minted this code for this receipt — which is the normal
     * answer to a retry, not an error, and means the buyer has not been charged twice.
     */
    data class Issued(
        val code: String,
        val passes: Int,
        val reissued: Boolean,
    ) : IssueResult

    /**
     * The server could not be reached, or answered with something unusable.
     *
     * Named for what the buyer should do rather than what went wrong, because the purchase is already
     * complete and safe. Retrying with the same receipt returns the same code.
     */
    data object Deferred : IssueResult

    /**
     * Google's signature over the purchase did not verify.
     *
     * Distinct from [Deferred] because retrying cannot help. It should be impossible for a real purchase, so
     * reaching it means either tampering or a misconfigured licensing key — and the buyer must not be left
     * retrying forever against something that will never succeed.
     */
    data object Rejected : IssueResult
}
