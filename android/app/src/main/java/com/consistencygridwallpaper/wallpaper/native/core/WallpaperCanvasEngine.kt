package com.consistencygridwallpaper.wallpaper.native.core

import android.graphics.*
import android.util.Log
import com.consistencygridwallpaper.utils.WellbeingState
import com.consistencygridwallpaper.wallpaper.native.components.*
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.util.Calendar

/**
 * Master orchestrator for the native Canvas wallpaper renderer.
 *
 * ══════════════════════════════════════════════════════════════════════════════
 * LAYOUT FLOW  (top → bottom, no overlapping)
 * ══════════════════════════════════════════════════════════════════════════════
 *
 *  ┌───────────────────────────────────────────────────────┐
 *  │  TOP_MARGIN  (H × 0.12  or  H × 0.35 for lockscreen)  │
 *  ├───────────────────────────────────────────────────────┤
 *  │  [A] Stats Header – Goals Growth chart + Streak badge  │  (optional)
 *  │      height returned by StatsHeaderPainter.draw()      │
 *  ├───────────────────────────────────────────────────────┤
 *  │  SECTION_GAP                                           │
 *  ├───────────────────────────────────────────────────────┤
 *  │  [B] Grid Section – mode label + title + grid          │
 *  │      height returned by GridSectionPainter.draw()      │
 *  ├───────────────────────────────────────────────────────┤
 *  │  [C] Reminder Popover (drawn over [B], bounded inside) │  (optional)
 *  ├───────────────────────────────────────────────────────┤
 *  │  BOTTOM_RESERVE  (quote space + habits/goals)          │
 *  ├───────────────────────────────────────────────────────┤
 *  │  [D] Habits + Goals  (pinned above bottom reserve)     │
 *  ├───────────────────────────────────────────────────────┤
 *  │  [E] Quote + Branding  (pinned at canvas bottom)       │  (optional)
 *  └───────────────────────────────────────────────────────┘
 *
 * Coordinates use same proportions as bundle.js Ee():
 *   sx = W × 0.08,  sw = W × 0.84   (stats / habits zone)
 *   gx = W × 0.10,  gw = W × 0.80   (grid zone)
 */
object WallpaperCanvasEngine {

    private const val TAG = "WallpaperCanvasEngine"

    // ── Vertical rhythm constants ─────────────────────────────────────────────
    private const val SECTION_GAP       = 40f   // gap between stats header and grid
    private const val GRID_BOTTOM_PAD   = 30f   // breathing room below grid
    private const val QUOTE_ZONE_H      = 220f  // height reserved at bottom for quote+brand (clears camera/flashlight icons)
    private const val HABITS_BOTTOM_PAD = 24f   // gap between habits/goals card and quote zone
    private const val LIFE_MODE_TOP_PAD = 60f   // extra top pad in life mode (no stats header)

