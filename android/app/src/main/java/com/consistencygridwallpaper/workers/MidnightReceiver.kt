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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * MidnightReceiver — Handles exact alarm wallpaper updates.
 *
 * Triggered at the configured update time by AlarmManager.setExactAndAllowWhileIdle.
 * Wakes the device safely and queues WallpaperWorker as an expedited job.
 *
 * Key safety measures:
 * - Duplicate-run guard lives in WallpaperWorker (single source of truth)
 * - 10-minute WakeLock timeout (covers 90s render + network + overhead)
 * - KEEP policy so FCM and Alarm don't spawn two workers simultaneously
 * - [TRIGGER=ALARM] structured logs for logcat tracing
 */
class MidnightReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightReceiver"
        const val WAKELOCK_TAG = "ConsistencyGrid::MidnightWakeLock"
        private const val WAKELOCK_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

        // Shared WakeLock — held from onReceive() until WallpaperWorker.doWork() finishes.
        // Also acquired by WallpaperMessagingService so the FCM path has the same protection.
        @Volatile
        private var wakeLock: PowerManager.WakeLock? = null

        /**
         * Acquires a 10-minute WakeLock. Safe to call from any path (Alarm or FCM).
         * Idempotent: if a lock is already held it is released first to avoid leaks.
         */
        fun acquireWakeLock(context: Context) {
            try {
                // Release any stale lock before acquiring a fresh one
                val existing = wakeLock
                if (existing != null && existing.isHeld) {
                    existing.release()
                    Log.d(TAG, "🔓 Released stale WakeLock before re-acquiring")
                }
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).also { wl ->
                    wl.acquire(WAKELOCK_TIMEOUT_MS)
                    Log.d(TAG, "🔒 WakeLock acquired (10 min max)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to acquire WakeLock", e)
            }
        }

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
        val firedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        Log.d(TAG, "[TRIGGER=ALARM] ⏰ Alarm fired at $firedAt")

        val forceUpdate = intent.getBooleanExtra("FORCE_UPDATE", false)

        // ── Duplicate-run guard ───────────────────────────────────────────
        // If the wallpaper was already successfully updated today, skip
        // launching a new worker and just reschedule tomorrow's alarm.
        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(context)
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastUpdateDate = userPrefs.getLastUpdateDate()
        if (!forceUpdate && lastUpdateDate == todayDate) {
            Log.d(TAG, "Already updated today ($todayDate). Skipping worker launch.")
            ExactAlarmScheduler.scheduleNextMidnightAlarm(context)
            return
        }
        // ─────────────────────────────────────────────────────────────────

        // 1. Acquire WakeLock — keeps CPU awake until WallpaperWorker finishes
        acquireWakeLock(context)

        // 2. Reschedule next alarm BEFORE enqueuing the worker.
        //    If the process dies mid-render, tomorrow's alarm is already set.
        Log.d(TAG, "[TRIGGER=ALARM] Rescheduling next alarm...")
        ExactAlarmScheduler.scheduleNextMidnightAlarm(context)

        // 3. Enqueue WallpaperWorker
        //    EXPEDITED → bypasses App Standby Buckets & Doze
        //    KEEP      → if FCM already started a worker, don't spawn a duplicate;
        //                the duplicate-run guard inside doWork() handles day-level dedup
        val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag("MIDNIGHT_UPDATE_EXECUTION")
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("TRIGGER", "ALARM")
                    .putBoolean("FORCE_UPDATE", forceUpdate)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, // 10 minutes initial, then 20, 40
                TimeUnit.MINUTES
            )
            .build()

        // When forced (testing), cancel any stuck job first so KEEP policy doesn't block
        if (forceUpdate) {
            WorkManager.getInstance(context).cancelUniqueWork("WallpaperUpdate_Daily")
            Log.d(TAG, "🗑️ Cancelled existing job for forced test")
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            "WallpaperUpdate_Daily",
            if (forceUpdate) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            workRequest
        )
        Log.d(TAG, "🚀 WallpaperWorker enqueued (force=$forceUpdate)")
    }
}
