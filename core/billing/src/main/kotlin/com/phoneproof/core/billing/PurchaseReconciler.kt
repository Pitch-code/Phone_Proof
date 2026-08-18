package com.phoneproof.core.billing

import com.phoneproof.core.preferences.Entitlement

/** What Play says has happened to a purchase. Mirrors the three states `Purchase.PurchaseState` has. */
enum class PurchaseState {
    /** Paid for. The only state that entitles anyone to anything. */
    PURCHASED,

    /**
     * Started and not finished. Common in India, where UPI and net-banking payments routinely sit here
     * for minutes, and cash-based methods for far longer.
     */
    PENDING,

    /** Play does not know. Treated exactly like [PENDING]: no entitlement either way. */
    UNSPECIFIED,
}

/**
 * One purchase, reduced to the four things any decision here depends on.
 *
 * A deliberately plain type rather than Play's `Purchase`, so every rule below can be tested without a
 * device, a Play account or a Robolectric shadow.
 */
data class PurchaseRecord(
    val productIds: List<String>,
    val state: PurchaseState,
    /**
     * Whether Play has been told the app has honoured this purchase.
     *
     * **A purchase left unacknowledged for three days is automatically refunded by Play.** The user pays,
     * gets what they paid for, and then silently loses both — which is the single most common way billing
     * is got wrong.
     */
    val acknowledged: Boolean,
    val purchaseToken: String,
)

/**
 * Whether the app managed to ask Play at all.
 *
 * Two cases rather than a nullable list, because the difference decides whether a paying user keeps what
 * they paid for. See [PurchaseReconciler.reconcile].
 */
sealed interface PurchaseQuery {
    data class Succeeded(val purchases: List<PurchaseRecord>) : PurchaseQuery

    /** Not connected, offline, Play Store missing, or the query returned an error. */
    data class Failed(val reason: String) : PurchaseQuery
}

/**
 * What to do about it.
 *
 * @param entitlement the tier the purchases imply, or **null meaning "change nothing"**. The null is the
 *   whole reason this type exists; see [PurchaseReconciler.reconcile].
 * @param toAcknowledge purchases that must be acknowledged, or Play will refund them.
 * @param pendingProductIds purchases the user has started paying for and Play has not settled, so the
 *   screen can say so instead of looking broken or, worse, looking like the payment failed.
 */
data class Reconciliation(
    val entitlement: Entitlement?,
    val toAcknowledge: List<PurchaseRecord> = emptyList(),
    val pendingProductIds: List<String> = emptyList(),
)

/**
 * Turns what Play reports into what the app should do.
 *
 * Pure, and the only place these rules live. The Play interaction cannot be verified anywhere except on a
 * real device signed into a real account, so the rules that decide who gets what are kept where they can
 * be tested exhaustively for free.
 */
object PurchaseReconciler {

    /**
     * ## The rule that matters most: a failed query never takes anything away
     *
     * Entitlement is recomputed from the full list on every cold start, so a refund or a chargeback
     * correctly removes access. That same mechanism, applied to a *failed* query, would remove access
     * from a paying user because their train went into a tunnel.
     *
     * So [PurchaseQuery.Failed] yields a null entitlement, meaning "leave whatever is stored alone". It is
     * expressed in the return type rather than as a caution in a comment, because the failure mode is
     * silent, affects only paying users, and would arrive as a support complaint rather than a crash.
     *
     * It also protects the debug tier switcher: on a sideloaded build Play cannot answer, the query fails,
     * and a manually chosen tier survives instead of being reset a second later.
     *
     * ## And a successful query with nothing in it *does* take it away
     *
     * That is not the same case. Play answered, and the answer was that this account owns nothing.
     */
    fun reconcile(query: PurchaseQuery): Reconciliation = when (query) {
        is PurchaseQuery.Failed -> Reconciliation(entitlement = null)

        is PurchaseQuery.Succeeded -> {
            val owned = query.purchases.filter { it.state == PurchaseState.PURCHASED }

            Reconciliation(
                // Highest tier wins. Someone who has bought both should not be demoted by whichever
                // purchase happens to come back last.
                entitlement = owned
                    .flatMap { it.productIds }
                    .mapNotNull(BillingProducts::entitlementFor)
                    .maxByOrNull { it.rank }
                    ?: Entitlement.FREE,
                // Only a completed purchase can be acknowledged, and only once.
                toAcknowledge = owned.filter { !it.acknowledged },
                pendingProductIds = query.purchases
                    .filter { it.state != PurchaseState.PURCHASED }
                    .flatMap { it.productIds }
                    .filter { BillingProducts.entitlementFor(it) != null }
                    .distinct(),
            )
        }
    }

    /** Ordering for "highest tier wins". Not on [Entitlement] itself, which has no opinion about price. */
    private val Entitlement.rank: Int
        get() = when (this) {
            Entitlement.FREE -> 0
            Entitlement.PREMIUM -> 1
            Entitlement.SHOP -> 2
        }
}
