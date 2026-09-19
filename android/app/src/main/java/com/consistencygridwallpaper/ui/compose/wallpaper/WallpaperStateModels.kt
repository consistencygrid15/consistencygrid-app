package com.consistencygridwallpaper.ui.compose.wallpaper

// ─────────────────────────────────────────────────────────────────────────────
// Data model
// ─────────────────────────────────────────────────────────────────────────────
data class WallpaperSettings(
    val dob: String = "",
    val lifeExpectancyYears: Int = 80,
    val theme: String = "minimal-dark",
    val width: Int = 1080,
    val height: Int = 2340,
    val yearGridMode: String = "weeks",
    val wallpaperType: String = "lockscreen",
    val showLifeGrid: Boolean = true,
    val showYearGrid: Boolean = true,
    val showAgeStats: Boolean = true,
    val showMissedDays: Boolean = false,
    val showHabitLayer: Boolean = true,
    val showLegend: Boolean = false,
    val showQuote: Boolean = true,
    val quote: String = "Make every week count.",
    val goalEnabled: Boolean = false,
    val goalTitle: String = "",
    val goalStartDate: String = "",
    val goalDurationDays: Int = 30,
    val goalUnit: String = "day",
    // customBackgroundUrl can be:
    //   - empty string: no custom background
    //   - "data:image/jpeg;base64,..." : local image encoded as base64
    //   - "https://..." : remote URL (legacy)
    val customBackgroundUrl: String = ""
)

// ─────────────────────────────────────────────────────────────────────────────
// Theme data
// ─────────────────────────────────────────────────────────────────────────────
data class AppTheme(
    val id: String,
    val name: String,
    val emoji: String,
    val previewColors: List<Long>,
    val desc: String
)

val THEMES = listOf(
    AppTheme("minimal-dark",  "Minimal Dark",  "🌙", listOf(0xFF09090B, 0xFFFFFFFF, 0xFF27272A), "Pure and minimal"),
    AppTheme("sunset-orange", "Sunset Orange", "🌅", listOf(0xFF09090B, 0xFFFF7A00, 0xFF2A2019), "Warm energy"),
    AppTheme("ocean-blue",    "Ocean Blue",    "🌊", listOf(0xFF09090B, 0xFF0088FF, 0xFF1E293B), "Cool waters"),
    AppTheme("forest-green",  "Forest Green",  "🌲", listOf(0xFF09090B, 0xFF00CC66, 0xFF065F46), "Natural growth"),
    AppTheme("purple-haze",   "Purple Haze",   "🔮", listOf(0xFF09090B, 0xFFA855F7, 0xFF4C1D95), "Mystical vibes"),
    AppTheme("monochrome",    "Monochrome",    "⚫", listOf(0xFFFFFFFF, 0xFF09090B, 0xFFE4E4E7), "Inverted")
)

// ─────────────────────────────────────────────────────────────────────────────
// Resolution presets
// ─────────────────────────────────────────────────────────────────────────────
data class ResolutionPreset(val width: Int, val height: Int, val label: String)

val RESOLUTIONS = listOf(
    ResolutionPreset(1080, 2340, "📱 Full HD+ (1080×2340)"),
    ResolutionPreset(1080, 2400, "📱 Full HD+ (1080×2400)"),
    ResolutionPreset(1080, 1920, "📱 FHD (1080×1920)"),
    ResolutionPreset(1440, 3200, "📱 QHD+ (1440×3200)"),
    ResolutionPreset(828,  1792, "🍎 iPhone XR (828×1792)"),
    ResolutionPreset(1125, 2436, "🍎 iPhone X (1125×2436)")
)