    // ─────────────────────────────────────────────────────────────────────────
    fun render(state: WallpaperDataState): Bitmap {
        val config = state.config
        val W      = config.canvasWidth
        val H      = config.canvasHeight

        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val theme  = ThemeFactory.getTheme(config.theme)
        val now    = Calendar.getInstance()

        val isHome = config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        // ── Digital Wellbeing: resolve effective theme ─────────────────────────
        // Bedtime Mode  → full monochrome (matches Android's own grayscale Bedtime effect)
        // Focus Mode    → dim accent colors by ~40% to reduce distraction
        // We never mutate the user's saved preference — this is render-time only.
        val wellbeingState = config.wellbeingState
        val effectiveTheme = when (wellbeingState) {
            WellbeingState.BEDTIME    -> ThemeFactory.getTheme("monochrome")
            WellbeingState.FOCUS_MODE -> theme.copy(
                accent         = dimColor(theme.accent, 0.42f),
                gridActive     = dimColor(theme.gridActive, 0.42f),
                intensityLight  = dimColor(theme.intensityLight, 0.42f),
                intensityMedium = dimColor(theme.intensityMedium, 0.42f),
                intensityDark   = dimColor(theme.intensityDark, 0.42f),
                intensityFull   = dimColor(theme.intensityFull, 0.42f),
                glowColor      = dimColor(theme.glowColor, 0.42f)
            )
            WellbeingState.NORMAL     -> theme
        }

        // ── Proportioned x/width zones ────────────────────────────────────────
        val sx = if (isHome) W * 0.04f else W * 0.08f   // stats / habits X
        val sw = if (isHome) W * 0.92f else W * 0.84f   // stats / habits width
        val gx = if (isHome) W * 0.06f else W * 0.10f   // grid X
        val gw = if (isHome) W * 0.88f else W * 0.80f   // grid width

        val hasCustomBg = config.customBackgroundUrl.startsWith("data:image")

        // ─────────────────────────────────────────────────────────────────────
        // [1] BACKGROUND
        // ─────────────────────────────────────────────────────────────────────
        BackgroundPainter.draw(canvas, W, H, effectiveTheme, config.customBackgroundUrl)

        // ─────────────────────────────────────────────────────────────────────
        // Starting Y — lockscreen leaves room for clock at the top, homescreen centers
        // ─────────────────────────────────────────────────────────────────────
        val totalH = estimateTotalHeight(state, W)
        var yPos = if (config.wallpaperType == "lockscreen") {
            H * 0.41f
        } else {
            val remaining = H - totalH
            (remaining / 2f).coerceIn(H * 0.14f, H * 0.32f)
        }

        // ─────────────────────────────────────────────────────────────────────
        // [2] STATS HEADER  (Goals Growth chart + circular ring)
        //     Drawn only when showAgeStats is enabled AND not in life mode.
        //     The streak badge is drawn at the same y-level (right-aligned).
        // ─────────────────────────────────────────────────────────────────────
        if (config.showAgeStats && config.yearGridMode != "life") {

            val statsH = StatsHeaderPainter.draw(
                canvas      = canvas,
                state       = state,
                theme       = effectiveTheme,
                x           = sx,
                y           = yPos,
                width       = sw,
                hasCustomBg = hasCustomBg
            )

            yPos += statsH + SECTION_GAP * scale

        } else if (config.yearGridMode == "life") {
            yPos += LIFE_MODE_TOP_PAD * scale
        }

        // ─────────────────────────────────────────────────────────────────────
        // Compute how much vertical space the bottom zone (habits + quote) needs
        // so the grid section knows its available height.
        // ─────────────────────────────────────────────────────────────────────
        val bottomZoneH = computeBottomZoneHeight(state, config.showQuote)

        // ─────────────────────────────────────────────────────────────────────
        // [3] GRID SECTION
        //     The grid is drawn from yPos down to at most (H - bottomZoneH - GRID_BOTTOM_PAD * scale).
        //     GridSectionPainter returns how much height it actually consumed.
        // ─────────────────────────────────────────────────────────────────────
        val gridMaxH   = (H - bottomZoneH - (GRID_BOTTOM_PAD * scale) - yPos).coerceAtLeast(200f)
        val gridResult = GridSectionPainter.draw(
            canvas  = canvas,
            state   = state,
            theme   = effectiveTheme,
            gx      = gx,
            gw      = gw,
            startY  = yPos,
            canvasH = (yPos + gridMaxH).toInt(),   // cap the painter's available height
            now     = now
        )

        val gridEndY = yPos + gridResult.heightConsumed

        // ─────────────────────────────────────────────────────────────────────
        // [4] REMINDER POPOVER
        //     Only for non-week_strip modes (week_strip has an inline agenda).
        //     The popover is constrained to [yPos … gridEndY] vertically.
        // ─────────────────────────────────────────────────────────────────────
        val anchor = gridResult.anchor
        if (anchor != null
            && config.yearGridMode != "week_strip"
            && state.reminders.isNotEmpty()
        ) {
            ReminderPopoverPainter.draw(
                canvas    = canvas,
                theme     = effectiveTheme,
                anchor    = anchor,
                reminders = state.reminders,
                canvasH   = (gridEndY + GRID_BOTTOM_PAD * scale).toInt(),
                gx        = gx,
                gw        = gw,
                now       = now
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // [5] HABITS + GOALS
        //     Pinned above the quote zone (or above canvas bottom when no quote).
        // ─────────────────────────────────────────────────────────────────────
        val habitsGoalsH = computeHabitsGoalsHeight(state)
        val quoteZone    = if (config.showQuote) QUOTE_ZONE_H * scale else 0f
        val habitsY = if (isHome) {
            gridEndY + SECTION_GAP * scale
        } else {
            (H - quoteZone - habitsGoalsH - HABITS_BOTTOM_PAD * scale)
                .coerceAtLeast(gridEndY + GRID_BOTTOM_PAD * scale)   // never overlap the grid
        }

        HabitsAndGoalsPainter.draw(
            canvas      = canvas,
            state       = state,
            theme       = effectiveTheme,
            x           = sx,
            y           = habitsY,
            width       = sw,
            hasCustomBg = hasCustomBg
        )

        // ─────────────────────────────────────────────────────────────────────
        // [6] QUOTE + BRANDING  (always at the very bottom)
        // ─────────────────────────────────────────────────────────────────────
        if (config.showQuote) {
            QuotePainter.draw(
                canvas  = canvas,
                canvasW = W,
                canvasH = H,
                quote   = config.quoteText,
                scale   = scale
            )
        }

        // ─────────────────────────────────────────────────────────────────────
        // [7] DIGITAL WELLBEING LABEL (Bedtime / Focus Mode)
        //     Small badge pinned at the very bottom — only visible when active.
        // ─────────────────────────────────────────────────────────────────────
        if (wellbeingState != WellbeingState.NORMAL) {
            drawWellbeingLabel(canvas, W, H, wellbeingState, scale)
        }

        Log.d(TAG,
            "✅ Rendered ${W}×${H} | theme=${config.theme} mode=${config.yearGridMode} " +
            "wellbeing=${wellbeingState} " +
            "yStart=%.0f gridEnd=%.0f habitsY=%.0f".format(
                if (config.wallpaperType == "lockscreen") H * 0.35f else H * 0.12f,
                gridEndY, habitsY
            )
        )

        return bitmap
    }

    private fun todayKey(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
    }

    private fun estimateTotalHeight(state: WallpaperDataState, W: Int): Float {
        val config = state.config
        val isHome = config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        val showStats = config.showAgeStats && config.yearGridMode != "life"
        val statsH = if (showStats) {
            val streak = state.stats.streak
            val showStreak = streak > 0
            val cardH = 200f * scale
            if (showStreak) (52f * scale) + (10f * scale) + cardH else cardH
        } else {
            0f
        }
        val gapStatsGrid = if (showStats) SECTION_GAP * scale else 0f
        val lifeModePad = if (config.yearGridMode == "life") LIFE_MODE_TOP_PAD * scale else 0f

        val gw = if (isHome) W * 0.88f else W * 0.80f


        // Estimate grid height
        val leftPad = 72f * scale
        val rightPad = 24f * scale
        val gridW = gw - (leftPad + rightPad)


        val headerH = 168f * scale // MODE_LABEL_GAP + TITLE_GAP + SUBTITLE_GAP

        val gridCardH = when (config.yearGridMode) {
            "weeks" -> {
                val cell = (gridW - (14f * scale) * 12) / 13
                val rows = 4
                val gridH = rows * cell + (rows - 1) * (14f * scale)
                gridH + 48f * scale + 16f
            }
            "days" -> {
                val cell = (gridW - (10f * scale) * 24) / 25
                val rows = 15
                val gridH = rows * cell + (rows - 1) * (10f * scale)
                gridH + 48f * scale + 16f
            }
            "life" -> {
                val cellByWidth = (gridW - (5f * scale) * 51) / 52
                val cell = cellByWidth
                val rows = config.lifeExpectancyYears
                val gridH = rows * cell + (rows - 1) * (5f * scale)
                gridH + 48f * scale + 16f
            }
            "week_strip" -> {
                val cellW = (gridW - (14f * scale) * 6) / 7
                val cellH = cellW * 1.2f
                val cardH = cellH + 40f * scale
                val reminders = state.reminders
                val today = todayKey()
                val todayReminders = reminders.filter { r ->
                    try {
                        val start = r.startDate.trim()
                        start == today || (r.startDate <= today && r.endDate >= today)
                    } catch (e: java.lang.Exception) { false }
                }
                val agendaH = maxOf(74f * scale, todayReminders.size * (66f * scale) + 18f * scale) + 36f * scale
                cardH + 24f * scale + agendaH
            }
            else -> {
                // month
                val cell = (gridW - (14f * scale) * 6) / 7
                val rows = 6
                val gridBottom = headerH + rows * (cell + 14f * scale)
                val legendY = gridBottom + 12f * scale
                val cardH = legendY + (20f * scale) + 24f * scale - headerH
                cardH + 16f
            }
        }

        val bottomZoneH = computeBottomZoneHeight(state, config.showQuote)
        val contentH = statsH + gapStatsGrid + lifeModePad + headerH + gridCardH + GRID_BOTTOM_PAD * scale

        if (isHome) {
            val habitsGoalsH = computeHabitsGoalsHeight(state)
            val showHabitsGoals = habitsGoalsH > 0
            val gap = if (showHabitsGoals) SECTION_GAP * scale else 0f
            val quoteH = if (config.showQuote) QUOTE_ZONE_H * scale else 0f
            return contentH + gap + habitsGoalsH + quoteH
        } else {
            return contentH + bottomZoneH
        }
    }

    private fun computeBottomZoneHeight(state: WallpaperDataState, showQuote: Boolean): Float {
        val config = state.config
        val isHome = config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        val quoteH = if (showQuote) QUOTE_ZONE_H * scale else 0f
        return computeHabitsGoalsHeight(state) + quoteH + (HABITS_BOTTOM_PAD * scale)
    }

    private fun computeHabitsGoalsHeight(state: WallpaperDataState): Float {
        val habits     = state.habits.take(10)
        val showHabits = habits.isNotEmpty() && state.config.showHabitLayer
        val goal       = state.goals.firstOrNull()
        val showGoals  = goal != null

        if (!showHabits && !showGoals) return 0f

        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        val sectionPadding = 24f * scale
        val habitRowH = 40f * scale
        val rows = if (habits.size > 5) (habits.size + 1) / 2 else habits.size
        
        val habitH = if (showHabits) sectionPadding + (15f * scale) + rows * habitRowH + sectionPadding else 0f
        val goalH  = if (showGoals && goal != null) {
            (110f * scale) + (if (goal.subGoals.isNotEmpty()) 46f * scale else 0f) + sectionPadding
        } else 0f

        val isStacked = !showHabits || !showGoals || isHome
        return if (isStacked) {
            val gap = 16f * scale
            val total = (if (showHabits) habitH else 0f) + (if (showGoals) goalH else 0f)
            if (showHabits && showGoals) total + gap else total
        } else {
            maxOf(habitH, goalH)   // side-by-side
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Digital Wellbeing helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Blends [color] toward black by [ratio] (0.0 = unchanged, 1.0 = black).
     * Used to dim accent colors in Focus Mode.
     */
    private fun dimColor(color: Int, ratio: Float): Int {
        val r = (Color.red(color)   * (1f - ratio)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1f - ratio)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * (1f - ratio)).toInt().coerceIn(0, 255)
        val a = Color.alpha(color)
        return Color.argb(a, r, g, b)
    }

    /**
     * Draws a small pill-shaped badge at the very bottom of the canvas indicating
     * the active wellbeing mode. Subtle enough not to distract, but clearly visible.
     *
     *  🌙 BEDTIME MODE   — monochrome pill (dark bg, light text)
     *  🎯 FOCUS MODE     — semi-transparent pill (dark bg, muted accent text)
     */
    private fun drawWellbeingLabel(
        canvas: Canvas,
        W: Int,
        H: Int,
        state: WellbeingState,
        scale: Float
    ) {
        val (emoji, label, pillBg, textColor) = when (state) {
            WellbeingState.BEDTIME    -> Quadruple("🌙", "BEDTIME MODE",  0xCC1C1C1E.toInt(), 0xFFE5E5EA.toInt())
            WellbeingState.FOCUS_MODE -> Quadruple("🎯", "FOCUS MODE",    0xCC1C1C1E.toInt(), 0xFFAAAAAA.toInt())
            WellbeingState.NORMAL     -> return  // should never be reached due to caller guard
        }

        val text       = "$emoji  $label"
        val textSizePx = 22f * scale
        val paddingH   = 18f * scale
        val paddingV   = 10f * scale
        val cornerR    = (textSizePx + paddingV) / 2f
        val bottomY    = H - 36f * scale

        // Measure text width
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize  = textSizePx
            this.typeface  = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            this.textAlign = Paint.Align.CENTER
            this.color     = textColor
        }
        val textW = textPaint.measureText(text)

        // Pill background
        val pillW  = textW + paddingH * 2f
        val pillH  = textSizePx + paddingV * 2f
        val left   = W / 2f - pillW / 2f
        val top    = bottomY - pillH
        val pillRf = RectF(left, top, left + pillW, top + pillH)

        canvas.drawRoundRect(
            pillRf, cornerR, cornerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillBg }
        )

        // Label text
        canvas.drawText(
            text,
            W / 2f,
            top + pillH / 2f + textSizePx / 3f,
            textPaint
        )
    }

    /** Minimal tuple to keep drawWellbeingLabel readable without a Pair<Pair<>> nest. */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

