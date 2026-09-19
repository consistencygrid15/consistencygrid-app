package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState

/**
 * Renders the statistics header section:
 *
 *   [Floating streak pill]  ← right-aligned, above the card, only if streak > 0
 *   ┌──────────────────────────────────────────────────────┐
 *   │  GOALS   │  bezier growth chart      │  ◯ ring       │
 *   │  GROWTH  │                           │  XX%          │
 *   │          │                           │  DAILY GOAL   │
 *   └──────────────────────────────────────────────────────┘
 *
 * Positioned at x = W*0.08, width = W*0.84.
 * Returns: total height consumed (PILL_H + GAP + CARD_H  or  just CARD_H when no streak).
 */
object StatsHeaderPainter {

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val CARD_HEIGHT     = 200f  // height of the main Goals Growth card
    private const val PILL_HEIGHT     = 38f   // height of the floating streak pill
    private const val PILL_GAP        = 10f   // gap between bottom of pill and top of card
    private const val CHART_HEIGHT    = 95f

    // ── Font sizes ────────────────────────────────────────────────────────────
    private const val LABEL_SIZE      = 24f   // "GOALS" / "GROWTH" stacked text
    private const val LABEL_GAP       = 28f   // vertical gap between the two labels
    private const val PCT_SIZE        = 32f   // center percentage inside ring
    private const val SUBTEXT_SIZE    = 11f   // "DAILY GOAL" caption
    private const val STREAK_NUM_SIZE  = 38f   // streak count inside pill
    private const val STREAK_PILL_FLAME = 34f  // flame size inside pill

    // ── Pill height (tall enough for bigger content) ────────────────────────
    private const val PILL_HEIGHT_OVERRIDE = 52f

