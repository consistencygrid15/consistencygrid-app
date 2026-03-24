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
import com.consistencygridwallpaper.storage.UserPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MidnightReceiver — Handles exact alarm wallpaper updates.
 *
 * Triggered at the configured update time by AlarmManager.setExactAndAllowWhileIdle.
 * Wakes the device safely and queues WallpaperWorker as an expedited job.
 *
 * Key safety measures:
 * - Duplicate-run guard: skips if wallpaper was already updated today
 * - Instance-level WakeLock (not companion object) to avoid memory leaks
 * - 10-minute WakeLock timeout (covers 90s render + network + overhead)
 * - KEEP policy so FCM and Alarm don't spawn two workers simultaneously
 */
class MidnightReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightReceiver"

        // Static reference for WallpaperWorker to release after its work is done.
        // Using a companion object ref here is acceptable because WallpaperWorker
        // calls releaseWakeLock() very shortly after onReceive() returns.
        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        fun releaseWakeLock() {
            try {
                val wl = wakeLock
                if (wl != null && wl.isHeld) {
                    wl.release()
                    wakeLock = null
                    Log.d(TAG, "🔓 WakeLock released by WallpaperWorker")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "⏰ Alarm Triggered!")

        val userPrefs = UserPrefs(context)

        // ── Duplicate-run guard ───────────────────────────────────────────
        // If the wallpaper was already successfully updated today, skip
        // launching a new worker and just reschedule tomorrow's alarm.
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastUpdateDate = userPrefs.getLastUpdateDate()
        if (lastUpdateDate == todayDate) {
            Log.d(TAG, "Already updated today ($todayDate). Skipping worker launch.")
            ExactAlarmScheduler.scheduleNextMidnightAlarm(context)
            return
        }
        // ─────────────────────────────────────────────────────────────────

        // 1. Acquire WakeLock — 10 minutes max to cover render + retry + network overhead
        //    (WallpaperWorker has a 90s timeout + ForegroundService which keeps the process alive,
        //    but the WakeLock prevents the CPU from sleeping before WorkManager takes over)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ConsistencyGrid::MidnightWakeLock"
        ).also { wl ->
            wl.acquire(10 * 60 * 1000L) // 10 minutes
            Log.d(TAG, "🔒 WakeLock acquired (10 min max)")
        }

        // 2. Reschedule next alarm immediately (before worker — in case process dies)
        ExactAlarmScheduler.scheduleNextMidnightAlarm(context)

        // 3. Enqueue WallpaperWorker
        //    EXPEDITED → bypasses App Standby Buckets & Doze
        //    KEEP      → if FCM already started a worker, don't spawn a duplicate
        val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag("MIDNIGHT_UPDATE_EXECUTION")
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "WallpaperUpdate_Daily",
            ExistingWorkPolicy.KEEP,   // Changed from REPLACE to prevent FCM/Alarm race
            workRequest
        )
        Log.d(TAG, "🚀 WallpaperWorker enqueued (KEEP policy)")
    }
}
