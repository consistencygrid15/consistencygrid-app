package com.consistencygridwallpaper.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.consistencygridwallpaper.storage.UserPrefs

/**
 * BootReceiver — Restores services + alarms on device restart or APK update.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "📱 Device boot completed. Restoring services and alarms...")
                onBootCompleted(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "📦 APK updated. Restoring services and alarms...")
                onBootCompleted(context)
            }
        }
    }

    private fun onBootCompleted(context: Context) {
        try {
            rescheduleWallpaperUpdates(context)
            Log.d(TAG, "✅ Boot recovery complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Boot recovery error: ${e.message}", e)
        }
    }

    private fun rescheduleWallpaperUpdates(context: Context) {
        val userPrefs = UserPrefs(context)
        if (userPrefs.isAutoUpdateEnabled()) {
            Log.d(TAG, "✅ Auto-update enabled. Re-arming wallpaper layers...")
            ExactAlarmScheduler.scheduleNextMidnightAlarm(context)
            MidnightWorkScheduler.schedule(context)
        }
    }
}
