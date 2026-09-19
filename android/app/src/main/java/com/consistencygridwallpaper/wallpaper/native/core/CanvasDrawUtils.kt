package com.consistencygridwallpaper.wallpaper.native.core

import android.graphics.*

/**
 * Low-level Canvas drawing utilities.
 * Every function here mirrors a specific helper in bundle.js for pixel-perfect parity.
 */
object CanvasDrawUtils {

    // ─── Rounded Rect (matches bundle.js L() function) ────────────────────────

    fun roundRect(
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        r: Float,
        fillColor: Int? = null,
        strokeColor: Int? = null,
        strokeWidth: Float = 2f
    ) {
        val rect = RectF(x, y, x + w, y + h)
        val radius = r.coerceAtMost(minOf(w, h) / 2f)
        if (fillColor != null) {
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = fillColor
            })
        }
        if (strokeColor != null) {
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = strokeColor
                this.strokeWidth = strokeWidth
            })
        }
    }

    // ─── Glassmorphic Card (matches bundle.js ne() function) ──────────────────

    /**
     * Draws a glassmorphic card with simulated shadow + frosted glass fill.
     * On software canvas (bitmap), setShadowLayer only works for text, so
     * we simulate the shadow by drawing an offset semi-transparent copy.
     */
    fun glassmorphicCard(
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float = 40f,
        fillColor: Int = Color.argb(107, 0, 0, 0),       // rgba(0,0,0,0.42)
        borderColor: Int = Color.argb(25, 255, 255, 255)  // rgba(255,255,255,0.10)
    ) {
        val r = radius.coerceAtMost(minOf(w, h) / 2f)
        // Shadow simulation (offset + blurred by drawing multiple layers)
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        listOf(
            Pair(Color.argb(80, 0, 0, 0),  RectF(x - 2f, y + 8f, x + w + 2f, y + h + 12f)),
            Pair(Color.argb(50, 0, 0, 0),  RectF(x - 4f, y + 12f, x + w + 4f, y + h + 18f)),
            Pair(Color.argb(25, 0, 0, 0),  RectF(x - 8f, y + 18f, x + w + 8f, y + h + 26f))
        ).forEach { (c, rect) ->
            shadowPaint.color = c
            canvas.drawRoundRect(rect, r + 4f, r + 4f, shadowPaint)
        }
        // Glass fill
        roundRect(canvas, x, y, w, h, r, fillColor, borderColor, 2f)
    }

    /**
     * Draws a premium card that automatically adapts to the wallpaper theme
     * and whether there is a custom background.
     */
    fun drawPremiumCard(
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        radius: Float,
        hasCustomBg: Boolean,
        theme: com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
    ) {
        if (hasCustomBg) {
            glassmorphicCard(canvas, x, y, w, h, radius)
        } else {
            val isLightTheme = theme.bg == 0xFFffffff.toInt()
            val cardFill = if (isLightTheme) Color.argb(8, 0, 0, 0) else Color.argb(5, 255, 255, 255)
            val cardStroke = if (isLightTheme) Color.argb(18, 0, 0, 0) else Color.argb(13, 255, 255, 255)
            roundRect(canvas, x, y, w, h, radius, cardFill, cardStroke, 1.5f)
        }
    }

    // ─── Grid Cell (matches bundle.js A() function) ───────────────────────────

    /**
     * Draws a single grid cell with optional glow (for today's cell).
     * Includes inner highlight shimmer on active cells.
     */
    fun drawGridCell(
        canvas: Canvas,
        x: Float, y: Float, size: Float,
        fillColor: Int,
        isToday: Boolean,
        cornerRadius: Float = 8f,
        theme: com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme
    ) {
        val rect = RectF(x, y, x + size, y + size)
        val rr = cornerRadius.coerceAtMost(size / 2f)
        val isInactive = fillColor == theme.gridInactive

        // Glow layers for today's cell (5 expanding semi-transparent layers)
        if (isToday) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            for (layer in 5 downTo 1) {
                glowPaint.color = ThemeFactory.withAlpha(theme.intensityFull, (64 / layer).coerceIn(1, 255))
                val exp = layer * 2.5f
                canvas.drawRoundRect(
                    RectF(x - exp, y - exp, x + size + exp, y + size + exp),
                    rr + 4f, rr + 4f, glowPaint
                )
            }
        }

        // Shadow simulation for non-inactive cells
        if (!isInactive) {
            canvas.drawRoundRect(
                RectF(x, y + 4f, x + size, y + size + 4f), rr, rr,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.argb(102, 0, 0, 0)
                }
            )
        }

        // Main fill
        canvas.drawRoundRect(rect, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        })

        // Outline stroke
        val outlineAlpha = when {
            isToday    -> 255
            isInactive -> 13   // rgba(255,255,255,0.05)
            else       -> 25   // rgba(255,255,255,0.10)
        }
        val outlineColor = if (isToday) theme.intensityFull
                           else ThemeFactory.withAlpha(Color.WHITE, outlineAlpha)
        canvas.drawRoundRect(rect, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = outlineColor
            strokeWidth = 1f
        })

        // Inner highlight shimmer (top-left → transparent → bottom-right darkening)
        if (!isInactive) {
            canvas.drawRoundRect(
                RectF(x + 1f, y + 1f, x + size - 1f, y + size - 1f),
                (rr - 1f).coerceAtLeast(0f), (rr - 1f).coerceAtLeast(0f),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    shader = LinearGradient(
                        x, y, x + size, y + size,
                        intArrayOf(
                            Color.argb(51, 255, 255, 255),
                            Color.TRANSPARENT,
                            Color.argb(25, 0, 0, 0)
                        ),
                        floatArrayOf(0f, 0.5f, 1f),
                        Shader.TileMode.CLAMP
                    )
                }
            )
        }
    }

    // ─── Text paint factory ───────────────────────────────────────────────────

    fun textPaint(
        color: Int,
        sizePx: Float,
        bold: Boolean = false,
        italic: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT,
        shadow: Boolean = false,
        letterSpacing: Float = 0f
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        this.textSize = sizePx
        this.textAlign = align
        this.typeface = Typeface.create(
            Typeface.SANS_SERIF,
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold           -> Typeface.BOLD
                italic         -> Typeface.ITALIC
                else           -> Typeface.NORMAL
            }
        )
        if (shadow) setShadowLayer(4f, 0f, 2f, Color.argb(102, 0, 0, 0))
        if (letterSpacing != 0f) this.letterSpacing = letterSpacing / sizePx
    }

    // ─── Flame Icon (matches bundle.js Ie() function) ─────────────────────────

    /**
     * Draws the flame icon using the exact bezier control points from bundle.js.
     * x, y = top-left corner of the icon bounding box. size = icon size in pixels.
     */
    fun drawFlame(canvas: Canvas, x: Float, y: Float, size: Float, color: Int) {
        val sc = size / 24f
        val r: (Float) -> Float = { v -> x + v * sc }
        val o: (Float) -> Float = { v -> y + v * sc }

        val path = Path()
        path.moveTo(r(8.5f), o(14.5f))
        path.cubicTo(r(8.5f), o(14.5f), r(11f), o(12f), r(11f), o(12f))
        path.cubicTo(r(11f), o(10.62f), r(10.5f), o(10f), r(10f), o(9f))
        path.cubicTo(r(8.928f), o(6.857f), r(9.776f), o(4.946f), r(12f), o(3f))
        path.cubicTo(r(12.5f), o(5.5f), r(14f), o(7.9f), r(16f), o(9.5f))
        path.cubicTo(r(18f), o(11.1f), r(19f), o(13f), r(19f), o(15f))
        path.cubicTo(r(19f), o(18.866f), r(15.866f), o(22f), r(12f), o(22f))
        path.cubicTo(r(8.134f), o(22f), r(5f), o(18.866f), r(5f), o(15f))
        path.cubicTo(r(5f), o(13.847f), r(5.433f), o(12.706f), r(6f), o(12f))
        path.cubicTo(r(6f), o(12f), r(8.5f), o(14.5f), r(8.5f), o(14.5f))
        path.close()

        // Stroke  
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 3f * sc
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        })

        // Gradient fill (top opaque → bottom transparent)
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(
                x, y, x, y + size,
                intArrayOf(ThemeFactory.withAlpha(color, 102), ThemeFactory.withAlpha(color, 34)),
                null, Shader.TileMode.CLAMP
            )
        })
    }
}
