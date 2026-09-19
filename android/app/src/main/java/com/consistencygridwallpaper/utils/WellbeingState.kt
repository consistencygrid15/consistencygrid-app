package com.consistencygridwallpaper.utils

/**
 * Represents the current Android Digital Wellbeing state at wallpaper render time.
 *
 * Derived from the system's Do Not Disturb interruption filter, which is the
 * only reliable public proxy for Focus Mode / Bedtime Mode detection.
 *
 * | DND Filter                       | WellbeingState |
 * |----------------------------------|----------------|
 * | INTERRUPTION_FILTER_ALL          | NORMAL         |
 * | INTERRUPTION_FILTER_PRIORITY     | FOCUS_MODE     |
 * | INTERRUPTION_FILTER_NONE         | BEDTIME        |
 * | INTERRUPTION_FILTER_ALARMS       | BEDTIME        |
 */
enum class WellbeingState {
    /** No DND active — normal wallpaper rendering */
    NORMAL,

    /** Priority DND — Focus Mode proxy: slightly dimmed accent colors */
    FOCUS_MODE,

    /** Silent/Alarms-only DND — Bedtime Mode proxy: full grayscale rendering */
    BEDTIME
}
