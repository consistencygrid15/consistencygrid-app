package com.consistencygridwallpaper.workers

import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.consistencygridwallpaper.storage.UserPrefs

/**
 * WallpaperMessagingService
 * 
 * Handles incoming Firebase Cloud Messaging (FCM) silent push notifications.
 * Received from the Next.js server when a user's wallpaper is ready to be updated.
 */
class WallpaperMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "WallpaperMsgService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔄 New FCM Token generated: $token")
        // Save token to SharedPreferences so MainActivity can send it to the server
        val prefs = UserPrefs(applicationContext)
        prefs.setFcmToken(token)
        prefs.setFcmTokenSynced(false) // Mark it as needing sync to server
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "📥 FCM Message received from: ${message.from}")

        // Ensure user is logged in
        val userPrefs = UserPrefs(applicationContext)
        if (userPrefs.getToken().isNullOrBlank()) {
            Log.w(TAG, "⚠️ No auth token found. Ignoring push update.")
            return
        }

        // Trigger immediate wallpaper update worker
        // Using PUSH_UPDATE tag ensures 0 jitter (instant execution) in WallpaperWorker
        Log.d(TAG, "🚀 Triggering background WorkManager for wallpaper update...")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val pushWorkRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag(WallpaperWorker.TAG)
            .addTag("PUSH_UPDATE")
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "DAILY_WALLPAPER_UPDATE_PUSH",
            ExistingWorkPolicy.REPLACE,
            pushWorkRequest
        )
        
        Log.d(TAG, "✅ Push-driven WallpaperWorker enqueued")
    }
}
