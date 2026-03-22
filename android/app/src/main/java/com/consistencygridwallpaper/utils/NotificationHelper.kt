package com.consistencygridwallpaper.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * NotificationHelper - Manages System Notifications
 *
 * Centralizes notification channel creation and management.
 * Useful for future enhancements where we might want to show notifications
 * for successful wallpaper updates or errors.
 */
object NotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID_UPDATES = "wallpaper_updates"
    const val CHANNEL_NAME_UPDATES = "Wallpaper Updates"

    /**
     * Creates notification channels for the app.
     * Should be called on app startup.
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val descriptionText = "Notifications for automatic wallpaper updates"
                val importance = NotificationManager.IMPORTANCE_LOW 
                val channel = NotificationChannel(CHANNEL_ID_UPDATES, CHANNEL_NAME_UPDATES, importance).apply {
                    description = descriptionText
                }
                
                val notificationManager: NotificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
                
                Log.d(TAG, "✅ Notification channel created: $CHANNEL_ID_UPDATES")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create notification channel", e)
            }
        }
    }
}
