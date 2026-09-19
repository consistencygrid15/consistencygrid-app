package com.consistencygridwallpaper.auth

import android.app.Activity
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
import com.consistencygridwallpaper.utils.AppLogger

/**
 * GoogleSignInHelper - Wrapper for Google Sign-In SDK
 * 
 * Handles Google Sign-In flow and backend verification.
 * Uses Google Sign-In SDK to get ID token, then verifies with backend.
 */
class GoogleSignInHelper(private val activity: Activity) {

    companion object {
        private const val TAG = "GoogleSignInHelper"

        // Must match the Web OAuth client used by the native-auth backend.
        private const val SERVER_CLIENT_ID = "44748701600-4sm76hqagnjrd097i4jvt1i72itm9bcu.apps.googleusercontent.com"
        
        private const val BACKEND_URL = "https://consistencygrid.com/api/auth/native/google"
        private const val FALLBACK_URL = "https://consistencygrid.com/api/native-auth/google"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val googleSignInClient: GoogleSignInClient

    @Volatile
    var lastErrorMessage: String? = null
        private set

    init {
        // Request the backend audience so the returned ID token can be verified server-side.
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
                lastErrorMessage = null
                // Get signed-in account
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken

                val googleName  = account?.displayName ?: ""
                val googleEmail = account?.email ?: ""

                if (googleEmail.isBlank()) {
                    lastErrorMessage = "Google did not return an email address. Please try again."
                    AppLogger.e(TAG, "Google account returned no email")
                    return@withContext null
                }

                if (idToken.isNullOrBlank()) {
                    lastErrorMessage = "Google did not return a sign-in token. Please try again."
                    AppLogger.e(TAG, "Google sign-in returned no ID token")
                    return@withContext null
                }

                // Authentication is server-authoritative. Never create a local token when
                // backend verification fails because that token cannot authorize API calls.
                val backendResult = verifyWithBackend(idToken, googleEmail, googleName)
                if (backendResult == null) {
                    if (lastErrorMessage.isNullOrBlank()) {
                        lastErrorMessage = "Could not verify your Google account. Check your connection and try again."
                    }
                    return@withContext null
                }

                val finalName = backendResult.name.ifBlank { googleName }
                val finalEmail = backendResult.email.ifBlank { googleEmail }
                AuthResult(
                    token = backendResult.token,
                    sessionToken = backendResult.sessionToken,
                    onboarded = backendResult.onboarded,
                    expiresAt = backendResult.expiresAt,
                    name = finalName,
                    email = finalEmail
                )
            } catch (e: ApiException) {
                val errorMsg = when (e.statusCode) {
                    com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Sign-in cancelled by user"
                    com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error during sign-in"
                    com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign-in required"
                    10 -> "Google setup error: release SHA-1 or Web Client ID is not registered"
                    else -> "Google Sign-In error (code ${e.statusCode})"
                }
                lastErrorMessage = errorMsg
                AppLogger.e(TAG, "Sign-in failed: $errorMsg", e)
                null
            } catch (e: Exception) {
                lastErrorMessage = "Google Sign-In error: ${e.localizedMessage ?: "unknown error"}"
                AppLogger.e(TAG, "Unexpected error during sign-in", e)
                null
            }
        }
    }

    /**
     * Verify ID token with backend (with fallback URL and detailed error diagnosis)
     */
    private suspend fun verifyWithBackend(idToken: String, email: String? = null, name: String? = null): AuthResult? {
        val baseUrl = com.consistencygridwallpaper.storage.UserPrefs(activity).getBaseUrl().trimEnd('/')
        val urls = listOf(
            "$baseUrl/api/auth/native/google",
            "$baseUrl/api/native-auth/google"
        )
        val client = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val json = JSONObject().apply {
            put("idToken", idToken)
            if (!email.isNullOrBlank()) put("email", email)
            if (!name.isNullOrBlank()) put("name", name)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())

        for (url in urls) {
            try {
                val request = Request.Builder().url(url).post(body).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && !responseBody.isNullOrBlank()) {
                    val jsonResponse = JSONObject(responseBody)

                    if (jsonResponse.optBoolean("success", false)) {
                        val token = jsonResponse.optString("token", "")
                        if (token.isEmpty()) {
                            AppLogger.e(TAG, "Backend returned success without an auth token")
                            continue
                        }

                        val sessionToken = jsonResponse.optString("sessionToken", "")
                        val onboarded = jsonResponse.optBoolean("onboarded", false)
                        val expiresAt = jsonResponse.optLong("expiresAt", 0L).let { raw ->
                            if (raw > 0L) raw else System.currentTimeMillis() + (30L * 24 * 60 * 60 * 1000)
                        }

                        val userObj = jsonResponse.optJSONObject("user")
                        val name = userObj?.optString("name") ?: ""
                        val email = userObj?.optString("email") ?: ""

                        AppLogger.d(TAG, "Backend verification successful via $url")
                        return AuthResult(token, sessionToken, onboarded, expiresAt, name, email)
                    } else {
                        val errMsg = jsonResponse.optString("error", "Verification rejected by server")
                        lastErrorMessage = errMsg
                        AppLogger.w(TAG, "Backend verification returned false: $errMsg")
                    }
                } else {
                    val errorDetail = try {
                        JSONObject(responseBody ?: "").optString("error", "Server returned HTTP ${response.code}")
                    } catch (_: Exception) {
                        "Server returned HTTP ${response.code}"
                    }
                    lastErrorMessage = errorDetail
                    AppLogger.w(TAG, "Backend HTTP failed ($url): ${response.code} $errorDetail")
                }
            } catch (e: Exception) {
                lastErrorMessage = "Network error: ${e.localizedMessage ?: "timeout"}"
                AppLogger.e(TAG, "Backend request error ($url)", e)
            }
        }
        return null
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
        val expiresAt: Long = 0L,  // Unix ms when JWT expires; 0 = unknown
        val name: String = "",
        val email: String = ""
    )
}
