package com.consistencygridwallpaper.reelcontrol.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.consistencygridwallpaper.reelcontrol.service.KeepAliveService
import java.util.concurrent.TimeUnit

/**
 * ReelWatchdogWorker — Runs every 15 minutes via WorkManager.
 *
 * Checks:
 *  1. If the feature is enabled
 *  2. If the AccessibilityService is still running
 *
 * If the service is dead (killed by OEM), it:
 *  a. Tries to restart the KeepAlive foreground service
 *  b. Sends a notification guiding the user to re-enable it
 *
 * This is the most robust approach on MIUI / aggressive OEM ROMs
 * since WorkManager jobs survive app removal from recents.
 */
class ReelWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "ReelWatchdog"
        private const val CHANNEL_ID = "reel_watchdog_alerts"
        private const val NOTIF_ID = 9002
        private const val WORK_NAME = "reel_watchdog_periodic"

        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val request = PeriodicWorkRequestBuilder<ReelWatchdogWorker>(
                    15, TimeUnit.MINUTES,
                    5, TimeUnit.MINUTES  // flex interval
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                    .build()

                // REPLACE: ensures we always have a fresh worker
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,                  
                    request
                )
                Log.d(TAG, "✅ Watchdog scheduled/updated")
            } catch (e: Exception) {
                Log.w(TAG, "Watchdog schedule bypassed (${e.message})")
            }
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override fun doWork(): Result {
        return try {
            val prefs = PreferencesManager(applicationContext)

            // Only run if the feature is enabled
            if (!prefs.isFeatureEnabled) {
                Log.d(TAG, "Feature disabled — watchdog idle")
                return Result.success()
            }

            // ✅ FIX: Check system settings directly — NOT the in-memory isRunning flag.
            // isRunning is a process-level flag: it becomes false when the process is killed
            // even if the OS has already restarted the AccessibilityService in a new process.
            // Settings.Secure is the real system-level source of truth.
            val isServiceEnabled = isAccessibilityServiceEnabled()

            if (!isServiceEnabled) {
                Log.w(TAG, "⚠️ Accessibility service is DISABLED in system settings — notifying user")
                sendRestoreNotification()
            } else {
                Log.d(TAG, "✅ Accessibility service is enabled in system settings")
                // Service is enabled in system settings — OS will keep it running.
                // We do NOT call startForegroundService() here from a background WorkManager
                // context because Android 12+ throws ForegroundServiceStartNotAllowedException
                // when called from a background process. KeepAliveService is already kept
                // alive by START_STICKY and onTaskRemoved alarm chain.
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog error: ${e.message}", e)
            Result.retry()
        }
    }

    /**
     * Checks whether the AccessibilityService is ENABLED in system settings.
     *
     * ✅ WHY Settings.Secure and NOT ReelTrackingService.isRunning:
     *  - `isRunning` is an in-memory static flag living in the app process.
     *  - When the OS kills the process (OEM RAM cleaner, Doze, etc.) isRunning = false.
     *  - But the OS often restarts the AccessibilityService in a new process → isRunning
     *    is true again in that process but the WorkManager context is a DIFFERENT process
     *    and still sees false. This caused false "service dead" notifications.
     *  - Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES is a system database entry that
     *    persists across process kills. If it contains our package name, the service is
     *    enabled and the OS WILL run it (or is already running it).
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                applicationContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val isEnabled = enabledServices.contains(applicationContext.packageName, ignoreCase = true)
            Log.d(TAG, "Accessibility enabled in settings: $isEnabled (services: $enabledServices)")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read accessibility settings: ${e.message}")
            // Fail safe: if we can't check, assume it's running (avoid false notifications)
            true
        }
    }

    private fun sendRestoreNotification() {
        try {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Reel Counter Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when the reel counter needs to be re-enabled"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }

            val fixIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val fixPending = PendingIntent.getActivity(
                applicationContext, 0, fixIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle("🎯 Reel Counter Paused")
                .setContentText("Tap to re-enable Reel Control so it keeps tracking your reels")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Your phone turned off the Reel Counter. Tap here → find 'ConsistencyGrid' in Accessibility → tap to re-enable it. Takes 5 seconds!"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(fixPending)
                .addAction(0, "RE-ENABLE NOW", fixPending)
                .build()

            manager.notify(NOTIF_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send notification: ${e.message}")
        }
    }
}
