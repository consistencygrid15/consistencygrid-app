package com.consistencygridwallpaper.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.consistencygridwallpaper.storage.UserPrefs

/**
 * BootReceiver - Restores Alarms on Device Restart
 *
 * Triggered when the device finishes booting (RECEIVE_BOOT_COMPLETED).
 * Responsibilities:
 * 1. Check if the user has enabled auto-updates
 * 2. If enabled, re-schedule the daily update alarm (since alarms are cleared on reboot)
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "📱 Device boot completed. Checking auto-update preference...")
            
            val userPrefs = UserPrefs(context)
            if (userPrefs.isAutoUpdateEnabled()) {
                Log.d(TAG, "✅ Auto-update is enabled. Rescheduling alarm and checking for missed updates...")
                WorkScheduler.scheduleDailyUpdate(context)
                WorkScheduler.checkAndScheduleMissedUpdate(context)
            } else {
                Log.d(TAG, "ℹ️ Auto-update disabled, skipping.")
            }
        }
    }
}
