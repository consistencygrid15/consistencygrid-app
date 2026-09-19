package com.consistencygridwallpaper.ui.compose.tour

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.consistencygridwallpaper.storage.UserPrefs

/**
 * TourManager — singleton that owns all tour state.
 *
 * Screens observe [isActive], [currentStep], and [currentStepData].
 * Navigation logic lives in NativeAppActivity / NativeAppShell — the manager
 * only exposes the *desired* route so the host can drive NavController.
 */
object TourManager {

    private const val PREF_TOUR_COMPLETED = "feature_tour_completed"

    // ── Public state (Compose-observable) ─────────────────────────────────────
    var isActive   by mutableStateOf(false)
        private set
    var currentStepIndex by mutableIntStateOf(0)
        private set

    private var steps: List<TourStep> = emptyList()

    val currentStep: TourStep?
        get() = steps.getOrNull(currentStepIndex)

    val totalSteps: Int
        get() = steps.size

    /** Route the overlay host should be on right now. */
    val requiredRoute: String?
        get() = currentStep?.screenRoute

    // ── Tour lifecycle ─────────────────────────────────────────────────────────

    /**
     * Call this immediately after onboarding completes.
     * No-ops if the user already finished the tour.
     */
    fun startTourIfNeeded(context: Context, tourSteps: List<TourStep>) {
        val prefs = UserPrefs(context)
        if (prefs.getBoolean(PREF_TOUR_COMPLETED, false)) return
        startTour(tourSteps)
    }

    /** Force-start (used by "Replay Tour" in Settings). */
    fun restartTour(context: Context, tourSteps: List<TourStep>) {
        UserPrefs(context).setBoolean(PREF_TOUR_COMPLETED, false)
        startTour(tourSteps)
    }

    private fun startTour(tourSteps: List<TourStep>) {
        steps            = tourSteps
        currentStepIndex = 0
        isActive         = true
    }

    /** Move to the next step; ends tour automatically after the last step. */
    fun nextStep(context: Context) {
        if (!isActive) return
        val next = currentStepIndex + 1
        if (next >= steps.size) {
            endTour(context)
        } else {
            currentStepIndex = next
        }
    }

    /** User pressed "Skip". */
    fun skipTour(context: Context) {
        endTour(context)
    }

    private fun endTour(context: Context) {
        isActive = false
        UserPrefs(context).setBoolean(PREF_TOUR_COMPLETED, true)
    }

    /** Called by the host when a navigation to [requiredRoute] has completed
     *  and the new screen's targets are ready to be measured. */
    fun onScreenReady() {
        // no-op — triggers recompose because isActive / currentStepIndex are
        // already Compose state; screens read currentStep to render targets.
    }
}
