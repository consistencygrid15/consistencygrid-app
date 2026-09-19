package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.util.*

/**
 * Anchor point of a grid cell — used to position the reminder popover.
 */
data class GridCellAnchor(val x: Float, val y: Float, val size: Float)

/**
 * Result of drawing any grid section.
 * heightConsumed = total vertical space used (header + grid).
 * anchor = the "today" cell position, if found.
 */
data class GridRenderResult(val heightConsumed: Float, val anchor: GridCellAnchor?)

/**
 * Draws the grid section header (mode label, big title, subtitle, progress pill)
 * and delegates the actual grid to the appropriate sub-painter.
 *
 * Matches bundle.js he() function exactly — all label strings, fonts, and positions.
 */
object GridSectionPainter {

    // ── Font sizes ────────────────────────────────────────────────────────────
    private const val MODE_LABEL_SIZE  = 18f   // small mode-category label
    private const val BIG_TITLE_SIZE   = 50f   // large year / mode title
    private const val SUBTITLE_SIZE    = 22f   // secondary descriptor line
    private const val PILL_TEXT_SIZE   = 18f   // progress pill text

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val MODE_LABEL_GAP   = 44f   // space below mode label
    private const val TITLE_GAP        = 64f   // space below big title
    private const val SUBTITLE_GAP     = 60f   // space below subtitle row
    private const val PILL_W           = 200f
    private const val PILL_H           = 52f

    // ─────────────────────────────────────────────────────────────────────────
    fun draw(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        gx: Float,      // W * 0.10
        gw: Float,      // W * 0.80
        startY: Float,
        canvasH: Int,
        now: Calendar = Calendar.getInstance()
    ): GridRenderResult {

        val mode = state.config.yearGridMode
        var yPos = startY

        // ── Compute header strings ────────────────────────────────────────────
        val modeLabel: String
        val bigTitle: String
        val subtitle: String
        val progressPill: String

        val year        = now.get(Calendar.YEAR)
        val isLeap      = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
        val daysInYear  = if (isLeap) 366 else 365
        val dayOfYear   = now.get(Calendar.DAY_OF_YEAR)
        val isoWeek     = getISOWeek(now)
        val monthName   = now.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
            ?.uppercase() ?: ""
        val daysInMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dayOfMonth  = now.get(Calendar.DAY_OF_MONTH)

        when (mode) {
            "days" -> {
                modeLabel    = "ANNUAL VIEW"
                bigTitle     = "365 DAYS"
                subtitle     = "$year \u2022 $daysInYear days to make count"
                progressPill = "${(dayOfYear.toFloat() / daysInYear * 100).toInt()}% COMPLETE"
            }
            "weeks" -> {
                modeLabel    = "WEEKLY METRICS"
                bigTitle     = "$year PROGRESS"
                subtitle     = "52 weeks of opportunity"
                progressPill = "WEEK $isoWeek/52"
            }
            "life" -> {
                modeLabel = "MEMENTO MORI"
                bigTitle  = "LIFE PROGRESS"

                val dob = state.config.dateOfBirth
                val age = if (dob.length >= 10) {
                    try {
                        val parts  = dob.split("-")
                        val dobCal = Calendar.getInstance().apply {
                            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        }
                        ((System.currentTimeMillis() - dobCal.timeInMillis) /
                                (365.25 * 24 * 3600 * 1000)).toInt()
                    } catch (e: Exception) { 0 }
                } else 0

                subtitle = "$age YEARS \u2022 ${state.config.lifeExpectancyYears}Y EXPECTANCY"

                val lifeWeeksLived = if (dob.length >= 10) {
                    try {
                        val parts  = dob.split("-")
                        val dobCal = Calendar.getInstance().apply {
                            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                        }
                        ((System.currentTimeMillis() - dobCal.timeInMillis) /
                                (7L * 24 * 3600 * 1000)).toInt()
                    } catch (e: Exception) { 0 }
                } else 0

                val totalLifeWeeks = state.config.lifeExpectancyYears * 52
                progressPill = "${(lifeWeeksLived.toFloat() / totalLifeWeeks * 100).toInt()}% LIVED"
            }
            "week_strip" -> {
                modeLabel    = "ACTIVE OPERATIONS"
                bigTitle     = "TACTICAL WEEK"
                subtitle     = "Current 7-day performance"
                progressPill = "DAY $dayOfYear"
            }
            else -> { // "month"
                modeLabel    = "MONTHLY VIEW"
                bigTitle     = "$monthName $year"
                subtitle     = "$daysInMonth DAYS TOTAL"
                progressPill = "DAY $dayOfMonth/$daysInMonth"
            }
        }

        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        val pillW = PILL_W * scale
        val pillH = PILL_H * scale

        // ── Standardize horizontal boundaries ─────────────────────────────────
        val sx = gx
        val sw = gw

        // ── Mode label (small caps, accent color) ─────────────────────────────
        canvas.drawText(
            modeLabel, sx, yPos - 16f * scale,
            CanvasDrawUtils.textPaint(theme.accent, MODE_LABEL_SIZE * scale, bold = true, letterSpacing = 3f * scale)
        )
        yPos += MODE_LABEL_GAP * scale

        // ── Big title ─────────────────────────────────────────────────────────
        canvas.drawText(
            bigTitle, sx, yPos,
            CanvasDrawUtils.textPaint(theme.textMain, BIG_TITLE_SIZE * scale, bold = true, shadow = true)
        )
        yPos += TITLE_GAP * scale

        // ── Subtitle ──────────────────────────────────────────────────────────
        canvas.drawText(
            subtitle, sx, yPos + 10f * scale,
            CanvasDrawUtils.textPaint(Color.argb(76, 255, 255, 255), SUBTITLE_SIZE * scale)
        )

        // ── Progress pill (right-aligned to card edge) ────────────────────────
        val pillX = sx + sw - pillW
        val pillY = yPos - 6f * scale
        CanvasDrawUtils.roundRect(
            canvas, pillX, pillY, pillW, pillH, 14f * scale,
            Color.argb(15, 255, 255, 255), Color.argb(25, 255, 255, 255)
        )
        canvas.drawText(
            progressPill, pillX + pillW / 2, pillY + pillH / 2 + 7f * scale,
            CanvasDrawUtils.textPaint(theme.accent, PILL_TEXT_SIZE * scale, bold = true, align = Paint.Align.CENTER)
        )

        yPos += SUBTITLE_GAP * scale

        // ── Delegate to appropriate grid painter ──────────────────────────────
        return when (mode) {
            "weeks"      -> WeeksGridPainter.draw(canvas, state, theme, gx, gw, yPos, now)
            "days"       -> DaysGridPainter.draw(canvas, state, theme, gx, gw, yPos, now, daysInYear)
            "life"       -> LifeGridPainter.draw(canvas, state, theme, gx, gw, yPos, canvasH, now)
            "week_strip" -> WeekStripPainter.draw(canvas, state, theme, gx, gw, yPos, now)
            else         -> MonthGridPainter.draw(canvas, state, theme, gx, gw, yPos, now)
        }.let { (h, anchor) ->
            GridRenderResult(yPos - startY + h, anchor)
        }
    }

    /** ISO week number — matches bundle.js De() function. */
    fun getISOWeek(cal: Calendar): Int {
        val c = cal.clone() as Calendar
        c.minimalDaysInFirstWeek = 4
        c.firstDayOfWeek = Calendar.MONDAY
        return c.get(Calendar.WEEK_OF_YEAR)
    }
}
