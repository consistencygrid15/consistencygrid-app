package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Draws the 4×13 (52-week) year grid.
 * Matches bundle.js "weeks" branch in he() exactly:
 *   cellSize = (gridWidth - GAP*(COLS-1)) / COLS
 *   gap = 14px
 *   Grid start: Sunday on or before Jan 1 of current year
 *   Today = ISO week number match
 *   Colors: intensity scale based on weekly completion %
 */
object WeeksGridPainter {

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val GAP  = 14f
    private const val COLS = 13
    private const val ROWS = 4

    // ─────────────────────────────────────────────────────────────────────────
    fun draw(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        gx: Float,
        gw: Float,
        startY: Float,
        now: Calendar
    ): Pair<Float, GridCellAnchor?> {

        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        // ── Standardize horizontal boundaries ─────────────────────────────────
        val sx          = gx
        val sw          = gw
        val hasCustomBg = state.config.customBackgroundUrl.startsWith("data:image")


        // ── Grid size calculations inside card ────────────────────────────────
        val gap         = GAP * scale
        val leftPad     = 72f * scale  // room for "Q1"..."Q4" labels
        val rightPad    = 24f * scale
        val gridW       = sw - (leftPad + rightPad)
        val cell        = (gridW - gap * (COLS - 1)) / COLS

        val gridH       = ROWS * cell + (ROWS - 1) * gap
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

        val currentISOWeek = GridSectionPainter.getISOWeek(now)
        val sdf            = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val totalHabits    = state.stats.totalHabits.coerceAtLeast(1)

        // Grid origin: Sunday on or before Jan 1 of the current year
        val yearStart = Calendar.getInstance().apply {
            set(now.get(Calendar.YEAR), 0, 1)
        }
        val gridStart = (yearStart.clone() as Calendar).also {
            it.add(Calendar.DAY_OF_MONTH, -yearStart.get(Calendar.DAY_OF_WEEK) + 1)
        }

        var anchor: GridCellAnchor? = null

        // ── Draw Quarters Labels and Separator Line ───────────────────────────
        val labelColor = ThemeFactory.withAlpha(theme.textMain, 102)
        val labelPaint = CanvasDrawUtils.textPaint(labelColor, 15f * scale, bold = true, letterSpacing = 1.5f * scale)
        
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemeFactory.withAlpha(theme.textMain, 20)
            strokeWidth = 1.5f * scale
        }
        canvas.drawLine(sx + 54f * scale, gridStartY, sx + 54f * scale, gridStartY + gridH, linePaint)

        // ── Draw cells and labels ─────────────────────────────────────────────
        for (row in 0 until ROWS) {
            // Draw Q1, Q2, Q3, Q4 label centered vertically with each row
            val cellY = gridStartY + row * (cell + gap)
            canvas.drawText("Q${row + 1}", sx + 20f * scale, cellY + cell / 2 + 5.5f * scale, labelPaint)

            for (col in 0 until COLS) {
                val weekIdx = row * COLS + col   // 0-indexed
                val weekNum = weekIdx + 1        // 1-indexed display
                if (weekNum > 52) break

                val cellX   = sx + leftPad + col * (cell + gap)
                val isToday = weekNum == currentISOWeek

                if (isToday) anchor = GridCellAnchor(cellX, cellY, cell)

                val fillColor = when {
                    isToday              -> theme.intensityFull
                    weekNum < currentISOWeek -> {
                        val weekDay = (gridStart.clone() as Calendar).also {
                            it.add(Calendar.DAY_OF_YEAR, weekIdx * 7)
                        }
                        var total = 0
                        val dayCheck = weekDay.clone() as Calendar
                        repeat(7) {
                            total += state.activityMap[sdf.format(dayCheck.time)] ?: 0
                            dayCheck.add(Calendar.DAY_OF_YEAR, 1)
                        }
                        val pct = (total.toFloat() / (totalHabits * 7) * 100).toInt().coerceIn(0, 100)
                        ThemeFactory.intensityColor(theme, pct)
                    }
                    else                 -> theme.gridInactive
                }

                CanvasDrawUtils.drawGridCell(canvas, cellX, cellY, cell, fillColor, isToday, 8f * scale, theme)
            }
        }

        return Pair(cardH + 16f * scale, anchor)
    }
}
