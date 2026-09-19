package com.consistencygridwallpaper.reelcontrol.data

import android.content.Context
import android.content.SharedPreferences
import com.consistencygridwallpaper.utils.AppLogger
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * PreferencesManager — Production-grade DUAL storage for Reel Control.
 *
 * DUAL STORAGE STRATEGY:
 *  - Primary:  EncryptedSharedPreferences  (AES-256-GCM, /data/data/pkg/shared_prefs/)
 *  - Backup:   JSON file (filesDir/reel_backup/ — internal, not accessible to other apps)
 *
 * WHY DUAL STORAGE?
 *  OEM phones (Xiaomi, OPPO, etc.) sometimes wipe SharedPreferences via
 *  aggressive "Clean" apps. Internal files in filesDir are NOT targeted by those
 *  cleaners — they survive even after "Clear Cache" on most OEMs.
 *  Only a deliberate "Clear Storage" from Android Settings can wipe both.
 *
 * RECOVERY & MULTI-PROCESS FLOW:
 *  - On init -> if SharedPrefs look fresh (no date key) -> restore from JSON backup.
 *  - On every significant write -> also write to the JSON backup file.
 *  - To prevent stale cached data when running in multiple processes (UI and Service processes),
 *    reads query the latest values from the JSON backup file if it exists and matches today's date.
 */
class PreferencesManager(context: Context) {

