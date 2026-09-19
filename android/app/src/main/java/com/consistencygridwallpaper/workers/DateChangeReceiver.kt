package com.consistencygridwallpaper.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.consistencygridwallpaper.storage.UserPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * DateChangeReceiver — Triggers wallpaper update when the phone's date rolls over.
 *
 * Fires on:
 *   - ACTION_DATE_CHANGED  → OS guarantees this fires at midnight when date changes
 *   - ACTION_TIME_CHANGED  → Fires if user manually changes device time (handles edge cases)
 *
 * This is the most reliable midnight trigger on Android:
 * - Works even without internet
 * - Works even if AlarmManager is killed by OEM battery saver (Xiaomi, OPPO, etc.)
 * - Works even without FCM / server push
 * - The phone itself is the clock — 100% local time
 *
 * IMPORTANT: ACTION_DATE_CHANGED is a protected broadcast and CANNOT be registered
 * dynamically via registerReceiver() — it MUST be declared in AndroidManifest.xml.
 */
class DateChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DateChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_DATE_CHANGED -> {
                // Phone date rolled over — this is midnight!
                val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                Log.d(TAG, "📅 [DATE_CHANGED] Phone date changed at $now — triggering wallpaper update")
                triggerUpdate(context, "DATE_CHANGED")
            }
            Intent.ACTION_TIME_CHANGED -> {
                // User manually changed device time — check if date is different
                val userPrefs = UserPrefs(context)
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val lastUpdateDate = userPrefs.getLastUpdateDate()
                if (lastUpdateDate != todayDate) {
                    Log.d(TAG, "🕐 [TIME_CHANGED] Time changed and date differs ($lastUpdateDate → $todayDate) — triggering update")
                    triggerUpdate(context, "TIME_CHANGED")
                } else {
                    Log.d(TAG, "🕐 [TIME_CHANGED] Time changed but still same date ($todayDate) — no update needed")
                }
            }
        }
    }

    private fun triggerUpdate(context: Context, reason: String) {
        val userPrefs = UserPrefs(context)

        // Only trigger if auto-update is enabled
        if (!userPrefs.isAutoUpdateEnabled()) {
            Log.d(TAG, "[$reason] Auto-update is disabled — skipping")
            return
        }

        // Duplicate guard: don't update if already done today
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastUpdateDate = userPrefs.getLastUpdateDate()
        if (lastUpdateDate == todayDate) {
            Log.d(TAG, "[$reason] Already updated today ($todayDate) — skipping")
            return
        }

        Log.d(TAG, "[$reason] ✅ Conditions met — enqueuing WallpaperWorker & refreshing widgets")

        // Immediately update all home screen widgets for the new date
        com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(context)

        // Acquire WakeLock so CPU stays alive during WebView render
        MidnightReceiver.acquireWakeLock(context)

        // Re-schedule the AlarmManager alarm for the next day as well
        // (in case it was killed by the OEM, this re-arms it)
        ExactAlarmScheduler.scheduleNextMidnightAlarm(context)

        val workRequest = OneTimeWorkRequestBuilder<WallpaperWorker>()
            .addTag("DATE_CHANGE_UPDATE")
            .setInputData(
                androidx.work.Data.Builder()
                    .putString("TRIGGER", reason)
                    .putBoolean("FORCE_UPDATE", false)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "WallpaperUpdate_DateChange",
            ExistingWorkPolicy.APPEND_OR_REPLACE,   // Guarantee it runs even if older jobs are stuck
            workRequest
        )
        Log.d(TAG, "[$reason] 🚀 WallpaperWorker enqueued via WallpaperUpdate_DateChange")
    }
}
