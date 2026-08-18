package com.phoneproof.core.billing

import com.phoneproof.core.diagnostics.Diagnostics
import com.phoneproof.core.preferences.SettingsRepository

/**
 * Brings the stored entitlement back in line with what Play says, and acknowledges anything owing.
 *
 * The only place in the app permitted to grant a paid tier — `PaidTierWritesTest` enforces that, so a
 * screen cannot quietly hand out `PREMIUM` on its own.
 *
 * ## Run this on every cold start
 *
 * Not only after a purchase. Three separate things depend on it:
 *
 *  - a purchase made on another device, or restored after a factory reset, appears without the user doing
 *    anything — no login, no restore button, no phone number;
 *  - a refund or a chargeback removes access, because the tier is recomputed rather than remembered;
 *  - an acknowledgement missed last time gets retried, which matters because Play refunds an
 *    unacknowledged purchase after three days and the app may well have been killed before it managed to.
 *
 * @return the pending product ids, so a screen can explain that a payment is still settling rather than
 *   showing the free tier to somebody who has just paid.
 */
class EntitlementSync(
    private val billing: PlayBilling,
    private val repository: SettingsRepository,
) {

    suspend fun sync(): List<String> {
        val query = billing.queryPurchases()
        val reconciliation = PurchaseReconciler.reconcile(query)

        // Null means the query failed, and a failed query must never take away what someone paid for.
        // Doing nothing is the whole point; see PurchaseReconciler.reconcile.
        reconciliation.entitlement?.let { entitlement ->
            repository.setEntitlement(entitlement)
            Diagnostics.info(TAG, "entitlement reconciled to ${entitlement.name}")
        } ?: Diagnostics.info(TAG, "billing did not answer, leaving the stored entitlement alone")

        // Acknowledged after the tier is stored, not before: if acknowledgement fails the user keeps the
        // feature they paid for and this is retried on the next launch, which is the right way round.
        reconciliation.toAcknowledge.forEach { purchase ->
            val ok = billing.acknowledge(purchase)
            Diagnostics.info(TAG, "acknowledge ${purchase.productIds}: $ok")
        }

        if (reconciliation.pendingProductIds.isNotEmpty()) {
            Diagnostics.info(TAG, "pending: ${reconciliation.pendingProductIds}")
        }
        return reconciliation.pendingProductIds
    }

    private companion object {
        const val TAG = "EntitlementSync"
    }
}
