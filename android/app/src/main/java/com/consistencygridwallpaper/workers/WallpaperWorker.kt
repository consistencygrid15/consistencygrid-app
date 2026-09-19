package com.consistencygridwallpaper.workers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.consistencygridwallpaper.utils.AppLogger
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.consistencygridwallpaper.storage.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * WallpaperWorker - Background Wallpaper Rendering and Application
 *
 * This worker executes in the background to automatically update the device wallpaper.
 * It operates by:
 * 1. Loading the wallpaper renderer page in a headless WebView
 * 2. Passing the user's authentication token and screen dimensions
 * 3. Waiting for the web app to render the wallpaper and call back via JavaScript bridge
 * 4. Applying the rendered wallpaper to the device's Home and/or Lock screens
 *
 * The worker is triggered by:
 * - Midnight alarm (via MidnightReceiver)
 * - Boot completion (via BootReceiver, if auto-update is enabled)
 *
 * Uses a headless WebView to leverage the existing web app rendering logic,
 * ensuring consistency between manual and automatic wallpaper updates.
 *
 * @property context The application context
 * @property params Worker parameters from WorkManager
 * @see MidnightReceiver for scheduled trigger
 * @see BootReceiver for boot persistence
 * @see WorkScheduler for scheduling logic
 */
class WallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "WallpaperWorker"
        private const val RENDER_TIMEOUT_MS = 90000L  // 90 seconds for WebView rendering

        fun scheduleImmediate(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<WallpaperWorker>()
                .setInputData(androidx.work.workDataOf("TRIGGER" to "MANUAL_UI", "FORCE_UPDATE" to true))
                .build()
            androidx.work.WorkManager.getInstance(context)
                .enqueueUniqueWork("IMMEDIATE_WALLPAPER_UPDATE", androidx.work.ExistingWorkPolicy.REPLACE, req)
        }
    }

    private val userPrefs = UserPrefs(context)

    /**
     * Executes the background wallpaper update.
     *
     * @return Result.success() if wallpaper was updated successfully,
     *         Result.retry() if an error occurred and the update should be retried,
     *         Result.failure() if the token is missing (non-retryable error)
     */
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        return createForegroundInfo("Updating wallpaper...")
    }

    /**
     * Sends analytics telemetry. Skipped silently if user is not logged in (no token).
     */
    private suspend fun sendAnalytics(token: String?, status: String, trigger: String) {
        if (token.isNullOrBlank()) {
            AppLogger.d(TAG, "[TRIGGER=$trigger] ℹ️ Skipping analytics — user not logged in")
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = userPrefs.getBaseUrl()
                val client = OkHttpClient()
                val json = JSONObject().apply {
                    put("status", status)
                    put("trigger", trigger)
                    put("timestamp", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date()))
                }
                val body = json.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$baseUrl/api/telemetry/wallpaper-update")
                    .post(body)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Cookie", "publicToken=$token; native_auth=true")
                    .build()
                val response = client.newCall(request).execute()
                AppLogger.d(TAG, "[TRIGGER=$trigger] 📊 Analytics sent: $status (HTTP ${response.code})")
            } catch (e: Exception) {
                AppLogger.e(TAG, "[TRIGGER=$trigger] ⚠️ Failed to send analytics", e)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        // Read which path triggered this worker (ALARM / FCM / BOOT / UNKNOWN)
        val trigger = inputData.getString("TRIGGER") ?: "UNKNOWN"
        val startedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        AppLogger.d(TAG, "[TRIGGER=$trigger] 🚀 Worker started at $startedAt")

        // ─────────────────────────────────────────────────────────────────────
        // Onboarding Gate (HIGHEST PRIORITY)
        //
        // NEVER set or change wallpaper on fresh install before onboarding is complete!
        // ─────────────────────────────────────────────────────────────────────
        if (!userPrefs.isOnboarded() && trigger != "ONBOARDING_FINISH" && trigger != "PREVIEW_SAVE") {
            AppLogger.d(TAG, "[TRIGGER=$trigger] ⏸️ User has not finished onboarding — skipping wallpaper render")
            return@withContext Result.success()
        }

        // ─────────────────────────────────────────────────────────────────────
        // Widgets-Only Mode Gate
        //
        // If the user has opted out of wallpaper updates (Settings / Onboarding →
        // Update Behaviour → Widgets Only), skip the wallpaper render immediately
        // for ALL triggers and refresh home-screen widgets only.
        // ─────────────────────────────────────────────────────────────────────
        if (userPrefs.isWidgetsOnlyMode()) {
            AppLogger.d(TAG, "[TRIGGER=$trigger] 🔕 Widgets-Only mode active — skipping wallpaper render, refreshing widgets only")
            com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(applicationContext)
            return@withContext Result.success()
        }

        // ─────────────────────────────────────────────────────────────────
        // Authoritative duplicate-run guard (single source of truth)
        // ─────────────────────────────────────────────────────────────────
        val forceUpdate = inputData.getBoolean("FORCE_UPDATE", false)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayDate = sdf.format(java.util.Date())
        val lastUpdateDate = userPrefs.getLastUpdateDate()
        AppLogger.d(TAG, "[TRIGGER=$trigger] Guard check: lastUpdateDate=$lastUpdateDate, todayDate=$todayDate, forceUpdate=$forceUpdate")
        if (!forceUpdate && lastUpdateDate == todayDate) {
            AppLogger.d(TAG, "[TRIGGER=$trigger] ⚠️ Already updated today ($todayDate) — skipping render")
            return@withContext Result.success()
        }

        // ─────────────────────────────────────────────────────────────────
        // Crash-Safe Recovery Guard
        // ─────────────────────────────────────────────────────────────────
        if (userPrefs.isUpdateInProgress()) {
            AppLogger.w(TAG, "[TRIGGER=$trigger] ⚠️ Previous update crashed mid-execution. Retrying now.")
            // Don't abort, just log it so we know we're recovering
        }
        userPrefs.setUpdateInProgress(true)

        // 🚨 Promote to Foreground Service to keep the process alive during WebView rendering
        try {
            setForeground(createForegroundInfo("Updating wallpaper..."))
            AppLogger.d(TAG, "[TRIGGER=$trigger] ✅ Promoted to Foreground Service")
        } catch (e: Exception) {
            AppLogger.e(TAG, "[TRIGGER=$trigger] ⚠️ Failed to promote to foreground service — continuing anyway", e)
        }

        // Token is optional — native rendering reads only from local Room DB + UserPrefs.
        // We still collect it for analytics but do NOT block unauthenticated users.
        val token = userPrefs.getToken()
        if (token.isNullOrBlank()) {
            AppLogger.d(TAG, "[TRIGGER=$trigger] ℹ️ No auth token (user not logged in) — proceeding with local-only render")
        } else {
            AppLogger.d(TAG, "[TRIGGER=$trigger] ✅ Token retrieved, proceeding with wallpaper render")
        }

        try {
            val success = withTimeout(RENDER_TIMEOUT_MS) {
                updateWallpaper(token, trigger)
            }

            if (success) {
                // Mark as updated ONLY after successful wallpaper application
                userPrefs.setLastUpdateDate(todayDate)
                val completedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                AppLogger.d(TAG, "[TRIGGER=$trigger] 📅 Marked $todayDate as updated at $completedAt")
                AppLogger.d(TAG, "[TRIGGER=$trigger] ✅ Wallpaper update completed successfully")
                sendAnalytics(token, "SUCCESS", trigger)
                Result.success()
            } else {
                AppLogger.w(TAG, "[TRIGGER=$trigger] ⚠️ Render returned failure — will retry")
                sendAnalytics(token, "FAILED", trigger)
                Result.retry()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AppLogger.e(TAG, "[TRIGGER=$trigger] ❌ Timed out after ${RENDER_TIMEOUT_MS}ms — will retry", e)
            sendAnalytics(token, "FAILED_TIMEOUT", trigger)
            Result.retry()
        } catch (e: Exception) {
            AppLogger.e(TAG, "[TRIGGER=$trigger] ❌ Failed: ${e.message} — will retry", e)
            sendAnalytics(token, "FAILED_EXCEPTION", trigger)
            Result.retry()
        } finally {
            userPrefs.setUpdateInProgress(false)
            // Always release the WakeLock whether success, failure, or timeout.
            // Safe to call even if FCM (not Alarm) was the trigger and the lock
            // was acquired by WallpaperMessagingService.
            com.consistencygridwallpaper.workers.MidnightReceiver.releaseWakeLock()
            val exitedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())
            AppLogger.d(TAG, "[TRIGGER=$trigger] 🏁 Worker finished at $exitedAt")
        }
    }

    /**
     * Creates the ForegroundInfo required to run this worker as a Foreground Service.
     */
    private fun createForegroundInfo(progress: String): androidx.work.ForegroundInfo {
        val id = com.consistencygridwallpaper.utils.NotificationHelper.CHANNEL_ID_UPDATES
        val title = "Consistency Grid"
        val cancel = "Cancel"
        
        // Create an intent to open the app when notification is clicked
        val intent = android.content.Intent(applicationContext, com.consistencygridwallpaper.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext, 0, intent, 
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the notification
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(com.consistencygridwallpaper.R.drawable.app_logo) // Ensure this resource exists
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW) // Low priority for "running" status
            .build()
            
        // For Android 14+ (SDK 34), we MUST specify the type if we declared it in Manifest
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
             return androidx.work.ForegroundInfo(
                 1001, // Notification ID
                 notification,
                 android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
             )
        }
        
        return androidx.work.ForegroundInfo(1001, notification)
    }

    /**
     * Renders and applies the wallpaper natively using WallpaperCanvasEngine.
     *
     * @param token   The user's authentication token (not needed for native local rendering)
     * @param trigger Who triggered this render: "ALARM", "FCM", "BOOT", or "UNKNOWN"
     * @return true if wallpaper applied successfully, false otherwise
     */
    private suspend fun updateWallpaper(token: String?, trigger: String = "UNKNOWN"): Boolean = withContext(Dispatchers.Default) {
        try {
            AppLogger.d(TAG, "[TRIGGER=$trigger] 🎨 Starting native wallpaper update...")
            
            // 1. Fetch data state from database/prefs
            val state = com.consistencygridwallpaper.wallpaper.native.data.WallpaperDataSource.getWallpaperState(applicationContext)
            
            // 3. Apply to device screens using WallpaperManager
            val wallpaperManager = WallpaperManager.getInstance(applicationContext)
            val target = userPrefs.getWallpaperTarget().trim().uppercase()
            
            when (target) {
                "HOME" -> {
                    val stateHome = state.copy(config = state.config.copy(wallpaperType = "homescreen"))
                    val bitmapHome = com.consistencygridwallpaper.wallpaper.native.core.WallpaperCanvasEngine.render(stateHome)
                    wallpaperManager.setBitmap(bitmapHome, null, true, WallpaperManager.FLAG_SYSTEM)
                    bitmapHome.recycle()
                }
                "LOCK" -> {
                    val stateLock = state.copy(config = state.config.copy(wallpaperType = "lockscreen"))
                    val bitmapLock = com.consistencygridwallpaper.wallpaper.native.core.WallpaperCanvasEngine.render(stateLock)
                    wallpaperManager.setBitmap(bitmapLock, null, true, WallpaperManager.FLAG_LOCK)
                    bitmapLock.recycle()
                }
                else -> {
                    val stateHome = state.copy(config = state.config.copy(wallpaperType = "homescreen"))
                    val bitmapHome = com.consistencygridwallpaper.wallpaper.native.core.WallpaperCanvasEngine.render(stateHome)
                    wallpaperManager.setBitmap(bitmapHome, null, true, WallpaperManager.FLAG_SYSTEM)
                    bitmapHome.recycle()

                    val stateLock = state.copy(config = state.config.copy(wallpaperType = "lockscreen"))
                    val bitmapLock = com.consistencygridwallpaper.wallpaper.native.core.WallpaperCanvasEngine.render(stateLock)
                    wallpaperManager.setBitmap(bitmapLock, null, true, WallpaperManager.FLAG_LOCK)
                    bitmapLock.recycle()
                }
            }
            
            AppLogger.d(TAG, "✅ Native wallpaper applied successfully")
            com.consistencygridwallpaper.widget.WidgetUpdateHelper.notifyAllWidgets(applicationContext)
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ Failed to update wallpaper natively: ${e.message}", e)
            false
        }
    }

    /**
     * Applies a locally generated fallback wallpaper when the device is offline.
     * Generates a basic dark gradient with a subtle grid pattern to maintain
     * the app's aesthetic without requiring a network request.
     *
     * @return true if applied successfully, false otherwise
     */
    private suspend fun applyOfflineFallbackWallpaper(): Boolean = withContext(Dispatchers.Default) {
        try {
            val result = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                AppLogger.d(TAG, "⚙️ Applying offline fallback wallpaper...")
                val context = applicationContext
                val metrics = context.resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels

                val cacheFile = java.io.File(context.cacheDir, "fallback_wallpaper_cache.png")
                val bitmap: android.graphics.Bitmap

                if (cacheFile.exists()) {
                    AppLogger.d(TAG, "⚙️ Loading offline fallback from cache")
                    bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                        ?: throw Exception("Failed to decode cached fallback bitmap")
                } else {
                    AppLogger.d(TAG, "⚙️ Generating new offline fallback wallpaper")
                    bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)

                    // 1. Draw flat dark background (faster than LinearGradient on low-end devices)
                    val paint = android.graphics.Paint()
                    paint.color = android.graphics.Color.parseColor("#121212")
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                    // 2. Draw subtle grid lines minimally
                    paint.color = android.graphics.Color.parseColor("#1AFFFFFF") // 10% white
                    paint.strokeWidth = 2f
                    
                    val gridSize = width / 10f
                    for (i in 0..10) {
                        val x = i * gridSize
                        canvas.drawLine(x, 0f, x, height.toFloat(), paint)
                    }
                    val numRows = (height / gridSize).toInt() + 1
                    for (i in 0..numRows) {
                        val y = i * gridSize
                        canvas.drawLine(0f, y, width.toFloat(), y, paint)
                    }

                    // 3. Draw fallback text
                    paint.color = android.graphics.Color.parseColor("#4DFFFFFF") // 30% white
                    paint.textSize = width / 20f
                    paint.textAlign = android.graphics.Paint.Align.CENTER
                    paint.isAntiAlias = true
                    canvas.drawText("CONSISTENCY GRID • OFFLINE", width / 2f, height - (height / 10f), paint)

                    // Store to cache for future daily fallback usage
                    java.io.FileOutputStream(cacheFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                }

                // Apply the generated bitmap
                val wallpaperManager = WallpaperManager.getInstance(context)
                val target = userPrefs.getWallpaperTarget().trim().uppercase()

                when (target) {
                    "HOME" -> wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    "LOCK" -> wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    else -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    }
                }

                bitmap.recycle()
                AppLogger.d(TAG, "✅ Offline fallback wallpaper applied successfully")
                true
            }
            
            if (result == null) {
                AppLogger.e(TAG, "❌ Offline fallback wallpaper generation timed out after 5s")
                return@withContext false
            } else {
                return@withContext result
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "❌ Failed to apply offline fallback wallpaper", e)
            return@withContext false
        }
    }
}
