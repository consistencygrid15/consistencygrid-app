package com.consistencygridwallpaper

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.consistencygridwallpaper.auth.AuthManager
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.utils.NotificationHelper
import com.consistencygridwallpaper.workers.ExactAlarmScheduler
import com.consistencygridwallpaper.workers.MidnightWorkScheduler

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // ── Splash Screen: MUST be called before super.onCreate() ─────────
        // Without this, Theme.SplashScreen is not AppCompat-compatible → crash
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val authManager = AuthManager.getInstance(this)
        val userPrefs = UserPrefs(this)
        
        NotificationHelper.createNotificationChannels(this)
        setupBackgroundTasks(userPrefs, authManager)

        // ── Recovery Check ────────────────────────────────────────────────
        // CRITICAL: NEVER trigger wallpaper update on fresh install before the user completes onboarding!
        if (userPrefs.isOnboarded()) {
            val todayDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
            val lastUpdate = userPrefs.getLastUpdateDate()
            if (lastUpdate != todayDate) {
                android.util.Log.d("MainActivity", "Recovery check: last update ($lastUpdate) is not today ($todayDate). Triggering update.")
                val recoveryRequest = androidx.work.OneTimeWorkRequestBuilder<com.consistencygridwallpaper.workers.WallpaperWorker>()
                    .setInputData(
                        androidx.work.Data.Builder()
                            .putString("TRIGGER", "RECOVERY_ON_OPEN")
                            .putBoolean("FORCE_UPDATE", false)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
                    "WallpaperUpdate_Recovery",
                    androidx.work.ExistingWorkPolicy.KEEP,
                    recoveryRequest
                )
            }
        }

        val nextIntent = Intent(this, com.consistencygridwallpaper.ui.compose.NativeAppActivity::class.java).apply {
            intent.extras?.let { putExtras(it) }
            intent.action?.let { action = it }
        }
        startActivity(nextIntent)
        finish()
    }

    private fun setupBackgroundTasks(userPrefs: UserPrefs, authManager: AuthManager) {
        if (userPrefs.isAutoUpdateEnabled() || authManager.isLoggedIn()) {
            if (!userPrefs.isAutoUpdateEnabled()) userPrefs.setAutoUpdate(true)
            ExactAlarmScheduler.scheduleNextMidnightAlarm(this)
            MidnightWorkScheduler.schedule(this)
        }
    }
}
