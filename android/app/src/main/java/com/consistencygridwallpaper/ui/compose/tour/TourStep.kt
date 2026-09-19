package com.consistencygridwallpaper.ui.compose.tour

import androidx.compose.ui.graphics.vector.ImageVector

// ─── Spotlight cutout shape ───────────────────────────────────────────────────
enum class SpotlightShape {
    CIRCLE,
    ROUNDED_RECT
}

// ─── Single step in the tour ──────────────────────────────────────────────────
data class TourStep(
    /** Unique key matching the string passed to Modifier.tourTarget(key) */
    val targetKey: String,
    /** Nav route the screen hosting this target lives on */
    val screenRoute: String,
    /** Card headline */
    val title: String,
    /** Card body copy */
    val description: String,
    /** Icon shown in the tooltip card */
    val icon: ImageVector,
    /** Shape of the cutout punched through the scrim */
    val shape: SpotlightShape = SpotlightShape.ROUNDED_RECT
)
