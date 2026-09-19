package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.text.SimpleDateFormat
import java.util.*

/**
 * Draws the current-month calendar grid.
 * Matches bundle.js "month" (default) branch in he() exactly:
 *   cols = 7, rows = 6
 *   cellSize = (gridWidth - GAP*(COLS-1)) / COLS
 *   gap = 14px
 *   Day headers: SUN MON TUE WED THU FRI SAT
 *   Today's cell: accent full color (even without activity)
 *   Past cells: intensity color based on completion %
 *   Legend at bottom: LESS → 5 shade dots → MORE
 */
object MonthGridPainter {

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val GAP           = 14f
    private const val COLS          = 7
    private const val HEADER_SIZE   = 16f   // day-of-week header text
    private const val NUM_TODAY     = 32f   // "today" date number text
    private const val NUM_ACTIVE    = 26f   // past day with activity
    private const val NUM_INACTIVE  = 24f   // future / no-activity day
    private const val LEGEND_SIZE   = 16f   // LESS / MORE legend labels
    private const val DOT_SIZE      = 20f
    private const val DOT_GAP       = 12f
    private const val HEADER_ROW_H  = 36f  // vertical space consumed by day headers

    private val DAY_NAMES = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

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

        val sdf         = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val totalHabits = state.stats.totalHabits.coerceAtLeast(1)

        val year        = now.get(Calendar.YEAR)
        val month       = now.get(Calendar.MONTH)
        val today       = now.get(Calendar.DAY_OF_MONTH)

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

        val monthStart  = Calendar.getInstance().apply { set(year, month, 1) }
        val startDow    = monthStart.get(Calendar.DAY_OF_WEEK) - 1   // 0 = Sun
        val daysInMonth = monthStart.getActualMaximum(Calendar.DAY_OF_MONTH)

        var yPos        = startY + 22f * scale
        var anchor: GridCellAnchor? = null

        // ── Day-of-week headers ───────────────────────────────────────────────
        DAY_NAMES.forEachIndexed { i, name ->
            val hx = sx + padding + i * (cell + gap) + cell / 2
            // Subtle pill behind each label
            CanvasDrawUtils.roundRect(
                canvas,
                sx + padding + i * (cell + gap) - 22f * scale, yPos - 10f * scale, 44f * scale, 22f * scale, 5f * scale,
                Color.argb(10, 255, 255, 255)
            )
            canvas.drawText(
                name, hx, yPos + 5f * scale,
                CanvasDrawUtils.textPaint(
                    Color.argb(51, 255, 255, 255),
                    HEADER_SIZE * scale, bold = true, align = Paint.Align.CENTER
                )
            )
        }
        yPos += HEADER_ROW_H * scale

        // ── Calendar cells ────────────────────────────────────────────────────
        var actualRows = 6
        for (r in 0 until 6) {
            val hasDay = (0 until COLS).any { c -> r * COLS + c - startDow + 1 in 1..daysInMonth }
            if (!hasDay) { actualRows = r; break }
        }

        for (row in 0 until actualRows) {
            for (col in 0 until COLS) {
                val dom = row * COLS + col - startDow + 1
                if (dom !in 1..daysInMonth) continue

                val cellX   = sx + padding + col * (cell + gap)
                val cellY   = yPos + row * (cell + gap)
                val isToday = dom == today

                if (isToday) anchor = GridCellAnchor(cellX, cellY, cell)

                val dayCal  = Calendar.getInstance().apply { set(year, month, dom) }
                val key     = sdf.format(dayCal.time)
                val act     = state.activityMap[key] ?: 0

                val fillColor = when {
                    isToday -> {
                        val pct = (act.toFloat() / totalHabits * 100).toInt().coerceIn(0, 100)
                        if (pct > 0) ThemeFactory.intensityColor(theme, pct) else theme.intensityFull
                    }
                    dom < today && act > 0 -> {
                        val pct = (act.toFloat() / totalHabits * 100).toInt().coerceIn(0, 100)
                        ThemeFactory.intensityColor(theme, pct)
                    }
                    else -> theme.gridInactive
                }

                CanvasDrawUtils.drawGridCell(canvas, cellX, cellY, cell, fillColor, isToday, 10f * scale, theme)

                // Day number
                val textColor = when {
                    isToday -> Color.BLACK
                    act > 0 -> Color.WHITE
                    else    -> Color.argb(51, 255, 255, 255)
                }
                val textSize = when {
                    isToday -> NUM_TODAY * scale
                    act > 0 -> NUM_ACTIVE * scale
                    else    -> NUM_INACTIVE * scale
                }
                canvas.drawText(
                    dom.toString(),
                    cellX + cell / 2, cellY + cell / 2 + 8f * scale,
                    CanvasDrawUtils.textPaint(textColor, textSize, bold = true, align = Paint.Align.CENTER)
                )
            }
        }

        val gridBottom = yPos + actualRows * (cell + gap)

        // ── Legend: LESS → 5 shades → MORE ───────────────────────────────────
        val legendY = gridBottom + 12f * scale
        val dotSize = DOT_SIZE * scale
        val dotGap = DOT_GAP * scale

        canvas.drawText(
            "LESS", sx + padding, legendY + 9f * scale,
            CanvasDrawUtils.textPaint(Color.argb(51, 255, 255, 255), LEGEND_SIZE * scale, bold = true)
        )

        listOf(0, 25, 50, 75, 100).forEachIndexed { i, pct ->
            val dx = sx + padding + 68f * scale + i * (dotSize + dotGap)
            CanvasDrawUtils.drawGridCell(
                canvas, dx, legendY, dotSize,
                ThemeFactory.intensityColor(theme, pct), false, 3f * scale, theme
            )
        }

        val dotsEnd = sx + padding + 68f * scale + 5 * (dotSize + dotGap) + 4f * scale
        canvas.drawText(
            "MORE", dotsEnd, legendY + 9f * scale,
            CanvasDrawUtils.textPaint(Color.argb(51, 255, 255, 255), LEGEND_SIZE * scale, bold = true)
        )

        val cardH = legendY + dotSize + 24f * scale - startY

        // ── Draw Card background ──────────────────────────────────────────────
        CanvasDrawUtils.drawPremiumCard(
            canvas = canvas,
            x = sx,
            y = startY - 14f * scale,
            w = sw,
            h = cardH,
            radius = 22f * scale,
            hasCustomBg = hasCustomBg,
            theme = theme
        )

        return Pair(cardH + 16f * scale, anchor)
    }
}