    // ── Ring geometry (restored to full size) ─────────────────────────────────
    private const val RING_RADIUS     = 55f
    private const val RING_STROKE     = 9f

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Draw the floating streak pill (if active) + Goals Growth card.
     * Returns total height consumed.
     */
    fun draw(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        x: Float,           // W * 0.08
        y: Float,
        width: Float,       // W * 0.84
        hasCustomBg: Boolean
    ): Float {
        val isHome = state.config.wallpaperType == "homescreen"
        val scale = if (isHome) 1.25f else 1.0f

        val cardHeight = CARD_HEIGHT * scale
        val pillHeightOverride = PILL_HEIGHT_OVERRIDE * scale
        val pillGap = PILL_GAP * scale
        val ringRadius = RING_RADIUS * scale

        val streak     = state.stats.streak
        val showStreak = streak > 0

        // ── [1] Floating streak pill (above the card, right-aligned) ──────────
        val cardY: Float
        if (showStreak) {
            drawStreakPill(canvas, state, theme, x, y, width, scale)
            cardY = y + pillHeightOverride + pillGap
        } else {
            cardY = y
        }

        // ── [2] Main Goals Growth card ────────────────────────────────────────
        CanvasDrawUtils.drawPremiumCard(
            canvas      = canvas,
            x           = x,
            y           = cardY,
            w           = width,
            h           = cardHeight,
            radius      = 22f * scale,
            hasCustomBg = hasCustomBg,
            theme       = theme
        )

        // ── "GOALS" + "GROWTH" stacked labels ────────────────────────────────
        val labelAlpha = ThemeFactory.withAlpha(theme.textMain, 178)
        val labelPaint = CanvasDrawUtils.textPaint(labelAlpha, LABEL_SIZE * scale, bold = true)
        canvas.drawText("GOALS",  x + 24f * scale, cardY + 40f * scale,             labelPaint)
        canvas.drawText("GROWTH", x + 24f * scale, cardY + 40f * scale + LABEL_GAP * scale, labelPaint)

        // ── Completion ring (right side, vertically centered in card) ─────────
        val ringRightPad = 36f * scale
        val ringCx = x + width - ringRightPad - ringRadius
        val ringCy = cardY + cardHeight / 2f
        drawCompletionRing(canvas, state, theme, ringCx, ringCy, scale)

        // ── Bezier growth line chart (fills space to the left of the ring) ────
        val chartStartX = x + 24f * scale
        val chartEndX   = ringCx - ringRadius - 32f * scale
        val chartWidth  = (chartEndX - chartStartX).coerceAtLeast(80f * scale)
        val chartY      = cardY + cardHeight / 2f - (CHART_HEIGHT * scale) / 2f + 10f * scale
        drawLineChart(canvas, state, theme, chartStartX, chartWidth, chartY, scale)

        val totalH = if (showStreak) pillHeightOverride + pillGap + cardHeight else cardHeight
        return totalH
    }

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Draws the floating streak pill.
     * A glassmorphic rounded badge, right-aligned above the card.
     * Contains: flame icon + streak count  (no status text).
     * Flame is vibrant orange-red when streak is active, cold gray when not.
     */
    private fun drawStreakPill(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        x: Float,
        y: Float,
        width: Float,
        scale: Float
    ) {
        val streak       = state.stats.streak
        // Flame is always vibrant orange when a streak exists — color never depends
        // on streakActiveToday (which requires ALL habits done, too strict for UI)
        val flameColor   = 0xFFff6b00.toInt()   // neon orange-red
        val numColor     = theme.textMain

        val streakNumSize = STREAK_NUM_SIZE * scale
        val streakPillFlame = STREAK_PILL_FLAME * scale
        val pillHeightOverride = PILL_HEIGHT_OVERRIDE * scale

        val numText  = streak.toString()
        val numPaint = CanvasDrawUtils.textPaint(numColor, streakNumSize, bold = true, shadow = true)
        val numWidth = numPaint.measureText(numText)

        val pillPadH  = 16f * scale   // horizontal padding inside pill
        val iconSpacing = 10f * scale
        val contentW  = streakPillFlame + iconSpacing + numWidth
        val pillW     = contentW + pillPadH * 2f

        // Centered pill horizontally
        val pillLeft  = (x + width / 2f) - pillW / 2f
        val pillRight = pillLeft + pillW

        // Draw pill background (glassmorphic)
        val pillRect   = RectF(pillLeft, y, pillRight, y + pillHeightOverride)
        val pillRadius = pillHeightOverride / 2f

        // Glass fill — always warm orange tint since streak exists
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(50, 255, 107, 0)
            })
        // Glass border
        canvas.drawRoundRect(pillRect, pillRadius, pillRadius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style       = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
                color       = Color.argb(100, 255, 107, 0)
            })

        // Flame icon inside pill (left side of content)
        val contentStartX = pillLeft + pillPadH
        val flameCy = y + pillHeightOverride / 2f
        CanvasDrawUtils.drawFlame(
            canvas,
            contentStartX,
            flameCy - streakPillFlame / 2f,
            streakPillFlame,
            flameColor
        )

        // Streak count text (right of flame)
        val textX = contentStartX + streakPillFlame + iconSpacing
        val textY = flameCy + streakNumSize * 0.35f  // vertically centred baseline
        canvas.drawText(numText, textX, textY, numPaint)
    }

    // ─────────────────────────────────────────────────────────────────────────
    /** Bezier growth line chart with gradient area fill. */
    private fun drawLineChart(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        x: Float,
        leftWidth: Float,
        chartY: Float,
        scale: Float
    ) {
        val history = state.stats.growthHistory.takeIf { it.isNotEmpty() }
            ?: listOf(2, 4, 3, 5, 4, 6, 5)

        val chartHeight = CHART_HEIGHT * scale
        val maxVal = (history.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val span   = (history.size - 1).coerceAtLeast(1).toFloat()

        val points = history.mapIndexed { i, v ->
            PointF(
                x + (i / span) * leftWidth,
                chartY + chartHeight - (v / maxVal * chartHeight)
            )
        }

        if (points.size < 2) return

        // Area fill under curve
        val fillPath = buildCurvePath(points, x, leftWidth, chartY + chartHeight)
        canvas.drawPath(fillPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style  = Paint.Style.FILL
            shader = LinearGradient(
                0f, chartY, 0f, chartY + chartHeight,
                intArrayOf(Color.argb(51, 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        })

        // Bezier line
        val linePath = buildLinePath(points)
        canvas.drawPath(linePath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            color       = theme.accent
            strokeWidth = 5f * scale
            strokeCap   = Paint.Cap.ROUND
            strokeJoin  = Paint.Join.ROUND
        })

        // Data-point dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = theme.accent
        }
        points.forEach { p -> canvas.drawCircle(p.x, p.y, 4f * scale, dotPaint) }
    }

    private fun buildCurvePath(
        pts: List<PointF>,
        x: Float, leftWidth: Float, bottomY: Float
    ): Path = Path().apply {
        moveTo(x, bottomY)
        lineTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val mx = (pts[i].x + pts[i + 1].x) / 2
            val my = (pts[i].y + pts[i + 1].y) / 2
            quadTo(pts[i].x, pts[i].y, mx, my)
        }
        lineTo(pts.last().x, pts.last().y)
        lineTo(x + leftWidth, bottomY)
        close()
    }

    private fun buildLinePath(pts: List<PointF>): Path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 0 until pts.size - 1) {
            val mx = (pts[i].x + pts[i + 1].x) / 2
            val my = (pts[i].y + pts[i + 1].y) / 2
            quadTo(pts[i].x, pts[i].y, mx, my)
        }
        lineTo(pts.last().x, pts.last().y)
    }

    // ─────────────────────────────────────────────────────────────────────────
    /** Circular progress ring with percentage in centre. */
    private fun drawCompletionRing(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        cx: Float,
        cy: Float,
        scale: Float
    ) {
        val todayPct = state.stats.todayCompletionPercentage
        val ringRadius = RING_RADIUS * scale
        val ringStroke = RING_STROKE * scale

        // Track ring
        canvas.drawCircle(cx, cy, ringRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            color       = 0xFF27272a.toInt()
            strokeWidth = ringStroke
        })

        // Progress arc
        if (todayPct > 0) {
            canvas.drawArc(
                RectF(cx - ringRadius, cy - ringRadius,
                      cx + ringRadius, cy + ringRadius),
                -90f, todayPct / 100f * 360f, false,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style       = Paint.Style.STROKE
                    color       = theme.accent
                    strokeWidth = ringStroke
                    strokeCap   = Paint.Cap.ROUND
                }
            )
        }

        // Centre percentage text
        canvas.drawText(
            "$todayPct%", cx, cy + 11f * scale,
            CanvasDrawUtils.textPaint(theme.textMain, PCT_SIZE * scale, bold = true, align = Paint.Align.CENTER)
        )

        // "DAILY GOAL" caption below ring
        canvas.drawText(
            "DAILY GOAL", cx, cy + ringRadius + 20f * scale,
            CanvasDrawUtils.textPaint(
                ThemeFactory.withAlpha(theme.textMain, 100),
                SUBTEXT_SIZE * scale, bold = true, align = Paint.Align.CENTER
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    /**
     * Public stub kept for binary compatibility with any callers.
     */
    fun drawStreak(
        canvas: Canvas,
        state: WallpaperDataState,
        theme: WallpaperTheme,
        x: Float,
        y: Float,
        hasCustomBg: Boolean
    ) {
        // No-op: streak is now rendered as a floating pill inside draw()
    }
}
