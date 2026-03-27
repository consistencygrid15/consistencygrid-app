package com.consistencygridwallpaper.workers

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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
     * Sends analytics telemetry indicating the status of a scheduled wallpaper update.
     */
    private suspend fun sendAnalytics(token: String, status: String, trigger: String) {
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
                    .addHeader("Cookie", "publicToken=$token; native_auth=true")
                    .build()
                val response = client.newCall(request).execute()
                Log.d(TAG, "[TRIGGER=$trigger] 📊 Analytics sent: $status (HTTP ${response.code})")
            } catch (e: Exception) {
                Log.e(TAG, "[TRIGGER=$trigger] ⚠️ Failed to send analytics", e)
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        // Read which path triggered this worker (ALARM / FCM / BOOT / UNKNOWN)
        val trigger = inputData.getString("TRIGGER") ?: "UNKNOWN"
        val startedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        Log.d(TAG, "[TRIGGER=$trigger] 🚀 Worker started at $startedAt")

        // ─────────────────────────────────────────────────────────────────
        // Authoritative duplicate-run guard (single source of truth)
        //
        // This guard is the ONLY place that checks whether today's wallpaper
        // has already been applied. It was moved here from MidnightReceiver
        // because the old guard caused the alarm to be silently skipped every
        // night after FCM ran first and marked lastUpdateDate for the same day.
        //
        // WorkManager KEEP policy prevents two workers from running in parallel.
        // This guard prevents a second sequential worker from re-rendering.
        // ─────────────────────────────────────────────────────────────────
        val forceUpdate = inputData.getBoolean("FORCE_UPDATE", false)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayDate = sdf.format(java.util.Date())
        val lastUpdateDate = userPrefs.getLastUpdateDate()
        Log.d(TAG, "[TRIGGER=$trigger] Guard check: lastUpdateDate=$lastUpdateDate, todayDate=$todayDate, forceUpdate=$forceUpdate")
        if (!forceUpdate && lastUpdateDate == todayDate) {
            Log.d(TAG, "[TRIGGER=$trigger] ⚠️ Already updated today ($todayDate) — skipping render")
            return@withContext Result.success()
        }

        // ─────────────────────────────────────────────────────────────────
        // Crash-Safe Recovery Guard
        // ─────────────────────────────────────────────────────────────────
        if (userPrefs.isUpdateInProgress()) {
            Log.w(TAG, "[TRIGGER=$trigger] ⚠️ Previous update crashed mid-execution. Retrying now.")
            // Don't abort, just log it so we know we're recovering
        }
        userPrefs.setUpdateInProgress(true)

        // 🚨 Promote to Foreground Service to keep the process alive during WebView rendering
        try {
            setForeground(createForegroundInfo("Updating wallpaper..."))
            Log.d(TAG, "[TRIGGER=$trigger] ✅ Promoted to Foreground Service")
        } catch (e: Exception) {
            Log.e(TAG, "[TRIGGER=$trigger] ⚠️ Failed to promote to foreground service — continuing anyway", e)
        }

        val token = userPrefs.getToken()
        if (token.isNullOrBlank()) {
            Log.e(TAG, "[TRIGGER=$trigger] ❌ Authentication token is null or empty!")
            return@withContext Result.failure()
        }
        Log.d(TAG, "[TRIGGER=$trigger] ✅ Token retrieved, proceeding with wallpaper render")

        // ─────────────────────────────────────────────────────────────────
        // Offline Fallback
        // ─────────────────────────────────────────────────────────────────
        if (!com.consistencygridwallpaper.utils.NetworkUtils.isNetworkAvailable(applicationContext)) {
            Log.w(TAG, "[TRIGGER=$trigger] 📡 No internet — will retry later when network is available")
            // Do NOT apply offline fallback and do NOT mark today as updated.
            // Marking the day as updated here would block the real render when
            // the network is restored, causing the user to see a stale wallpaper all day.
            // WorkManager will retry this job when network conditions improve.
            return@withContext Result.retry()
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
                Log.d(TAG, "[TRIGGER=$trigger] 📅 Marked $todayDate as updated at $completedAt")
                Log.d(TAG, "[TRIGGER=$trigger] ✅ Wallpaper update completed successfully")
                sendAnalytics(token, "SUCCESS", trigger)
                Result.success()
            } else {
                Log.w(TAG, "[TRIGGER=$trigger] ⚠️ Render returned failure (empty base64 or bitmap decode failed) — will retry")
                sendAnalytics(token, "FAILED", trigger)
                Result.retry()
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "[TRIGGER=$trigger] ❌ Timed out after ${RENDER_TIMEOUT_MS}ms — will retry", e)
            sendAnalytics(token, "FAILED_TIMEOUT", trigger)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "[TRIGGER=$trigger] ❌ Failed: ${e.message} — will retry", e)
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
            Log.d(TAG, "[TRIGGER=$trigger] 🏁 Worker finished at $exitedAt")
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
     * Loads the wallpaper renderer in a headless WebView and waits for the result.
     *
     * Returns true if wallpaper was applied successfully, false otherwise.
     *
     * @param token   The user's authentication token for fetching wallpaper data
     * @param trigger Who triggered this render: "ALARM", "FCM", "BOOT", or "UNKNOWN"
     * @return true if wallpaper applied successfully, false otherwise
     */
    private suspend fun updateWallpaper(token: String, trigger: String = "UNKNOWN"): Boolean = suspendCancellableCoroutine { continuation ->
        Log.d(TAG, "[TRIGGER=$trigger] 🌐 Loading wallpaper renderer...")
        val baseUrl = userPrefs.getBaseUrl()
        
        // Detect physical screen dimensions for perfect scaling in background
        val metrics = applicationContext.resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        
        Log.d(TAG, "📱 Screen dimensions: ${width}x${height}")
        
        // Bust cache once per calendar day using the DEVICE's local date,
        // and pass the device timezone so the server uses the correct date.
        val deviceTz = java.util.TimeZone.getDefault()
        val localSdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        localSdf.timeZone = deviceTz
        val localDate = localSdf.format(java.util.Date())
        val tzId = java.net.URLEncoder.encode(deviceTz.id, "UTF-8")
        val url = "$baseUrl/wallpaper-renderer?token=$token&canvasWidth=$width&canvasHeight=$height&date=$localDate&tz=$tzId"
        Log.d(TAG, "🔗 Loading URL: $url (device tz=${deviceTz.id}, localDate=$localDate)")
        
        val webView = WebView(applicationContext)
        
        // 🔑 CRITICAL: Enable cookies so the web app's API calls work
        // Without this, fetch('/api/wallpaper-data') returns 401 or fails silently
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 🍪 Inject auth cookies before loading the renderer.
        // At midnight the app may not be in the foreground, so the CookieManager's
        // in-memory cache could be cold. We inject both required cookies here so
        // the worker is completely self-sufficient regardless of app state.
        //
        // These mirror exactly what MainActivity.injectNativeAuthCookies() sets:
        //   publicToken  → read by getUniversalSession() on every API route
        //   native_auth  → lets the middleware know this is a native Android request
        cookieManager.setCookie(baseUrl, "publicToken=$token; HttpOnly; Secure; Path=/; SameSite=None; Max-Age=2592000")
        cookieManager.setCookie(baseUrl, "native_auth=true; Secure; Path=/; SameSite=None; Max-Age=2592000")
        cookieManager.flush()
        // Layout is done later when setting up layer type
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_NO_CACHE  // headless renderer MUST get fresh data
            userAgentString = "ConsistencyGridApp/1.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36"
            // Allow mixed content (http+https) needed for some CDN assets
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            // Enable hardware acceleration for canvas rendering
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
        
        // 🚨 CRITICAL FIX for Locked Screens: 
        // 1. Force the WebEngine to run its timers even if it's off-screen/background.
        webView.resumeTimers()
        
        // 2. Hardware acceleration sometimes blocks off-screen renders if the GPU is sleeping. 
        // We use SOFTWARE/HARDWARE fallback or just ensure layout is passed correctly.
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        
        // 3. Fake attach to window by requesting layout continuously if needed, 
        // but just calling layout() usually works.
        webView.layout(0, 0, width, height)
        
        // Add JavaScript bridge for the web app to call back with the rendered wallpaper
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun saveWallpaper(base64Image: String) {
                Log.d(TAG, "📥 saveWallpaper called from JavaScript!")
                try {
                    // Validate input
                    if (base64Image.isBlank()) {
                        Log.e(TAG, "❌ Empty base64 image received")
                        if (continuation.isActive) continuation.resume(false)
                        return
                    }
                    
                    // Remove data URI prefix if present
                    val cleanedBase64 = if (base64Image.contains(",")) {
                        base64Image.split(",")[1]
                    } else {
                        base64Image
                    }

                    // Decode base64 to bitmap
                    val decodedString = Base64.decode(cleanedBase64, Base64.DEFAULT)
                    val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                    
                    if (decodedByte == null) {
                        Log.e(TAG, "❌ Failed to decode bitmap from base64")
                        if (continuation.isActive) continuation.resume(false)
                        return
                    }
                    
                    Log.d(TAG, "✅ Bitmap decoded successfully (${decodedByte.width}x${decodedByte.height})")

                    val userPrefs = UserPrefs(applicationContext)
                    val target = userPrefs.getWallpaperTarget().trim().uppercase()
                    val wallpaperManager = WallpaperManager.getInstance(applicationContext)
                    
                    Log.d(TAG, "🎯 Applying wallpaper to target: $target")
                    
                    // Apply wallpaper based on user preference
                    when (target) {
                        "HOME" -> {
                            wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_SYSTEM)
                            Log.d(TAG, "✅ Home screen wallpaper updated")
                        }
                        "LOCK" -> {
                            wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_LOCK)
                            Log.d(TAG, "✅ Lock screen wallpaper updated")
                        }
                        else -> {
                            // Apply sequentially for better compatibility with OEM lock screens (e.g. Glance)
                            wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_SYSTEM)
                            wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_LOCK)
                            Log.d(TAG, "✅ Both screens updated")
                        }
                    }
                    
                    // 🧹 Recycle bitmap to free memory
                    if (!decodedByte.isRecycled) decodedByte.recycle()
                    
                    // Resume the coroutine after successful application
                    if (continuation.isActive) {
                        Log.d(TAG, "✅ Wallpaper applied successfully, resuming coroutine")
                        continuation.resume(true)
                    }
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "❌ Invalid base64 data", e)
                    if (continuation.isActive) continuation.resume(false)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to apply wallpaper", e)
                    // Resume even on error to prevent hanging
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }, "Android")
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "📄 Page finished loading: $url")
                // The actual rendering and call to saveWallpaper 
                // happens inside the web app's JavaScript (async, after fetch + canvas draw).
                // We just wait for the JS callback - do NOT resume here.
            }
            
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e(TAG, "❌ WebView load error: $description (code: $errorCode) for: $failingUrl")
                // Only fatal if the main page itself fails to load
                if (failingUrl == url && continuation.isActive) {
                    // Resume with false = wallpaper was NOT applied
                    continuation.resume(false)
                }
            }
            
            // Catch HTTP errors (401, 404, 500) from API calls within the page
            override fun onReceivedHttpError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                val statusCode = errorResponse?.statusCode ?: 0
                val requestUrl = request?.url?.toString() ?: ""
                if (statusCode >= 400) {
                    Log.e(TAG, "❌ HTTP $statusCode error for: $requestUrl")
                    // If the main renderer itself has an HTTP error (e.g. 401 token expired),
                    // treat it as a fatal failure so we retry next time
                    if (request?.isForMainFrame == true && continuation.isActive) {
                        Log.e(TAG, "❌ Main frame HTTP $statusCode — aborting render")
                        continuation.resume(false)
                    }
                }
            }
        }
        
        // Handle cancellation — always destroy WebView to free resources
        continuation.invokeOnCancellation {
            Log.w(TAG, "⚠️ Wallpaper update cancelled — cleaning up WebView")
            webView.stopLoading()
            webView.destroy()
        }
        
        // Load the wallpaper renderer
        webView.loadUrl(url)
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
                Log.d(TAG, "⚙️ Applying offline fallback wallpaper...")
                val context = applicationContext
                val metrics = context.resources.displayMetrics
                val width = metrics.widthPixels
                val height = metrics.heightPixels

                val cacheFile = java.io.File(context.cacheDir, "fallback_wallpaper_cache.png")
                val bitmap: android.graphics.Bitmap

                if (cacheFile.exists()) {
                    Log.d(TAG, "⚙️ Loading offline fallback from cache")
                    bitmap = android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
                        ?: throw Exception("Failed to decode cached fallback bitmap")
                } else {
                    Log.d(TAG, "⚙️ Generating new offline fallback wallpaper")
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
                Log.d(TAG, "✅ Offline fallback wallpaper applied successfully")
                true
            }
            
            if (result == null) {
                Log.e(TAG, "❌ Offline fallback wallpaper generation timed out after 5s")
                return@withContext false
            } else {
                return@withContext result
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to apply offline fallback wallpaper", e)
            return@withContext false
        }
    }
}
