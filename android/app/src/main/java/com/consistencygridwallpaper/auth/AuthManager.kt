package com.consistencygridwallpaper.auth

import android.content.Context
import android.util.Log
import com.consistencygridwallpaper.storage.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AuthManager - Central authentication state management
 *
 * Singleton that manages authentication state across the app.
 * Handles login status, token storage, expiry, silent refresh, and logout.
 */
class AuthManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val REFRESH_URL = "https://consistencygrid.com/api/native-auth/refresh"
        private const val TIMEOUT_SECONDS = 30L

        @Volatile
        private var instance: AuthManager? = null

        /**
         * Thread-safe singleton via double-checked locking.
         */
        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val userPrefs = UserPrefs(context)

    // ─── Read-only accessors ───────────────────────────────────────────────────

    /** @return true if a non-blank publicToken exists in storage. */
    fun isLoggedIn(): Boolean {
        val token = userPrefs.getToken()
        val isLoggedIn = !token.isNullOrBlank()
        Log.d(TAG, "isLoggedIn: $isLoggedIn")
        return isLoggedIn
    }

    fun isOnboarded(): Boolean = userPrefs.isOnboarded()

    fun getToken(): String? = userPrefs.getToken()

    fun getSessionToken(): String? = userPrefs.getSessionToken()

    /** @return true if the stored JWT is known to have expired. */
    fun isTokenExpired(): Boolean = userPrefs.isTokenExpired()

    /** @return true if expiry is within the next 24 hours. */
    fun isTokenExpiringSoon(): Boolean = userPrefs.isTokenExpiringSoon()

    // ─── Write Operations ─────────────────────────────────────────────────────

    /**
     * Save all auth data after a successful login or token refresh.
     *
     * @param token       publicToken (stable, long-lived identifier)
     * @param sessionToken NextAuth JWT used by the WebView cookie
     * @param onboarded   Whether the user has completed onboarding
     * @param expiresAt   Unix timestamp in milliseconds when the JWT expires.
     *                    Pass 0 if unknown (defaults to 30 days from now).
     */
    fun saveAuthData(
        token: String,
        sessionToken: String = "",
        onboarded: Boolean,
        expiresAt: Long = 0L
    ) {
        Log.d(TAG, "saveAuthData: token present=${token.isNotBlank()}, onboarded=$onboarded")
        userPrefs.saveToken(token)
        if (sessionToken.isNotEmpty()) {
            userPrefs.saveSessionToken(sessionToken)
        }
        userPrefs.saveOnboardedStatus(onboarded)

        // Persist expiry — fallback to 30 days if backend didn't send it
        val expiry = if (expiresAt > 0L) expiresAt
        else System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
        userPrefs.saveTokenExpiry(expiry)
    }

    // ─── Refresh Flow ─────────────────────────────────────────────────────────

    /**
     * Attempts a silent JWT refresh using the stored publicToken.
     *
     * Calls POST /api/native-auth/refresh with { publicToken }.
     * On success, persists the new sessionToken + expiresAt.
     *
     * @return true if refresh succeeded, false otherwise.
     */
    suspend fun refreshSessionToken(): Boolean = withContext(Dispatchers.IO) {
        val publicToken = userPrefs.getToken()
        if (publicToken.isNullOrBlank()) {
            Log.w(TAG, "refreshSessionToken: No publicToken stored, cannot refresh")
            return@withContext false
        }

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

            val json = JSONObject().apply { put("publicToken", publicToken) }
            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(REFRESH_URL)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val obj = JSONObject(responseBody)
                if (obj.optBoolean("success", false)) {
                    val newSessionToken = obj.getString("sessionToken")
                    val newExpiresAt = obj.optLong("expiresAt", 0L)

                    userPrefs.saveSessionToken(newSessionToken)
                    val expiry = if (newExpiresAt > 0L) newExpiresAt
                    else System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                    userPrefs.saveTokenExpiry(expiry)

                    Log.d(TAG, "refreshSessionToken: ✅ Token refreshed successfully")
                    return@withContext true
                }
                Log.w(TAG, "refreshSessionToken: Backend returned success=false: $responseBody")
            } else {
                Log.w(TAG, "refreshSessionToken: HTTP ${response.code} — $responseBody")
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshSessionToken: Network error", e)
        }
        false
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    /**
     * Logs the user out completely.
     * Clears all stored data and signs out of the native Google session.
     */
    fun logout() {
        Log.d(TAG, "logout: Clearing all user data")
        userPrefs.clear()

        try {
            val gso = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            ).build()
            val googleSignInClient =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, gso)
            googleSignInClient.signOut()
            Log.d(TAG, "logout: Google Sign-In session cleared")
        } catch (e: Exception) {
            Log.e(TAG, "logout: Failed to clear Google Sign-In session", e)
        }
    }
}
