package com.consistencygridwallpaper.ui.compose.wallpaper

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.consistencygridwallpaper.storage.room.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG_DATA = "WallpaperDataBuilder"

/**
 * Builds the full JSON payload that the local WebView renderer expects,
 * exactly matching the schema from /api/wallpaper-data.
 *
 * Reads all data from the local Room database — 100% offline, zero network.
 */
suspend fun buildWallpaperJson(
    context: Context,
    settings: WallpaperSettings
): String = withContext(Dispatchers.IO) {
    val db          = AppDatabase.getDatabase(context)
    val habitDao    = db.habitDao()
    val logDao      = db.habitLogDao()
    val goalDao     = db.goalDao()
    val reminderDao = db.reminderDao()

    val today = todayKey()
    val tz    = TimeZone.getDefault().id

    // ── 1. Read local DB data ────────────────────────────────────────────────
    val habits    = habitDao.getActiveHabitsList()
    val allLogs   = logDao.getAllDoneLogs()
    val goals     = goalDao.getActiveGoalsList()
    val reminders = reminderDao.getRemindersList()

    // ── 2. Build activityMap  { "YYYY-MM-DD" → count } ──────────────────────
    val activityMap = JSONObject()
    allLogs.groupBy { it.date }.forEach { (date, logs) ->
        activityMap.put(date, logs.size)
    }

    // ── 3. Stats ─────────────────────────────────────────────────────────────
    val totalHabits               = habits.size
    val todayDoneCount            = allLogs.count { it.date == today && it.done }
    val todayCompletionPercentage = if (totalHabits > 0)
        ((todayDoneCount.toFloat() / totalHabits) * 100).toInt()
    else 0
    val streakActiveToday = todayDoneCount > 0

    // Streak = consecutive days going backwards where activityMap has entries
    val streak = computeStreak(activityMap, today)

    // Growth history = last 7 days activity counts
    val growthHistory = JSONArray()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    for (offset in 6 downTo 0) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        val dayKey = sdf.format(cal.time)
        growthHistory.put(activityMap.optInt(dayKey, 0))
    }

    // ── 4. Habit list for renderer ───────────────────────────────────────────
    val habitsArray = JSONArray()
    habits.forEach { habit ->
        habitsArray.put(JSONObject().apply {
            put("id",    habit.id)
            put("title", habit.title)
            val logsArr = JSONArray()
            allLogs.filter { it.habitId == habit.id && it.done }.forEach { log ->
                logsArr.put(JSONObject().apply { put("date", log.date) })
            }
            put("logs", logsArr)
        })
    }

    // ── 5. Assemble final payload ────────────────────────────────────────────
    JSONObject().apply {
        put("meta", JSONObject().apply {
            put("generatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            put("timezone", tz)
            put("version", "1.0.2")
        })
        put("user", JSONObject().apply {
            put("settings", JSONObject().apply {
                put("canvasWidth",         settings.width)
                put("canvasHeight",        settings.height)
                put("width",               settings.width)
                put("height",              settings.height)
                put("dateOfBirth",         settings.dob)
                put("lifeExpectancyYears", settings.lifeExpectancyYears)
                put("theme",               settings.theme)
                put("yearGridMode",        settings.yearGridMode)
                put("wallpaperType",       settings.wallpaperType)
                put("showLifeGrid",        settings.showLifeGrid)
                put("showYearGrid",        settings.showYearGrid)
                put("showAgeStats",        settings.showAgeStats)
                put("showHabitLayer",      settings.showHabitLayer)
                put("showMissedDays",      settings.showMissedDays)
                put("showLegend",          settings.showLegend)
                put("showQuote",           settings.showQuote)
                put("quoteText",           settings.quote)
                put("goalEnabled",         settings.goalEnabled)
                put("goalTitle",           settings.goalTitle)
                put("customBackgroundUrl", settings.customBackgroundUrl)
            })
        })
        put("stats", JSONObject().apply {
            put("streak",                  streak)
            put("streakActiveToday",       streakActiveToday)
            put("todayCompletionPercentage", todayCompletionPercentage)
            put("growthHistory",           growthHistory)
            put("totalHabits",             totalHabits)
        })
        put("data", JSONObject().apply {
            put("activityMap", activityMap)
            put("habits",      habitsArray)

            // Inject Reminders
            val rArr = JSONArray()
            reminders.forEach { r ->
                rArr.put(JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    r.description?.let { put("description", it) }
                    put("startDate", r.startDate)
                    put("endDate", r.endDate)
                    r.startTime?.let { put("startTime", it) }
                    r.endTime?.let { put("endTime", it) }
                    put("isFullDay", r.isFullDay)
                    put("priority", r.priority)
                    put("markerColor", r.markerColor)
                })
            }
            put("reminders", rArr)

            // Inject Goals
            val gArr = JSONArray()
            goals.forEach { g ->
                gArr.put(JSONObject().apply {
                    put("id", g.id)
                    put("title", g.title)
                    put("progress", g.progress)
                    put("category", g.category)
                    put("isCompleted", g.isCompleted)
                    put("isPinned", g.isPinned)
                })
            }
            put("goals", gArr)
        })
    }.toString()
}

/** Injects the full wallpaper payload into the WebView on the main thread. */
fun injectFullDataIntoWebView(webView: WebView?, jsonStr: String) {
    if (webView == null) return
    // Escape for embedding in a JS single-quoted string
    val escaped = jsonStr
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "")
        .replace("\r", "")
    webView.post {
        webView.evaluateJavascript(
            "if(window.renderOfflineData) window.renderOfflineData('$escaped');",
            null
        )
        Log.d(TAG_DATA, "Injected ${jsonStr.length} bytes into WebView")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun todayKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

fun computeStreak(activityMap: JSONObject, today: String): Int {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    var streak = 0
    val cal = Calendar.getInstance()

    // Check today first
    if (activityMap.optInt(today, 0) > 0) streak++

    cal.add(Calendar.DAY_OF_YEAR, -1)
    repeat(365) {
        val dayKey = sdf.format(cal.time)
        if (activityMap.optInt(dayKey, 0) > 0) {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        } else return streak
    }
    return streak
}
