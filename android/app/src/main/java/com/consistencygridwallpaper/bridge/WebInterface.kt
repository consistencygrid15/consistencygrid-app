package com.consistencygridwallpaper.bridge

import android.app.DownloadManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.work.WorkManager
import com.consistencygridwallpaper.MainActivity
import com.consistencygridwallpaper.storage.UserPrefs
import java.io.File
import java.io.FileOutputStream

/**
 * WebInterface - JavaScript Bridge for Native Android Integration
 *
 * This class provides a bridge between the WebView JavaScript environment and native Android APIs.
 * It enables the web application to:
 * - Set wallpapers on Home and/or Lock screens
 * - Save and retrieve user authentication tokens
 * - Manage wallpaper target preferences (HOME, LOCK, or BOTH)
 * - Enable/disable automatic wallpaper updates
 * - Download files (images and CSV exports)
 * - Apply custom themes to the native UI
 * - Handle logout and session cleanup
 *
 * All methods annotated with @JavascriptInterface are callable from JavaScript
 * using the window.Android object.
 *
 * @property context The Android context, typically the MainActivity
 * @see MainActivity for WebView setup
 * @see UserPrefs for persistent storage
 */
class WebInterface(private val context: Context) {

    companion object {
        private const val TAG = "WebInterface"
    }

