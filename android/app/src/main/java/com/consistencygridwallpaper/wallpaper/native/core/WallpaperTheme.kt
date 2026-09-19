package com.consistencygridwallpaper.wallpaper.native.core

import android.graphics.Color

/**
 * Exact color palette matching bundle.js theme definitions.
 * All hex values taken directly from the Ae() function in bundle.js.
 */
data class WallpaperTheme(
    val bg: Int,
    val card: Int,
    val textMain: Int,
    val textSub: Int,
    val accent: Int,
    val gridActive: Int,
    val gridInactive: Int,
    // Intensity scale (bundle.js $ object per theme)
    val intensityLight: Int,
    val intensityMedium: Int,
    val intensityDark: Int,
    val intensityFull: Int,
    val glowColor: Int          // semi-transparent accent for glow effects
)

object ThemeFactory {

    fun getTheme(name: String): WallpaperTheme = when (name) {

        "sunset-orange", "orange-glow" -> WallpaperTheme(
            bg             = 0xFF09090b.toInt(),
            card           = 0xFF1a0f0a.toInt(),
            textMain       = 0xFFfafafa.toInt(),
            textSub        = 0xFFa1a1aa.toInt(),
            accent         = 0xFFff8c42.toInt(),
            gridActive     = 0xFFff8c42.toInt(),
            gridInactive   = 0xFF2a2019.toInt(),
            intensityLight  = 0xFFfed7aa.toInt(),
            intensityMedium = 0xFFfb923c.toInt(),
            intensityDark   = 0xFFf97316.toInt(),
            intensityFull   = 0xFFea580c.toInt(),
            glowColor      = Color.argb(127, 234, 88, 12)
        )

        "ocean-blue" -> WallpaperTheme(
            bg             = 0xFF09090b.toInt(),
            card           = 0xFF0f172a.toInt(),
            textMain       = 0xFFfafafa.toInt(),
            textSub        = 0xFFa1a1aa.toInt(),
            accent         = 0xFF3b82f6.toInt(),
            gridActive     = 0xFF3b82f6.toInt(),
            gridInactive   = 0xFF1e293b.toInt(),
            intensityLight  = 0xFFbfdbfe.toInt(),
            intensityMedium = 0xFF60a5fa.toInt(),
            intensityDark   = 0xFF3b82f6.toInt(),
            intensityFull   = 0xFF2563eb.toInt(),
            glowColor      = Color.argb(127, 37, 99, 235)
        )

        "forest-green" -> WallpaperTheme(
            bg             = 0xFF09090b.toInt(),
            card           = 0xFF064e3b.toInt(),
            textMain       = 0xFFfafafa.toInt(),
            textSub        = 0xFFa1a1aa.toInt(),
            accent         = 0xFF10b981.toInt(),
            gridActive     = 0xFF10b981.toInt(),
            gridInactive   = 0xFF065f46.toInt(),
            intensityLight  = 0xFFbbf7d0.toInt(),
            intensityMedium = 0xFF4ade80.toInt(),
            intensityDark   = 0xFF22c55e.toInt(),
            intensityFull   = 0xFF16a34a.toInt(),
            glowColor      = Color.argb(127, 22, 163, 74)
        )

        "purple-haze" -> WallpaperTheme(
            bg             = 0xFF09090b.toInt(),
            card           = 0xFF2e1065.toInt(),
            textMain       = 0xFFfafafa.toInt(),
            textSub        = 0xFFa1a1aa.toInt(),
            accent         = 0xFF8b5cf6.toInt(),
            gridActive     = 0xFF8b5cf6.toInt(),
            gridInactive   = 0xFF4c1d95.toInt(),
            intensityLight  = 0xFFe9d5ff.toInt(),
            intensityMedium = 0xFFc084fc.toInt(),
            intensityDark   = 0xFFa855f7.toInt(),
            intensityFull   = 0xFF9333ea.toInt(),
            glowColor      = Color.argb(127, 147, 51, 234)
        )

        "monochrome", "white-clean" -> WallpaperTheme(
            bg             = 0xFFffffff.toInt(),
            card           = 0xFFf4f4f5.toInt(),
            textMain       = 0xFF09090b.toInt(),
            textSub        = 0xFF71717a.toInt(),
            accent         = 0xFF09090b.toInt(),
            gridActive     = 0xFF09090b.toInt(),
            gridInactive   = 0xFFe4e4e7.toInt(),
            intensityLight  = 0xFF71717a.toInt(),
            intensityMedium = 0xFF52525b.toInt(),
            intensityDark   = 0xFF3f3f46.toInt(),
            intensityFull   = 0xFF27272a.toInt(),
            glowColor      = Color.argb(127, 39, 39, 42)
        )

        else -> WallpaperTheme( // "minimal-dark", "dark-minimal" (default)
            bg             = 0xFF09090b.toInt(),
            card           = 0xFF18181b.toInt(),
            textMain       = 0xFFfafafa.toInt(),
            textSub        = 0xFFa1a1aa.toInt(),
            accent         = 0xFFffffff.toInt(),
            gridActive     = 0xFFffffff.toInt(),
            gridInactive   = 0xFF27272a.toInt(),
            intensityLight  = 0xFF52525b.toInt(),
            intensityMedium = 0xFF71717a.toInt(),
            intensityDark   = 0xFFa1a1aa.toInt(),
            intensityFull   = 0xFFffffff.toInt(),
            glowColor      = Color.argb(102, 255, 255, 255)
        )
    }

    /**
     * Returns the appropriate grid cell color for a given completion percentage.
     * Matches bundle.js x() function exactly.
     */
    fun intensityColor(theme: WallpaperTheme, percent: Int): Int = when {
        percent == 0  -> theme.gridInactive
        percent <= 25 -> theme.intensityLight
        percent <= 50 -> theme.intensityMedium
        percent <= 75 -> theme.intensityDark
        else          -> theme.intensityFull
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    fun blendColor(base: Int, accent: Int, ratio: Float): Int {
        val r = (Color.red(base)   * (1 - ratio) + Color.red(accent)   * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * (1 - ratio) + Color.green(accent) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base)  * (1 - ratio) + Color.blue(accent)  * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
