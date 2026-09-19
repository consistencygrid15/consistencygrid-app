package com.consistencygridwallpaper.billing

import android.content.Context
import android.util.Log
import com.android.billingclient.api.Purchase
import com.consistencygridwallpaper.network.ApiClient
import com.consistencygridwallpaper.network.models.SubscriptionVerifyRequest
import com.consistencygridwallpaper.storage.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * SubscriptionRepository — Business logic layer for subscription management.
 *
 * Responsibilities:
 *  - Verify Play Store purchases with the backend server
 *  - Cache Pro status locally in [UserPrefs] (offline-first)
 *  - Expose [SubscriptionStatus] as a [StateFlow] for the UI
 *  - Refresh status from server on demand (e.g. on app launch)
 *
 * Data Flow:
 *   Google Play → [BillingManager] → [SubscriptionRepository.handlePurchase()]
 *       → POST /api/mobile/subscription/verify
 *           → [UserPrefs.setProStatus()]
 *               → [_status] StateFlow → UI
 */
class SubscriptionRepository(private val context: Context) {

    private val TAG = "SubscriptionRepo"
    private val prefs = UserPrefs(context)

    private val _status = MutableStateFlow(loadCachedStatus())
    /** Real-time subscription status — observe in ViewModel */
    val status: StateFlow<SubscriptionStatus> = _status.asStateFlow()

    // ── Load from Cache ────────────────────────────────────────────────────────

    private fun loadCachedStatus(): SubscriptionStatus {
        return SubscriptionStatus(
            isPro       = prefs.isPro(),
            plan        = prefs.getProPlan(),
            expiresAtMs = prefs.getProExpiresAt(),
            isGracePeriod = false
        )
    }

    // ── Handle New Purchase ────────────────────────────────────────────────────

    /**
     * Called after a successful Google Play purchase.
     * Sends the purchase token to server for verification, then updates local cache.
     *
     * @param purchase The Purchase object from Google Play
     * @return [VerifyResult.Success] or [VerifyResult.Error]
     */
    suspend fun handlePurchase(purchase: Purchase): VerifyResult = withContext(Dispatchers.IO) {
        val productId = purchase.products.firstOrNull() ?: return@withContext VerifyResult.Error("No product ID in purchase")
        val token = purchase.purchaseToken

        return@withContext try {
            val request = SubscriptionVerifyRequest(
                purchaseToken = token,
                productId     = productId,
                packageName   = context.packageName
            )
            val response = ApiClient.getService(
                authToken = prefs.getToken()
            ).verifySubscription(request)

            if (response.isPro) {
                // Save verified status locally
                val plan = response.plan ?: productId
                prefs.setProStatus(
                    isPro     = true,
                    plan      = plan,
                    expiresAt = response.expiresAtMs,
                    token     = token
                )
                updateLocalUserProfile(isPro = true, plan = plan)
                _status.value = SubscriptionStatus(
                    isPro         = true,
                    plan          = plan,
                    expiresAtMs   = response.expiresAtMs,
                    isGracePeriod = response.status == "grace_period"
                )
                Log.d(TAG, "Purchase verified ✓ plan=${response.plan} expires=${response.expiresAtMs}")
                VerifyResult.Success
            } else {
                Log.w(TAG, "Server rejected purchase token (status=${response.status})")
                VerifyResult.Error("Subscription verification failed. Status: ${response.status}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server verify failed — falling back to local acknowledgment", e)
            // Fallback: trust Play Store if server is unreachable.
            // Server will re-verify on next launch via refreshFromServer().
            prefs.setProStatus(
                isPro     = true,
                plan      = productId,
                expiresAt = System.currentTimeMillis() + 32L * 24 * 60 * 60 * 1000, // 32 days buffer
                token     = token
            )
            updateLocalUserProfile(isPro = true, plan = productId)
            _status.value = SubscriptionStatus(
                isPro         = true,
                plan          = productId,
                expiresAtMs   = prefs.getProExpiresAt(),
                isGracePeriod = false
            )
            VerifyResult.Success // Optimistic — will re-verify next launch
        }
    }

    // ── Refresh from Server ────────────────────────────────────────────────────

    /**
     * Fetches the canonical subscription status from the server.
     * Call this on app launch to catch cancellations or renewals.
     */
    suspend fun refreshFromServer(): Unit = withContext(Dispatchers.IO) {
        try {
            val response = ApiClient.getService(
                authToken = prefs.getToken()
            ).getSubscriptionStatus()

            val isPro = response.isPro && response.status in listOf("active", "grace_period")

            if (isPro) {
                val plan = response.plan ?: prefs.getProPlan() ?: ""
                prefs.setProStatus(
                    isPro     = true,
                    plan      = plan,
                    expiresAt = response.expiresAtMs,
                    token     = response.purchaseToken ?: prefs.getProPurchaseToken() ?: ""
                )
                updateLocalUserProfile(isPro = true, plan = plan)
            } else {
                // Subscription cancelled or expired server-side
                prefs.clearProStatus()
                updateLocalUserProfile(isPro = false, plan = "free")
            }

            _status.value = SubscriptionStatus(
                isPro         = isPro,
                plan          = response.plan,
                expiresAtMs   = response.expiresAtMs,
                isGracePeriod = response.status == "grace_period"
            )
            Log.d(TAG, "Status refreshed from server: isPro=$isPro plan=${response.plan}")
        } catch (e: Exception) {
            Log.w(TAG, "refreshFromServer failed — using cached status", e)
            // Don't clear local cache on network error — offline-first behavior
        }
    }

    private suspend fun updateLocalUserProfile(isPro: Boolean, plan: String) {
        try {
            val db = com.consistencygridwallpaper.storage.room.AppDatabase.getDatabase(context)
            val current = db.userProfileDao().get() ?: com.consistencygridwallpaper.storage.room.UserProfileEntity(id = 1)
            db.userProfileDao().upsert(
                current.copy(
                    isPremium = isPro,
                    plan = plan,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update UserProfileEntity with Pro status", e)
        }
    }

    // ── Restore Purchases ─────────────────────────────────────────────────────

    /**
     * Queries active Google Play purchases and verifies with server.
     * Use this for "Restore Previous Purchase" button.
     */
    suspend fun restorePurchases(): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val activePurchases = BillingManager.queryActivePurchases()
            if (activePurchases.isEmpty()) {
                Log.d(TAG, "restorePurchases: No active subscriptions found in Play Store")
                return@withContext RestoreResult.NoPurchasesFound
            }

            var restored = false
            for (purchase in activePurchases) {
                val result = handlePurchase(purchase)
                if (result is VerifyResult.Success) {
                    restored = true
                }
            }

            if (restored) RestoreResult.Restored else RestoreResult.NoPurchasesFound
        } catch (e: Exception) {
            Log.e(TAG, "restorePurchases failed", e)
            RestoreResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns current cached Pro status without a server call */
    fun isProLocally(): Boolean = prefs.isPro()
}

// ── Data Models ───────────────────────────────────────────────────────────────

/**
 * Represents the user's current subscription state.
 */
data class SubscriptionStatus(
    val isPro: Boolean,
    val plan: String?,           // "pro_monthly" | "pro_yearly" | null
    val expiresAtMs: Long,
    val isGracePeriod: Boolean   // True if payment failed but still within grace period
)

sealed class VerifyResult {
    object Success : VerifyResult()
    data class Error(val reason: String) : VerifyResult()
}

sealed class RestoreResult {
    object Restored : RestoreResult()
    object NoPurchasesFound : RestoreResult()
    data class Error(val reason: String) : RestoreResult()
}
