package com.consistencygridwallpaper.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.consistencygridwallpaper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that forces a guaranteed refresh of the Reminder widget list.
 * This runs in the background process — no app foreground required.
 * Called after any reminder completion/un-completion from widget or app.
 */
class ReminderWidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        try {
            val awm = AppWidgetManager.getInstance(applicationContext)
            val ids = awm.getAppWidgetIds(
                ComponentName(applicationContext, ReminderWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                awm.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
                Log.d(TAG, "✅ Reminder widget refresh completed for ${ids.size} widget(s)")
            } else {
                Log.d(TAG, "No reminder widgets found — nothing to refresh")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh reminder widget", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "ReminderWidgetRefresh"
    }
}
