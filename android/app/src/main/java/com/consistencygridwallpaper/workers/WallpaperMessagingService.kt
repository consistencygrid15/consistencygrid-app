package com.consistencygridwallpaper.workers

import com.consistencygridwallpaper.utils.AppLogger
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
import java.util.concurrent.TimeUnit

class WallpaperMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Security: do not log the full raw FCM registration token
        AppLogger.d(TAG, "Refreshed token: [REDACTED]")
        sendRegistrationToServer(token)
    }

    private fun sendRegistrationToServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(applicationContext)
                val userToken = userPrefs.getToken()
                
                if (userToken.isNullOrEmpty()) {
                     AppLogger.w(TAG, "No user token available to sync FCM token")
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
                
                // Add Authorization Bearer header + Cookie auth
                val request = Request.Builder()
                    .url("$baseUrl/api/device-token")
                    .post(body)
                    .addHeader("Authorization", "Bearer $userToken")
                    .addHeader("Cookie", "publicToken=$userToken; native_auth=true")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    AppLogger.d(TAG, "Successfully synced FCM token to server")
                } else {
                    AppLogger.e(TAG, "Failed to sync FCM token: ${response.code}")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error syncing FCM token", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val receivedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        AppLogger.d(TAG, "[TRIGGER=FCM] 📩 Message received at $receivedAt")

        if (remoteMessage.data.isNotEmpty()) {
            val type = remoteMessage.data["type"]
            if (type == "WALLPAPER_UPDATE_TRIGGER") {
                val jitterMax = remoteMessage.data["jitter_max_minutes"]?.toIntOrNull() ?: 60
                val isInstantUpdate = (jitterMax == 0)
                val triggerName = if (isInstantUpdate) "FCM_INSTANT" else "FCM"

                AppLogger.d(TAG, "[TRIGGER=$triggerName] Wallpaper update requested — isInstant=$isInstantUpdate")

                MidnightReceiver.acquireWakeLock(applicationContext)
                AppLogger.d(TAG, "[TRIGGER=$triggerName] 🔒 WakeLock acquired")

                AppLogger.d(TAG, "[TRIGGER=$triggerName] Rescheduling next alarm to ensure chain continuity...")
                ExactAlarmScheduler.scheduleNextMidnightAlarm(applicationContext)

                val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString("TRIGGER", triggerName)
                            .putBoolean("FORCE_UPDATE", isInstantUpdate)
                            .build()
                    )
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        10, // 10 minutes initial, then 20, 40
                        TimeUnit.MINUTES
                    )
                    .build()

                val queueName = if (isInstantUpdate) "WallpaperUpdate_FCM_Instant" else "WallpaperUpdate_FCM"
                WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                    queueName,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
                AppLogger.d(TAG, "[TRIGGER=$triggerName] 🚀 WallpaperWorker enqueued via $queueName (force=$isInstantUpdate)")
            }
        }
    }
}
