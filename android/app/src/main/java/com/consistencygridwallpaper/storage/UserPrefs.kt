package com.consistencygridwallpaper.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey


/**
 * UserPrefs - Persistent Storage Manager
 *
 * Handles saving and retrieving user preferences and session data using SharedPreferences.
 * Manages:
 * - Authentication tokens
 * - Wallpaper target preferences (HOME, LOCK, BOTH)
 * - Auto-update settings
 * - Theme customization
 * - Base URL configuration
 *
 * This class abstracts the underlying storage mechanism, allowing for potential
 * future upgrades to EncryptedSharedPreferences without affecting the rest of the app.
 */
class UserPrefs(context: Context) {
    
    companion object {
        private const val TAG = "UserPrefs"
        private const val PREFS_NAME = "CONSISTENCY_GRID_PREFS"
        
        // Keys
        const val KEY_TOKEN = "user_token"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_BASE_URL = "base_url" 
        const val KEY_WALLPAPER_TARGET = "wallpaper_target" // BOTH, HOME, or LOCK
        const val KEY_THEME_COLOR = "theme_color"
        const val KEY_IS_DARK_MODE = "is_dark_mode"
        const val KEY_HAS_SHOWN_VERIFICATION = "has_shown_verification"
        const val KEY_HAS_SHOWN_GLANCE_WARNING = "has_shown_glance_warning"
        const val KEY_LAST_UPDATE_DATE = "last_update_date"
        const val KEY_ONBOARDED = "onboarded"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_TOKEN_EXPIRY = "token_expiry_ms" // Unix timestamp in millis
        const val KEY_UPDATE_HOUR = "update_hour"
        const val KEY_UPDATE_MINUTE = "update_minute"
        const val KEY_UPDATE_IN_PROGRESS = "update_in_progress"
        
        // Defaults
        private const val DEFAULT_THEME_COLOR = "#FF7A00" // Orange
        private const val DEFAULT_BASE_URL = "https://consistencygrid.com"
        private const val DEFAULT_TARGET = "BOTH"
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences first init failed — retrying without clearing data", e)
            // ⚠️ IMPORTANT: Do NOT call clear() here. Wiping the prefs would delete the auth
            // token and force a logout on every launch if ESP has a transient keystore error.
            // Simply retry with a fresh MasterKey build — if the keystore recovers, data survives.
            try {
                val retryMasterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    retryMasterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (retryEx: Exception) {
                // The encrypted file is truly unreadable (e.g. corrupted key-store after
                // a factory-reset-like scenario). Only now is it safe to delete the file
                // and start fresh — user will have to log in once, but this beats a
                // perpetual crash loop.
                Log.e(TAG, "ESP retry failed — deleting encrypted prefs file and starting fresh", retryEx)
                try {
                    val prefsFile = java.io.File(
                        context.filesDir.parent + "/shared_prefs/$PREFS_NAME.xml"
                    )
                    if (prefsFile.exists()) prefsFile.delete()
                } catch (deleteEx: Exception) {
                    Log.e(TAG, "Failed to delete stale encrypted prefs file", deleteEx)
                }

                val fallbackMasterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    fallbackMasterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }
    }

    /**
     * Saves user theme preferences.
     * @param colorHex Hex color code (e.g., "#FF7A00")
     * @param isDark True for dark mode, false for light mode
     */
    fun saveTheme(colorHex: String, isDark: Boolean) {
        Log.d(TAG, "saveTheme: color=$colorHex, dark=$isDark")
        prefs.edit()
            .putString(KEY_THEME_COLOR, colorHex)
            .putBoolean(KEY_IS_DARK_MODE, isDark)
            .apply()
    }

    /**
     * Retrieves saved theme color.
     * @return Hex color string, defaults to Orange (#FF7A00)
     */
    fun getThemeColor(): String {
        return prefs.getString(KEY_THEME_COLOR, DEFAULT_THEME_COLOR) ?: DEFAULT_THEME_COLOR
    }

    /**
     * Retrieves dark mode preference.
     * @return True if dark mode is enabled (default), false otherwise
     */
    fun isDarkMode(): Boolean {
        // Default to dark mode as it's the primary aesthetic
        return prefs.getBoolean(KEY_IS_DARK_MODE, true)
    }

    /**
     * Saves the authentication token.
     * @param token The session token to save
     */
    fun saveToken(token: String) {
        if (token.isBlank()) {
            Log.w(TAG, "saveToken: Attempted to save empty token")
            return
        }
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /**
     * Retrieves the authentication token.
     * @return The token string or null if not found
     */
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    /**
     * Saves the NextAuth session token.
     * @param token The session JWT to save
     */
    fun saveSessionToken(token: String) {
        if (token.isBlank()) {
            return
        }
        prefs.edit().putString(KEY_SESSION_TOKEN, token).apply()
    }

    /**
     * Retrieves the NextAuth session token.
     * @return The session token string or null if not found
     */
    fun getSessionToken(): String? {
        return prefs.getString(KEY_SESSION_TOKEN, null)
    }

    /**
     * Saves the expiry timestamp for the current JWT session.
     * @param expiresAtMs Unix timestamp in milliseconds when the session expires
     */
    fun saveTokenExpiry(expiresAtMs: Long) {
        Log.d(TAG, "saveTokenExpiry: expires at $expiresAtMs")
        prefs.edit().putLong(KEY_TOKEN_EXPIRY, expiresAtMs).apply()
    }

    /**
     * Retrieves the stored token expiry timestamp.
     * @return Unix timestamp in millis, or 0 if not set
     */
    fun getTokenExpiry(): Long {
        return prefs.getLong(KEY_TOKEN_EXPIRY, 0L)
    }

    /**
     * Checks if the stored JWT has expired.
     * Returns true if the expiry time is in the past.
     */
    fun isTokenExpired(): Boolean {
        val expiresAt = getTokenExpiry()
        if (expiresAt == 0L) return false // Unknown expiry → assume valid
        return System.currentTimeMillis() > expiresAt
    }

    /**
     * Checks if the token will expire within the next 24 hours.
     * Used to trigger proactive background refresh.
     */
    fun isTokenExpiringSoon(): Boolean {
        val expiresAt = getTokenExpiry()
        if (expiresAt == 0L) return false
        val oneDayMs = 24 * 60 * 60 * 1000L
        return System.currentTimeMillis() > (expiresAt - oneDayMs)
    }

    /**
     * Enables or disables automatic midnight updates.
     * @param enabled True to enable, false to disable
     */
    fun setAutoUpdate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
    }

    /**
     * Checks if auto-update is enabled.
     * @return True if enabled, false otherwise. Defaults to false.
     */
    fun isAutoUpdateEnabled(): Boolean {
        // Default TRUE: every fresh install (or after an EncryptedSharedPreferences key reset)
        // automatically schedules the midnight alarm. Without this, users who never opened
        // the settings page — or whose prefs file was wiped by an OEM keystore reset — had
        // auto-update silently disabled and the alarm was never registered.
        return prefs.getBoolean(KEY_AUTO_UPDATE, true)
    }

    /**
     * Sets the custom time for daily wallpaper updates.
     * @param hour Hour of day (0-23)
     * @param minute Minute of hour (0-59)
     */
    fun setAutoUpdateTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_UPDATE_HOUR, hour)
            .putInt(KEY_UPDATE_MINUTE, minute)
            .apply()
    }

    /**
     * Retrieves the configured hour for daily updates.
     * @return Hour of day (0-23), defaults to 0 (midnight)
     */
    fun getUpdateHour(): Int {
        return prefs.getInt(KEY_UPDATE_HOUR, 0)
    }

    /**
     * Retrieves the configured minute for daily updates.
     * @return Minute of hour (0-59), defaults to 0
     */
    fun getUpdateMinute(): Int {
        return prefs.getInt(KEY_UPDATE_MINUTE, 0)
    }

    /**
     * Sets the target screen(s) for wallpaper updates.
     * @param target One of: "HOME", "LOCK", "BOTH"
     */
    fun setWallpaperTarget(target: String) {
        val normalized = target.trim().uppercase()
        val validTarget = if (normalized in listOf("HOME", "LOCK", "BOTH")) normalized else DEFAULT_TARGET
        prefs.edit().putString(KEY_WALLPAPER_TARGET, validTarget).apply()
    }

    /**
     * Retrieves the wallpaper target preference.
     * @return "HOME", "LOCK", or "BOTH" (default)
     */
    fun getWallpaperTarget(): String {
        return prefs.getString(KEY_WALLPAPER_TARGET, DEFAULT_TARGET) ?: DEFAULT_TARGET
    }

    /**
     * Retrieves the base URL for the web app.
     * Used for constructing API and renderer URLs.
     */
    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    /**
     * Clears all saved data.
     * Used during logout to ensure a clean state.
     */
    fun clear() {
        Log.d(TAG, "clear: Wiping all user preferences")
        prefs.edit().clear().apply()
    }

    /**
     * Records that the auto-update verification dialog has been shown.
     */
    fun setHasShownVerificationDialog(shown: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_VERIFICATION, shown).apply()
    }

    /**
     * Checks if the auto-update verification dialog has already been shown.
     * @return True if already shown, false otherwise.
     */
    fun hasShownVerificationDialog(): Boolean {
        return prefs.getBoolean(KEY_HAS_SHOWN_VERIFICATION, false)
    }

    /**
     * Records that the Glance lock screen warning dialog has been shown.
     */
    fun setHasShownGlanceWarning(shown: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SHOWN_GLANCE_WARNING, shown).apply()
    }

    /**
     * Checks if the Glance lock screen warning dialog has already been shown.
     * @return True if already shown, false otherwise.
     */
    fun hasShownGlanceWarning(): Boolean {
        return prefs.getBoolean(KEY_HAS_SHOWN_GLANCE_WARNING, false)
    }

    /**
     * Records the date of the last successful wallpaper update.
     * Format: YYYY-MM-DD
     */
    fun setLastUpdateDate(date: String) {
        prefs.edit().putString(KEY_LAST_UPDATE_DATE, date).apply()
    }

    /**
     * Retrieves the date of the last successful wallpaper update.
     */
    fun getLastUpdateDate(): String? {
        return prefs.getString(KEY_LAST_UPDATE_DATE, null)
    }

    /**
     * Saves the user's onboarded status.
     * @param onboarded True if user has completed onboarding, false otherwise
     */
    fun saveOnboardedStatus(onboarded: Boolean) {
        Log.d(TAG, "saveOnboardedStatus: $onboarded")
        prefs.edit().putBoolean(KEY_ONBOARDED, onboarded).apply()
    }

    /**
     * Checks if the user has completed onboarding.
     * @return True if onboarded, false otherwise (defaults to false)
     */
    fun isOnboarded(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDED, false)
    }

    /**
     * Marks whether a wallpaper update is currently running.
     * Used for crash-safe recovery.
     */
    fun setUpdateInProgress(inProgress: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_IN_PROGRESS, inProgress).apply()
    }

    /**
     * Checks if a wallpaper update crashed mid-execution.
     */
    fun isUpdateInProgress(): Boolean {
        return prefs.getBoolean(KEY_UPDATE_IN_PROGRESS, false)
    }
}
