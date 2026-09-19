package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import com.consistencygridwallpaper.wallpaper.native.data.ReminderItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * Draws the tactical week strip (7 days) + TODAY'S AGENDA below it.
 * Matches bundle.js Ce() and me() functions exactly:
 *   cellWidth  = (gridWidth - GAP*(COLS-1)) / COLS
 *   cellHeight = cellWidth * 1.2
 *   gap = 14px
 *   Each cell: day label (MON/TUE…) at top, date number at 60%, dot if has activity
 *   Today's cell: accent full color, BLACK text
 *   Has-activity cell: intensity-based color, accent dot at bottom
 *   Agenda: TODAY'S AGENDA header + reminder cards
 */
object WeekStripPainter {

    // ── Font sizes ────────────────────────────────────────────────────────────
    private const val DAY_LABEL_SIZE   = 16f   // MON / TUE … inside each cell
    private const val DATE_NUM_SIZE    = 38f   // large date number in each cell
    private const val AGENDA_HEAD_SIZE = 19f   // "TODAY'S AGENDA" label
    private const val AGENDA_SUB_SIZE  = 16f   // "SYSTEM.READY // SCANNING"
    private const val ITEM_TITLE_SIZE  = 24f   // reminder title in agenda
    private const val ITEM_TIME_SIZE   = 19f   // time right-aligned in agenda
    private const val EMPTY_TEXT_SIZE  = 19f   // "NO EVENTS…" placeholder

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val GAP  = 14f
    private const val COLS = 7

    private val SDF       = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val DAY_LABELS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private val PRIORITY_COLORS = mapOf(
        1 to 0xFF6b7280.toInt(),
        2 to 0xFF7c3aed.toInt(),
        3 to 0xFFf59e0b.toInt(),
        4 to 0xFFef4444.toInt()
    )

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


        // ── Week cells card layout ────────────────────────────────────────────
        val gap         = GAP * scale
        val padding     = 20f * scale
        val gridW       = sw - padding * 2
        val cellW       = (gridW - gap * (COLS - 1)) / COLS
        val cellH       = cellW * 1.2f
        val cardH       = cellH + 40f * scale   // 20f top/bottom padding
        val gridStartY  = startY + 20f * scale

        // ── Draw week cells card background ───────────────────────────────────
        CanvasDrawUtils.drawPremiumCard(
            canvas = canvas,
            x = sx,
            y = startY,
            w = sw,
            h = cardH,
            radius = 22f * scale,
            hasCustomBg = hasCustomBg,
            theme = theme
        )

        val totalHabits = state.stats.totalHabits.coerceAtLeast(1)
        val nowKey      = SDF.format(now.time)

        // Find Monday of current week
        val monday    = now.clone() as Calendar
        val dow       = monday.get(Calendar.DAY_OF_WEEK)
        val fromMon   = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY
        monday.add(Calendar.DAY_OF_MONTH, -fromMon)
        monday.set(Calendar.HOUR_OF_DAY, 0)
        monday.set(Calendar.MINUTE, 0)
        monday.set(Calendar.SECOND, 0)
        monday.set(Calendar.MILLISECOND, 0)

        var anchor: GridCellAnchor? = null

