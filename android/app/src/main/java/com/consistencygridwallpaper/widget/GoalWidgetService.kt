package com.consistencygridwallpaper.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.storage.room.AppDatabase
import kotlinx.coroutines.runBlocking

class GoalWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        GoalWidgetFactory(applicationContext)
}

class GoalWidgetFactory(private val ctx: Context) : RemoteViewsService.RemoteViewsFactory {

    private data class GoalRow(val id: String, val title: String, val progress: Int)

    private var rows = listOf<GoalRow>()

    override fun onCreate() {}
    override fun onDestroy() {}

    override fun onDataSetChanged() {
        val db = AppDatabase.getDatabase(ctx)
        try {
            val goals = db.goalDao().getActiveGoalsListSync()
            rows = goals
                .filter { !it.isCompleted }
                .map { GoalRow(it.id, it.title, it.progress) }
            android.util.Log.d("GoalWidget", "onDataSetChanged: loaded ${rows.size} goals")
        } catch (e: Exception) {
            android.util.Log.e("GoalWidget", "Error in onDataSetChanged", e)
        }
    }

    override fun getCount(): Int {
        android.util.Log.d("GoalWidget", "getCount: ${rows.size}")
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
        android.util.Log.d("GoalWidget", "getViewAt: position=$position")
        if (position < 0 || position >= rows.size)
            return RemoteViews(ctx.packageName, R.layout.widget_goal_item)

        val row = rows[position]
        val views = RemoteViews(ctx.packageName, R.layout.widget_goal_item)
        views.setTextViewText(R.id.item_title, row.title)
        views.setTextViewText(R.id.item_subtitle, "Goal • ${row.progress}% done")
        views.setImageViewResource(R.id.item_checkbox, R.drawable.ic_checkbox_unchecked)

        val fillIn = Intent().apply { putExtra("goal_id", row.id) }
        views.setOnClickFillInIntent(R.id.item_root, fillIn)
        return views
    }

}
