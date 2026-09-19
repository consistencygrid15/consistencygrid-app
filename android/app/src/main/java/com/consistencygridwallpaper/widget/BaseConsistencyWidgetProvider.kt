package com.consistencygridwallpaper.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseConsistencyWidgetProvider : android.appwidget.AppWidgetProvider() {

    abstract val widgetType: WidgetType

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = try { goAsync() } catch (e: Exception) { null }
        updateWidgets(context, appWidgetManager, appWidgetIds, pendingResult)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == "com.consistencygridwallpaper.ACTION_WIDGET_DATA_CHANGED") {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, this::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                val pendingResult = try { goAsync() } catch (e: Exception) { null }
                updateWidgets(context, appWidgetManager, appWidgetIds, pendingResult)
            }
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun updateWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        pendingResult: android.content.BroadcastReceiver.PendingResult? = null
    ) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch state from Room
                val state = WallpaperDataSource.getWallpaperState(appContext)
                
                // Generate the premium Bitmap for this specific widget type
                val bitmap = WidgetCanvasEngine.generate(appContext, state, widgetType)
                
                // Update each widget instance
                appWidgetIds.forEach { widgetId ->
                    val views = RemoteViews(appContext.packageName, R.layout.widget_consistency)
                    views.setImageViewBitmap(R.id.widget_image, bitmap)
                    
                    // Launch intent configuration
                    val launchIntent = appContext.packageManager
                        .getLaunchIntentForPackage(appContext.packageName)?.apply {
                            val targetRoute = when (widgetType) {
                                WidgetType.STATS -> "habits"
                                WidgetType.WEEK_AGENDA -> "habits"
                                WidgetType.LIFE -> "goals"
                                WidgetType.YEAR -> "wallpaper_generator"
                                WidgetType.MONTH -> "wallpaper_generator"
                            }
                            putExtra("open_screen", targetRoute)
                            action = "com.consistencygridwallpaper.ACTION_WIDGET_CLICK_${widgetType.name}"
                        }
                    if (launchIntent != null) {
                        val pendingIntent = PendingIntent.getActivity(
                            appContext,
                            widgetId, // Use widgetId as requestCode to ensure uniqueness
                            launchIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                    }
                    
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                Log.e("BaseWidgetProvider", "Failed to update widgets for type $widgetType", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}

