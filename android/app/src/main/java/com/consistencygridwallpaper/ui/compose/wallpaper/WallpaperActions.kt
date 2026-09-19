package com.consistencygridwallpaper.ui.compose.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.consistencygridwallpaper.storage.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLEncoder

private const val TAG_ACTIONS = "WallpaperActions"

// ─────────────────────────────────────────────────────────────────────────────
// Server sync helpers
// ─────────────────────────────────────────────────────────────────────────────

suspend fun loadSettingsFromServer(prefs: UserPrefs, onResult: (WallpaperSettings) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val token = prefs.getToken() ?: return@withContext  // no token = offline fine
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val baseUrl = prefs.getBaseUrl().trimEnd('/')
            val req = Request.Builder()
                .url("$baseUrl/api/wallpaper-data?token=${java.net.URLEncoder.encode(token, "UTF-8")}")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Cookie", "publicToken=$token; native_auth=true")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext   // server error — keep local data
            val body = resp.body?.string() ?: return@withContext
            val json = JSONObject(body)
            val s = json.optJSONObject("settings") ?: json.optJSONObject("user")?.optJSONObject("settings") ?: return@withContext

            // CRITICAL: Read local custom background BEFORE building the loaded settings.
            // The server NEVER has the local base64 image — it would return "" or a remote URL.
            // We must preserve whatever the user picked locally — local always wins for custom bg.
            val localCustomBg = prefs.getWallpaperSettings()
                ?.let { runCatching { org.json.JSONObject(it).optString("customBackgroundUrl", "") }.getOrElse { "" } }
                .orEmpty()

            val loaded = WallpaperSettings(
                dob                 = s.optString("dateOfBirth", "").let { if (it.length >= 10) it.substring(0, 10) else "" },
                lifeExpectancyYears = s.optInt("lifeExpectancyYears", 80),
                theme               = s.optString("theme", "minimal-dark"),
                width               = s.optInt("canvasWidth", 1080),
                height              = s.optInt("canvasHeight", 2340),
                yearGridMode        = s.optString("yearGridMode", "weeks"),
                wallpaperType       = s.optString("wallpaperType", "lockscreen"),
                showLifeGrid        = s.optBoolean("showLifeGrid", true),
                showYearGrid        = s.optBoolean("showYearGrid", true),
                showAgeStats        = s.optBoolean("showAgeStats", true),
                showMissedDays      = s.optBoolean("showMissedDays", false),
                showHabitLayer      = s.optBoolean("showHabitLayer", true),
                showLegend          = s.optBoolean("showLegend", false),
                showQuote           = s.optBoolean("showQuote", true),
                quote               = s.optString("quoteText", "Make every week count."),
                goalEnabled         = s.optBoolean("goalEnabled", false),
                goalTitle           = s.optString("goalTitle", ""),
                // Local custom background always wins — NEVER overwrite with server value
                customBackgroundUrl = localCustomBg
            )
            withContext(Dispatchers.Main) { onResult(loaded) }
        } catch (e: Exception) {
            // Network unavailable or timed out — silently ignore.
            // Local offline data is the source of truth.
            Log.w(TAG_ACTIONS, "loadSettingsFromServer skipped (offline?): ${e.message}")
        }
    }
}

