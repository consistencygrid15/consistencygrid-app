package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.GoalItem
import com.consistencygridwallpaper.wallpaper.native.data.HabitItem
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState

/**
 * Renders the bottom section: Daily Habits list + Active Goal.
 * Matches bundle.js pe() function exactly:
 *   - Habits and Goals rendered side-by-side (when both present)
 *   - Habits: colored circles + habit names (up to 10)
 *   - Goal: title + gradient progress bar + next sub-goal
 *   - Position: fixed from bottom of canvas
 */
object HabitsAndGoalsPainter {

    // ── Font sizes ────────────────────────────────────────────────────────────
    private const val SECTION_LABEL_SIZE = 14f   // "DAILY HABITS" / "ACTIVE FOCUS"
    private const val HABIT_TITLE_SIZE   = 18f   // habit name text
    private const val GOAL_TITLE_SIZE    = 21f   // goal name text
    private const val PROGRESS_PCT_SIZE  = 14f   // "XX% COMPLETE"
    private const val NEXT_STEP_LBL_SIZE = 13f   // "NEXT STEP:" label
    private const val NEXT_STEP_VAL_SIZE = 16f   // next step title

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val CARD_RADIUS        = 22f
    private const val SECTION_PADDING    = 24f   // left padding inside cards
    private const val HEADER_TOP         = 16f   // distance from card top to header
    private const val HEADER_BAR_W       = 3f
    private const val HEADER_BAR_H       = 12f
    private const val HABIT_ROW_H        = 40f   // vertical pitch per habit row
    private const val HABIT_CIRCLE_R     = 7f
    private const val GOAL_TITLE_TOP     = 50f   // y-offset from card top to goal title
    private const val PROGRESS_BAR_H     = 7f

    // Rotating accent colors for habit circles (matches bundle.js g array)
    private val HABIT_COLORS = listOf(
        0xFF34d399.toInt(),  // green
        0xFFfbbf24.toInt(),  // amber
        0xFF22d3ee.toInt(),  // cyan
        0xFFa78bfa.toInt(),  // purple
        0xFFf472b6.toInt(),  // pink
        0xFF818cf8.toInt()   // indigo
    )

    fun draw(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        x: Float,       // W * 0.08
        y: Float,       // computed from canvas bottom
        width: Float,   // W * 0.84
        hasCustomBg: Boolean
    ) {
        val habits     = state.habits.take(10)
        val goals      = state.goals.take(1)
        val showHabits = habits.isNotEmpty() && state.config.showHabitLayer
        val showGoals  = goals.isNotEmpty()

        if (!showHabits && !showGoals) return

        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        val sectionPadding = SECTION_PADDING * scale
        val habitRowH      = HABIT_ROW_H * scale

        val goal          = goals.firstOrNull()
        val rows = if (habits.size > 5) (habits.size + 1) / 2 else habits.size
        val habitSectionH = if (showHabits) sectionPadding + 15f * scale + rows * habitRowH + sectionPadding else 0f
        val goalSectionH  = if (showGoals && goal != null) {
            (110f * scale) + (if (goal.subGoals.isNotEmpty()) 46f * scale else 0f) + sectionPadding
        } else 0f

        val isStacked = !showHabits || !showGoals || isHome

        val (habitsX, habitsY, habitsW, goalsX, goalsY, goalsW) = if (isStacked) {
            SixFloat(x, y, width,  x, if (showHabits) y + habitSectionH + 16f * scale else y, width)
        } else {
            val colW = (width - 16f * scale) / 2f
            SixFloat(x, y, colW,  x + colW + 16f * scale, y, colW)
        }

        if (showHabits) drawHabitsSection(canvas, habits, theme, habitsX, habitsY, habitsW, hasCustomBg, state, scale)
        if (showGoals && goal != null) drawGoalSection(canvas, goal, theme, goalsX, goalsY, goalsW, hasCustomBg, scale)
    }

