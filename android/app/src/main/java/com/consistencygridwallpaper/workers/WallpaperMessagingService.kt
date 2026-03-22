package com.consistencygridwallpaper.workers

import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.TimeZone

class WallpaperMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        // We need to send this to Next.js API
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get the base token/session from userPrefs to authenticate the API call
                val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(applicationContext)
                val userToken = userPrefs.getToken()
                
                if (userToken.isNullOrEmpty()) {
                     Log.w(TAG, "No user token available to sync FCM token")
                     return@launch
                }

                val timezone = TimeZone.getDefault().id
                val baseUrl = userPrefs.getBaseUrl()
                val client = OkHttpClient()

                val json = JSONObject().apply {
                    put("token", token)
                    put("timezone", timezone)
                    put("deviceType", "android")
                }

                val body = json.toString().toRequestBody("application/json".toMediaType())
                
                // Add exact cookies required by getUniversalSession
                val request = Request.Builder()
                    .url("$baseUrl/api/device-token")
                    .post(body)
                    .addHeader("Cookie", "publicToken=$userToken; native_auth=true")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully synced FCM token to server")
                } else {
                    Log.e(TAG, "Failed to sync FCM token: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing FCM token", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")

            val type = remoteMessage.data["type"]
            if (type == "WALLPAPER_UPDATE_TRIGGER") {
                Log.d(TAG, "Received Wallpaper Update Trigger. Launching Worker...")
                
                val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(applicationContext)
                
                // 🚀 Start WorkManager instantly using Expedited job to bypass App Standby Buckets & Doze mode
                val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    "WallpaperUpdate_FCM",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            }
        }
    }
}