        // ── Week cells ────────────────────────────────────────────────────────
        for (col in 0 until COLS) {
            val dayCal = (monday.clone() as Calendar).also {
                it.add(Calendar.DAY_OF_MONTH, col)
            }
            val dayKey  = SDF.format(dayCal.time)
            val isToday = dayKey == nowKey
            val cellX   = sx + padding + col * (cellW + gap)

            if (isToday) anchor = GridCellAnchor(cellX, gridStartY, cellW)

            val act = state.activityMap[dayKey] ?: 0
            val pct = (act.toFloat() / totalHabits * 100).toInt().coerceIn(0, 100)

            val bgColor = when {
                isToday -> theme.gridActive
                act > 0 -> when {
                    pct <= 30  -> Color.argb(51,  255, 255, 255)
                    pct <= 60  -> Color.argb(102, 255, 255, 255)
                    pct <= 90  -> Color.argb(178, 255, 255, 255)
                    else       -> Color.WHITE
                }
                else -> theme.gridInactive
            }

            // Cell background shadow (only when cell has content)
            if (isToday || act > 0) {
                CanvasDrawUtils.roundRect(
                    canvas, cellX, gridStartY + 4f * scale, cellW, cellH, 11f * scale,
                    Color.argb(90, 0, 0, 0)
                )
            }

            // Cell background
            CanvasDrawUtils.roundRect(
                canvas, cellX, gridStartY, cellW, cellH, 11f * scale, bgColor,
                if (isToday) Color.WHITE else Color.argb(13, 255, 255, 255)
            )

            // Day label (MON, TUE, …) — positioned proportionally so it sits
            // roughly in the upper-centre of the cell, not glued to the top
            val labelColor = if (isToday) Color.argb(178, 0, 0, 0)
                             else Color.argb(114, 255, 255, 255)
            canvas.drawText(
                DAY_LABELS[col],
                cellX + cellW / 2, gridStartY + cellH * 0.28f,
                CanvasDrawUtils.textPaint(
                    labelColor, DAY_LABEL_SIZE * scale,
                    bold = true, align = Paint.Align.CENTER, letterSpacing = 1f * scale
                )
            )

            // Date number — shifted down slightly to match the new label position
            val dateColor = if (isToday) Color.BLACK else Color.WHITE
            canvas.drawText(
                dayCal.get(Calendar.DAY_OF_MONTH).toString(),
                cellX + cellW / 2, gridStartY + cellH * 0.72f,
                CanvasDrawUtils.textPaint(
                    dateColor, DATE_NUM_SIZE * scale,
                    bold = true, align = Paint.Align.CENTER
                )
            )

            // Activity dot at bottom (non-today cells with activity)
            if (!isToday && act > 0) {
                canvas.drawCircle(
                    cellX + cellW / 2, gridStartY + cellH - 9f * scale, 3f * scale,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = theme.accent
                    }
                )
            }
        }

        val stripBottom = startY + cardH + 24f * scale

        // ── Today's Agenda ────────────────────────────────────────────────────
        val agendaHeight = drawAgenda(canvas, state.reminders, theme, sx, sw, stripBottom, now, hasCustomBg, scale)

        return Pair(stripBottom - startY + agendaHeight, anchor)
    }

    /** Renders the TODAY'S AGENDA section below the week strip. */
    private fun drawAgenda(
        canvas: Canvas,
        reminders: List<ReminderItem>,
        theme: WallpaperTheme,
        gx: Float,      // Standardized sx
        gw: Float,      // Standardized sw
        startY: Float,
        now: Calendar,
        hasCustomBg: Boolean,
        scale: Float
    ): Float {
        val nowDate = SDF.format(now.time)

        // ── Section header ────────────────────────────────────────────────────
        canvas.drawText(
            "TODAY'S AGENDA", gx + 6f * scale, startY,
            CanvasDrawUtils.textPaint(
                ThemeFactory.withAlpha(theme.accent, 178),
                AGENDA_HEAD_SIZE * scale, bold = true, letterSpacing = 3f * scale
            )
        )
        canvas.drawText(
            "SYSTEM.READY // SCANNING", gx + gw - 6f * scale, startY,
            CanvasDrawUtils.textPaint(
                Color.argb(51, 255, 255, 255),
                AGENDA_SUB_SIZE * scale, bold = true,
                align = Paint.Align.RIGHT, letterSpacing = 1f * scale
            )
        )

        // ── Filter today's reminders ──────────────────────────────────────────
        val todayReminders = reminders.filter { r ->
            try {
                val start = r.startDate.trim()
                start == nowDate || (r.startDate <= nowDate && r.endDate >= nowDate)
            } catch (e: Exception) { false }
        }

        val cardH = maxOf(74f * scale, todayReminders.size * 66f * scale + 18f * scale)
        val cardY = startY + 32f * scale

        // Card background
        CanvasDrawUtils.drawPremiumCard(
            canvas = canvas,
            x = gx,
            y = cardY,
            w = gw,
            h = cardH,
            radius = 22f * scale,
            hasCustomBg = hasCustomBg,
            theme = theme
        )

        if (todayReminders.isEmpty()) {
            canvas.drawText(
                "NO EVENTS SCHEDULED FOR TODAY",
                gx + gw / 2, cardY + cardH / 2 + 5f * scale,
                CanvasDrawUtils.textPaint(
                    Color.argb(25, 255, 255, 255),
                    EMPTY_TEXT_SIZE * scale, bold = true,
                    align = Paint.Align.CENTER, letterSpacing = 2f * scale
                )
            )
        } else {
            todayReminders.take(4).forEachIndexed { i, r ->
                val ry = cardY + 14f * scale + i * 66f * scale

                // Accent marker color: parse hex markerColor, fallback to priority, fallback to theme accent
                val markerColor = try {
                    if (r.markerColor.startsWith("#")) Color.parseColor(r.markerColor)
                    else PRIORITY_COLORS[r.priority] ?: theme.accent
                } catch (e: Exception) {
                    PRIORITY_COLORS[r.priority] ?: theme.accent
                }

                // Accent bar on left (padded 24f inside card)
                CanvasDrawUtils.roundRect(canvas, gx + 24f * scale, ry, 3f * scale, 46f * scale, 1.5f * scale, markerColor)

                // Title (padded 42f)
                val title = r.title.take(40).let { if (r.title.length > 40) "$it…" else it }
                canvas.drawText(
                    title.uppercase(), gx + 42f * scale, ry + 11f * scale,
                    CanvasDrawUtils.textPaint(theme.textMain, ITEM_TITLE_SIZE * scale, bold = true, letterSpacing = 0.5f * scale)
                )

                // Time (right-aligned, padded 32f from right)
                if (r.startTime != null) {
                    canvas.drawText(
                        r.startTime, gx + gw - 32f * scale, ry + 11f * scale,
                        CanvasDrawUtils.textPaint(
                            Color.argb(76, 255, 255, 255),
                            ITEM_TIME_SIZE * scale, bold = true, align = Paint.Align.RIGHT
                        )
                    )
                }

                // Subtle separator line between items
                if (i < todayReminders.size - 1 && i < 3) {
                    val lineY = ry + 58f * scale
                    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(13, 255, 255, 255)
                        strokeWidth = 1f * scale
                    }
                    canvas.drawLine(gx + 24f * scale, lineY, gx + gw - 24f * scale, lineY, linePaint)
                }
            }
        }

        return cardH + 36f * scale
    }
}
