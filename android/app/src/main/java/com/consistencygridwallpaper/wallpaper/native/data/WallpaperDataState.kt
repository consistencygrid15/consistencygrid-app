package com.consistencygridwallpaper.wallpaper.native.data

import com.consistencygridwallpaper.utils.WellbeingState

/**
 * Complete data state for the native Canvas wallpaper renderer.
 * Matches the full JSON schema that bundle.js consumes, so the native
 * fallback renders identically to the WebView path.
 */
data class WallpaperDataState(
    val config: WallpaperConfig,
    val stats: ActivityStats,
    val activityMap: Map<String, Int>,       // "YYYY-MM-DD" → completions count
    val habits: List<HabitItem> = emptyList(),
    val goals: List<GoalItem> = emptyList(),
    val reminders: List<ReminderItem> = emptyList()
)

data class WallpaperConfig(
    val canvasWidth: Int = 1080,
    val canvasHeight: Int = 2340,
    val theme: String = "minimal-dark",
    val dateOfBirth: String = "",
    val lifeExpectancyYears: Int = 80,
    val yearGridMode: String = "weeks",          // "weeks" | "days" | "life" | "month" | "week_strip"
    val wallpaperType: String = "lockscreen",    // "lockscreen" | "homescreen" | "calendar"
    val showLifeGrid: Boolean = true,
    val showYearGrid: Boolean = true,
    val showHabitLayer: Boolean = true,
    val showAgeStats: Boolean = true,
    val showMissedDays: Boolean = false,
    val showQuote: Boolean = true,
    val quoteText: String = "Make every week count.",
    val customBackgroundUrl: String = "",        // base64 data URI or empty
    // ── Digital Wellbeing ─────────────────────────────────────────────────────
    // Injected at render time from DigitalWellbeingHelper. NORMAL by default.
    // BEDTIME → grayscale render; FOCUS_MODE → dimmed accent.
    val wellbeingState: WellbeingState = WellbeingState.NORMAL
)

data class ActivityStats(
    val streak: Int = 0,
    val totalHabits: Int = 1,
    val todayCompletionPercentage: Int = 0,
    val streakActiveToday: Boolean = false,
    val growthHistory: List<Int> = emptyList()  // last 7 days activity counts
)

// ─── Bottom section models ────────────────────────────────────────────────────

data class HabitItem(
    val id: String,
    val title: String,
    val doneToday: Boolean
)

data class GoalItem(
    val title: String,
    val progress: Int,                          // 0-100
    val subGoals: List<SubGoalItem> = emptyList()
)

data class SubGoalItem(
    val title: String,
    val isCompleted: Boolean
)

data class ReminderItem(
    val title: String,
    val startDate: String,                      // "YYYY-MM-DD"
    val endDate: String,
    val startTime: String?,
    val isFullDay: Boolean,
    val priority: Int,
    val markerColor: String
)
