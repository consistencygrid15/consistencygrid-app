package com.consistencygridwallpaper.workers

import android.content.Context
import android.graphics.*
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * WallpaperNativeRenderer — Full-featured offline Canvas renderer.
 *
 * Renders the user's EXACT wallpaper design at midnight (or any background trigger)
 * using Android Canvas — no WebView, no screen, no internet required.
 *
 * Respects ALL user settings from the wallpaper JSON:
 *  ✅ 6 Themes (minimal-dark, sunset-orange, ocean-blue, forest-green, purple-haze, monochrome)
 *  ✅ Custom background image (base64 data URI)
 *  ✅ Year grid — weeks / months / days mode
 *  ✅ Life grid  — DOB-based life-weeks progress
 *  ✅ Month habit calendar — completion intensity per day
 *  ✅ Streak stats          — uses theme accent color
 *  ✅ Quote                 — user's custom quote text
 *  ✅ showLifeGrid / showYearGrid / showHabitLayer / showAgeStats / showMissedDays / showQuote flags
 */
class WallpaperNativeRenderer(private val context: Context) {
    private val TAG = "WallpaperNativeRenderer"

    // ── Theme palette ──────────────────────────────────────────────────────────────
    private data class Palette(val bg: Int, val accent: Int, val secondary: Int, val text: Int)

    private fun palette(theme: String): Palette = when (theme) {
        "sunset-orange" -> Palette(0xFF09090b.toInt(), 0xFFFF7A00.toInt(), 0xFF2A2019.toInt(), 0xFFfafafa.toInt())
        "ocean-blue"    -> Palette(0xFF09090b.toInt(), 0xFF0088FF.toInt(), 0xFF1E293B.toInt(), 0xFFfafafa.toInt())
        "forest-green"  -> Palette(0xFF09090b.toInt(), 0xFF00CC66.toInt(), 0xFF065F46.toInt(), 0xFFfafafa.toInt())
        "purple-haze"   -> Palette(0xFF09090b.toInt(), 0xFFA855F7.toInt(), 0xFF4C1D95.toInt(), 0xFFfafafa.toInt())
        "monochrome"    -> Palette(0xFFFFFFFF.toInt(), 0xFF09090b.toInt(), 0xFFE4E4E7.toInt(), 0xFF09090b.toInt())
        else            -> Palette(0xFF09090b.toInt(), 0xFFfafafa.toInt(), 0xFF27272a.toInt(), 0xFFfafafa.toInt()) // minimal-dark
    }

    // ── Entry point ────────────────────────────────────────────────────────────────
    suspend fun generateWallpaper(jsonString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Generating native wallpaper from local JSON data")
            val json     = JSONObject(jsonString)
            val settings = json.optJSONObject("user")?.optJSONObject("settings") ?: JSONObject()
            val stats    = json.optJSONObject("stats") ?: JSONObject()
            val data     = json.optJSONObject("data")  ?: JSONObject()
            val actMap   = data.optJSONObject("activityMap") ?: JSONObject()

            // ── Canvas size
            val W = settings.optInt("canvasWidth",  1080).coerceAtLeast(720)
            val H = settings.optInt("canvasHeight", 2340).coerceAtLeast(1280)
            val sc = W / 1080f          // resolution scale factor
            val cx = W / 2f

            // ── User settings
            val theme          = settings.optString("theme", "minimal-dark")
            val dob            = settings.optString("dateOfBirth", "")
            val lifeExp        = settings.optInt("lifeExpectancyYears", 80)
            val yearGridMode   = settings.optString("yearGridMode", "weeks")
            val showLifeGrid   = settings.optBoolean("showLifeGrid", true)
            val showYearGrid   = settings.optBoolean("showYearGrid", true)
            val showHabitLayer = settings.optBoolean("showHabitLayer", true)
            val showAgeStats   = settings.optBoolean("showAgeStats", true)
            val showMissed     = settings.optBoolean("showMissedDays", false)
            val showQuote      = settings.optBoolean("showQuote", true)
            val quoteText      = settings.optString("quoteText", "Make every week count.")
            val customBg       = settings.optString("customBackgroundUrl", "")

            // ── Stats
            val streak      = stats.optInt("streak", 0)
            val totalHabits = stats.optInt("totalHabits", 0).coerceAtLeast(1)

            // ── Palette
            val p = palette(theme)

            // ── Bitmap + Canvas
            val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Background ──────────────────────────────────────────────────────
            if (customBg.startsWith("data:image")) {
                drawCustomBackground(canvas, W, H, customBg)
            } else {
                val bgPaint = Paint().apply {
                    shader = LinearGradient(0f, 0f, 0f, H.toFloat(),
                        intArrayOf(p.bg, blendColor(p.bg, p.accent, 0.08f)),
                        null, Shader.TileMode.CLAMP)
                }
                canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bgPaint)
            }

