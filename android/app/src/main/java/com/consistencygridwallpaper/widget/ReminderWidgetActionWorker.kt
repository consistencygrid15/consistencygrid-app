package com.consistencygridwallpaper.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.workers.WallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that handles reminder completion from widget taps.
 *
 * WHY WorkManager instead of goAsync() + CoroutineScope?
 * - When the app is killed, Android can also kill a CoroutineScope mid-execution.
 * - WorkManager GUARANTEES the job will complete even if the process is killed and
 *   restarted. This is the ONLY reliable mechanism for background work when app is off.
 */
class ReminderWidgetActionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val reminderId = inputData.getString(KEY_REMINDER_ID)
        if (reminderId.isNullOrBlank()) {
            Log.e(TAG, "No reminder_id provided — skipping")
            return@withContext Result.failure()
        }

        try {
            val db = AppDatabase.getDatabase(applicationContext)

            // 1. Mark reminder as completed in local database
            db.reminderDao().completeReminder(reminderId)
            Log.d(TAG, "✅ Reminder completed in DB: $reminderId")

            // 2. Refresh all widgets
            WidgetUpdateHelper.notifyAllWidgets(applicationContext)
            Log.d(TAG, "✅ All widgets notified of data change")

            // 3. Update the wallpaper to reflect the change
            WallpaperWorker.scheduleImmediate(applicationContext)

            // 4. Push the change to the server
            try {
                SyncRepository(applicationContext).syncWithServer()
                Log.d(TAG, "✅ Server sync completed")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Server sync failed (offline?) — queueing retry: ${e.message}")
                SyncRepository.scheduleRetry(applicationContext)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to complete reminder: ${e.message}", e)
            // Retry up to WorkManager's default retry policy
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ReminderWidgetAction"
        const val KEY_REMINDER_ID = "reminder_id"
    }
}