    private val appContext = context.applicationContext
    // Security: use EncryptedSharedPreferences (AES-256-GCM) for all reel stats.
    // Falls back to plaintext only on catastrophic Keystore failure.
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "EncryptedSharedPreferences init failed, using plaintext fallback", e)
            appContext.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }
    private val lock = ReentrantReadWriteLock()

    // Backup JSON in internal storage (filesDir is private to this app, not accessible by others)
    private val backupFile: File? = try {
        val dir = File(appContext.filesDir, "reel_backup").also { it.mkdirs() }
        File(dir, "reel_state.json")
    } catch (e: Exception) {
        AppLogger.w(TAG, "Internal backup storage unavailable: ${e.message}")
        null
    }

    companion object {
        const val PREFS_NAME = "reel_control_v2"
        private const val TAG = "PrefsManager"
        private val backupExecutor = Executors.newSingleThreadExecutor()

        val TRACKED_PACKAGES = listOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.snapchat.android",
            "com.zhiliaoapp.musically"
        )

        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private fun today() = DATE_FMT.format(Date())
    }

    // --- In-Memory Cache for multi-process safety ---
    private var cachedJson: JSONObject? = null
    private var lastFileTimestamp = 0L

    private fun getLatestBackupJson(): JSONObject? {
        val file = backupFile ?: return null
        if (!file.exists()) return null
        val currentTimestamp = file.lastModified()
        if (currentTimestamp != lastFileTimestamp || cachedJson == null) {
            try {
                val content = file.readText()
                if (content.isNotBlank()) {
                    cachedJson = JSONObject(content)
                    lastFileTimestamp = currentTimestamp
                }
            } catch (_: Exception) {}
        }
        return cachedJson
    }

    private fun readBackupValue(key: String, defaultVal: Any): Any {
        try {
            val json = getLatestBackupJson() ?: return defaultVal
            val savedDate = json.optString("last_stats_date", "")
            if (savedDate == today()) {
                if (json.has(key)) {
                    return json.get(key)
                }
            }
        } catch (_: Exception) {}
        return defaultVal
    }

    init {
        try {
            val currentDate = prefs.getString("last_stats_date", null)
            if (currentDate == null) {
                // SharedPrefs appear fresh — try to restore from backup first
                AppLogger.d(TAG, "SharedPrefs are fresh — attempting restore from backup")
                restoreFromBackup()
            }
            // Ensure date is always initialized so ensureDailyReset() works correctly
            if (prefs.getString("last_stats_date", null) == null) {
                prefs.edit().putString("last_stats_date", today()).apply()
                AppLogger.d(TAG, "Initialized last_stats_date to ${today()}")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Init error: ${e.message}")
        }
    }

    private fun invalidateBackupCache() {
        cachedJson = null
        lastFileTimestamp = 0L
    }

    /**
     * Generic config-changed broadcast for toggles (block mode, feature enable, app enable).
     * The service refreshes limits for whatever tracked package it is currently watching.
     */
    private fun notifyConfigChanged() {
        try {
            val intent = android.content.Intent("com.consistencygridwallpaper.ACTION_CONFIG_CHANGED").apply {
                setPackage(appContext.packageName)
            }
            appContext.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    /**
     * Per-package limit broadcast — carries the EXACT new values inside the Intent.
     *
     * WHY: The service runs in android:process=":reelservice" (a separate OS process).
     * Each process has its own in-memory EncryptedSharedPreferences cache that does NOT
     * automatically sync when another process writes to the same file. The service's
     * prefs.getReelLimit() would return the OLD stale value even after the UI saved a new one.
     *
     * The fix: instead of hoping the service re-reads from disk (unreliable), we embed the
     * new values DIRECTLY in the broadcast Intent extras. The service applies them to its own
     * prefs instance inside configReceiver before calling refreshLimitsForPackage().
     */
    private fun notifyLimitChanged(pkg: String, reelLimit: Int, timeLimit: Int) {
        try {
            val intent = android.content.Intent("com.consistencygridwallpaper.ACTION_CONFIG_CHANGED").apply {
                setPackage(appContext.packageName)
                putExtra("changed_pkg", pkg)
                putExtra("new_reel_limit", reelLimit)
                putExtra("new_time_limit", timeLimit)
            }
            appContext.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    // ─── Feature Master Switch ─────────────────────────────────────────────────
    var isFeatureEnabled: Boolean
        get() = read {
            if (prefs.contains("feature_enabled")) {
                prefs.getBoolean("feature_enabled", true)
            } else {
                val backupVal = readBackupValue("feature_enabled", -1)
                if (backupVal is Boolean) backupVal else prefs.getBoolean("feature_enabled", true)
            }
        }
        set(v) = write {
            prefs.edit().putBoolean("feature_enabled", v).apply()
            invalidateBackupCache()
            saveBackupAsync()
            notifyConfigChanged()
        }

    // ——— Onboarding ———————————————————————————————————————————————————————————
    var hasCompletedOnboarding: Boolean
        get() = read { prefs.getBoolean("onboarding_done", false) }
        set(v) = write { prefs.edit().putBoolean("onboarding_done", v).commit() }

    // ——— Accessibility Disclosure —————————————————————————————————────────────
    var hasSeenAccessibilityDisclosure: Boolean
        get() = read { prefs.getBoolean("accessibility_disclosure_seen", false) }
        set(v) = write { prefs.edit().putBoolean("accessibility_disclosure_seen", v).apply() }

    // ——— Autostart Dismissed ——————————————————————————————————————————————————
    var isAutostartDismissed: Boolean
        get() = read {
            if (prefs.contains("autostart_dismissed")) {
                prefs.getBoolean("autostart_dismissed", false)
            } else {
                val backupVal = readBackupValue("autostart_dismissed", -1)
                if (backupVal is Boolean) backupVal else prefs.getBoolean("autostart_dismissed", false)
            }
        }
        set(v) = write {
            prefs.edit().putBoolean("autostart_dismissed", v).apply()
            invalidateBackupCache()
            saveBackupAsync()
        }

    // ─── Block Mode ───────────────────────────────────────────────────────────
    var isBlockModeEnabled: Boolean
        get() = read {
            if (prefs.contains("block_mode_enabled")) {
                prefs.getBoolean("block_mode_enabled", false)
            } else {
                val backupVal = readBackupValue("block_mode_enabled", -1)
                if (backupVal is Boolean) backupVal else prefs.getBoolean("block_mode_enabled", false)
            }
        }
        set(v) = write {
            prefs.edit().putBoolean("block_mode_enabled", v).apply()
            invalidateBackupCache()
            saveBackupAsync()
            notifyConfigChanged()
        }

    var isHardBlockEnabled: Boolean
        get() = read {
            if (prefs.contains("hard_block_enabled")) {
                prefs.getBoolean("hard_block_enabled", false)
            } else {
                val backupVal = readBackupValue("hard_block_enabled", -1)
                if (backupVal is Boolean) backupVal else prefs.getBoolean("hard_block_enabled", false)
            }
        }
        set(v) = write {
            prefs.edit().putBoolean("hard_block_enabled", v).apply()
            invalidateBackupCache()
            saveBackupAsync()
            notifyConfigChanged()
        }

    // ─── Per-App: Enable/Disable ──────────────────────────────────────────────
    fun isAppEnabled(pkg: String): Boolean = read {
        if (prefs.contains("enabled_$pkg")) {
            prefs.getBoolean("enabled_$pkg", true)
        } else {
            val backupVal = readBackupValue("enabled_$pkg", -1)
            if (backupVal is Boolean) backupVal else prefs.getBoolean("enabled_$pkg", true)
        }
    }
    fun setAppEnabled(pkg: String, enabled: Boolean) = write {
        prefs.edit().putBoolean("enabled_$pkg", enabled).apply()
        AppLogger.d(TAG, "App $pkg tracking = $enabled")
        invalidateBackupCache()
        saveBackupAsync()
        notifyConfigChanged()
    }

    // ─── Per-App: Reel Limits ──────────────────────────────────────────────────
    fun getReelLimit(pkg: String): Int = read {
        if (prefs.contains("limit_reels_$pkg")) {
            prefs.getInt("limit_reels_$pkg", 30)
        } else {
            val backupVal = readBackupValue("limit_reels_$pkg", -1)
            if (backupVal is Int && backupVal != -1) backupVal else prefs.getInt("limit_reels_$pkg", 30)
        }
    }
    fun setReelLimit(pkg: String, v: Int) = write {
        prefs.edit().putInt("limit_reels_$pkg", v).apply()
        invalidateBackupCache()
        saveBackupAsync()
        // FIX: Use notifyLimitChanged instead of notifyConfigChanged.
        // Carries the new reelLimit value in the Intent so the :reelservice process can
        // apply it to its own stale prefs instance without a disk read race condition.
        val currentTimeLimit = prefs.getInt("limit_time_$pkg", 20)
        notifyLimitChanged(pkg, v, currentTimeLimit)
    }

    /**
     * Direct prefs write for use by the service's configReceiver ONLY.
     *
     * Called when the service receives ACTION_CONFIG_CHANGED with new limit values embedded
     * in Intent extras. Updates the service's own in-memory EncryptedSharedPreferences cache
     * without triggering another broadcast (which would cause an infinite loop).
     * This ensures prefs.getReelLimit() returns the correct value in the same process.
     */
    fun setReelLimitDirect(pkg: String, v: Int) = write {
        prefs.edit().putInt("limit_reels_$pkg", v).apply()
        // No backup, no broadcast — this is a one-way sync from UI process to service process
    }

    // ─── Per-App: Time Limits ──────────────────────────────────────────────────
    fun getTimeLimit(pkg: String): Int = read {
        if (prefs.contains("limit_time_$pkg")) {
            prefs.getInt("limit_time_$pkg", 20)
        } else {
            val backupVal = readBackupValue("limit_time_$pkg", -1)
            if (backupVal is Int && backupVal != -1) backupVal else prefs.getInt("limit_time_$pkg", 20)
        }
    }
    fun setTimeLimit(pkg: String, v: Int) = write {
        prefs.edit().putInt("limit_time_$pkg", v).apply()
        invalidateBackupCache()
        saveBackupAsync()
        // FIX: Use notifyLimitChanged instead of notifyConfigChanged.
        // Carries the new timeLimit value in the Intent so the :reelservice process can
        // apply it to its own stale prefs instance without a disk read race condition.
        val currentReelLimit = prefs.getInt("limit_reels_$pkg", 30)
        notifyLimitChanged(pkg, currentReelLimit, v)
    }

    /**
     * Direct prefs write for use by the service's configReceiver ONLY.
     * See setReelLimitDirect() for full explanation.
     */
    fun setTimeLimitDirect(pkg: String, v: Int) = write {
        prefs.edit().putInt("limit_time_$pkg", v).apply()
        // No backup, no broadcast — this is a one-way sync from UI process to service process
    }

    // ─── Daily Stats ──────────────────────────────────────────────────────────
    fun getTodayReelCount(pkg: String): Int {
        // BUG 1 FIX: ensureDailyReset() manages its own write internally;
        // calling it inside write{} created a nested write-lock path. Call outside.
        ensureDailyReset()
        return read {
            val backupVal = readBackupValue("today_count_$pkg", -1)
            if (backupVal is Int && backupVal != -1) backupVal else prefs.getInt("today_count_$pkg", 0)
        }
    }

    fun getTodayWatchTimeSeconds(pkg: String): Long {
        // BUG 1 FIX: same as above — read-only op, use read lock not write lock.
        ensureDailyReset()
        return read {
            val backupVal = readBackupValue("today_time_$pkg", -1L)
            if (backupVal is Long && backupVal != -1L) {
                backupVal
            } else if (backupVal is Int && backupVal != -1) {
                backupVal.toLong()
            } else {
                prefs.getLong("today_time_$pkg", 0L)
            }
        }
    }

    fun incrementReelCount(pkg: String): Int {
        // FIX: Call ensureDailyReset() BEFORE entering write{} to avoid calling it
        // while holding the write-lock (ensureDailyReset uses editor.commit() internally).
        ensureDailyReset()
        val count = write {
            val c = prefs.getInt("today_count_$pkg", 0) + 1
            prefs.edit().putInt("today_count_$pkg", c).apply()
            c
        }
        // FIX: Call saveHistoryCount OUTSIDE the write{} lock to avoid double-apply
        // reentrancy (saveHistoryCount also calls prefs.edit().apply() on the same prefs).
        saveHistoryCount(pkg, count)
        AppLogger.d(TAG, "Reel count $pkg: $count")
        saveBackupAsync()
        return count
    }

    fun addWatchTime(pkg: String, seconds: Long) {
        if (seconds <= 0) return
        // FIX: Call ensureDailyReset() BEFORE entering write{} — same reason as incrementReelCount.
        ensureDailyReset()
        write {
            val current = prefs.getLong("today_time_$pkg", 0L)
            prefs.edit().putLong("today_time_$pkg", current + seconds).apply()
            AppLogger.d(TAG, "Watch time $pkg: +${seconds}s = ${current + seconds}s total")
        }
        // M4 FIX: Call saveBackupAsync() OUTSIDE the write{} block.
        // saveBackupAsync() takes a read-lock snapshot internally. Calling it inside write{}
        // causes a WriteLock → ReadLock re-entry which, while technically allowed by
        // ReentrantReadWriteLock, is an anti-pattern and violates the lock-ordering rule.
        saveBackupAsync()
    }

    fun resetDailyProgress() = write {
        val todayStr = today()
        val editor = prefs.edit()
        for (pkg in TRACKED_PACKAGES) {
            editor.putInt("today_count_$pkg", 0)
            editor.putLong("today_time_$pkg", 0L)
            editor.putInt("hist_count_${pkg}_$todayStr", 0)
        }
        editor.putString("last_stats_date", todayStr)
        editor.commit()
        saveBackupAsync()
        AppLogger.d(TAG, "Daily progress reset")
    }

    fun resetStatsForPackage(pkg: String) = write {
        prefs.edit()
            .putInt("today_count_$pkg", 0)
            .putLong("today_time_$pkg", 0L)
            .commit()
        saveBackupAsync()
        AppLogger.d(TAG, "Stats reset for $pkg (logout detected)")
    }

    // ─── Streak ────────────────────────────────────────────────────────────────
    var noReelStreak: Int
        get() = read {
            val backupVal = readBackupValue("no_reel_streak", -1)
            if (backupVal is Int && backupVal != -1) backupVal else prefs.getInt("no_reel_streak", 0)
        }
        set(v) = write {
            prefs.edit().putInt("no_reel_streak", v).apply()
            saveBackupAsync()
        }

    // ─── Scheduled Pause ───────────────────────────────────────────────────────
    var scheduledPauseUntilMs: Long
        get() = read {
            val backupVal = readBackupValue("scheduled_pause_until", -1L)
            if (backupVal is Long && backupVal != -1L) {
                backupVal
            } else if (backupVal is Int && backupVal != -1) {
                backupVal.toLong()
            } else {
                prefs.getLong("scheduled_pause_until", 0L)
            }
        }
        set(v) = write {
            prefs.edit().putLong("scheduled_pause_until", v).apply()
            saveBackupAsync()
        }

    fun isPaused(): Boolean = System.currentTimeMillis() < scheduledPauseUntilMs
    fun clearPause() { scheduledPauseUntilMs = 0L }

    // ─── Weekly History ────────────────────────────────────────────────────────
    fun getWeeklyReelCounts(pkg: String): List<Int> {
        // FIX: Reuse a single DateFormat instance instead of creating one per iteration.
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            read { prefs.getInt("hist_count_${pkg}_${dateFmt.format(cal.time)}", 0) }
        }
    }

    fun getWeeklyReelCountsAll(): List<Int> {
        // FIX: Reuse a single DateFormat instance instead of creating one per iteration.
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            val dateStr = dateFmt.format(cal.time)
            read { TRACKED_PACKAGES.sumOf { pkg -> prefs.getInt("hist_count_${pkg}_$dateStr", 0) } }
        }
    }

    internal fun saveHistoryCount(pkg: String, todayCount: Int) {
        val dateStr = today()
        val histKey = "hist_count_${pkg}_$dateStr"
        prefs.edit().putInt(histKey, todayCount).apply()
    }

    // ─── Daily Reset Logic ─────────────────────────────────────────────────────
    private fun ensureDailyReset() {
        val lastDate = prefs.getString("last_stats_date", "") ?: ""
        val todayStr = today()
        if (lastDate.isNotEmpty() && lastDate != todayStr) {
            AppLogger.d(TAG, "New day: $lastDate -> $todayStr — resetting daily stats")
            val editor = prefs.edit()
            
            var watchedAnyYesterday = false
            for (pkg in TRACKED_PACKAGES) {
                if (prefs.getInt("today_count_$pkg", 0) > 0) {
                    watchedAnyYesterday = true
                    break
                }
            }
            val currentStreak = prefs.getInt("no_reel_streak", 0)
            if (!watchedAnyYesterday) {
                editor.putInt("no_reel_streak", currentStreak + 1)
            } else {
                editor.putInt("no_reel_streak", 0)
            }

            for (pkg in TRACKED_PACKAGES) {
                editor.putInt("today_count_$pkg", 0)
                editor.putLong("today_time_$pkg", 0L)
            }
            editor.putString("last_stats_date", todayStr)
            editor.commit()
            saveBackupAsync()
        }
    }

    // ─── Backup / Restore ──────────────────────────────────────────────────────
    private fun saveBackupAsync() {
        val file = backupFile ?: return
        // ISSUE 11 FIX: take a consistent snapshot under read lock so we never
        // read a partially-written prefs state from a concurrent write.
        val snapshot = try {
            read {
                JSONObject().apply {
                    put("backup_ts", System.currentTimeMillis())
                    put("last_stats_date", prefs.getString("last_stats_date", today()))
                    put("feature_enabled", prefs.getBoolean("feature_enabled", true))
                    put("block_mode_enabled", prefs.getBoolean("block_mode_enabled", false))
                    put("hard_block_enabled", prefs.getBoolean("hard_block_enabled", false))
                    put("autostart_dismissed", prefs.getBoolean("autostart_dismissed", false))
                    put("no_reel_streak", prefs.getInt("no_reel_streak", 0))
                    put("scheduled_pause_until", prefs.getLong("scheduled_pause_until", 0L))
                    for (pkg in TRACKED_PACKAGES) {
                        put("today_count_$pkg", prefs.getInt("today_count_$pkg", 0))
                        put("today_time_$pkg", prefs.getLong("today_time_$pkg", 0L))
                        put("limit_reels_$pkg", prefs.getInt("limit_reels_$pkg", 30))
                        put("limit_time_$pkg", prefs.getInt("limit_time_$pkg", 20))
                        put("enabled_$pkg", prefs.getBoolean("enabled_$pkg", true))
                    }
                }.toString()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "saveBackup snapshot error: ${e.message}")
            return
        }

        backupExecutor.execute {
            try {
                file.writeText(snapshot)
                AppLogger.v(TAG, "✅ Backup saved")
            } catch (e: Exception) {
                AppLogger.e(TAG, "saveBackup write error: ${e.message}")
            }
        }
    }

    private fun restoreFromBackup() {
        val file = backupFile ?: return
        if (!file.exists()) return
        try {
            val content = file.readText()
            if (content.isBlank()) return
            val json = JSONObject(content)

            val savedDate = json.optString("last_stats_date", "")
            val todayStr = today()
            val editor = prefs.edit()

            val restoreCounts = savedDate == todayStr
            if (restoreCounts) {
                for (pkg in TRACKED_PACKAGES) {
                    if (json.has("today_count_$pkg"))
                        editor.putInt("today_count_$pkg", json.getInt("today_count_$pkg"))
                    if (json.has("today_time_$pkg"))
                        editor.putLong("today_time_$pkg", json.getLong("today_time_$pkg"))
                }
            }

            if (json.has("feature_enabled"))
                editor.putBoolean("feature_enabled", json.getBoolean("feature_enabled"))
            if (json.has("block_mode_enabled"))
                editor.putBoolean("block_mode_enabled", json.getBoolean("block_mode_enabled"))
            if (json.has("hard_block_enabled"))
                editor.putBoolean("hard_block_enabled", json.getBoolean("hard_block_enabled"))
            if (json.has("autostart_dismissed"))
                editor.putBoolean("autostart_dismissed", json.getBoolean("autostart_dismissed"))
            if (json.has("no_reel_streak"))
                editor.putInt("no_reel_streak", json.getInt("no_reel_streak"))
            if (json.has("scheduled_pause_until"))
                editor.putLong("scheduled_pause_until", json.getLong("scheduled_pause_until"))

            for (pkg in TRACKED_PACKAGES) {
                if (json.has("limit_reels_$pkg"))
                    editor.putInt("limit_reels_$pkg", json.getInt("limit_reels_$pkg"))
                if (json.has("limit_time_$pkg"))
                    editor.putInt("limit_time_$pkg", json.getInt("limit_time_$pkg"))
                if (json.has("enabled_$pkg"))
                    editor.putBoolean("enabled_$pkg", json.getBoolean("enabled_$pkg"))
            }

            editor.putString("last_stats_date", todayStr)
            editor.commit()
        } catch (e: Exception) {
            AppLogger.e(TAG, "restoreFromBackup error: ${e.message}")
        }
    }

    private inline fun <T> read(block: () -> T): T {
        lock.readLock().lock()
        try { return block() } finally { lock.readLock().unlock() }
    }

    private inline fun <T> write(block: () -> T): T {
        lock.writeLock().lock()
        try { return block() } finally { lock.writeLock().unlock() }
    }
}
