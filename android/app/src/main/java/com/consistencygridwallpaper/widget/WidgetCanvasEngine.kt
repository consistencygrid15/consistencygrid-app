package com.consistencygridwallpaper.widget

import android.content.Context
import android.graphics.*
import com.consistencygridwallpaper.wallpaper.native.components.*
import com.consistencygridwallpaper.wallpaper.native.core.CanvasDrawUtils
import com.consistencygridwallpaper.wallpaper.native.core.ThemeFactory
import com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataState
import java.util.Calendar

/**
 * Specialized engine that draws specific segments of the consistency grid based on WidgetType.
 */
object WidgetCanvasEngine {

    fun generate(context: Context, state: WallpaperDataState, type: WidgetType): Bitmap {
        val W = 1080
        val H = when (type) {
            WidgetType.YEAR -> 850
            WidgetType.MONTH -> 980
            WidgetType.STATS -> 350
            WidgetType.WEEK_AGENDA -> 650
            WidgetType.LIFE -> 850
        }
        
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val theme = ThemeFactory.getTheme(state.config.theme)
        val now = Calendar.getInstance()

        // Common Glass Background
        CanvasDrawUtils.roundRect(canvas, 0f, 0f, W.toFloat(), H.toFloat(), 64f, theme.bg)
        if (theme.bg == 0xFF09090b.toInt()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    W / 2f, H / 3f, H.toFloat(),
                    intArrayOf(theme.bg, Color.BLACK),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), 64f, 64f, paint)
            CanvasDrawUtils.roundRect(canvas, 0f, 0f, W.toFloat(), H.toFloat(), 64f, 
                null, Color.argb(25, 255, 255, 255), 4f)
        }

        val gx = W * 0.05f 
        val gw = W * 0.90f
        val startY = 80f

        when (type) {
            WidgetType.YEAR -> {
                // Year = WeeksGrid internally
                val tempState = state.copy(config = state.config.copy(yearGridMode = "weeks"))
                GridSectionPainter.draw(canvas, tempState, theme, gx, gw, startY, H, now)
            }
            WidgetType.MONTH -> {
                val tempState = state.copy(config = state.config.copy(yearGridMode = "month"))
                GridSectionPainter.draw(canvas, tempState, theme, gx, gw, startY, H, now)
            }
            WidgetType.LIFE -> {
                val tempState = state.copy(config = state.config.copy(yearGridMode = "life"))
                GridSectionPainter.draw(canvas, tempState, theme, gx, gw, startY, H, now)
            }
            WidgetType.WEEK_AGENDA -> {
                val tempState = state.copy(config = state.config.copy(yearGridMode = "week_strip"))
                GridSectionPainter.draw(canvas, tempState, theme, gx, gw, startY, H, now)
            }
            WidgetType.STATS -> {
                StatsHeaderPainter.draw(canvas, state, theme, gx, startY, gw, false)
            }
        }
        
        return bitmap
    }
}
