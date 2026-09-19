package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.ReminderItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Renders the reminder popover tooltip anchored to a specific grid cell.
 * Matches bundle.js reminder popover logic for non-week_strip modes.
 *
 * Visual design:
 *   - Dashed line connector from grid cell to popup card
 *   - Dark glassmorphic popup card with "UPCOMING" header
 *   - Up to 4 reminders with priority-color dots
 */
object ReminderPopoverPainter {

    // ── Font sizes ────────────────────────────────────────────────────────────
    private const val HEADER_SIZE    = 18f
    private const val TITLE_SIZE     = 22f
    private const val TIME_SIZE      = 16f

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val POPUP_W        = 680f
    private const val ROW_H          = 60f
    private const val HEADER_H       = 52f
    private const val ICON_SIZE      = 28f
    private const val DOT_RADIUS     = 6f
    private const val BADGE_H        = 38f
    private const val CORNER_RADIUS  = 28f
    private const val MIN_POPUP_Y    = 240f

    private val SDF = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private val PRIORITY_COLORS = mapOf(
        1 to 0xFF6b7280.toInt(),
        2 to 0xFF7c3aed.toInt(),
        3 to 0xFFf59e0b.toInt(),
        4 to 0xFFef4444.toInt()
    )

    // ─────────────────────────────────────────────────────────────────────────
    @Suppress("UNUSED_PARAMETER")
    fun draw(
        canvas: Canvas,
        theme: WallpaperTheme,
        anchor: GridCellAnchor,
        reminders: List<ReminderItem>,
        canvasH: Int,
        gx: Float,
        gw: Float,
        now: Calendar
    ) {
        if (reminders.isEmpty()) return

        val nowDate = SDF.format(now.time)

        // Filter to reminders active on today
        val active = reminders.filter { r ->
            try { r.startDate <= nowDate && r.endDate >= nowDate }
            catch (e: Exception) { false }
        }.sortedBy { it.startTime ?: "00:00" }.take(4)

        if (active.isEmpty()) return

        val popupH   = HEADER_H + active.size * ROW_H + 16f
        val popupX   = gx + maxOf(0f, (gw - POPUP_W) / 2)
        val popupY   = computePopupY(anchor, popupH, canvasH)

        // ── Connector line ────────────────────────────────────────────────────
        drawConnector(canvas, anchor, popupX, popupY, popupH)

        // ── Popup card ────────────────────────────────────────────────────────
        drawCard(canvas, active, popupX, popupY, popupH)
    }

    private fun computePopupY(anchor: GridCellAnchor, popupH: Float, canvasH: Int): Float {
        return if (anchor.y > canvasH / 2) {
            (anchor.y - popupH - 20f).coerceAtLeast(MIN_POPUP_Y)
        } else {
            (anchor.y + anchor.size + 20f)
        }.coerceAtMost(canvasH - popupH - 260f)
    }

    private fun drawConnector(
        canvas: Canvas,
        anchor: GridCellAnchor,
        popupX: Float,
        popupY: Float,
        popupH: Float
    ) {
        val cellCX  = anchor.x + anchor.size / 2
        val cellCY  = anchor.y + anchor.size / 2
        val popupCX = popupX + POPUP_W / 2
        val endY    = if (popupY > cellCY) popupY else popupY + popupH

        // Anchor dot
        canvas.drawCircle(cellCX, cellCY, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        })

