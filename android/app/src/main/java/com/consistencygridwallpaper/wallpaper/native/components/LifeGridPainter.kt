package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Draws the full-life grid (lifeExpYears × 52 weeks).
 * Matches bundle.js "life" branch in he() exactly:
 *   cols = 52, rows = lifeExpYears
 *   cellSize = max(3.5, min((gridWidth - GAP*(COLS-1)) / COLS, (availH - GAP*(ROWS-1)) / ROWS))
 *   gap = 5px
 *   Grid origin = Sunday on or before DOB
 *   Current week = highlighted with full accent + glow
 */
object LifeGridPainter {

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val COLS        = 52
    private const val GAP         = 5f
    private const val MIN_CELL    = 3.5f
    private const val BOTTOM_PAD  = 140f   // reserved space below the grid

    // ─────────────────────────────────────────────────────────────────────────
    fun draw(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        gx: Float,
        gw: Float,
        startY: Float,
        canvasH: Int,
        now: Calendar
    ): Pair<Float, GridCellAnchor?> {

        val config       = state.config
        val dob          = config.dateOfBirth
        val lifeExpYears = config.lifeExpectancyYears
        val totalHabits  = state.stats.totalHabits.coerceAtLeast(1)

        if (dob.length < 10) return Pair(0f, null)

        val dobCal = try {
            Calendar.getInstance().apply {
                val p = dob.split("-")
                set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (e: Exception) { return Pair(0f, null) }

        val weeksLived = ((System.currentTimeMillis() - dobCal.timeInMillis) /
                          (7L * 24 * 3600 * 1000)).toInt().coerceAtLeast(0)
        val totalWeeks = lifeExpYears * 52
        val ROWS       = lifeExpYears

        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        // ── Standardize horizontal boundaries ─────────────────────────────────
        val sx          = gx
        val sw          = gw
        val hasCustomBg = state.config.customBackgroundUrl.startsWith("data:image")


        // ── Grid size calculations inside card ────────────────────────────────
        val gap         = GAP * scale
        val leftPad     = 72f * scale   // room for age milestone labels
        val rightPad    = 24f * scale
        val gridW       = sw - (leftPad + rightPad)

        // Cell size: fit whichever dimension is more constrained
        val bottomPad   = BOTTOM_PAD * scale
        val availableH  = maxOf(400f * scale, canvasH - startY - bottomPad - 48f * scale) // 24f top/bottom padding
        val cellByWidth  = (gridW - gap * (COLS - 1)) / COLS
        val cellByHeight = (availableH - gap * (ROWS - 1)) / ROWS
        val cell         = (MIN_CELL * scale).coerceAtLeast(minOf(cellByWidth, cellByHeight))

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

        // Grid origin: Sunday on or before DOB
        val gridOrigin = (dobCal.clone() as Calendar).also {
            it.add(Calendar.DAY_OF_MONTH, -(it.get(Calendar.DAY_OF_WEEK) - 1))
        }

        val sdf    = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        var anchor: GridCellAnchor? = null

        // ── Draw Age Milestone Labels and Separator Line ──────────────────────
        val labelColor = ThemeFactory.withAlpha(theme.textMain, 102)
        val labelPaint = CanvasDrawUtils.textPaint(labelColor, 13f * scale, bold = true, align = Paint.Align.LEFT)
        
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ThemeFactory.withAlpha(theme.textMain, 20)
            strokeWidth = 1.5f * scale
        }
        canvas.drawLine(sx + 54f * scale, gridStartY, sx + 54f * scale, gridStartY + gridH, linePaint)

        // ── Draw cells and labels ─────────────────────────────────────────────
        for (weekIdx in 0 until totalWeeks) {
            val col  = weekIdx % COLS
            val row  = weekIdx / COLS
            val cellX = sx + leftPad + col * (cell + gap)
            val cellY = gridStartY + row * (cell + gap)

            // Draw age labels every 10 years (row 0, 10, 20...)
            if (col == 0 && row % 10 == 0) {
                canvas.drawText(row.toString(), sx + 20f * scale, cellY + cell / 2 + 5.5f * scale, labelPaint)
            }

            val isCurrentWeek = weekIdx == weeksLived
            if (isCurrentWeek) anchor = GridCellAnchor(cellX, cellY, cell)

            val fillColor = when {
                isCurrentWeek       -> theme.intensityFull
                weekIdx < weeksLived -> {
                    val weekStart = (gridOrigin.clone() as Calendar).also {
                        it.add(Calendar.DAY_OF_YEAR, weekIdx * 7)
                    }
                    var total = 0
                    val dayCheck = weekStart.clone() as Calendar
                    repeat(7) {
                        total += state.activityMap[sdf.format(dayCheck.time)] ?: 0
                        dayCheck.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    val pct = (total.toFloat() / (totalHabits * 7) * 100).toInt().coerceIn(0, 100)
                    ThemeFactory.intensityColor(theme, pct)
                }
                else                -> theme.gridInactive
            }

            val rr = maxOf(2f * scale, cell / 3.5f)
            CanvasDrawUtils.drawGridCell(canvas, cellX, cellY, cell, fillColor, isCurrentWeek, rr, theme)
        }

        return Pair(cardH + 16f * scale, anchor)
    }
}
