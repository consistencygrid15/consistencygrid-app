package com.consistencygridwallpaper.network

import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Body
import com.google.gson.JsonObject
import okhttp3.RequestBody

/**
 * ApiService — Retrofit service interface for all API endpoints.
 *
 * Security: All endpoints that require authentication rely on the
 * "Authorization: Bearer <token>" header injected by ApiClient's OkHttp
 * interceptor. Tokens are NEVER passed as @Query parameters.
 *
 * All methods are suspend functions for coroutine integration.
 */
interface ApiService {

    // ─── Authentication ───────────────────────────────────────────────────────

    @POST("/api/native-auth/email-login")
    suspend fun emailLogin(@Body payload: String): String

    @POST("/api/native-auth/email-signup")
    suspend fun emailSignup(@Body payload: String): String

    @POST("/api/auth/native/google")
    suspend fun googleLogin(@Body payload: String): String

    @POST("/api/native-auth/refresh")
    suspend fun refreshToken(@Body payload: RequestBody): String

    /** Token is sent via Authorization header — not as a query param. */
    @POST("/api/native-auth/delete-account")
    suspend fun deleteAccount(@Body payload: JsonObject): JsonObject


    // ─── Wallpaper Data ───────────────────────────────────────────────────────

    @GET("/api/wallpaper-data")
    suspend fun getWallpaperData(
        @Query("tz") timezone: String,
        @Query("deviceDate") deviceDate: String
    ): JsonObject

    // ─── Sync Operations ──────────────────────────────────────────────────────

    @POST("/api/mobile/habits/tick")
    suspend fun syncHabitLogs(
        @Body payload: JsonObject
    ): JsonObject

    @POST("/api/mobile/habits/sync")
    suspend fun syncHabits(
        @Body payload: JsonObject
    ): JsonObject

    @POST("/api/mobile/goals/sync")
    suspend fun syncGoals(
        @Body payload: JsonObject
    ): JsonObject

    @POST("/api/mobile/reminders/sync")
    suspend fun syncReminders(
        @Body payload: JsonObject
    ): JsonObject

    @GET("/api/goals")
    suspend fun getGoals(): JsonObject

    // ─── Reel Controller ──────────────────────────────────────────────────────

    @POST("/api/mobile/reel-controller/sync")
    suspend fun syncReelController(
        @Body payload: JsonObject
    ): JsonObject

    @GET("/api/mobile/reel-controller")
    suspend fun getReelController(): JsonObject

    // ─── Device Management ────────────────────────────────────────────────────

    @POST("/api/device-token")
    suspend fun registerDeviceToken(@Body payload: JsonObject): JsonObject

    // ─── Telemetry ────────────────────────────────────────────────────────────

    @POST("/api/telemetry/wallpaper-update")
    suspend fun sendTelemetry(@Body payload: JsonObject): JsonObject

    // ─── Subscription ─────────────────────────────────────────────────────────

    /**
     * Verifies a Google Play purchase token with the server.
     * Server validates against the Google Play Developer API and returns Pro status.
     */
    @POST("/api/mobile/subscription/verify")
    suspend fun verifySubscription(
        @Body body: com.consistencygridwallpaper.network.models.SubscriptionVerifyRequest
    ): com.consistencygridwallpaper.network.models.SubscriptionVerifyResponse

    /**
     * Fetches the current subscription status from the server.
     * Used to refresh Pro cache on app launch.
     */
    @GET("/api/mobile/subscription/status")
    suspend fun getSubscriptionStatus(): com.consistencygridwallpaper.network.models.SubscriptionStatusResponse
}
