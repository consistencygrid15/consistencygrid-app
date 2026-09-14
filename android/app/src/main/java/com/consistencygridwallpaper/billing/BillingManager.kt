package com.consistencygridwallpaper.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.*
import com.consistencygridwallpaper.storage.UserPrefs
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

/**
 * BillingManager — Singleton wrapper around Google Play Billing Library v7.
 *
 * Responsibilities:
 *  - Manage BillingClient lifecycle (connect / disconnect)
 *  - Query subscription ProductDetails for plan cards
 *  - Launch the billing flow (payment sheet)
 *  - Acknowledge purchases (mandatory within 3 days or Google auto-refunds)
 *  - Restore purchases after reinstall
 *  - Emit PurchaseResult events to SubscriptionViewModel
 *
 * Usage:
 *   BillingManager.initialize(applicationContext)   ← in MainApplication.onCreate()
 *   BillingManager.getInstance().launchBillingFlow(activity, "pro_monthly")
 */
object BillingManager {

    private const val TAG = "BillingManager"

    // ── Product IDs — must match what you create in Google Play Console ────────
    const val PRODUCT_MONTHLY = "pro_monthly"
    const val PRODUCT_YEARLY  = "pro_yearly"

    private val SUBSCRIPTION_IDS = listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY)

    // ── Internal state ─────────────────────────────────────────────────────────
    private lateinit var billingClient: BillingClient
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(extraBufferCapacity = 1)
    /** Emit purchase events — observe in SubscriptionViewModel */
    val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    /** Available subscription plans with pricing info from Play Store */
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails.asStateFlow()

    private val _billingReady = MutableStateFlow(false)
    /** True once BillingClient is connected and ready */
    val billingReady: StateFlow<Boolean> = _billingReady.asStateFlow()

    // ── Initialization ─────────────────────────────────────────────────────────

    /**
     * Call once in [MainApplication.onCreate].
     * Builds the BillingClient and starts the connection.
     */
    fun initialize(context: Context) {
        appContext = context.applicationContext

        billingClient = BillingClient.newBuilder(appContext)
            .setListener { billingResult, purchases ->
                // Called on every purchase update (new, restored, pending)
                handlePurchaseUpdate(billingResult, purchases)
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()

        connect()
    }

    // ── Connection ─────────────────────────────────────────────────────────────

    private fun connect() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "BillingClient connected")
                    _billingReady.value = true
                    scope.launch { querySubscriptionPlans() }
                    scope.launch { restoreAndAcknowledgePendingPurchases() }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    _billingReady.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "BillingClient disconnected — will retry on next operation")
                _billingReady.value = false
                // Retry after 5 seconds
                scope.launch {
                    delay(5_000)
                    connect()
                }
            }
        })
    }

    // ── Query Products ─────────────────────────────────────────────────────────

    /**
     * Fetches subscription ProductDetails from Play Store.
     * Updates [productDetails] StateFlow with pricing info.
     */
    suspend fun querySubscriptionPlans() {
        if (!billingClient.isReady) {
            Log.w(TAG, "querySubscriptionPlans: BillingClient not ready")
            return
        }

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                SUBSCRIPTION_IDS.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList ?: emptyList()
                Log.d(TAG, "Fetched ${details.size} subscription products")
                _productDetails.value = details
            } else {
                Log.w(TAG, "queryProductDetails failed: ${billingResult.debugMessage}")
            }
        }
    }

    // ── Launch Billing Flow ────────────────────────────────────────────────────

    /**
     * Launches the Google Play payment sheet for the given [productId].
     *
     * @param activity  Must be a foreground Activity
     * @param productId One of [PRODUCT_MONTHLY] or [PRODUCT_YEARLY]
     */
    fun launchBillingFlow(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            Log.e(TAG, "launchBillingFlow: BillingClient not ready")
            _purchaseResults.tryEmit(PurchaseResult.Error(
                BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                "Billing service unavailable. Please try again."
            ))
            return
        }

        val product = _productDetails.value.find { it.productId == productId }
        if (product == null) {
            Log.e(TAG, "launchBillingFlow: ProductDetails not found for $productId")
            _purchaseResults.tryEmit(PurchaseResult.Error(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "Product not available. Please check your connection."
            ))
            return
        }

        // For subscriptions, use the first available offer
        val offerToken = product.subscriptionOfferDetails?.firstOrNull()?.offerToken
        if (offerToken == null) {
            Log.e(TAG, "launchBillingFlow: No offer token for $productId")
            _purchaseResults.tryEmit(PurchaseResult.Error(
                BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                "No offers available for this plan."
            ))
            return
        }

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .setOfferToken(offerToken)
            .build()

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, billingFlowParams)
        Log.d(TAG, "launchBillingFlow result: ${result.responseCode} — ${result.debugMessage}")
    }

    // ── Purchase Update Handler ────────────────────────────────────────────────

    private fun handlePurchaseUpdate(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    scope.launch { processPurchase(purchase) }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled billing flow")
                _purchaseResults.tryEmit(PurchaseResult.Cancelled)
            }
            else -> {
                Log.w(TAG, "Purchase update error: ${billingResult.responseCode} — ${billingResult.debugMessage}")
                _purchaseResults.tryEmit(PurchaseResult.Error(
                    billingResult.responseCode,
                    billingResult.debugMessage
                ))
            }
        }
    }

    private suspend fun processPurchase(purchase: Purchase) {
        Log.d(TAG, "Processing purchase: ${purchase.products} state=${purchase.purchaseState}")

        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) {
            Log.d(TAG, "Purchase state is not PURCHASED (${purchase.purchaseState}), skipping")
            return
        }

        // Acknowledge purchase — mandatory within 3 days or Google auto-refunds
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams) { ackResult ->
                if (ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged: ${purchase.purchaseToken.take(20)}...")
                } else {
                    Log.w(TAG, "Acknowledgment failed: ${ackResult.debugMessage}")
                }
            }
        }

        // Emit success so SubscriptionRepository can verify with server
        _purchaseResults.tryEmit(PurchaseResult.Success(purchase))
    }

    // ── Restore Purchases ──────────────────────────────────────────────────────

    /**
     * Queries existing subscriptions from Play Store.
     * Called on startup to restore Pro status after reinstall.
     * Returns list of active purchases.
     */
    suspend fun queryActivePurchases(): List<Purchase> = suspendCancellableCoroutine { continuation ->
        if (!billingClient.isReady) {
            continuation.resume(emptyList())
            return@suspendCancellableCoroutine
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchasesList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d(TAG, "Restored ${purchasesList.size} active subscription(s)")
                continuation.resume(purchasesList)
            } else {
                Log.w(TAG, "queryPurchasesAsync failed: ${billingResult.debugMessage}")
                continuation.resume(emptyList())
            }
        }
    }

    /**
     * On startup: check for unacknowledged purchases and emit them.
     * Handles edge case where app crashed after purchase but before acknowledgment.
     */
    private suspend fun restoreAndAcknowledgePendingPurchases() {
        val purchases = queryActivePurchases()
        purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                processPurchase(purchase)
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Returns pricing text for the given product (e.g. "₹99.00/month").
     * Returns null if product details are not yet loaded.
     */
    fun getPricingText(productId: String): String? {
        val product = _productDetails.value.find { it.productId == productId }
        return product?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
    }
}

// ── Purchase Result Sealed Class ──────────────────────────────────────────────

/**
 * Represents the outcome of a Google Play purchase attempt.
 */
sealed class PurchaseResult {
    /** Purchase completed and acknowledged. Token ready for server verification. */
    data class Success(val purchase: Purchase) : PurchaseResult()

    /** User dismissed the billing dialog. */
    object Cancelled : PurchaseResult()

    /** Billing error occurred. */
    data class Error(val code: Int, val message: String) : PurchaseResult()
}