            var yPos = 140f * sc

            // 2. Date header ─────────────────────────────────────────────────────
            val datePaint = paint(p.text, 72f * sc, Typeface.BOLD, Paint.Align.CENTER)
            val dateStr = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
            canvas.drawText(dateStr, cx, yPos + 72f * sc, datePaint)
            yPos += 72f * sc + 50f * sc

            // 3. Life grid ───────────────────────────────────────────────────────
            if (showLifeGrid && dob.length >= 10) {
                yPos = drawLifeGrid(canvas, cx, W, yPos, sc, dob, lifeExp, p)
                yPos += 40f * sc
            }

            // 4. Year grid ───────────────────────────────────────────────────────
            if (showYearGrid) {
                yPos = drawYearGrid(canvas, cx, W, yPos, sc, yearGridMode, actMap, p)
                yPos += 40f * sc
            }

            // 5. Habit calendar ──────────────────────────────────────────────────
            if (showHabitLayer) {
                yPos = drawHabitCalendar(canvas, cx, W, yPos, sc, actMap, totalHabits, showMissed, p)
                yPos += 40f * sc
            }

            // 6. Streak stats ────────────────────────────────────────────────────
            if (showAgeStats) {
                yPos = drawStreak(canvas, cx, yPos, sc, streak, p)
            }

            // 7. Quote ───────────────────────────────────────────────────────────
            if (showQuote && quoteText.isNotBlank()) {
                val qPaint = paint(withAlpha(p.text, 120), 34f * sc, Typeface.ITALIC, Paint.Align.CENTER)
                val qY = maxOf(yPos + 40f * sc, H - 140f * sc)
                val display = if (quoteText.length > 58) quoteText.take(55) + "…" else quoteText
                canvas.drawText("\"$display\"", cx, qY, qPaint)
            }

