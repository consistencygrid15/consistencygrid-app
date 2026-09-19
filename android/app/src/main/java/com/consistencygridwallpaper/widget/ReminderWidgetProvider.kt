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
import com.consistencygridwallpaper.repository.SyncRepository
import com.consistencygridwallpaper.workers.WallpaperWorker

class ReminderWidgetProvider : android.appwidget.AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId -> bindWidget(context, appWidgetManager, widgetId) }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive: action=$action")
        super.onReceive(context, intent)

        when (action) {
            ACTION_DISMISS_REMINDER -> {
                val reminderId = intent.getStringExtra("reminder_id") ?: return
                Log.d(TAG, "Completing reminder: $reminderId via WorkManager")
                scheduleCompleteReminder(context, reminderId)
            }
            ACTION_DATA_CHANGED, "com.consistencygridwallpaper.ACTION_WIDGET_DATA_CHANGED" -> {
                notifyDataChanged(context)
            }
        }
    }

    companion object {
        private const val TAG = "ReminderWidget"
        const val ACTION_DISMISS_REMINDER = "com.consistencygridwallpaper.ACTION_DISMISS_REMINDER"
        const val ACTION_DATA_CHANGED = "com.consistencygridwallpaper.ACTION_REMINDER_WIDGET_DATA_CHANGED"

        fun notifyDataChanged(context: Context) {
            val awm = AppWidgetManager.getInstance(context)
            val ids = awm.getAppWidgetIds(ComponentName(context, ReminderWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                ids.forEach { widgetId ->
                    bindWidget(context, awm, widgetId)
                }
                awm.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }

        fun bindWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_reminder)

            val serviceIntent = Intent(context, ReminderWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "?ts=" + System.currentTimeMillis())
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty_view)

            val templateIntent = Intent(context, ReminderWidgetProvider::class.java).apply {
                action = ACTION_DISMISS_REMINDER
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            val templatePi = PendingIntent.getBroadcast(
                context, widgetId, templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, templatePi)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        /** Schedule WorkManager job to complete a reminder — works even if app is killed */
        fun scheduleCompleteReminder(context: Context, reminderId: String) {
            try {
                val inputData = Data.Builder()
                    .putString(ReminderWidgetActionWorker.KEY_REMINDER_ID, reminderId)
                    .build()
                val work = OneTimeWorkRequestBuilder<ReminderWidgetActionWorker>()
                    .setInputData(inputData)
                    .build()
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "reminder_complete_$reminderId",
                        ExistingWorkPolicy.REPLACE,
                        work
                    )
                Log.d(TAG, "✅ WorkManager job scheduled for reminder: $reminderId")
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager schedule failed: ${e.message}")
            }
        }
    }
}