suspend fun saveSettingsToServer(prefs: UserPrefs, s: WallpaperSettings) {
    withContext(Dispatchers.IO) {
        val sessionToken = prefs.getSessionToken() ?: throw Exception("Not authenticated")
        val body = JSONObject().apply {
            put("dob",                s.dob)
            put("lifeExpectancyYears", s.lifeExpectancyYears)
            put("theme",              s.theme)
            put("width",              s.width)
            put("height",             s.height)
            put("yearGridMode",       s.yearGridMode)
            put("wallpaperType",      s.wallpaperType)
            put("showLifeGrid",       s.showLifeGrid)
            put("showYearGrid",       s.showYearGrid)
            put("showAgeStats",       s.showAgeStats)
            put("showMissedDays",     s.showMissedDays)
            put("showHabitLayer",     s.showHabitLayer)
            put("showLegend",         s.showLegend)
            put("showQuote",          s.showQuote)
            put("quote",              s.quote)
            put("goalEnabled",        s.goalEnabled)
            put("goalTitle",          s.goalTitle)
            put("goalStartDate",      s.goalStartDate)
            put("goalDurationDays",   s.goalDurationDays)
            put("goalUnit",           s.goalUnit)
            // Send a flag so server knows a custom bg exists, but NOT the raw base64
            // (it would be 500KB+ which would bloat the request and the DB).
            // The image lives permanently on the local device in app cache.
            put("hasCustomBackground", s.customBackgroundUrl.isNotBlank())
        }.toString()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val req = Request.Builder()
            .url("https://consistencygrid.com/api/settings/save")
            .addHeader("Cookie", "next-auth.session-token=$sessionToken")
            .addHeader("Content-Type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            val err = resp.body?.string() ?: "Unknown error"
            throw Exception("HTTP ${resp.code}: $err")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Local settings persistence helpers
// ─────────────────────────────────────────────────────────────────────────────

fun WallpaperSettings.toJson(): String = JSONObject().apply {
    put("dob",                dob)
    put("lifeExpectancyYears", lifeExpectancyYears)
    put("theme",              theme)
    put("width",              width)
    put("height",             height)
    put("yearGridMode",       yearGridMode)
    put("wallpaperType",      wallpaperType)
    put("showLifeGrid",       showLifeGrid)
    put("showYearGrid",       showYearGrid)
    put("showAgeStats",       showAgeStats)
    put("showMissedDays",     showMissedDays)
    put("showHabitLayer",     showHabitLayer)
    put("showLegend",         showLegend)
    put("showQuote",          showQuote)
    put("quote",              quote)
    put("goalEnabled",        goalEnabled)
    put("goalTitle",          goalTitle)
    put("goalStartDate",      goalStartDate)
    put("goalDurationDays",   goalDurationDays)
    put("goalUnit",           goalUnit)
    put("customBackgroundUrl", customBackgroundUrl)
}.toString()

fun wallpaperSettingsFromJson(json: JSONObject) = WallpaperSettings(
    dob                 = json.optString("dob", ""),
    lifeExpectancyYears = json.optInt("lifeExpectancyYears", 80),
    theme               = json.optString("theme", "minimal-dark"),
    width               = json.optInt("width", 1080),
    height              = json.optInt("height", 2340),
    yearGridMode        = json.optString("yearGridMode", "weeks"),
    wallpaperType       = json.optString("wallpaperType", "lockscreen"),
    showLifeGrid        = json.optBoolean("showLifeGrid", true),
    showYearGrid        = json.optBoolean("showYearGrid", true),
    showAgeStats        = json.optBoolean("showAgeStats", true),
    showMissedDays      = json.optBoolean("showMissedDays", false),
    showHabitLayer      = json.optBoolean("showHabitLayer", true),
    showLegend          = json.optBoolean("showLegend", false),
    showQuote           = json.optBoolean("showQuote", true),
    quote               = json.optString("quote", "Make every week count."),
    goalEnabled         = json.optBoolean("goalEnabled", false),
    goalTitle           = json.optString("goalTitle", ""),
    goalStartDate       = json.optString("goalStartDate", ""),
    goalDurationDays    = json.optInt("goalDurationDays", 30),
    goalUnit            = json.optString("goalUnit", "day"),
    customBackgroundUrl = json.optString("customBackgroundUrl", "")
)

// Filename used everywhere — single source of truth
private const val CUSTOM_BG_FILENAME = "custom_background.jpg"

/**
 * Converts a gallery URI to a compressed base64 data URL.
 *
 * Saves the image to TWO persistent locations:
 *   1. context.filesDir/custom_background.jpg — survives cache clears, used as primary
 *   2. context.cacheDir/custom_bg.jpg         — for backward-compat with WallpaperWorker
 *
 * Returns the data URL string on success, null on failure.
 */
suspend fun uriToBase64DataUrl(context: Context, uri: Uri, maxSizePx: Int = 1080): String? =
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Scale down so it fits in the canvas without huge memory usage
            val scale = maxSizePx.toFloat() / maxOf(original.width, original.height)
            val scaledBitmap: Bitmap = if (scale < 1f) {
                val w = (original.width * scale).toInt()
                val h = (original.height * scale).toInt()
                Bitmap.createScaledBitmap(original, w, h, true).also { original.recycle() }
            } else original

            // Compress to JPEG at 85% quality
            val out = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            scaledBitmap.recycle()
            val jpegBytes = out.toByteArray()

            // ── Primary persistent store: filesDir is never cleared by system ──
            File(context.filesDir, CUSTOM_BG_FILENAME).writeBytes(jpegBytes)

            // ── Secondary: cacheDir for WallpaperWorker backward-compat ─────────
            File(context.cacheDir, "custom_bg.jpg").writeBytes(jpegBytes)

            val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (e: Exception) {
            Log.e(TAG_ACTIONS, "uriToBase64DataUrl failed", e)
            null
        }
    }

/**
 * Reloads the custom background from the persistent file in filesDir.
 * Call this on app start to restore the image even if SharedPrefs was
 * cleared or the base64 string was lost.
 * Returns null if no image has been saved.
 */
fun loadCustomBackgroundFromFile(context: Context): String? {
    return try {
        val file = File(context.filesDir, CUSTOM_BG_FILENAME)
        if (!file.exists()) return null
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return null
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    } catch (e: Exception) {
        Log.w(TAG_ACTIONS, "loadCustomBackgroundFromFile failed: ${e.message}")
        null
    }
}

/** Deletes the locally saved custom background image. */
fun clearCustomBackground(context: Context) {
    try {
        File(context.filesDir, CUSTOM_BG_FILENAME).delete()
        File(context.cacheDir, "custom_bg.jpg").delete()
    } catch (_: Exception) {}
}


// ─────────────────────────────────────────────────────────────────────────────
// Wallpaper apply helper
// ─────────────────────────────────────────────────────────────────────────────

fun applyBitmapAsWallpaper(context: Context, base64: String, target: String): String {
    val clean  = if (base64.contains(",")) base64.split(",")[1] else base64
    val bytes  = Base64.decode(clean, Base64.DEFAULT)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalArgumentException("Failed to decode bitmap")

    val wm = WallpaperManager.getInstance(context)
    val t  = target.trim().uppercase()

    if (t == "NONE") {
        if (!bitmap.isRecycled) bitmap.recycle()
        return "⏭️ Wallpaper applied skipped (Widgets Only mode)."
    }

    // Read-back of another wallpaper target is restricted on modern Android and
    // requires privileged permissions. Apply only the requested target instead.
    when (t) {
        "HOME" -> wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
        "LOCK" -> wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        else   -> wm.setBitmap(bitmap, null, true,
            WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
    }

    // ── 2. MIUI/Xiaomi extra: write directly to MIUI wallpaper files ─────────
    // MIUI's carousel feature can override wallpapers set via the standard API.
    // Writing directly to MIUI's image paths ensures the wallpaper is preserved
    // even when carousel is active. This is a best-effort supplement, not a replacement.
    try {
        val isMiui = run {
            val isXiaomi = android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
            val miuiProp = try {
                val clazz = Class.forName("android.os.SystemProperties")
                val getMethod = clazz.getMethod("get", String::class.java, String::class.java)
                (getMethod.invoke(null, "ro.miui.ui.version.name", "") as? String).orEmpty()
            } catch (_: Exception) { "" }
            isXiaomi || miuiProp.isNotBlank()
        }

        if (isMiui) {
            val homeWallpaperPath = "/data/system/theme_magic/users/0/wallpaper/image/home_wallpaper.jpg"
            val lockWallpaperPath = "/data/system/theme_magic/users/0/wallpaper/image/lock_wallpaper.jpg"

            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out)
            val jpegBytes = out.toByteArray()

            when (t) {
                "HOME" -> java.io.File(homeWallpaperPath).takeIf { it.parentFile?.exists() == true }
                    ?.writeBytes(jpegBytes)
                "LOCK" -> java.io.File(lockWallpaperPath).takeIf { it.parentFile?.exists() == true }
                    ?.writeBytes(jpegBytes)
                else -> {
                    java.io.File(homeWallpaperPath).takeIf { it.parentFile?.exists() == true }
                        ?.writeBytes(jpegBytes)
                    java.io.File(lockWallpaperPath).takeIf { it.parentFile?.exists() == true }
                        ?.writeBytes(jpegBytes)
                }
            }
        }
    } catch (_: Exception) {
        // MIUI path write is best-effort — standard API already set the wallpaper
    }

    if (!bitmap.isRecycled) bitmap.recycle()

    return when (t) {
        "HOME" -> "🏠 Home screen updated!"
        "LOCK" -> "🔒 Lock screen updated!"
        else   -> "✨ Both screens updated!"
    }
}