            Log.d(TAG, "✅ Native Wallpaper Generated Successfully!")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to generate native wallpaper", e)
            null
        }
    }

    // ── Custom background ──────────────────────────────────────────────────────────
    private fun drawCustomBackground(canvas: Canvas, W: Int, H: Int, dataUri: String) {
        try {
            val b64   = dataUri.substringAfter(",")
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val src   = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            val sc    = maxOf(W.toFloat() / src.width, H.toFloat() / src.height)
            val sw    = (src.width  * sc).toInt()
            val sh    = (src.height * sc).toInt()
            val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
            canvas.drawBitmap(scaled, (W - sw) / 2f, (H - sh) / 2f, null)
            src.recycle(); scaled.recycle()
            // Dark overlay for text readability
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(),
                Paint().apply { color = Color.argb(160, 0, 0, 0) })
        } catch (e: Exception) {
            Log.w(TAG, "Custom bg failed: ${e.message}")
            canvas.drawColor(0xFF09090b.toInt())
        }
    }

    // ── Life grid ──────────────────────────────────────────────────────────────────
    private fun drawLifeGrid(
        canvas: Canvas, cx: Float, W: Int, startY: Float, sc: Float,
        dob: String, lifeExpYears: Int, p: Palette
    ): Float {
        return try {
            val dobCal = Calendar.getInstance().apply {
                time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dob) ?: return startY
            }
            val weeksLived = ((System.currentTimeMillis() - dobCal.timeInMillis) /
                    (7L * 24 * 3600 * 1000)).toInt().coerceIn(0, lifeExpYears * 52)
            val totalWeeks = lifeExpYears * 52

            val cols     = 52
            val rows     = lifeExpYears
            val cellMax  = ((W - 80f * sc) / cols)
            val cell     = cellMax.coerceIn(6f * sc, 14f * sc)
            val gap      = cell * 0.25f
            val gridW    = cols * (cell + gap) - gap
            val gx       = cx - gridW / 2f
            var yPos     = startY

            label(canvas, gx, yPos, sc, "LIFE IN WEEKS", withAlpha(p.text, 120))
            yPos += 28f * sc + 8f * sc

            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            var idx = 0
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x  = gx + col * (cell + gap)
                    val y  = yPos + row * (cell + gap)
                    cp.color = if (idx < weeksLived) p.accent else p.secondary
                    cp.alpha = if (idx < weeksLived) 210 else 70
                    canvas.drawRoundRect(RectF(x, y, x + cell, y + cell), cell * 0.2f, cell * 0.2f, cp)
                    idx++
                }
            }
            yPos + rows * (cell + gap)
        } catch (e: Exception) {
            Log.w(TAG, "Life grid error: ${e.message}")
            startY
        }
    }

    // ── Year grid ──────────────────────────────────────────────────────────────────
    private fun drawYearGrid(
        canvas: Canvas, cx: Float, W: Int, startY: Float, sc: Float,
        mode: String, actMap: JSONObject, p: Palette
    ): Float {
        val gx       = cx - (W - 80f * sc) / 2f
        val gridW    = W - 80f * sc
        val now      = Calendar.getInstance()
        val sdfKey   = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        return when (mode) {
            "months" -> {
                val cols  = 6; val rows = 2
                val cell  = (gridW / cols) - 8f * sc
                val cellH = cell * 0.65f
                var yPos  = startY
                label(canvas, gx, yPos, sc, "THIS YEAR (MONTHS)", withAlpha(p.text, 120))
                yPos += 28f * sc + 8f * sc
                val monthNames = listOf("JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC")
                val curMonth = now.get(Calendar.MONTH)
                val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                val tp = paint(Color.WHITE, 22f * sc, Typeface.BOLD, Paint.Align.CENTER)
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val mi = row * cols + col
                        val x  = gx + col * (cell + 8f * sc)
                        val y  = yPos + row * (cellH + 8f * sc)
                        // Count activity
                        val mc = Calendar.getInstance().apply { set(Calendar.MONTH, mi); set(Calendar.DAY_OF_MONTH, 1) }
                        var act = 0
                        for (d in 1..mc.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                            mc.set(Calendar.DAY_OF_MONTH, d)
                            if (actMap.optInt(sdfKey.format(mc.time), 0) > 0) act++
                        }
                        cp.color = when { mi == curMonth -> p.accent; mi > curMonth -> p.secondary; act > 0 -> p.accent; else -> p.secondary }
                        cp.alpha = when { mi == curMonth -> 255; mi > curMonth -> 55; act > 0 -> 160; else -> 90 }
                        canvas.drawRoundRect(RectF(x, y, x + cell, y + cellH), 10f * sc, 10f * sc, cp)
                        tp.color = if (mi <= curMonth) Color.WHITE else withAlpha(Color.WHITE, 100)
                        canvas.drawText(monthNames[mi], x + cell / 2, y + cellH / 2 + tp.textSize / 3, tp)
                    }
                }
                yPos + rows * (cellH + 8f * sc)
            }
            "days" -> {
                val cols   = 30
                val dot    = ((gridW - (cols - 1) * 3f * sc) / cols).coerceIn(5f * sc, 14f * sc)
                val rows   = (365 + cols - 1) / cols
                val doy    = now.get(Calendar.DAY_OF_YEAR)
                var yPos   = startY
                label(canvas, gx, yPos, sc, "THIS YEAR (DAYS)", withAlpha(p.text, 120))
                yPos += 28f * sc + 8f * sc
                val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                for (i in 0 until 365) {
                    val row = i / cols; val col = i % cols
                    val dc  = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, i + 1) }
                    val has = actMap.optInt(sdfKey.format(dc.time), 0) > 0
                    val x   = gx + col * (dot + 3f * sc) + dot / 2
                    val y   = yPos + row * (dot + 3f * sc) + dot / 2
                    cp.color = when { i + 1 == doy -> p.accent; i + 1 > doy -> p.secondary; has -> p.accent; else -> p.secondary }
                    cp.alpha = when { i + 1 == doy -> 255; i + 1 > doy -> 45; has -> 190; else -> 90 }
                    canvas.drawCircle(x, y, dot / 2, cp)
                }
                yPos + rows * (dot + 3f * sc)
            }
            else -> { // "weeks"
                val cols   = 13; val rows = 4
                val cell   = (gridW / cols - 4f * sc).coerceIn(16f * sc, 60f * sc)
                val cellH  = cell * 1.1f
                val curWk  = now.get(Calendar.WEEK_OF_YEAR)
                var yPos   = startY
                label(canvas, gx, yPos, sc, "THIS YEAR (WEEKS)", withAlpha(p.text, 120))
                yPos += 28f * sc + 8f * sc
                val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
                val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * sc; color = p.accent }
                for (row in 0 until rows) {
                    for (col in 0 until cols) {
                        val wk = row * cols + col + 1
                        if (wk > 52) break
                        val x  = gx + col * (cell + 4f * sc)
                        val y  = yPos + row * (cellH + 4f * sc)
                        val rc = RectF(x, y, x + cell, y + cellH)
                        // Check week activity
                        val wc = Calendar.getInstance().apply { set(Calendar.WEEK_OF_YEAR, wk); set(Calendar.DAY_OF_WEEK, Calendar.MONDAY) }
                        var act = 0
                        for (d in 0 until 7) { act += actMap.optInt(sdfKey.format(wc.time), 0); wc.add(Calendar.DAY_OF_YEAR, 1) }
                        cp.color = when { wk == curWk -> p.accent; wk > curWk -> p.secondary; act > 0 -> p.accent; else -> p.secondary }
                        cp.alpha = when { wk == curWk -> 255; wk > curWk -> 55; act > 0 -> 175; else -> 90 }
                        canvas.drawRoundRect(rc, 6f * sc, 6f * sc, cp)
                        if (wk == curWk) canvas.drawRoundRect(rc, 6f * sc, 6f * sc, bp)
                    }
                }
                yPos + rows * (cellH + 4f * sc)
            }
        }
    }

    // ── Habit calendar (current month) ─────────────────────────────────────────────
    private fun drawHabitCalendar(
        canvas: Canvas, cx: Float, W: Int, startY: Float, sc: Float,
        actMap: JSONObject, totalHabits: Int, showMissed: Boolean, p: Palette
    ): Float {
        val sdfKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val now    = Calendar.getInstance()
        val curDay = now.get(Calendar.DAY_OF_MONTH)
        val curMon = now.get(Calendar.MONTH)
        val curYr  = now.get(Calendar.YEAR)
        val mc     = Calendar.getInstance().apply { set(curYr, curMon, 1) }
        val offset = mc.get(Calendar.DAY_OF_WEEK) - 1
        val days   = mc.getActualMaximum(Calendar.DAY_OF_MONTH)

        val cols   = 7; val rows = 6
        val gridW  = W - 80f * sc
        val cell   = (gridW / cols - 4f * sc).coerceIn(60f * sc, 140f * sc)
        val gx     = cx - (cols * (cell + 4f * sc) - 4f * sc) / 2f
        var yPos   = startY

        // Month label
        val mlabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(now.time).uppercase()
        label(canvas, gx, yPos, sc, mlabel, withAlpha(p.text, 120))
        yPos += 28f * sc + 8f * sc

        // Day headers  S M T W T F S
        val dayNames = listOf("S","M","T","W","T","F","S")
        val dhp = paint(withAlpha(p.text, 80), 22f * sc, Typeface.NORMAL, Paint.Align.CENTER)
        dayNames.forEachIndexed { i, n ->
            canvas.drawText(n, gx + i * (cell + 4f * sc) + cell / 2, yPos + 22f * sc, dhp)
        }
        yPos += 22f * sc + 12f * sc

        // Cells
        val cp  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val bp  = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * sc; color = p.accent }
        val dnp = paint(p.text, (cell * 0.32f).coerceIn(16f * sc, 28f * sc), Typeface.BOLD, Paint.Align.CENTER)

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val di = row * cols + col - offset + 1
                val x  = gx + col * (cell + 4f * sc)
                val y  = yPos + row * (cell + 4f * sc)
                val rc = RectF(x, y, x + cell, y + cell)
                val r  = cell * 0.18f

                if (di in 1..days) {
                    val dayCal = Calendar.getInstance().apply { set(curYr, curMon, di) }
                    val key    = sdfKey.format(dayCal.time)
                    val done   = actMap.optInt(key, 0)

                    when {
                        done > 0 -> {
                            val intensity = (done.toFloat() / totalHabits).coerceIn(0.2f, 1f)
                            cp.color = p.accent
                            cp.alpha = (255 * intensity).toInt()
                        }
                        di > curDay -> { cp.color = p.secondary; cp.alpha = 45 }
                        showMissed  -> { cp.color = 0xFFEF4444.toInt(); cp.alpha = 110 }
                        else        -> { cp.color = p.secondary; cp.alpha = 80 }
                    }
                    canvas.drawRoundRect(rc, r, r, cp)
                    if (di == curDay) canvas.drawRoundRect(rc, r, r, bp)

                    // Day number
                    dnp.color = if (done > 0) Color.WHITE else withAlpha(p.text, 160)
                    canvas.drawText("$di", x + cell / 2, y + cell / 2 + dnp.textSize / 3, dnp)
                } else {
                    cp.color = p.bg; cp.alpha = 40
                    canvas.drawRoundRect(rc, r, r, cp)
                }
            }
        }
        return yPos + rows * (cell + 4f * sc)
    }

    // ── Streak ─────────────────────────────────────────────────────────────────────
    private fun drawStreak(
        canvas: Canvas, cx: Float, startY: Float, sc: Float,
        streak: Int, p: Palette
    ): Float {
        val sp = paint(p.accent, 72f * sc, Typeface.BOLD, Paint.Align.CENTER)
        val lp = paint(withAlpha(p.text, 140), 28f * sc, Typeface.NORMAL, Paint.Align.CENTER)
        canvas.drawText("$streak 🔥", cx, startY + 72f * sc, sp)
        canvas.drawText("DAY STREAK", cx, startY + 72f * sc + 36f * sc, lp)
        return startY + 72f * sc + 36f * sc + 16f * sc
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────
    private fun paint(color: Int, textSize: Float, style: Int, align: Paint.Align) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color    = color
            this.textSize = textSize
            this.typeface = Typeface.create(Typeface.SANS_SERIF, style)
            this.textAlign = align
        }

    private fun label(canvas: Canvas, x: Float, y: Float, sc: Float, text: String, color: Int) {
        canvas.drawText(text, x, y + 24f * sc,
            paint(color, 24f * sc, Typeface.NORMAL, Paint.Align.LEFT))
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun blendColor(base: Int, accent: Int, ratio: Float): Int {
        val r = (Color.red(base)   * (1 - ratio) + Color.red(accent)   * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(base) * (1 - ratio) + Color.green(accent) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(base)  * (1 - ratio) + Color.blue(accent)  * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
