package com.consistencygridwallpaper.reelcontrol.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager

/**
 * 📅 ReelDateChangeReceiver
 *
 * Fires when the device's date changes (at midnight) or timezone changes.
 * Ensures daily reel stats are properly reset even if the app isn't open.
 *
 * FIX 1: Added ACTION_TIMEZONE_CHANGED — a timezone shift can cross midnight
 *         without emitting DATE_CHANGED, so we'd silently skip the reset.
 * FIX 2: Loop over ALL tracked packages instead of only Instagram. Previously
 *         YouTube, Snapchat, and TikTok stats were never reset at midnight.
 */
class ReelDateChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ReelDateChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.intent.action.DATE_CHANGED",
            "android.intent.action.TIME_SET",
            "android.intent.action.TIMEZONE_CHANGED" -> {
                Log.d(TAG, "📅 Date/Time/Timezone changed. Checking reel stats for all packages...")
                handleDateChange(context)
            }
        }
    }

    private fun handleDateChange(context: Context) {
        try {
            val prefs = PreferencesManager(context.applicationContext)
            // FIX: Trigger ensureDailyReset() for ALL tracked packages, not just Instagram.
            // getTodayReelCount() internally calls ensureDailyReset() which resets stats
            // if the stored date is different from today.
            for (pkg in PreferencesManager.TRACKED_PACKAGES) {
                prefs.getTodayReelCount(pkg)
            }
            Log.d(TAG, "✅ Daily reset check complete for ${PreferencesManager.TRACKED_PACKAGES.size} packages")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Date change handling error: ${e.message}", e)
        }
    }
}
