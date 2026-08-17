package com.phoneproof.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.phoneproof.core.diagnostics.Diagnostics
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Play Billing plumbing, kept as thin and as stupid as it can be.
 *
 * Every decision — who is entitled to what, what needs acknowledging, what a failure may change — lives in
 * [PurchaseReconciler], which is plain Kotlin and tested exhaustively. This class only fetches and reports.
 *
 * That split is not tidiness. **None of the code in this file can be tested anywhere in this project.** A
 * purchase requires a real device, signed into a real Google account, running a build distributed through a
 * Play track; a sideloaded debug APK cannot complete one, and the debug build even has a different
 * application id. So the untestable part is made as small and as decision-free as possible, and everything
 * that can be reasoned about is moved out of it.
 *
 * ## Two things that are easy to get wrong and expensive to get wrong
 *
 * **Pending purchases must be enabled explicitly.** Without `enablePendingPurchases`, Play refuses to
 * complete the connection at all — and pending purchases are not an edge case here: UPI and net-banking
 * are ordinary payment methods in India and routinely sit pending for minutes.
 *
 * **A purchase must be acknowledged within three days.** Play refunds it automatically otherwise, so the
 * user pays, gets the feature, and then quietly loses both. Acknowledgement is therefore attempted on
 * every reconcile, not only immediately after a purchase, because the app may have been killed in between.
 */
class PlayBilling(context: Context) {

    private val _connected = MutableStateFlow(false)

    /** Whether Play is reachable. False on a sideloaded build, on a device with no Play Store, or offline. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _purchaseUpdates = MutableStateFlow<PurchaseQuery?>(null)

    /**
     * Purchases as Play reports them changing, including the moment a pending payment settles.
     *
     * Null until something happens. Callers reconcile this the same way they reconcile a cold-start query,
     * so a purchase completing and a purchase discovered at launch take exactly the same code path.
     */
    val purchaseUpdates: StateFlow<PurchaseQuery?> = _purchaseUpdates.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .setListener { result, purchases ->
            // Fires for purchases made in this session, and for pending ones that later settle.
            _purchaseUpdates.value = if (result.ok) {
                PurchaseQuery.Succeeded(purchases.orEmpty().map(Purchase::toRecord))
            } else {
                PurchaseQuery.Failed("purchase update: ${result.debugMessage.ifBlank { result.responseCode.toString() }}")
            }
        }
        .build()

    /** Connects if not already connected. Safe to call repeatedly; Play is asked once. */
    suspend fun connect(): Boolean {
        if (client.isReady) {
            _connected.value = true
            return true
        }
        return suspendCoroutine { continuation ->
            runCatching {
                client.startConnection(object : BillingClientStateListener {
                    private var resumed = false

                    override fun onBillingSetupFinished(result: BillingResult) {
                        // Guarded: Play has been observed calling this more than once, and resuming a
                        // continuation twice is a crash.
                        if (resumed) return
                        resumed = true
                        _connected.value = result.ok
                        if (!result.ok) {
                            Diagnostics.warn(TAG, "billing unavailable: ${result.debugMessage}")
                        }
                        continuation.resume(result.ok)
                    }

                    override fun onBillingServiceDisconnected() {
                        // Not a failure to report: the next call reconnects. Marking it disconnected is
                        // what stops the UI offering a button that cannot work.
                        _connected.value = false
                    }
                })
            }.onFailure {
                Diagnostics.error(TAG, "could not start the billing connection", it)
                _connected.value = false
                continuation.resume(false)
            }
        }
    }

    /**
     * Everything this account owns, one-time products and subscriptions alike.
     *
     * Both are queried because they are separate calls in Play's API, and a purchase queried under the
     * wrong type is simply never found.
     */
    suspend fun queryPurchases(): PurchaseQuery {
        if (!connect()) return PurchaseQuery.Failed("not connected")

        val records = mutableListOf<PurchaseRecord>()
        for (type in listOf(BillingClient.ProductType.INAPP, BillingClient.ProductType.SUBS)) {
            val result = runCatching {
                client.queryPurchasesAsync(
                    QueryPurchasesParams.newBuilder().setProductType(type).build(),
                )
            }.getOrNull() ?: return PurchaseQuery.Failed("query threw for $type")

            if (!result.billingResult.ok) {
                // A partial answer is worse than no answer: it would look like a successful query
                // returning nothing, which downgrades a paying user.
                return PurchaseQuery.Failed("$type query: ${result.billingResult.debugMessage}")
            }
            records += result.purchasesList.map(Purchase::toRecord)
        }
        return PurchaseQuery.Succeeded(records)
    }

    /** Tells Play the purchase has been honoured. Failing to do this within three days refunds it. */
    suspend fun acknowledge(purchase: PurchaseRecord): Boolean {
        if (!connect()) return false
        val result = runCatching {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build(),
            )
        }.onFailure { Diagnostics.error(TAG, "acknowledgement threw", it) }.getOrNull()

        val ok = result?.ok == true
        if (!ok) Diagnostics.warn(TAG, "could not acknowledge ${purchase.productIds}")
        return ok
    }

    /** Prices as Play states them, which is the only authoritative source: they vary by country and tax. */
    suspend fun priceOf(productId: String): String? {
        if (!connect()) return null
        val type = if (BillingProducts.isSubscription(productId)) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
        val details = runCatching {
            client.queryProductDetails(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(type)
                                .build(),
                        ),
                    )
                    .build(),
            )
        }.getOrNull() ?: return null

        val product = details.productDetailsList?.firstOrNull() ?: return null
        return product.displayPrice()
    }

    /** Opens Play's checkout sheet. The result arrives on [purchaseUpdates], not from here. */
    suspend fun launchPurchase(activity: Activity, productId: String): Boolean {
        if (!connect()) return false
        val type = if (BillingProducts.isSubscription(productId)) {
            BillingClient.ProductType.SUBS
        } else {
            BillingClient.ProductType.INAPP
        }
        val details = runCatching {
            client.queryProductDetails(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(productId)
                                .setProductType(type)
                                .build(),
                        ),
                    )
                    .build(),
            )
        }.getOrNull()

        val product = details?.productDetailsList?.firstOrNull() ?: run {
            Diagnostics.warn(TAG, "no product details for $productId")
            return false
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .apply {
                            // Subscriptions require an offer token; one-time products must not have one.
                            product.subscriptionOfferDetails
                                ?.firstOrNull()
                                ?.offerToken
                                ?.let { setOfferToken(it) }
                        }
                        .build(),
                ),
            )
            .build()

        val result = runCatching { client.launchBillingFlow(activity, params) }
            .onFailure { Diagnostics.error(TAG, "could not open checkout", it) }
            .getOrNull()
        return result?.ok == true
    }

    private companion object {
        const val TAG = "PlayBilling"
    }
}

private val BillingResult.ok: Boolean
    get() = responseCode == BillingClient.BillingResponseCode.OK

/** The one-time price, or the first phase of a subscription. */
private fun ProductDetails.displayPrice(): String? =
    oneTimePurchaseOfferDetails?.formattedPrice
        ?: subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice

/** Play's purchase reduced to the four fields any decision depends on. */
private fun Purchase.toRecord(): PurchaseRecord = PurchaseRecord(
    productIds = products,
    state = when (purchaseState) {
        Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
        Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
        else -> PurchaseState.UNSPECIFIED
    },
    acknowledged = isAcknowledged,
    purchaseToken = purchaseToken,
)
