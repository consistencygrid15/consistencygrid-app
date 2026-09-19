package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Draws the 365-day grid (25 columns × up to 15 rows).
 * Matches bundle.js "days" branch in he() exactly:
 *   cellSize = (gridWidth - GAP*(COLS-1)) / COLS
 *   gap = 10px
 *   rows = ceil(365 / 25) = 15
 *   Colors: intensity scale based on that day's completion %
 */
object DaysGridPainter {

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val GAP  = 10f
    private const val COLS = 25

    // ─────────────────────────────────────────────────────────────────────────
    fun draw(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        gx: Float,
        gw: Float,
        startY: Float,
        now: Calendar,
        daysInYear: Int
    ): Pair<Float, GridCellAnchor?> {

        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        // ── Standardize horizontal boundaries ─────────────────────────────────
        val sx          = gx
        val sw          = gw
        val hasCustomBg = state.config.customBackgroundUrl.startsWith("data:image")


        // ── Grid size calculations inside card ────────────────────────────────
        val gap         = GAP * scale
        val padding     = 24f * scale
        val gridW       = sw - padding * 2
        val cell        = (gridW - gap * (COLS - 1)) / COLS
        val rows        = Math.ceil(daysInYear / COLS.toDouble()).toInt()

        val gridH       = rows * cell + (rows - 1) * gap
        val cardH       = gridH + 48f * scale   // 24f top/bottom padding
        val gridStartY  = startY + 2f * scale

        // ── Draw Card background ──────────────────────────────────────────────
        CanvasDrawUtils.drawPremiumCard(
            canvas = canvas,
            x = sx,
            y = startY - 22f * scale,
            w = sw,
            h = cardH,
            radius = 22f * scale,
            hasCustomBg = hasCustomBg,
            theme = theme
        )

        val dayOfYear   = now.get(Calendar.DAY_OF_YEAR)
        val totalHabits = state.stats.totalHabits.coerceAtLeast(1)
        val sdf         = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        var anchor: GridCellAnchor? = null

        for (row in 0 until rows) {
            for (col in 0 until COLS) {
                val doy = row * COLS + col + 1   // 1-indexed day of year
                if (doy > daysInYear) break

                val cellX   = sx + padding + col * (cell + gap)
                val cellY   = gridStartY + row * (cell + gap)
                val isToday = doy == dayOfYear

                if (isToday) anchor = GridCellAnchor(cellX, cellY, cell)

                val dayDate = Calendar.getInstance().apply {
                    set(Calendar.YEAR, now.get(Calendar.YEAR))
                    set(Calendar.DAY_OF_YEAR, doy)
                }
                val key = sdf.format(dayDate.time)

                val fillColor = when {
                    isToday          -> theme.intensityFull
                    doy < dayOfYear  -> {
                        val act = state.activityMap[key] ?: 0
                        val pct = (act.toFloat() / totalHabits * 100).toInt().coerceIn(0, 100)
                        ThemeFactory.intensityColor(theme, pct)
                    }
                    else             -> theme.gridInactive
                }

                CanvasDrawUtils.drawGridCell(canvas, cellX, cellY, cell, fillColor, isToday, 5f * scale, theme)
            }
        }

        return Pair(cardH + 16f * scale, anchor)
    }
}
