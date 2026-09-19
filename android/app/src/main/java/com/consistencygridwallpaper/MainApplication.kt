package com.consistencygridwallpaper

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import com.consistencygridwallpaper.billing.BillingManager
import com.consistencygridwallpaper.repository.ReelControllerRepository
import com.consistencygridwallpaper.utils.NotificationHelper
import com.consistencygridwallpaper.workers.HabitSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainApplication : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        // Initialize CookieManager globally for better persistence
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        // ── Create notification channels at app start ────────────────────────
        // CRITICAL: Channels must exist before ANY WorkManager foreground job runs.
        // If a background worker starts (via AlarmManager/FCM) before this runs,
        // Android throws CannotPostForegroundServiceNotificationException and kills
        // the process. Creating them here in Application.onCreate() guarantees
        // they're always available.
        NotificationHelper.createNotificationChannels(this)

        if (isMainProcess()) {
            // ── Initialize Google Play Billing (restores Pro status on launch) ───────
            BillingManager.initialize(this)

            // ── Schedule background habit sync (runs every 30 min when online) ──────
            // Ensures offline ticks and new habits are pushed to the server even
            // when the user hasn't opened the app in a while.
            HabitSyncWorker.schedule(this)

            // ── Ensure midnight alarm & background updates are registered ──────────────────
            val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(this)
            if (userPrefs.isAutoUpdateEnabled()) {
                com.consistencygridwallpaper.workers.ExactAlarmScheduler.scheduleNextMidnightAlarm(this)
                com.consistencygridwallpaper.workers.MidnightWorkScheduler.schedule(this)
            }

            // ── Restore reel controller config from Room → SharedPreferences ──────
            // WHY: SharedPreferences can be wiped by OEM "Clean" apps or if the encrypted
            // prefs key resets.  Room (SQLite) is NEVER targeted by those cleaners.
            // Restoring here, before AccessibilityService starts reading prefs, ensures
            // the user's limits survive phone restarts and OEM cache clearing.
            appScope.launch {
                try {
                    ReelControllerRepository(applicationContext).restoreLocalFromRoom()
                } catch (e: Exception) {
                    android.util.Log.e("MainApplication", "Reel restore failed: ${e.message}")
                }
            }
        } else {
            android.util.Log.d("MainApplication", "Skipping main process init for background process: ${getProcessNameCompat()}")
        }
    }

    private fun isMainProcess(): Boolean {
        val processName = getProcessNameCompat()
        return processName == packageName
    }

    private fun getProcessNameCompat(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }
        try {
            val pid = android.os.Process.myPid()
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses
            if (processes != null) {
                for (info in processes) {
                    if (info.pid == pid) {
                        return info.processName
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }
}
