package com.consistencygridwallpaper.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Async worker for fetching DB data and rendering WidgetCanvasEngine without blocking.
 */
class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appWidgetIds = inputData.getIntArray("appWidgetIds") ?: return@withContext Result.success()
        val typeStr = inputData.getString("widgetType") ?: return@withContext Result.success()
        
        Log.d("WidgetUpdateWorker", "Updating ${appWidgetIds.size} widgets natively. Type: $typeStr")
        
        try {
            val type = WidgetType.valueOf(typeStr)
            
            // Fetch state from Room
            val state = WallpaperDataSource.getWallpaperState(applicationContext)
            
            // Generate the premium Bitmap for this specific widget type
            val bitmap = WidgetCanvasEngine.generate(applicationContext, state, type)
            
            val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
            
            // Update each widget instance
            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(applicationContext.packageName, R.layout.widget_consistency)
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                
                // Intention to launch app
                val launchIntent = applicationContext.packageManager
                    .getLaunchIntentForPackage(applicationContext.packageName)?.apply {
                        val targetRoute = when (type) {
                            WidgetType.STATS -> "habits"
                            WidgetType.WEEK_AGENDA -> "habits"
                            WidgetType.LIFE -> "goals"
                            WidgetType.YEAR -> "wallpaper_generator"
                            WidgetType.MONTH -> "wallpaper_generator"
                        }
                        putExtra("open_screen", targetRoute)
                        action = "com.consistencygridwallpaper.ACTION_WIDGET_CLICK_${type.name}"
                    }
                if (launchIntent != null) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        applicationContext, 
                        0, 
                        launchIntent, 
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_image, pendingIntent)
                }
                
                appWidgetManager.updateAppWidget(widgetId, views)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("WidgetUpdateWorker", "Failed to update widgets", e)
            Result.failure()
        }
    }
}
