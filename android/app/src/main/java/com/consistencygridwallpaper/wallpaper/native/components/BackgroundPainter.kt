package com.consistencygridwallpaper.wallpaper.native.components

import android.graphics.*
import android.util.Base64
import android.util.Log
import com.consistencygridwallpaper.wallpaper.native.core.WallpaperTheme

/**
 * Renders the canvas background.
 * Matches bundle.js fe() + de() functions exactly:
 *   - Custom bg: decode base64 image → cover/center-crop → rgba(0,0,0,0.55) overlay
 *   - Default  : radial gradient from BG colour at centre to black at edges
 */
object BackgroundPainter {

    private const val TAG            = "BackgroundPainter"
    private const val OVERLAY_ALPHA  = 140   // 0.55 × 255 ≈ 140

    // ─────────────────────────────────────────────────────────────────────────
    fun draw(canvas: Canvas, W: Int, H: Int, theme: WallpaperTheme, customBgBase64: String) {
        if (customBgBase64.startsWith("data:image")) {
            drawCustomBackground(canvas, W, H, customBgBase64)
        } else {
            drawRadialGradientBackground(canvas, W, H, theme)
        }
    }

    // ── Default: radial gradient background ───────────────────────────────────
    /**
     * Radial gradient: BG colour at (W/2, H/3) → dark at edges.
     * Matches bundle.js fe() default path.
     */
    private fun drawRadialGradientBackground(canvas: Canvas, W: Int, H: Int, theme: WallpaperTheme) {
        canvas.drawColor(theme.bg)

        val darkColor = if (theme.bg == 0xFF09090b.toInt()) Color.BLACK else theme.bg
        canvas.drawRect(
            0f, 0f, W.toFloat(), H.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    W / 2f, H / 3f, H.toFloat(),
                    intArrayOf(theme.bg, darkColor),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
        )
    }

    // ── Custom image background ───────────────────────────────────────────────
    /**
     * Decodes a base64 data-URI image, draws it in cover mode,
     * then overlays rgba(0,0,0,0.55) to darken.
     * Matches bundle.js fe() image path exactly.
     */
    private fun drawCustomBackground(canvas: Canvas, W: Int, H: Int, dataUri: String) {
        try {
            val b64   = dataUri.substringAfter(",")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val src   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: run {
                canvas.drawColor(0xFF09090b.toInt())
                return
            }

            // Cover crop: scale so the image fills the entire canvas, centred
            val srcRect = computeCoverRect(src.width, src.height, W, H)
            canvas.drawBitmap(
                src, srcRect,
                RectF(0f, 0f, W.toFloat(), H.toFloat()),
                null
            )
            src.recycle()

            // Dark overlay
            canvas.drawRect(
                0f, 0f, W.toFloat(), H.toFloat(),
                Paint().apply { color = Color.argb(OVERLAY_ALPHA, 0, 0, 0) }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Custom background render failed: ${e.message}")
            canvas.drawColor(0xFF09090b.toInt())
        }
    }

    /**
     * Returns a [Rect] that crops [srcW]×[srcH] to the same aspect ratio as
     * [dstW]×[dstH], centred — equivalent to CSS "object-fit: cover".
     */
    private fun computeCoverRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val imgRatio    = srcW.toFloat() / srcH
        val canvasRatio = dstW.toFloat() / dstH

        return if (imgRatio > canvasRatio) {
            // Image is wider → crop horizontally
            val newW = (srcH * canvasRatio).toInt()
            val offX = (srcW - newW) / 2
            Rect(offX, 0, offX + newW, srcH)
        } else {
            // Image is taller → crop vertically
            val newH = (srcW / canvasRatio).toInt()
            val offY = (srcH - newH) / 2
            Rect(0, offY, srcW, offY + newH)
        }
    }
}
