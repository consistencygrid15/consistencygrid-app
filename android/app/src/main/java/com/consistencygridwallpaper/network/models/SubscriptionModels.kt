package com.consistencygridwallpaper.network.models

/**
 * Request body sent to server when verifying a Play Store purchase.
 * Server will validate the token against Google Play Developer API.
 */
data class SubscriptionVerifyRequest(
    val purchaseToken: String,
    val productId: String,
    val packageName: String
)

/**
 * Server response after verifying a purchase token.
 *
 * @param isPro        True if subscription is active
 * @param plan         "pro_monthly" | "pro_yearly" | null
 * @param expiresAtMs  Unix timestamp (ms) when subscription expires
 * @param status       "active" | "grace_period" | "expired" | "cancelled"
 */
data class SubscriptionVerifyResponse(
    val isPro: Boolean,
    val plan: String?,
    val expiresAtMs: Long,
    val status: String
)

/**
 * Server response for GET subscription status.
 * Used to refresh local Pro cache on app launch.
 */
data class SubscriptionStatusResponse(
    val isPro: Boolean,
    val plan: String?,
    val expiresAtMs: Long,
    val status: String,
    val purchaseToken: String?
)
