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
 * WorkManager worker that handles goal completion from widget taps.
 *
 * WHY WorkManager instead of goAsync() + CoroutineScope?
 * - When the app is killed, Android can also kill a CoroutineScope mid-execution.
 * - WorkManager GUARANTEES the job will complete even if the process is killed and
 *   restarted. This is the ONLY reliable mechanism for background work when app is off.
 */
class GoalWidgetActionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val goalId = inputData.getString(KEY_GOAL_ID)
        if (goalId.isNullOrBlank()) {
            Log.e(TAG, "No goal_id provided — skipping")
            return@withContext Result.failure()
        }

        try {
            val db = AppDatabase.getDatabase(applicationContext)

            // 1. Mark goal as completed in local database
            val goal = db.goalDao().getActiveGoalsList().find { it.id == goalId }
            if (goal != null) {
                db.goalDao().insertGoal(
                    goal.copy(
                        isCompleted = true,
                        progress    = 100,
                        isSynced    = false,
                        updatedAt   = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "✅ Goal completed in DB: $goalId")
            } else {
                Log.w(TAG, "Goal not found: $goalId — may already be completed or deleted")
            }

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
            Log.e(TAG, "❌ Failed to complete goal: ${e.message}", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "GoalWidgetAction"
        const val KEY_GOAL_ID = "goal_id"
    }
}
