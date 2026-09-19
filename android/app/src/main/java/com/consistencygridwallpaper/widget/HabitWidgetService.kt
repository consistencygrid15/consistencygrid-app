package com.consistencygridwallpaper.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.storage.room.AppDatabase
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HabitWidgetService : RemoteViewsService() {
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d("HabitWidgetService", "onCreate called")
    }

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        android.util.Log.d("HabitWidgetService", "onGetViewFactory called for widgetId=$widgetId")
        return HabitWidgetFactory(applicationContext)
    }
}

class HabitWidgetFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class HabitRow(val id: String, val title: String, val isDone: Boolean)

    private var rows = listOf<HabitRow>()

    override fun onCreate() {
        android.util.Log.d("HabitWidgetFactory", "onCreate called")
    }
    override fun onDestroy() {
        android.util.Log.d("HabitWidgetFactory", "onDestroy called")
    }

    override fun onDataSetChanged() {
        val db = AppDatabase.getDatabase(ctx)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        try {
            val habits = db.habitDao().getActiveHabitsListSync()
            val logs = db.habitLogDao().getLogsForDateSync(today)
            val doneIds = logs.filter { it.done }.map { it.habitId }.toSet()
            rows = habits.map { HabitRow(it.id, it.title, doneIds.contains(it.id)) }
            android.util.Log.d("HabitWidget", "onDataSetChanged: loaded ${rows.size} habits today=$today")
        } catch (e: Exception) {
            android.util.Log.e("HabitWidget", "Error in onDataSetChanged", e)
        }
    }

    override fun getCount(): Int {
        android.util.Log.d("HabitWidget", "getCount: ${rows.size}")
        return rows.size
    }

    override fun getViewTypeCount() = 1
    override fun hasStableIds() = true
    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long {
        if (position < 0 || position >= rows.size) return 0L
        return rows[position].id.hashCode().toLong()
    }

    override fun getViewAt(position: Int): RemoteViews {
        android.util.Log.d("HabitWidget", "getViewAt: position=$position")
        if (position < 0 || position >= rows.size)
            return RemoteViews(ctx.packageName, R.layout.widget_habit_item)

        val row = rows[position]
        val views = RemoteViews(ctx.packageName, R.layout.widget_habit_item)
        views.setTextViewText(R.id.item_title, row.title)
        views.setTextViewText(R.id.item_subtitle, "Daily Habit")
        views.setImageViewResource(
            R.id.item_checkbox,
            if (row.isDone) R.drawable.ic_checkbox_checked else R.drawable.ic_checkbox_unchecked
        )

        val fillIn = Intent().apply {
            putExtra("habit_id", row.id)
            putExtra("is_done", row.isDone)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillIn)
        return views
    }

}
