package com.consistencygridwallpaper.ui.compose.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.android.billingclient.api.ProductDetails
import com.consistencygridwallpaper.billing.BillingManager
import com.consistencygridwallpaper.billing.PurchaseResult
import com.consistencygridwallpaper.billing.RestoreResult
import com.consistencygridwallpaper.billing.SubscriptionRepository
import com.consistencygridwallpaper.billing.SubscriptionStatus
import com.consistencygridwallpaper.billing.VerifyResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * SubscriptionViewModel — UI state management for the subscription/paywall screen.
 *
 * Collects from:
 *  - [BillingManager.productDetails] — plan pricing info
 *  - [BillingManager.purchaseResults] — purchase completion events
 *  - [SubscriptionRepository.status] — current Pro status
 *
 * Exposes:
 *  - [uiState] — complete screen state for the Composable
 *  - [purchaseState] — loading / success / error for the buy button
 */
class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SubscriptionRepository(application)

    // ── Subscription Status ────────────────────────────────────────────────────

    /** Current subscription status (isPro, plan, expiry). Cached + live. */
    val subscriptionStatus: StateFlow<SubscriptionStatus> = repo.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), repo.status.value)

    // ── Available Plans ────────────────────────────────────────────────────────

    /** Subscription plans with pricing from Play Store. Empty until BillingClient connects. */
    val availablePlans: StateFlow<List<ProductDetails>> = BillingManager.productDetails
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Purchase Flow State ────────────────────────────────────────────────────

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    // ── Billing Ready ──────────────────────────────────────────────────────────

    val billingReady: StateFlow<Boolean> = BillingManager.billingReady
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ── Init — Observe Purchase Events ────────────────────────────────────────

    init {
        // Observe purchase results from BillingManager (payment sheet outcomes)
        viewModelScope.launch {
            BillingManager.purchaseResults.collect { result ->
                when (result) {
                    is PurchaseResult.Success -> {
                        _purchaseState.value = PurchaseState.Loading
                        when (val verify = repo.handlePurchase(result.purchase)) {
                            is VerifyResult.Success -> {
                                _purchaseState.value = PurchaseState.Success
                            }
                            is VerifyResult.Error -> {
                                _purchaseState.value = PurchaseState.Error(verify.reason)
                            }
                        }
                    }
                    is PurchaseResult.Cancelled -> {
                        _purchaseState.value = PurchaseState.Idle
                    }
                    is PurchaseResult.Error -> {
                        _purchaseState.value = PurchaseState.Error(result.message)
                    }
                }
            }
        }

        // Refresh status from server on ViewModel creation (app launch / screen open)
        viewModelScope.launch {
            repo.refreshFromServer()
        }

        // Load product details if not already loaded
        viewModelScope.launch {
            if (BillingManager.productDetails.value.isEmpty()) {
                BillingManager.querySubscriptionPlans()
            }
        }
    }

    // ── Public Actions ─────────────────────────────────────────────────────────

    /**
     * Initiates a subscription purchase for the given [productId].
     * Launches the Google Play billing sheet.
     *
     * @param activity The foreground Activity (required by Billing Library)
     * @param productId [BillingManager.PRODUCT_MONTHLY] or [BillingManager.PRODUCT_YEARLY]
     */
    fun subscribe(activity: Activity, productId: String) {
        if (_purchaseState.value is PurchaseState.Loading) return
        _purchaseState.value = PurchaseState.Loading
        // launchBillingFlow is synchronous — result comes via purchaseResults SharedFlow
        BillingManager.launchBillingFlow(activity, productId)
        // Reset loading if billing sheet dismisses without triggering a result
        // (Handled by PurchaseResult.Cancelled in the collector above)
    }

    /**
     * Restores a previous subscription purchase (e.g. after reinstall).
     */
    fun restorePurchases() {
        if (_purchaseState.value is PurchaseState.Loading) return
        _purchaseState.value = PurchaseState.Loading
        viewModelScope.launch {
            when (val result = repo.restorePurchases()) {
                is RestoreResult.Restored -> {
                    _purchaseState.value = PurchaseState.Restored
                }
                is RestoreResult.NoPurchasesFound -> {
                    _purchaseState.value = PurchaseState.Error("No previous subscription found on this Google account.")
                }
                is RestoreResult.Error -> {
                    _purchaseState.value = PurchaseState.Error(result.reason)
                }
            }
        }
    }

    /** Resets purchase state to Idle (e.g. after showing success/error dialog) */
    fun resetPurchaseState() {
        _purchaseState.value = PurchaseState.Idle
    }

    /** Convenience accessor — current Pro status without Flow collection */
    fun isProLocally(): Boolean = repo.isProLocally()

    /** Returns formatted price string for a product (e.g. "₹99.00/month") */
    fun getPricingText(productId: String): String? = BillingManager.getPricingText(productId)
}

// ── Purchase State ────────────────────────────────────────────────────────────

sealed class PurchaseState {
    /** No ongoing purchase */
    object Idle : PurchaseState()

    /** Purchase or verification in progress — show loading indicator */
    object Loading : PurchaseState()

    /** Purchase completed and verified successfully */
    object Success : PurchaseState()

    /** Previous subscription restored successfully */
    object Restored : PurchaseState()

    /** An error occurred during purchase or verification */
    data class Error(val message: String) : PurchaseState()
}
