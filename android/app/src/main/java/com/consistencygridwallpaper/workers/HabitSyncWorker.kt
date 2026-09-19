package com.consistencygridwallpaper.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.UserPrefs
import java.util.concurrent.TimeUnit

/**
 * HabitSyncWorker — runs every 30 minutes via WorkManager.
 *
 * Pushes any locally unsynced habit ticks, new habits, or deletes
 * to the server — even when the app is completely closed.
 * Uses exponential backoff for retry on network failure.
 */
class HabitSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG       = "HabitSyncWorker"
        private const val WORK_NAME = "habit_periodic_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<HabitSyncWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "✅ HabitSyncWorker scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = UserPrefs(applicationContext)
            val token = prefs.getToken()

            if (token.isNullOrBlank()) {
                Log.d(TAG, "No auth token — skipping background sync")
                return Result.success()
            }

            Log.d(TAG, "Starting background habit sync...")
            val sync = SyncRepository(applicationContext)
            val success = sync.syncWithServer()
            if (success) {
                Log.d(TAG, "✅ Background habit sync complete")
                Result.success()
            } else {
                Log.e(TAG, "Background sync failed")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Background sync failed with exception: ${e.message}", e)
            Result.retry()
        }
    }
}