    /**
     * Sets the device wallpaper from a base64-encoded image.
     *
     * Called from JavaScript when the user generates and applies a wallpaper.
     * Decodes the base64 image data and applies it to the Home screen, Lock screen,
     * or both, based on the user's saved preference.
     *
     * @param base64Image Base64-encoded image data, optionally with data URI prefix
     *                    (e.g., "data:image/png;base64,iVBORw...")
     */
    @JavascriptInterface
    fun saveWallpaper(base64Image: String) {
        // Run on background thread to prevent UI freezing/crashing
        Thread {
            try {
                Log.d(TAG, "saveWallpaper: Received request on thread: ${Thread.currentThread().name}")
                
                // Validate input
                if (base64Image.isBlank()) {
                    Log.e(TAG, "saveWallpaper: Empty base64 data received")
                    showToast("Error: No image data received")
                    return@Thread
                }
                
                // Remove data URI prefix if present
                val cleanedBase64 = if (base64Image.contains(",")) {
                    base64Image.split(",")[1]
                } else {
                    base64Image
                }

                // Decode base64 to bitmap
                // detailed breakdown for large memory handling
                val decodedString = Base64.decode(cleanedBase64, Base64.DEFAULT)
                val options = BitmapFactory.Options()
                // options.inMutable = true // optional optimization
                val decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size, options)
                
                if (decodedByte == null) {
                    Log.e(TAG, "saveWallpaper: Failed to decode bitmap")
                    showToast("Error: Failed to decode image")
                    return@Thread
                }
                
                Log.d(TAG, "saveWallpaper: Bitmap decoded successfully (${decodedByte.width}x${decodedByte.height})")

                val wallpaperManager = WallpaperManager.getInstance(context)
                val userPrefs = UserPrefs(context)
                val target = userPrefs.getWallpaperTarget().trim().uppercase()
                
                Log.d(TAG, "saveWallpaper: Applying wallpaper to target: $target")

                when (target) {
                    "HOME" -> {
                        wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_SYSTEM)
                        showToast("HOME UPDATED 🏠 (Lock preserved)")
                        Log.d(TAG, "saveWallpaper: Home screen wallpaper updated")
                    }
                    "LOCK" -> {
                        wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_LOCK)
                        showToast("LOCK UPDATED 🔒 (Home preserved)")
                        Log.d(TAG, "saveWallpaper: Lock screen wallpaper updated")
                    }
                    else -> {
                        // Combined
                        wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_SYSTEM)
                        wallpaperManager.setBitmap(decodedByte, null, true, WallpaperManager.FLAG_LOCK)
                        showToast("BOTH SCREENS UPDATED ✨")
                        Log.d(TAG, "saveWallpaper: Both screens updated")
                    }
                }
                
                // Clean up bitmap to free memory immediately
                if (!decodedByte.isRecycled) {
                    decodedByte.recycle()
                }
                
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "saveWallpaper: OOM Error", e)
                showToast("Error: Image too large for device memory")
                System.gc()
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "saveWallpaper: Invalid base64 data", e)
                showToast("Error: Invalid image data")
            } catch (e: Exception) {
                Log.e(TAG, "saveWallpaper: Failed to set wallpaper", e)
                showToast("Error: ${e.localizedMessage ?: "Failed to set wallpaper"}")
            }
        }.start()
    }

    /**
     * Sets the device wallpaper by downloading from a URL.
     * ... existing doc ...
     */
    @JavascriptInterface
    fun saveWallpaperUrl(url: String) {
        Thread {
            try {
                Log.d(TAG, "saveWallpaperUrl: Downloading wallpaper from $url")
                
                // Validate URL
                if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                    Log.e(TAG, "saveWallpaperUrl: Invalid URL: $url")
                    showToast("Error: Invalid wallpaper URL")
                    return@Thread
                }
                
                val inputStream = java.net.URL(url).openStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap == null) {
                    Log.e(TAG, "saveWallpaperUrl: Failed to decode image from URL")
                    showToast("Error: Failed to load image from URL")
                    return@Thread
                }
                
                Log.d(TAG, "saveWallpaperUrl: Image downloaded successfully (${bitmap.width}x${bitmap.height})")
                
                val wallpaperManager = WallpaperManager.getInstance(context)
                val userPrefs = UserPrefs(context)
                val target = userPrefs.getWallpaperTarget().trim().uppercase()
                
                Log.d(TAG, "saveWallpaperUrl: Applying to target: $target")
                
                when (target) {
                    "HOME" -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        showToast("SYNC: Home Screen Only 🏠")
                    }
                    "LOCK" -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        showToast("SYNC: Lock Screen Only 🔒")
                    }
                    else -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                        showToast("SYNC: Both Screens ✨")
                    }
                }
                
                if (!bitmap.isRecycled) bitmap.recycle()
                
            } catch (e: Exception) {
                Log.e(TAG, "saveWallpaperUrl: Failed to set wallpaper", e)
                showToast("Error: ${e.message}")
            }
        }.start()
    }

    /**
     * Saves the user's authentication token to persistent storage.
     * ... existing doc ...
     */
    @JavascriptInterface
    fun saveToken(token: String) {
        Log.d(TAG, "saveToken: Saving authentication token")
        try {
            val userPrefs = UserPrefs(context)
            userPrefs.saveToken(token)
            
            // CRITICAL: Force cookie flush immediately to ensure session persistence
            // This fixes the login redirection race condition
            android.webkit.CookieManager.getInstance().flush()
            
            Log.d(TAG, "saveToken: Token saved and cookies flushed")
        } catch (e: Exception) {
            Log.e(TAG, "saveToken: Failed to save token", e)
        }
    }

    /**
     * Retrieves the authentication token from persistent storage.
     *
     * Called from JavaScript to restore the session if cookies are missing.
     *
     * @return The token string or null if not found
     */
    @JavascriptInterface
    fun getToken(): String? {
        val userPrefs = UserPrefs(context)
        val token = userPrefs.getToken()
        Log.d(TAG, "getToken: Returning ${if (token == null) "null" else "token"}")
        return token
    }

    /**
     * Sets the wallpaper target preference (HOME, LOCK, or BOTH).
     *
     * Called from JavaScript when the user changes their wallpaper target preference.
     * Determines which screen(s) will be updated when setting wallpapers.
     *
     * @param target The target screen(s): "HOME", "LOCK", or "BOTH" (case-insensitive)
     */
    @JavascriptInterface
    fun setWallpaperTarget(target: String?) {
        val userPrefs = UserPrefs(context)
        val normalized = target?.trim()?.uppercase() ?: "BOTH"
        Log.d(TAG, "setWallpaperTarget: Setting target to $normalized")
        userPrefs.setWallpaperTarget(normalized)
        android.webkit.CookieManager.getInstance().flush()
        showToast("Preference: $normalized ✅")

        // Show Glance warning when setting to LOCK or BOTH
        if (normalized == "LOCK" || normalized == "BOTH") {
             if (context is MainActivity && !userPrefs.hasShownGlanceWarning()) {
                 context.runOnUiThread {
                     context.showGlanceWarningDialog()
                     userPrefs.setHasShownGlanceWarning(true)
                 }
             }
        }
    }

    /**
     * Retrieves the current wallpaper target preference.
     *
     * Called from JavaScript to sync the UI with the saved preference.
     *
     * @return The current target: "HOME", "LOCK", or "BOTH"
     */
    @JavascriptInterface
    fun getWallpaperTarget(): String {
        val userPrefs = UserPrefs(context)
        val target = userPrefs.getWallpaperTarget().trim().uppercase()
        // Ensure we always return something the web app understands
        val result = if (target == "HOME" || target == "LOCK") target else "BOTH"
        Log.d(TAG, "getWallpaperTarget: Returning $result")
        return result
    }

    /**
     * Checks if automatic wallpaper updates are enabled.
     *
     * Called from JavaScript to sync the UI toggle state.
     *
     * @return true if auto-updates are enabled, false otherwise
     */
    @JavascriptInterface
    fun isAutoUpdateEnabled(): Boolean {
        val userPrefs = UserPrefs(context)
        val enabled = userPrefs.isAutoUpdateEnabled()
        Log.d(TAG, "isAutoUpdateEnabled: $enabled")
        return enabled
    }

    /**
     * Enables or disables automatic daily wallpaper updates.
     *
     * When enabled, schedules a daily alarm for midnight (12:00 AM) to automatically
     * update the wallpaper. Shows a verification dialog with testing instructions.
     * When disabled, cancels the scheduled updates.
     *
     * @param enabled true to enable auto-updates, false to disable
     */
    @JavascriptInterface
    fun setAutoUpdateEnabled(enabled: Boolean) {
        Log.d(TAG, "setAutoUpdateEnabled: $enabled")
        val userPrefs = UserPrefs(context)
        val previouslyEnabled = userPrefs.isAutoUpdateEnabled()
        
        // Save current state
        userPrefs.setAutoUpdate(enabled)

        if (enabled) {
            // Only show FEEDBACK if it was just turned ON
            val shouldShowFeedback = !previouslyEnabled
            
            com.consistencygridwallpaper.workers.ExactAlarmScheduler.scheduleNextMidnightAlarm(context)
            
            if (shouldShowFeedback) {
                showToast("Exact Daily 12 AM updates enabled! ⏰")
                Log.d(TAG, "setAutoUpdateEnabled: Daily updates scheduled with feedback")
                
                // Show verification dialog with testing instructions ONLY ONCE
                if (context is MainActivity && !userPrefs.hasShownVerificationDialog()) {
                    context.runOnUiThread {
                        context.showVerificationDialog()
                        userPrefs.setHasShownVerificationDialog(true)
                    }
                }
            } else {
                Log.d(TAG, "setAutoUpdateEnabled: Daily updates re-scheduled silently")
            }
        } else {
            com.consistencygridwallpaper.workers.ExactAlarmScheduler.cancelMidnightAlarm(context)
            if (previouslyEnabled) {
                showToast("Daily updates disabled.")
            }
            Log.d(TAG, "setAutoUpdateEnabled: Daily updates cancelled")
        }
    }

    /**
     * Sets the preferred time for automatic wallpaper updates.
     * @param hour Hour of day (0-23)
     * @param minute Minute of hour (0-59)
     */
    @JavascriptInterface
    fun setAutoUpdateTime(hour: Int, minute: Int) {
        Log.d(TAG, "setAutoUpdateTime: $hour:$minute")
        val userPrefs = UserPrefs(context)
        userPrefs.setAutoUpdateTime(hour, minute)

        // Reschedule the alarm if auto-update is currently enabled
        if (userPrefs.isAutoUpdateEnabled()) {
            com.consistencygridwallpaper.workers.ExactAlarmScheduler.scheduleNextMidnightAlarm(context)
            showToast("Update time saved. Next alarm scheduled.")
        } else {
            showToast("Update time saved. Enable auto updates to apply.")
        }
    }

    /**
     * Sets the preferred time for automatic wallpaper updates using a time string.
     * @param timeStr Time string in "HH:MM" format (24-hour)
     */
    @JavascriptInterface
    fun setAutoUpdateTimeStr(timeStr: String) {
        try {
            val parts = timeStr.split(":")
            if (parts.size == 2) {
                val hour = parts[0].toInt()
                val minute = parts[1].toInt()
                setAutoUpdateTime(hour, minute)
            } else {
                showToast("Invalid time format. Use HH:MM")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setAutoUpdateTimeStr: Invalid time format", e)
            showToast("Invalid time format. Use HH:MM")
        }
    }

    /**
     * Retrieves the configured update time in format "HH:mm".
     */
    @JavascriptInterface
    fun getAutoUpdateTime(): String {
        val userPrefs = UserPrefs(context)
        val hour = userPrefs.getUpdateHour().toString().padStart(2, '0')
        val minute = userPrefs.getUpdateMinute().toString().padStart(2, '0')
        val timeStr = "$hour:$minute"
        Log.d(TAG, "getAutoUpdateTime: Returning $timeStr")
        return timeStr
    }

    /**
     * Clears all user data and performs a complete logout.
     *
     * Called from JavaScript when the user logs out. This method:
     * 1. Routes to the internal native logout flow.
     */
    @JavascriptInterface
    fun clearToken() {
        Log.d(TAG, "clearToken: Routing to native logout()")
        logout()
    }

    /**
     * Updates the app's theme colors and appearance.
     *
     * Called from JavaScript when the user changes theme settings in the web app.
     * Saves the theme preference and immediately applies it to the native UI elements
     * (status bar, navigation bar, progress bar).
     *
     * @param colorHex The theme color in hex format (e.g., "#FF7A00")
     * @param isDark Whether the theme is dark mode
     */
    @JavascriptInterface
    fun updateAppTheme(colorHex: String, isDark: Boolean) {
        Log.d(TAG, "updateAppTheme: color=$colorHex, isDark=$isDark")
        val userPrefs = UserPrefs(context)
        userPrefs.saveTheme(colorHex, isDark)
        
        // Update UI immediately if possible
        if (context is MainActivity) {
            context.runOnUiThread {
                context.applyTheme(colorHex, isDark)
                android.webkit.CookieManager.getInstance().flush()
            }
            Log.d(TAG, "updateAppTheme: Theme applied to UI")
        }
    }

    /**
     * Displays a native Android toast message.
     *
     * Called from JavaScript to show brief feedback messages to the user.
     * Uses native Android Toast for consistent system-level notifications.
     *
     * @param message The message to display
     */
    /**
     * Replaced Toast with thread-safe logging as requested.
     * Prevents background thread crashes while still providing feedback in logs.
     * 
     * @param message The message to log
     */
    @JavascriptInterface
    fun showToast(message: String) {
        // Log message for debugging instead of showing a visual toast
        // This is inherently thread-safe and prevents "Can't toast on a thread" crashes
        Log.d(TAG, "Bridge Notification: $message")
    }

    /**
     * Downloads a file from base64 data to the device's Downloads folder.
     *
     * Called from JavaScript to download files (images, CSV exports, etc.) that are
     * generated client-side as blobs or base64 data. Saves the file to the public
     * Downloads directory and notifies the system DownloadManager so it appears
     * in the Downloads app and notification.
     *
     * @param base64Data Base64-encoded file data, optionally with data URI prefix
     * @param fileName The name to save the file as
     * @param mimeType The MIME type of the file (e.g., "image/png", "text/csv")
     */
    @JavascriptInterface
    fun downloadFile(base64Data: String, fileName: String, mimeType: String) {
        try {
            Log.d(TAG, "downloadFile: Downloading $fileName (type: $mimeType)")
            
            // Validate inputs
            if (base64Data.isBlank()) {
                Log.e(TAG, "downloadFile: Empty base64 data")
                showToast("Error: No file data to download")
                return
            }
            
            if (fileName.isBlank()) {
                Log.e(TAG, "downloadFile: Empty filename")
                showToast("Error: Invalid filename")
                return
            }
            
            // Remove data URI prefix if present
            val cleanedBase64 = if (base64Data.contains(",")) {
                base64Data.split(",")[1]
            } else {
                base64Data
            }

            val decodedBytes = Base64.decode(cleanedBase64, Base64.DEFAULT)
            Log.d(TAG, "downloadFile: Decoded ${decodedBytes.size} bytes")
            
            // Save to Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            
            val fos = FileOutputStream(file)
            fos.write(decodedBytes)
            fos.close()
            
            Log.d(TAG, "downloadFile: File saved to ${file.absolutePath}")

            // Notify DownloadManager so it shows up in notifications/downloads app
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.addCompletedDownload(
                fileName,
                "Exported from ConsistencyGrid",
                true,
                mimeType,
                file.absolutePath,
                decodedBytes.size.toLong(),
                true
            )

            showToast("File saved to Downloads: $fileName ✅")
            Log.d(TAG, "downloadFile: Download completed successfully")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "downloadFile: Invalid base64 data", e)
            showToast("Download failed: Invalid file data")
        } catch (e: java.io.IOException) {
            Log.e(TAG, "downloadFile: File I/O error", e)
            showToast("Download failed: Could not save file")
        } catch (e: Exception) {
            Log.e(TAG, "downloadFile: Download failed", e)
            showToast("Download failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Opens the native Android gallery to pick an image.
     * The result will be passed back to the WebView via window.onAndroidImagePicked.
     */
    @JavascriptInterface
    fun pickImage() {
        Log.d(TAG, "pickImage: Requested by WebView")
        if (context is MainActivity) {
            context.runOnUiThread {
                context.openImagePicker()
            }
        } else {
            Log.e(TAG, "pickImage: Context is not MainActivity")
        }
    }

    /**
     * Logout user and redirect to native authentication screen.
     *
     * Called from JavaScript when the user logs out. This method:
     * 1. Clears all authentication data via AuthManager
     * 2. Cancels all scheduled background work
     * 3. Redirects to AuthActivity for re-authentication
     *
     * This replaces the web-based logout flow with native authentication.
     */
    @JavascriptInterface
    fun logout() {
        Log.d(TAG, "logout: Starting native logout process")
        
        if (context is MainActivity) {
            context.runOnUiThread {
                try {
                    // 1. Clear auth data
                    val authManager = com.consistencygridwallpaper.auth.AuthManager.getInstance(context)
                    authManager.logout() // also clears Google Session natively
                    Log.d(TAG, "logout: Auth data cleared")
                    
                    // 2. Cancel background work
                    com.consistencygridwallpaper.workers.ExactAlarmScheduler.cancelMidnightAlarm(context)
                    Log.d(TAG, "logout: Background work cancelled")
                    
                    // 3. Clear WebView Cookies for a fresh start
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                    Log.d(TAG, "logout: Cookies cleared")
                    
                    // 4. Redirect to AuthActivity
                    val intent = android.content.Intent(context, com.consistencygridwallpaper.auth.AuthActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                    context.finish()
                    
                    Log.d(TAG, "logout: Redirected to AuthActivity")
                } catch (e: Exception) {
                    Log.e(TAG, "logout: Failed", e)
                    showToast("Logout error: ${e.localizedMessage}")
                }
            }
        } else {
            Log.e(TAG, "logout: Context is not MainActivity")
        }
    }

    /**
     * Explicitly requests location permission from the Android system.
     * Called from JavaScript when the user clicks 'Start' on the running tracker.
     */
    @JavascriptInterface
    fun requestLocationPermission() {
        Log.d(TAG, "requestLocationPermission: Requested by WebView")
        if (context is MainActivity) {
            context.requestLocationPermissionNatively()
        } else {
            Log.e(TAG, "requestLocationPermission: Context is not MainActivity")
        }
    }
}
