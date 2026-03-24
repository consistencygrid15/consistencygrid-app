package com.consistencygridwallpaper.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.consistencygridwallpaper.storage.UserPrefs

/**
 * BootReceiver — Restores alarms on device restart or APK update.
 *
 * Handles:
 * - BOOT_COMPLETED: device rebooted (alarms are cleared on reboot)
 * - MY_PACKAGE_REPLACED: app was updated (alarms are also cleared by the system
 *   when the APK is replaced, so we must reschedule here too)
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "📱 Device boot completed. Checking auto-update preference...")
                rescheduleIfEnabled(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "📦 MY_PACKAGE_REPLACED received. Rescheduling alarm after APK update...")
                rescheduleIfEnabled(context)
            }
        }
    }

    private fun rescheduleIfEnabled(context: Context) {
        val userPrefs = UserPrefs(context)
        if (userPrefs.isAutoUpdateEnabled()) {
            Log.d(TAG, "✅ Auto-update is enabled. Rescheduling exact midnight alarm...")
            ExactAlarmScheduler.scheduleNextMidnightAlarm(context)
        } else {
            Log.d(TAG, "ℹ️ Auto-update disabled, skipping reschedule.")
        }
    }
}
