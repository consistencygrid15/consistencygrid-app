package com.consistencygridwallpaper.wallpaper.native.data

import android.content.Context
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.ui.compose.wallpaper.computeStreak
import com.consistencygridwallpaper.ui.compose.wallpaper.todayKey
import com.consistencygridwallpaper.utils.DigitalWellbeingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fetches the complete data state needed by WallpaperCanvasEngine.
 * Reads from Room (habits, logs, goals, reminders) + UserPrefs (settings).
 * Produces growth history (last 7 days), streak, and full habit/goal/reminder lists.
 */
object WallpaperDataSource {

    suspend fun getWallpaperState(context: Context): WallpaperDataState = withContext(Dispatchers.IO) {

        val userPrefs = UserPrefs(context)
        val db        = AppDatabase.getDatabase(context)

        // ── Config ────────────────────────────────────────────────────────
        val settingsStr = userPrefs.getWallpaperSettings() ?: "{}"
        val json = try { JSONObject(settingsStr) } catch (e: Exception) { JSONObject() }

        val config = WallpaperConfig(
            canvasWidth          = json.optInt("width", 1080),
            canvasHeight         = json.optInt("height", 2340),
            theme                = json.optString("theme", "minimal-dark"),
            dateOfBirth          = json.optString("dob", ""),
            lifeExpectancyYears  = json.optInt("lifeExpectancyYears", 80),
            yearGridMode         = json.optString("yearGridMode", "weeks"),
            wallpaperType        = json.optString("wallpaperType", "lockscreen"),
            showLifeGrid         = json.optBoolean("showLifeGrid", true),
            showYearGrid         = json.optBoolean("showYearGrid", true),
            showHabitLayer       = json.optBoolean("showHabitLayer", true),
            showAgeStats         = json.optBoolean("showAgeStats", true),
            showMissedDays       = json.optBoolean("showMissedDays", false),
            showQuote            = json.optBoolean("showQuote", true),
            quoteText            = json.optString("quote", "Make every week count."),
            customBackgroundUrl  = json.optString("customBackgroundUrl", ""),
            // ── Digital Wellbeing: read DND state now so the renderer can adapt ──
            wellbeingState       = DigitalWellbeingHelper.getCurrentState(context)
        )

        // ── Activity map (date → completions count) ───────────────────────
        val today   = todayKey()
        val allLogs = db.habitLogDao().getAllDoneLogs()
        val activeHabits = db.habitDao().getActiveHabitsList()

        val activityMap = mutableMapOf<String, Int>()
        val activityJson = JSONObject()
        allLogs.groupBy { it.date }.forEach { (date, logs) ->
            activityMap[date] = logs.size
            activityJson.put(date, logs.size)
        }

        val totalHabits   = activeHabits.size.coerceAtLeast(1)
        val todayDone     = allLogs.count { it.date == today && it.done }
        val todayPct      = (todayDone.toFloat() / totalHabits * 100).toInt().coerceIn(0, 100)
        val streak        = computeStreak(activityJson, today)
        val streakActiveToday = todayDone >= totalHabits

        // ── Growth history: last 7 days completion counts ─────────────────
        val cal = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val growthHistory = (6 downTo 0).map { daysAgo ->
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            activityMap[sdf.format(c.time)] ?: 0
        }

        val stats = ActivityStats(
            streak                    = streak,
            totalHabits               = totalHabits,
            todayCompletionPercentage = todayPct,
            streakActiveToday         = streakActiveToday,
            growthHistory             = growthHistory
        )

        // ── Habits with doneToday flag ────────────────────────────────────
        val habitItems = activeHabits.map { h ->
            HabitItem(
                id        = h.id,
                title     = h.title,
                doneToday = allLogs.any { it.habitId == h.id && it.date == today && it.done }
            )
        }

        // ── Goals with sub-goals ──────────────────────────────────────────
        val goalItems = db.goalDao().getActiveGoalsList()
            .filter { !it.isCompleted }
            .take(1)
            .map { g ->
                val subGoals = parseSubGoals(g.subGoalsJson)
                GoalItem(
                    title    = g.title,
                    progress = g.progress,
                    subGoals = subGoals
                )
            }

        // ── Reminders ─────────────────────────────────────────────────────
        val reminderItems = db.reminderDao().getRemindersList().map { r ->
            ReminderItem(
                title      = r.title,
                startDate  = r.startDate,
                endDate    = r.endDate,
                startTime  = r.startTime,
                isFullDay  = r.isFullDay,
                priority   = r.priority,
                markerColor = r.markerColor
            )
        }

        WallpaperDataState(
            config      = config,
            stats       = stats,
            activityMap = activityMap,
            habits      = habitItems,
            goals       = goalItems,
            reminders   = reminderItems
        )
    }

    private fun parseSubGoals(json: String): List<SubGoalItem> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            SubGoalItem(
                title       = obj.optString("title", ""),
                isCompleted = obj.optBoolean("isCompleted", false)
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
