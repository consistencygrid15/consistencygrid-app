package com.consistencygridwallpaper.utils

import android.app.NotificationManager
import android.content.Context
import android.util.Log

/**
 * DigitalWellbeingHelper — Detects Android Focus Mode / Bedtime Mode state.
 *
 * Android's Digital Wellbeing does not expose a direct Focus Mode API.
 * The most reliable proxy is the system's **Do Not Disturb (DND) interruption filter**:
 *
 *   - Bedtime Mode sets DND to INTERRUPTION_FILTER_NONE or INTERRUPTION_FILTER_ALARMS
 *   - Focus Mode sets DND to INTERRUPTION_FILTER_PRIORITY
 *
 * Reading `NotificationManager.currentInterruptionFilter` requires only the
 * `ACCESS_NOTIFICATION_POLICY` permission — a normal (install-time) permission
 * that does NOT require a runtime user prompt.
 */
object DigitalWellbeingHelper {

    private const val TAG = "DigitalWellbeingHelper"

    /**
     * Returns the current [WellbeingState] based on the system DND interruption filter.
     *
     * Safe to call from any thread. Never throws.
     */
    fun getCurrentState(context: Context): WellbeingState {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val filter = nm.currentInterruptionFilter

            val state = when (filter) {
                NotificationManager.INTERRUPTION_FILTER_NONE,
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> WellbeingState.BEDTIME

                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> WellbeingState.FOCUS_MODE

                else -> WellbeingState.NORMAL  // INTERRUPTION_FILTER_ALL or unknown
            }

            Log.d(TAG, "DND filter=$filter → WellbeingState=$state")
            state
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read DND state — defaulting to NORMAL", e)
            WellbeingState.NORMAL
        }
    }
}
