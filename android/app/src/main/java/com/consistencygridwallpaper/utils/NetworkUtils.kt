package com.consistencygridwallpaper.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * NetworkUtils - Helper for Network Connectivity
 *
 * Provides a centralized way to check for active internet connection.
 * Used to preventing network calls when offline and providing appropriate user feedback.
 */
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    /**
     * Checks if the device currently has an active internet connection.
     *
     * @param context The application context
     * @return true if connected to a network with internet capability, false otherwise
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error checking network availability", e)
            false
        }
    }

    /**
     * Sends the FCM device token to the Next.js server so the backend can
     * trigger push notifications.
     */
    fun syncFcmToken(context: Context, fcmToken: String) {
        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(context)
        val authToken = userPrefs.getToken()
        
        if (authToken.isNullOrBlank()) {
            Log.w(TAG, "Cannot sync FCM token: User not logged in")
            return
        }

        if (userPrefs.isFcmTokenSynced()) {
            Log.d(TAG, "FCM token already synced")
            return
        }

        Thread {
            try {
                val baseUrl = userPrefs.getBaseUrl()
                val client = okhttp3.OkHttpClient()
                
                val json = """{"fcmToken":"${fcmToken}"}"""
                val body = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse("application/json; charset=utf-8"), 
                    json
                )

                val request = okhttp3.Request.Builder()
                    .url("$baseUrl/api/device-token")
                    .post(body)
                    .addHeader("Cookie", "publicToken=$authToken; native_auth=true")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ FCM token synced successfully to server")
                    userPrefs.setFcmTokenSynced(true)
                } else {
                    Log.e(TAG, "❌ Failed to sync FCM token: HTTP ${response.code} - ${response.body?.string()}")
                }
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception while syncing FCM token", e)
            }
        }.start()
    }
}
