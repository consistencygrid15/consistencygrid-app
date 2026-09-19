package com.consistencygridwallpaper.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.HabitLogEntity
import com.consistencygridwallpaper.workers.WallpaperWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager worker that handles habit toggle from widget taps.
 *
 * WHY WorkManager instead of goAsync() + CoroutineScope?
 * - When the app is killed, Android can also kill a CoroutineScope mid-execution.
 * - WorkManager GUARANTEES the job will complete even if the process is killed and
 *   restarted. This is the ONLY reliable mechanism for background work when app is off.
 */
class HabitWidgetActionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val habitId = inputData.getString(KEY_HABIT_ID)
        val isDone = inputData.getBoolean(KEY_IS_DONE, false)

        if (habitId.isNullOrBlank()) {
            Log.e(TAG, "No habit_id provided — skipping")
            return@withContext Result.failure()
        }

        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val newDone = !isDone

            // 1. Upsert the habit log with a stable/deterministic ID (matches server format)
            db.habitLogDao().insertLog(
                HabitLogEntity(
                    id      = "${habitId}_${today}",
                    habitId = habitId,
                    date    = today,
                    done    = newDone,
                    isSynced = false
                )
            )
            Log.d(TAG, "✅ Habit toggled in DB: $habitId → done=$newDone")

            // 2. Refresh all widgets
            WidgetUpdateHelper.notifyAllWidgets(applicationContext)
            Log.d(TAG, "✅ All widgets notified of data change")

            // 3. Update the wallpaper
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
            Log.e(TAG, "❌ Failed to toggle habit: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "HabitWidgetAction"
        const val KEY_HABIT_ID = "habit_id"
        const val KEY_IS_DONE  = "is_done"
    }
}