    // ── Habits section ────────────────────────────────────────────────────────
    private fun drawHabitsSection(
        canvas: Canvas,
        habits: List<HabitItem>,
        theme: WallpaperTheme,
        x: Float, y: Float, width: Float,
        hasCustomBg: Boolean,
        state: WallpaperDataState,
        scale: Float
    ) {
        val rows = if (habits.size > 5) (habits.size + 1) / 2 else habits.size
        val sectionPadding = SECTION_PADDING * scale
        val habitRowH      = HABIT_ROW_H * scale
        val sectionH = sectionPadding + 15f * scale + rows * habitRowH + sectionPadding

        CanvasDrawUtils.drawPremiumCard(
            canvas = canvas,
            x = x,
            y = y,
            w = width,
            h = sectionH,
            radius = CARD_RADIUS * scale,
            hasCustomBg = hasCustomBg,
            theme = theme
        )

        // Header
        val headerY = y + HEADER_TOP * scale
        val headerBarW = HEADER_BAR_W * scale
        val headerBarH = HEADER_BAR_H * scale
        CanvasDrawUtils.roundRect(canvas, x + sectionPadding, headerY, headerBarW, headerBarH, 1.5f * scale, theme.accent)
        canvas.drawText(
            "DAILY HABITS",
            x + sectionPadding + headerBarW + 10f * scale, headerY + headerBarH - 1f * scale,
            CanvasDrawUtils.textPaint(
                ThemeFactory.withAlpha(theme.textMain, 102),
                SECTION_LABEL_SIZE * scale, bold = true, letterSpacing = 2f * scale
            )
        )

        // Habit rows (multi-column if > 5)
        val isMultiColumn = habits.size > 5
        val colWidth = if (isMultiColumn) width / 2f else width
        val startY = y + sectionPadding + 15f * scale
        val habitCircleR = HABIT_CIRCLE_R * scale
        
        habits.forEachIndexed { idx, habit ->
            val col = if (isMultiColumn) idx / rows else 0
            val row = if (isMultiColumn) idx % rows else idx
            
            val currentX = x + (col * colWidth)
            val currentY = startY + (row * habitRowH)

            val accentColor = HABIT_COLORS[idx % HABIT_COLORS.size]
            val cx = currentX + sectionPadding + habitCircleR
            val cy = currentY + 5f * scale

            canvas.drawCircle(
                cx, cy, habitCircleR,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style       = if (habit.doneToday) Paint.Style.FILL else Paint.Style.STROKE
                    color       = if (habit.doneToday) accentColor else Color.argb(25, 255, 255, 255)
                    strokeWidth = 1.8f * scale
                }
            )

            val textPaint = CanvasDrawUtils.textPaint(
                if (habit.doneToday) Color.WHITE else Color.argb(178, 255, 255, 255),
                HABIT_TITLE_SIZE * scale, bold = true, letterSpacing = 0.5f * scale
            )
            
            val maxWidth = colWidth - 50f * scale // Leave padding
            var title = habit.title.uppercase()
            if (textPaint.measureText(title) > maxWidth) {
                while (title.isNotEmpty() && textPaint.measureText(title + "\u2026") > maxWidth) {
                    title = title.dropLast(1)
                }
                title += "\u2026"
            }

            canvas.drawText(
                title,
                currentX + sectionPadding + habitCircleR * 2 + 10f * scale, currentY + 10f * scale,
                textPaint
            )
        }
    }

    // ── Goal section ──────────────────────────────────────────────────────────
    private fun drawGoalSection(
        canvas: Canvas,
        goal: GoalItem,
        theme: WallpaperTheme,
        x: Float, y: Float, width: Float,
        hasCustomBg: Boolean,
        scale: Float
    ) {
        val sectionPadding = SECTION_PADDING * scale
        val sectionH = (110f * scale) + (if (goal.subGoals.isNotEmpty()) 46f * scale else 0f) + sectionPadding

        CanvasDrawUtils.drawPremiumCard(
            canvas = canvas,
            x = x,
            y = y,
            w = width,
            h = sectionH,
            radius = CARD_RADIUS * scale,
            hasCustomBg = hasCustomBg,
            theme = theme
        )

        // Header
        val headerY = y + HEADER_TOP * scale
        val headerBarW = HEADER_BAR_W * scale
        val headerBarH = HEADER_BAR_H * scale
        CanvasDrawUtils.roundRect(canvas, x + sectionPadding, headerY, headerBarW, headerBarH, 1.5f * scale, theme.accent)
        canvas.drawText(
            "ACTIVE FOCUS",
            x + sectionPadding + headerBarW + 10f * scale, headerY + headerBarH - 1f * scale,
            CanvasDrawUtils.textPaint(
                ThemeFactory.withAlpha(theme.textMain, 102),
                SECTION_LABEL_SIZE * scale, bold = true, letterSpacing = 2f * scale
            )
        )

        var gy = y + GOAL_TITLE_TOP * scale

        // Goal title
        val titlePaint = CanvasDrawUtils.textPaint(theme.textMain, GOAL_TITLE_SIZE * scale, bold = true, letterSpacing = 0.5f * scale)
        var titleText = goal.title.uppercase()
        val maxTitleW = width - sectionPadding * 2f
        if (titlePaint.measureText(titleText) > maxTitleW) {
            while (titleText.isNotEmpty() && titlePaint.measureText(titleText + "\u2026") > maxTitleW) {
                titleText = titleText.dropLast(1)
            }
            titleText += "\u2026"
        }
        
        canvas.drawText(titleText, x + sectionPadding, gy + 11f * scale, titlePaint)
        gy += 32f * scale

        // Progress bar track
        val barW = width - sectionPadding * 2
        val barX = x + sectionPadding
        val progressBarH = PROGRESS_BAR_H * scale
        CanvasDrawUtils.roundRect(canvas, barX, gy, barW, progressBarH, 3.5f * scale, Color.argb(13, 255, 255, 255))

        // Progress bar fill
        val progress = if (goal.subGoals.isNotEmpty()) {
            val done = goal.subGoals.count { it.isCompleted }
            (done.toFloat() / goal.subGoals.size * 100).toInt()
        } else {
            goal.progress
        }

        if (progress > 0) {
            canvas.drawRoundRect(
                RectF(barX, gy, barX + barW * progress / 100f, gy + progressBarH),
                3.5f * scale, 3.5f * scale,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        barX, 0f, barX + barW, 0f,
                        intArrayOf(theme.accent, Color.WHITE),
                        null, Shader.TileMode.CLAMP
                    )
                }
            )
        }

        // Progress percentage
        canvas.drawText(
            "$progress% COMPLETE", barX + barW, gy + 23f * scale,
            CanvasDrawUtils.textPaint(theme.accent, PROGRESS_PCT_SIZE * scale, bold = true, align = Paint.Align.RIGHT)
        )

        // Next sub-goal
        if (goal.subGoals.isNotEmpty()) {
            gy += 46f * scale
            val nextStep = goal.subGoals.firstOrNull { !it.isCompleted }
            if (nextStep != null) {
                canvas.drawText(
                    "NEXT STEP:", x + sectionPadding, gy,
                    CanvasDrawUtils.textPaint(Color.argb(76, 255, 255, 255), NEXT_STEP_LBL_SIZE * scale, letterSpacing = 1f * scale)
                )
                val stepPaint = CanvasDrawUtils.textPaint(Color.argb(153, 255, 255, 255), NEXT_STEP_VAL_SIZE * scale, bold = true)
                var stepText = nextStep.title.uppercase()
                val maxStepW = width - sectionPadding * 2f
                if (stepPaint.measureText(stepText) > maxStepW) {
                    while (stepText.isNotEmpty() && stepPaint.measureText(stepText + "\u2026") > maxStepW) {
                        stepText = stepText.dropLast(1)
                    }
                    stepText += "\u2026"
                }
                canvas.drawText(stepText, x + sectionPadding, gy + 17f * scale, stepPaint)
            }
        }
    }

    // ── Helper: destructure 6 floats neatly ───────────────────────────────────
    private data class SixFloat(
        val habitsX: Float, val habitsY: Float, val habitsW: Float,
        val goalsX: Float,  val goalsY: Float,  val goalsW: Float
    )
}