        // Dashed line
        canvas.drawPath(
            Path().apply {
                moveTo(cellCX, cellCY)
                lineTo(cellCX, endY)
                lineTo(popupCX, endY)
            },
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style      = Paint.Style.STROKE
                color      = Color.argb(102, 255, 255, 255)
                strokeWidth = 2f
                pathEffect  = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            }
        )

        // End dot
        canvas.drawCircle(popupCX, endY, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(204, 255, 255, 255)
        })
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        val count = paint.breakText(text, true, maxWidth - ellipsisWidth, null)
        return text.take(count) + ellipsis
    }

    private fun drawCard(
        canvas: Canvas,
        active: List<ReminderItem>,
        popupX: Float,
        popupY: Float,
        popupH: Float
    ) {
        // Drop shadow (using a soft, low-alpha rounded rect offset)
        CanvasDrawUtils.roundRect(
            canvas, popupX, popupY + 6f, POPUP_W, popupH, CORNER_RADIUS,
            Color.argb(80, 0, 0, 0)
        )
        
        // Card background: Premium frosted dark glass look
        CanvasDrawUtils.roundRect(
            canvas, popupX, popupY, POPUP_W, popupH, CORNER_RADIUS,
            Color.argb(235, 18, 18, 24), // Frosted dark glass
            Color.argb(40, 255, 255, 255) // Elegant border stroke
        )

        // ── Header: calendar icon + "UPCOMING" ───────────────────────────────
        val headerCenterY = popupY + HEADER_H / 2f
        val iconX = popupX + 24f
        val iconY = headerCenterY - ICON_SIZE / 2f

        // Draw elegant binder calendar icon
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(178, 255, 255, 255)
            strokeWidth = 2f
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(25, 255, 255, 255)
            style = Paint.Style.FILL
        }
        
        // Outer calendar frame
        val iconRect = RectF(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE)
        canvas.drawRoundRect(iconRect, 6f, 6f, iconFillPaint)
        canvas.drawRoundRect(iconRect, 6f, 6f, iconPaint)
        
        // Header bar in the calendar icon
        canvas.drawLine(iconX, iconY + 8f, iconX + ICON_SIZE, iconY + 8f, iconPaint)
        
        // Binder rings (two vertical ticks)
        canvas.drawLine(iconX + 7f, iconY - 3f, iconX + 7f, iconY + 2f, iconPaint)
        canvas.drawLine(iconX + ICON_SIZE - 7f, iconY - 3f, iconX + ICON_SIZE - 7f, iconY + 2f, iconPaint)
        
        // Dots representing days on calendar grid
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(128, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(iconX + 8f, iconY + 14f, 2f, dotPaint)
        canvas.drawCircle(iconX + 14f, iconY + 14f, 2f, dotPaint)
        canvas.drawCircle(iconX + 20f, iconY + 14f, 2f, dotPaint)
        canvas.drawCircle(iconX + 8f, iconY + 20f, 2f, dotPaint)
        canvas.drawCircle(iconX + 14f, iconY + 20f, 2f, dotPaint)
        canvas.drawCircle(iconX + 20f, iconY + 20f, 2f, dotPaint)

        // UPCOMING header text (letter spaced, semi-bold/bold)
        val headerPaint = CanvasDrawUtils.textPaint(
            Color.argb(178, 255, 255, 255),
            HEADER_SIZE,
            bold = true,
            letterSpacing = 2f
        )
        val headerTextY = headerCenterY - (headerPaint.descent() + headerPaint.ascent()) / 2f
        canvas.drawText("UPCOMING", iconX + ICON_SIZE + 12f, headerTextY, headerPaint)

        // ── Reminder rows ─────────────────────────────────────────────────────
        var ry = popupY + HEADER_H

        active.forEachIndexed { index, r ->
            val centerY = ry + ROW_H / 2f
            val pColor = PRIORITY_COLORS[r.priority] ?: 0xFF7c3aed.toInt()
            val rx = popupX + 24f

            // Priority dot (centered vertically)
            val dotX = rx + DOT_RADIUS
            
            // Draw glow behind high priority dots (priority >= 3)
            if (r.priority >= 3) {
                canvas.drawCircle(dotX, centerY, DOT_RADIUS + 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = ThemeFactory.withAlpha(pColor, 50)
                })
            }
            
            // Main priority dot
            canvas.drawCircle(dotX, centerY, DOT_RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = pColor
            })

            // Time badge calculations
            val timeText = if (r.isFullDay || r.startTime == null) "All Day" else r.startTime.take(5)
            val badgeW = if (r.isFullDay) 110f else 95f
            
            // Premium theme color for "All Day" vs generic time
            val badgeColor = if (r.isFullDay) Color.argb(40, 124, 58, 237) // Subtle violet glass
                             else Color.argb(20, 255, 255, 255)
            val badgeTextColor = if (r.isFullDay) 0xFFa78bfa.toInt() // Light violet for dark mode readability
                                 else Color.argb(178, 255, 255, 255)
            
            val badgeX = popupX + POPUP_W - 24f - badgeW
            val badgeY = centerY - BADGE_H / 2f

            // Draw capsule badge
            CanvasDrawUtils.roundRect(canvas, badgeX, badgeY, badgeW, BADGE_H, BADGE_H / 2f, badgeColor)
            val badgePaint = CanvasDrawUtils.textPaint(badgeTextColor, TIME_SIZE, align = Paint.Align.CENTER, bold = r.isFullDay)
            val badgeTextY = centerY - (badgePaint.descent() + badgePaint.ascent()) / 2f
            canvas.drawText(timeText, badgeX + badgeW / 2f, badgeTextY, badgePaint)

            // Title (centered vertically, dynamically truncated)
            val titlePaint = CanvasDrawUtils.textPaint(Color.argb(242, 255, 255, 255), TITLE_SIZE)
            val titleX = dotX + DOT_RADIUS + 16f
            val maxTitleWidth = badgeX - 16f - titleX
            val displayTitle = truncateText(r.title, titlePaint, maxTitleWidth)
            val titleY = centerY - (titlePaint.descent() + titlePaint.ascent()) / 2f
            
            canvas.drawText(displayTitle, titleX, titleY, titlePaint)

            // Row divider lines (between rows, not below the last row)
            if (index < active.size - 1) {
                val dividerY = ry + ROW_H
                val dividerPaint = Paint().apply {
                    color = Color.argb(15, 255, 255, 255)
                    strokeWidth = 1f
                }
                canvas.drawLine(popupX + 24f, dividerY, popupX + POPUP_W - 24f, dividerY, dividerPaint)
            }

            ry += ROW_H
        }
    }
}
