package com.consistencygridwallpaper.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * MidnightReceiver - Handles exact constraints-free wallpaper updates.
 *
 * Triggered precisely at 12:00 AM by AlarmManager's setExactAndAllowWhileIdle.
 * Wakes the device safely and queues the WallpaperWorker to run instantly as an expedited job.
 */
class MidnightReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightReceiver"
        private var wakeLock: PowerManager.WakeLock? = null

        fun releaseWakeLock() {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    Log.d(TAG, "🔓 WakeLock released by WallpaperWorker")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Midnight Alarm Triggered! Waking up device...")

        // 1. Acquire WakeLock to prevent the device from sleeping before WorkManager takes over
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ConsistencyGrid::MidnightWakeLock"
        )
        wakeLock?.acquire(2 * 60 * 1000L /* 2 minutes max to prevent infinite drain */)
        Log.d(TAG, "🔒 WakeLock acquired safely")

        // 2. Schedule the next day's alarm immediately
        ExactAlarmScheduler.scheduleNextMidnightAlarm(context)

        // 3. Launch WallpaperWorker (Expedited = bypass Doze & App Standby Buckets)
        val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag("MIDNIGHT_UPDATE_EXECUTION")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "WallpaperUpdate_Exact",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
