package com.consistencygridwallpaper.reelcontrol.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.reelcontrol.workers.ReelWatchdogWorker
import java.util.concurrent.TimeUnit

/**
 * KeepAliveService — Persistent foreground service that prevents the OS
 * from killing the Reel tracking process.
 *
 * - START_STICKY: OS will restart this service automatically if killed
 * - Notification channel is created null-safely and only on supported OS versions
 * - Fully wrapped in try/catch — never crashes
 */
class KeepAliveService : Service() {

    companion object {
        private const val TAG = "KeepAliveService"
        private const val CHANNEL_ID = "reel_control_keepalive"
        private const val NOTIFICATION_ID = 9001
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🛡 KeepAliveService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            ensureNotificationChannel()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Reel Control Active")
                .setContentText("Monitoring scroll activity for digital wellbeing")
                .setSmallIcon(R.drawable.app_logo)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "✅ Foreground started")
        } catch (e: Exception) {
            Log.e(TAG, "❌ onStartCommand failed: ${e.message}", e)
        }
        // START_STICKY: if killed by OS, it will be restarted automatically
        return START_STICKY
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val manager = getSystemService(NotificationManager::class.java) ?: return
                if (manager.getNotificationChannel(CHANNEL_ID) != null) return // already exists
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Reel Control",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "Keeps reel tracking active in the background"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
                Log.d(TAG, "Notification channel created")
            } catch (e: Exception) {
                Log.e(TAG, "Channel creation failed: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "⚠️ KeepAliveService destroyed — will be restarted by START_STICKY")
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "🧹 App swiped from recents. Scheduling auto-restart...")

        // --- Layer 1: AlarmManager restart (fires in ~1 second) ---
        // ✅ Use setAndAllowWhileIdle (non-exact) instead of setExactAndAllowWhileIdle.
        // setExactAndAllowWhileIdle requires SCHEDULE_EXACT_ALARM permission on Android 13+
        // which many users may not have granted, causing silent failure.
        // Non-exact alarms work without any special permission on all Android versions.
        try {
            val restartIntent = Intent(applicationContext, KeepAliveService::class.java)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    applicationContext,
                    1,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    applicationContext,
                    1,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Non-exact alarm: no SCHEDULE_EXACT_ALARM permission required
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 2000L,
                pendingIntent
            )
            Log.d(TAG, "🕑 AlarmManager restart scheduled (non-exact, ~2s)")
        } catch (e: Exception) {
            Log.e(TAG, "AlarmManager restart failed: ${e.message}")
        }

        // --- Layer 2: WorkManager backup (fires within ~15 min if alarm fails) ---
        // Ensures the watchdog reschedules itself even on aggressive OEM ROMs.
        try {
            ReelWatchdogWorker.schedule(applicationContext)
            Log.d(TAG, "🛡 WorkManager watchdog re-scheduled as backup")
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager backup schedule failed: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}