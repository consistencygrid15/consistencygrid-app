package com.consistencygridwallpaper.ui.compose.tour

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*

/**
 * The canonical tour step list for ConsistencyGrid.
 *
 * Keys here **must** match the strings passed to Modifier.tourTarget(key = "…")
 * in each screen composable.
 *
 * Order:  Dashboard (4 steps) → Habits (3) → Goals (2) → Reminders (2) → Streaks (1)
 *         = 12 steps total
 */
val ConsistencyGridTourSteps: List<TourStep> = listOf(

    // ── Dashboard ─────────────────────────────────────────────────────────────
    TourStep(
        targetKey   = "dashboard_progress_ring",
        screenRoute = "dashboard",
        title       = "Today's Progress",
        description = "This banner shows how many habits you've completed today. The arc gauge fills as you tick off each one — aim for 100%!",
        icon        = Icons.Default.Assignment,
        shape       = SpotlightShape.ROUNDED_RECT
    ),

    TourStep(
        targetKey   = "dashboard_quick_actions",
        screenRoute = "dashboard",
        title       = "Quick Actions",
        description = "Jump instantly to Habits, Goals, Streaks, or Reminders from here. Your entire consistency system is one tap away.",
        icon        = Icons.Default.GridView,
        shape       = SpotlightShape.ROUNDED_RECT
    ),
    TourStep(
        targetKey   = "dashboard_wallpaper_card",
        screenRoute = "dashboard",
        title       = "Wallpaper Studio",
        description = "Your consistency grid lives on your lock screen. Tap here to customise themes, quotes, and layouts. It updates automatically at midnight.",
        icon        = Icons.Default.Wallpaper,
        shape       = SpotlightShape.ROUNDED_RECT
    ),

    // ── Habits ────────────────────────────────────────────────────────────────
    TourStep(
        targetKey   = "habits_stats_header",
        screenRoute = "habits",
        title       = "Stats Dashboard",
        description = "At a glance: today's completion rate, your best streak, and your overall consistency strength. Watch these numbers grow every day.",
        icon        = Icons.Default.ShowChart,
        shape       = SpotlightShape.ROUNDED_RECT
    ),
    TourStep(
        targetKey   = "habits_add_button",
        screenRoute = "habits",
        title       = "Add a Habit",
        description = "Tap + to create your first habit. Give it a name and an optional reminder time — ConsistencyGrid does the rest.",
        icon        = Icons.Default.Add,
        shape       = SpotlightShape.CIRCLE
    ),

    // ── Goals ─────────────────────────────────────────────────────────────────
    TourStep(
        targetKey   = "goals_momentum_ring",
        screenRoute = "goals",
        title       = "Momentum Tracker",
        description = "Your overall goal completion at a glance. The ring fills as you hit milestones. Keep it growing!",
        icon        = Icons.Default.TrendingUp,
        shape       = SpotlightShape.ROUNDED_RECT
    ),
    TourStep(
        targetKey   = "goals_fab",
        screenRoute = "goals",
        title       = "Add a Goal",
        description = "Tap + to define a new life goal. Break it into sub-tasks, assign a category, and track every milestone on its own progress bar.",
        icon        = Icons.Default.Flag,
        shape       = SpotlightShape.CIRCLE
    ),

    // ── Reminders ─────────────────────────────────────────────────────────────
    TourStep(
        targetKey   = "reminders_summary_card",
        screenRoute = "reminders",
        title       = "Schedule Overview",
        description = "See your total reminders, how many are due today, and how many are high priority — all in one dashboard card.",
        icon        = Icons.Default.Schedule,
        shape       = SpotlightShape.ROUNDED_RECT
    ),
    TourStep(
        targetKey   = "reminders_fab",
        screenRoute = "reminders",
        title       = "Add a Reminder",
        description = "Tap + to schedule a reminder. Pick a priority level, colour code it, and set start / end dates. Never miss a commitment again.",
        icon        = Icons.Default.NotificationAdd,
        shape       = SpotlightShape.CIRCLE
    ),

    // ── Streaks ───────────────────────────────────────────────────────────────
    TourStep(
        targetKey   = "streaks_hero_card",
        screenRoute = "streaks",
        title       = "Your Streak Ring",
        description = "This is your consistency trophy. The ring shows your 30-day completion rate; the number inside is your current active streak. Protect it!",
        icon        = Icons.Default.Whatshot,
        shape       = SpotlightShape.ROUNDED_RECT
    ),
    TourStep(
        targetKey   = "streaks_heatmap",
        screenRoute = "streaks",
        title       = "Activity Heatmap",
        description = "A bird's-eye view of your consistency over the last 16 weeks. The brighter the orange blocks, the more habits you completed that day. Keep the grid glowing!",
        icon        = Icons.Default.GridOn,
        shape       = SpotlightShape.ROUNDED_RECT
    )
)
