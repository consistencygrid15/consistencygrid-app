package com.consistencygridwallpaper.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.storage.room.AppDatabase
import kotlinx.coroutines.runBlocking

class ReminderWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ReminderWidgetFactory(applicationContext)
}

class ReminderWidgetFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class ReminderRow(val id: String, val title: String, val date: String, val isCompleted: Boolean)

    private var rows = listOf<ReminderRow>()

    override fun onCreate() {}
    override fun onDestroy() {}

    override fun onDataSetChanged() {
        val db = AppDatabase.getDatabase(ctx)
        try {
            // Only load active (non-completed, non-deleted) reminders
            val reminders = db.reminderDao().getRemindersListSync()
            rows = reminders.map { ReminderRow(it.id, it.title, it.startDate, it.isCompleted) }
            android.util.Log.d("ReminderWidget", "onDataSetChanged: loaded ${rows.size} active reminders")
        } catch (e: Exception) {
            android.util.Log.e("ReminderWidget", "Error in onDataSetChanged", e)
        }
    }

    override fun getCount(): Int {
        android.util.Log.d("ReminderWidget", "getCount: ${rows.size}")
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
        android.util.Log.d("ReminderWidget", "getViewAt: position=$position")
        if (position < 0 || position >= rows.size)
            return RemoteViews(ctx.packageName, R.layout.widget_reminder_item)

        val row = rows[position]
        val views = RemoteViews(ctx.packageName, R.layout.widget_reminder_item)
        views.setTextViewText(R.id.item_title, row.title)
        views.setTextViewText(R.id.item_subtitle, row.date)
        // Always show unchecked — clicking it will complete the reminder
        views.setImageViewResource(R.id.item_checkbox, R.drawable.ic_checkbox_unchecked)

        val fillIn = Intent().apply { putExtra("reminder_id", row.id) }
        views.setOnClickFillInIntent(R.id.item_root, fillIn)
        return views
    }

}
