package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme

/**
 * Renders the motivational quote + "CONSISTENCY GRID" branding at the bottom.
 * Matches bundle.js Te() function exactly:
 *   Quote  : centred, slightly muted, below main content
 *   Branding: centred, darker, just below quote
 */
object QuotePainter {

    // ── Font sizes ────────────────────────────────────────────────────────────
    private const val QUOTE_SIZE    = 28f
    private const val BRAND_SIZE    = 22f

    // ── Layout constants ──────────────────────────────────────────────────────
    private const val BOTTOM_OFFSET = 180f   // distance from canvas bottom to quote baseline
    private const val BRAND_GAP     = 26f    // gap from quote to branding line

    // ─────────────────────────────────────────────────────────────────────────
    fun draw(
        canvas: Canvas,
        canvasW: Int,
        canvasH: Int,
        quote: String,
        scale: Float = 1.0f
    ) {
        if (quote.isBlank()) return

        val cx = canvasW / 2f
        val qy = canvasH - BOTTOM_OFFSET * scale

        // Quote text (uppercase — matches bundle.js .toUpperCase())
        canvas.drawText(
            quote.uppercase(), cx, qy,
            CanvasDrawUtils.textPaint(
                color  = 0xFF525252.toInt(),
                sizePx = QUOTE_SIZE * scale,
                bold   = false,
                align  = Paint.Align.CENTER,
                shadow = true
            )
        )

        // App branding
        canvas.drawText(
            "CONSISTENCY GRID", cx, qy + BRAND_GAP * scale,
            CanvasDrawUtils.textPaint(
                color  = 0xFF333333.toInt(),
                sizePx = BRAND_SIZE * scale,
                align  = Paint.Align.CENTER
            )
        )
    }
}
