package com.consistencygridwallpaper.workers

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * MidnightWorkScheduler — Layer 2 backup for the AlarmManager midnight trigger.
 *
 * Architecture summary:
 *   Layer 1 (Primary)   → AlarmManager.setExactAndAllowWhileIdle  [ExactAlarmScheduler]
 *   Layer 2 (Backup)    → WorkManager PeriodicWorkRequest          [this class]
 *   Layer 3 (Tertiary)  → FCM Data Message                         [WallpaperMessagingService]
 *   Layer 4 (Recovery)  → On-app-open missed-day check             [MainActivity]
 *
 * Why PeriodicWorkRequest is the ideal backup:
 * ─────────────────────────────────────────────────────────────────────────────
 * • It uses JobScheduler internally → the OS scheduler, which is immune to most
 *   OEM battery killers (Xiaomi MIUI, ColorOS, FunTouch, etc.) that block
 *   AlarmManager-based wakeups.
 * • Survives device reboots WITHOUT a BootReceiver because WorkManager persists
 *   its job queue across reboots via JobScheduler / Room.
 * • Survives app updates — JobScheduler entries are keyed by package name, not
 *   cleared when the APK is replaced the way AlarmManager alarms are.
 * • The duplicate-run guard inside WallpaperWorker.doWork() ensures that even
 *   if both Layer 1 AND Layer 2 fire on the same day, only one update runs.
 *
 * Why PeriodicWorkRequest is NOT sufficient alone:
 * ─────────────────────────────────────────────────────────────────────────────
 * • PeriodicWorkRequest has a minimum interval of 15 minutes and a flex window.
 *   It is NOT guaranteed to run at exactly midnight — WorkManager fires it within
 *   a flex window at the end of the period. Use Layer 1 for precision timing.
 * • The flexIntervalMinutes controls when in the last N minutes of each 24-hour
 *   cycle the work is allowed to run.
 *
 * How it interacts with the duplicate guard:
 * ─────────────────────────────────────────────────────────────────────────────
 * If Layer 1 (alarm) already ran and marked lastUpdateDate = today, this worker
 * will call doWork(), hit the guard, log "Already updated today — skipping",
 * and return Result.success() within milliseconds. No WebView is spun up.
 *
 * KEEP_EXISTING policy:
 * ─────────────────────────────────────────────────────────────────────────────
 * We use KEEP rather than REPLACE so re-scheduling (e.g. on every app open) does
 * not reset the 24-hour countdown. If you call schedule() first thing in onCreate(),
 * subsequent opens won't delay the next fire time.
 */
object MidnightWorkScheduler {

    private const val TAG = "MidnightWorkScheduler"
    private const val WORK_NAME = "WallpaperPeriodicBackup_Daily"

    // How often the job repeats. Must be ≥ 15 minutes (WorkManager minimum).
    private const val REPEAT_INTERVAL_HOURS = 24L

    // Flex window: the job may fire anywhere in the last FLEX_MINUTES of a 24-hour period.
    // A 45-minute flex gives WorkManager enough room to batch efficiently while still hitting
    // the midnight window even if the alarm fires 30 minutes late on an OEM device.
    private const val FLEX_MINUTES = 45L

    private fun calculateInitialDelayMs(): Long {
        val now = java.util.Calendar.getInstance()
        val target = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    /**
     * Schedules (or keeps alive) the 24-hour periodic backup job.
     *
     * Safe to call multiple times — KEEP policy means the existing job is not
     * disturbed if it's already running. Call this from:
     *  - MainActivity.onCreate()
     *  - BootReceiver.onReceive()
     */
    fun schedule(context: Context) {
        val userPrefs = com.consistencygridwallpaper.storage.UserPrefs(context)
        val isAligned = userPrefs.getBoolean("isPeriodicWorkAlignedToMidnight", false)

        val initialDelayMs = calculateInitialDelayMs()
        val delayHours = TimeUnit.MILLISECONDS.toHours(initialDelayMs)
        val delayMinutes = TimeUnit.MILLISECONDS.toMinutes(initialDelayMs) % 60
        Log.d(TAG, "Calculating initial delay to midnight: ${delayHours}h ${delayMinutes}m ($initialDelayMs ms)")

        // NO network constraint — rendering is 100% local (Room DB + WebView asset).
        // Internet is needed only for optional server sync (analytics), which we do
        // best-effort after the wallpaper is applied, not before.
        val request = PeriodicWorkRequestBuilder<WallpaperWorker>(
            REPEAT_INTERVAL_HOURS, TimeUnit.HOURS,
            FLEX_MINUTES,           TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("TRIGGER", "PERIODIC_BACKUP")
                    .build()
            )
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.MINUTES   // 10 → 20 → 40 min backoff on failure
            )
            .addTag("WALLPAPER_PERIODIC_BACKUP")
            .build()

        if (!isAligned) {
            // Force update once to align existing/old periodic work requests to midnight
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            userPrefs.setBoolean("isPeriodicWorkAlignedToMidnight", true)
            Log.d(TAG, "✅ Periodic backup job updated to midnight (UPDATE policy used to reschedule)")
        } else {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "✅ Periodic backup job scheduled (24h interval, ${FLEX_MINUTES}min flex, KEEP policy)")
        }
    }

    /**
     * Cancels the periodic backup job.
     * Call this only if the user explicitly disables auto-update.
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Log.d(TAG, "🗑️ Periodic backup job cancelled")
    }
}
