package com.consistencygridwallpaper.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object WidgetUpdateHelper {
    private const val TAG = "WidgetUpdateHelper"

    fun notifyAllWidgets(context: Context) {
        val appContext = context.applicationContext

        // 1. Explicitly notify interactive list widgets first
        try {
            HabitWidgetProvider.notifyDataChanged(appContext)
            GoalWidgetProvider.notifyDataChanged(appContext)
            ReminderWidgetProvider.notifyDataChanged(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to notify list widgets directly", e)
        }

        // 2. Broadcast to all registered AppWidgetProviders
        val providers = listOf(
            YearGridWidgetProvider::class.java,
            MonthGridWidgetProvider::class.java,
            LifeGridWidgetProvider::class.java,
            StatsWidgetProvider::class.java,
            WeekAgendaWidgetProvider::class.java,
            HabitWidgetProvider::class.java,
            GoalWidgetProvider::class.java,
            ReminderWidgetProvider::class.java
        )

        val awm = AppWidgetManager.getInstance(appContext)

        providers.forEach { cls ->
            try {
                // Custom broadcast action
                val customIntent = Intent(appContext, cls).apply {
                    action = "com.consistencygridwallpaper.ACTION_WIDGET_DATA_CHANGED"
                }
                appContext.sendBroadcast(customIntent)

                // Standard system update intent for existing widget instances
                val compName = ComponentName(appContext, cls)
                val ids = awm.getAppWidgetIds(compName)
                if (ids.isNotEmpty()) {
                    val systemIntent = Intent(appContext, cls).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    appContext.sendBroadcast(systemIntent)
                }
                Log.d(TAG, "Notified ${cls.simpleName} (${ids.size} active instances)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify ${cls.simpleName}", e)
            }
        }
    }
}

