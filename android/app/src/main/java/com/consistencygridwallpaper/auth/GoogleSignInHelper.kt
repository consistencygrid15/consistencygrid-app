package com.consistencygridwallpaper.auth

import android.app.Activity
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GoogleSignInHelper - Wrapper for Google Sign-In SDK
 * 
 * Handles Google Sign-In flow and backend verification.
 * Uses Google Sign-In SDK to get ID token, then verifies with backend.
 */
class GoogleSignInHelper(private val activity: Activity) {

    companion object {
        private const val TAG = "GoogleSignInHelper"
        
        // TODO: Replace with your actual Web Application Client ID from Google Cloud Console
        private const val SERVER_CLIENT_ID = "44748701600-4sm76hqagnjrd097i4jvt1i72itm9bcu.apps.googleusercontent.com"
        
        private const val BACKEND_URL = "https://consistencygrid.com/api/auth/native/google"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val googleSignInClient: GoogleSignInClient

    init {
        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(SERVER_CLIENT_ID)
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(activity, gso)
    }

    /**
     * Get Google Sign-In client for launching sign-in intent
     * 
     * @return GoogleSignInClient instance
     */
    fun getSignInClient(): GoogleSignInClient {
        return googleSignInClient
    }

    /**
     * Handle sign-in result and verify with backend
     * 
     * @param task The sign-in task from Google
     * @return AuthResult with token and onboarded status, or null if failed
     */
    suspend fun handleSignInResult(task: Task<GoogleSignInAccount>): AuthResult? {
        return withContext(Dispatchers.IO) {
            try {
                // Get signed-in account
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                if (idToken == null) {
                    Log.e(TAG, "ID token is null")
                    return@withContext null
                }

                Log.d(TAG, "Got ID token, verifying with backend")

                // Verify with backend
                verifyWithBackend(idToken)

            } catch (e: ApiException) {
                val errorMsg = when (e.statusCode) {
                    com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Sign-in cancelled by user"
                    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error during sign-in"
                    com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign-in required"
                    else -> "Google Sign-In error (code ${e.statusCode})"
                }
                Log.e(TAG, "Sign-in failed: $errorMsg", e)
                null
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during sign-in", e)
                null
            }
        }
    }

    /**
     * Verify ID token with backend
     * 
     * @param idToken The ID token from Google
     * @return AuthResult with token and onboarded status, or null if failed
     */
    private suspend fun verifyWithBackend(idToken: String): AuthResult? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()

            val json = JSONObject()
            json.put("idToken", idToken)

            val body = json.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(BACKEND_URL)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                
                // Use robust optBoolean instead of getBoolean to prevent crash if missing
                if (jsonResponse.optBoolean("success", false)) {
                    val token = jsonResponse.optString("token", "")
                    if (token.isEmpty()) {
                        Log.e(TAG, "Backend returned success but token is empty!")
                        return null
                    }
                    
                    val sessionToken = jsonResponse.optString("sessionToken", "")
                    
                    // Supabase migration resilience: check both "onboarded" and "isNewUser"
                    val onboarded = jsonResponse.optBoolean("onboarded", false) || jsonResponse.optBoolean("isNewUser", false)
                    
                    // expiresAt from backend (Unix millis). Fallback: 30 days from now.
                    val expiresAt = jsonResponse.optLong("expiresAt", 0L).let { raw ->
                        if (raw > 0L) raw else System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                    }
                    
                    Log.d(TAG, "Backend verification successful")
                    AuthResult(token, sessionToken, onboarded, expiresAt)
                } else {
                    val error = jsonResponse.optString("error", "Unknown error")
                    Log.e(TAG, "Backend verification failed: $error")
                    null
                }
            } else {
                Log.e(TAG, "Backend HTTP failed: ${response.code} body: $responseBody")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "Backend verification error", e)
            null
        }
    }

    /**
     * Sign out from Google
     */
    fun signOut() {
        googleSignInClient.signOut()
    }

    /**
     * Data class for authentication result
     */
    data class AuthResult(
        val token: String,
        val sessionToken: String,
        val onboarded: Boolean,
        val expiresAt: Long = 0L  // Unix ms when JWT expires; 0 = unknown
    )
}
