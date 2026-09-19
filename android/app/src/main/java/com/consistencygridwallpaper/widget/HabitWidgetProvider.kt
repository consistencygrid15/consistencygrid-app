package com.consistencygridwallpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.consistencygridwallpaper.R

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.HabitLogEntity
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.workers.WallpaperWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HabitWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> bindWidget(context, appWidgetManager, widgetId) }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive: action=$action")
        super.onReceive(context, intent)

        when (action) {
            ACTION_TOGGLE_HABIT -> {
                val habitId = intent.getStringExtra("habit_id") ?: return
                val isDone = intent.getBooleanExtra("is_done", false)
                Log.d(TAG, "Toggling habit: $habitId, currently isDone=$isDone via WorkManager")
                scheduleToggleHabit(context, habitId, isDone)
            }
            ACTION_DATA_CHANGED, "com.consistencygridwallpaper.ACTION_WIDGET_DATA_CHANGED" -> {
                notifyDataChanged(context)
            }
        }
    }

    companion object {
        private const val TAG = "HabitWidget"
        const val ACTION_TOGGLE_HABIT = "com.consistencygridwallpaper.ACTION_TOGGLE_HABIT"
        const val ACTION_DATA_CHANGED = "com.consistencygridwallpaper.ACTION_HABIT_WIDGET_DATA_CHANGED"

        fun notifyDataChanged(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, HabitWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                ids.forEach { widgetId ->
                    bindWidget(context, awm, widgetId)
                }
                awm.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }

        fun bindWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_habit)

            val serviceIntent = Intent(context, HabitWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "?ts=" + System.currentTimeMillis())
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty_view)

            val templateIntent = Intent(context, HabitWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_HABIT
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val templatePi = PendingIntent.getBroadcast(
                context, widgetId, templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, templatePi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        /** Schedule WorkManager job to toggle a habit — works even if app is killed */
        fun scheduleToggleHabit(context: Context, habitId: String, isDone: Boolean) {
            try {
                val inputData = Data.Builder()
                    .putString(HabitWidgetActionWorker.KEY_HABIT_ID, habitId)
                    .putBoolean(HabitWidgetActionWorker.KEY_IS_DONE, isDone)
                    .build()
                val work = OneTimeWorkRequestBuilder<HabitWidgetActionWorker>()
                    .setInputData(inputData)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "habit_toggle_$habitId",
                        ExistingWorkPolicy.REPLACE,
                        work
                    )
                Log.d(TAG, "✅ WorkManager job scheduled for habit: $habitId")
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager schedule failed: ${e.message}")
            }
        }
    }
}
