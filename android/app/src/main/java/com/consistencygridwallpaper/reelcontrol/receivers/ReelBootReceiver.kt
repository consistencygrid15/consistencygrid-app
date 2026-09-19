package com.consistencygridwallpaper.reelcontrol.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.consistencygridwallpaper.reelcontrol.service.KeepAliveService
import com.consistencygridwallpaper.reelcontrol.workers.ReelWatchdogWorker

/**
 * ReelBootReceiver — Fires on BOOT_COMPLETED, MY_PACKAGE_REPLACED, and QUICKBOOT_POWERON.
 *
 * After reboot:
 *  - The AccessibilityService (ReelTrackingService) is auto-restarted by the OS IF the
 *    user had it enabled in system settings. We do NOT need to start it manually.
 *  - KeepAliveService will be started by ReelTrackingService.onServiceConnected() once
 *    the accessibility service comes up. No need to start it from here.
 *  - We ONLY schedule the WorkManager Watchdog here, which is always safe from any context.
 *
 * ⚠️ WHY we removed startForegroundService() from here:
 *  Android 12+ (API 31+) throws ForegroundServiceStartNotAllowedException when
 *  startForegroundService() is called from a background BroadcastReceiver context.
 *  The system only allows this in certain foreground states. Calling it from a boot
 *  receiver is unreliable and can cause crashes on Pixel, Samsung, and others on Android 12+.
 */
class ReelBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReelBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.d(TAG, "📲 Boot/update event received: $action")

        try {
            val prefs = PreferencesManager(context.applicationContext)

            if (!prefs.isFeatureEnabled) {
                Log.d(TAG, "ℹ️ Feature disabled — skipping boot setup")
                return
            }

            // ✅ Safe: WorkManager scheduling is always allowed from any BroadcastReceiver context.
            // The Watchdog will run within 15 minutes and verify the accessibility service state.
            // ReelTrackingService (AccessibilityService) is restarted automatically by the OS —
            // once it connects, it will start KeepAliveService itself via onServiceConnected().
            ReelWatchdogWorker.schedule(context.applicationContext)
            Log.d(TAG, "✅ Watchdog scheduled after boot/update")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ReelBootReceiver error: ${e.message}", e)
        }
    }
}
