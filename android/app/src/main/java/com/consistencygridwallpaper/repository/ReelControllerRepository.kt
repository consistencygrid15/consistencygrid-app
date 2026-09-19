package com.consistencygridwallpaper.repository

import android.content.Context
import android.util.Log
import com.consistencygridwallpaper.reelcontrol.data.PreferencesManager
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.storage.room.AppDatabase
import com.consistencygridwallpaper.storage.room.ReelControllerEntity
import com.consistencygridwallpaper.storage.room.ReelGlobalConfigEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * ReelControllerRepository — Local-first write-through repository.
 *
 * FLOW:
 *  User changes setting
 *    → 1. Write to SharedPreferences  (instant, for in-process reads by AccessibilityService)
 *    → 2. Write to Room/SQLite         (persistent, survives phone restart / SharedPrefs wipe)
 *    → 3. Mark Room row as un-synced   (isSynced = false)
 *    → 4. Background sync to server    (next SyncRepository.syncWithServer() call)
 *
 * WHY ROOM AND NOT JUST SHAREDPREFS?
 *  SharedPreferences lives in /data/data/pkg/shared_prefs/ which OEM "Clean" apps
 *  can wipe. Room (SQLite) lives in /data/data/pkg/databases/ — OEM cleaners NEVER
 *  touch it; only a deliberate "Clear Data" from Android Settings clears both.
 *  This means settings survive: phone restart, battery pull, OEM cache clean.
 */
class ReelControllerRepository(context: Context) {

    private val appCtx = context.applicationContext
    private val db     = AppDatabase.getDatabase(appCtx)
    private val prefs  = PreferencesManager(appCtx)
    private val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "ReelControllerRepo"
    }

    // ─── Per-App Settings ─────────────────────────────────────────────────────

    fun setReelLimit(pkg: String, limit: Int) {
        // 1. Fast write → SharedPreferences
        prefs.setReelLimit(pkg, limit)
        val rm = com.consistencygridwallpaper.reelcontrol.manager.ReelManager.getInstance(appCtx)
        rm.clearSnooze()
        rm.refreshLimitsForPackage(pkg)
        // 2. Persistent write → Room (dirty flag set)
        scope.launch {
            val current = db.reelControllerDao().getApp(pkg) ?: defaultEntity(pkg)
            db.reelControllerDao().upsertApp(
                current.copy(reelLimit = limit, isSynced = false, updatedAt = System.currentTimeMillis())
            )
            Log.d(TAG, "setReelLimit($pkg, $limit) → Room+Prefs saved")
        }
    }

    fun setTimeLimit(pkg: String, limitMinutes: Int) {
        prefs.setTimeLimit(pkg, limitMinutes)
        val rm = com.consistencygridwallpaper.reelcontrol.manager.ReelManager.getInstance(appCtx)
        rm.clearSnooze()
        rm.refreshLimitsForPackage(pkg)
        scope.launch {
            val current = db.reelControllerDao().getApp(pkg) ?: defaultEntity(pkg)
            db.reelControllerDao().upsertApp(
                current.copy(timeLimit = limitMinutes, isSynced = false, updatedAt = System.currentTimeMillis())
            )
            Log.d(TAG, "setTimeLimit($pkg, $limitMinutes) → Room+Prefs saved")
        }
    }

    fun setAppEnabled(pkg: String, enabled: Boolean) {
        prefs.setAppEnabled(pkg, enabled)
        com.consistencygridwallpaper.reelcontrol.manager.ReelManager.getInstance(appCtx).refreshLimitsForPackage(pkg)
        scope.launch {
            val current = db.reelControllerDao().getApp(pkg) ?: defaultEntity(pkg)
            db.reelControllerDao().upsertApp(
                current.copy(isEnabled = enabled, isSynced = false, updatedAt = System.currentTimeMillis())
            )
            Log.d(TAG, "setAppEnabled($pkg, $enabled) → Room+Prefs saved")
        }
    }

    // ─── Global Toggles ───────────────────────────────────────────────────────

    fun setFeatureEnabled(enabled: Boolean) {
        prefs.isFeatureEnabled = enabled
        com.consistencygridwallpaper.reelcontrol.manager.ReelManager.getInstance(appCtx).refreshLimitsForPackage()
        scope.launch {
            val current = db.reelControllerDao().getGlobalConfig() ?: ReelGlobalConfigEntity()
            db.reelControllerDao().upsertGlobalConfig(
                current.copy(isFeatureEnabled = enabled, isSynced = false, updatedAt = System.currentTimeMillis())
            )
            Log.d(TAG, "setFeatureEnabled($enabled) → Room+Prefs saved")
        }
    }

    fun setBlockModeEnabled(enabled: Boolean) {
        prefs.isBlockModeEnabled = enabled
        com.consistencygridwallpaper.reelcontrol.manager.ReelManager.getInstance(appCtx).refreshBlockMode()
        scope.launch {
            val current = db.reelControllerDao().getGlobalConfig() ?: ReelGlobalConfigEntity()
            db.reelControllerDao().upsertGlobalConfig(
                current.copy(isBlockModeEnabled = enabled, isSynced = false, updatedAt = System.currentTimeMillis())
            )
            Log.d(TAG, "setBlockModeEnabled($enabled) → Room+Prefs saved")
        }
    }

    fun setHardBlockEnabled(enabled: Boolean) {
        prefs.isHardBlockEnabled = enabled
        com.consistencygridwallpaper.reelcontrol.manager.ReelManager.getInstance(appCtx).refreshBlockMode()
        scope.launch {
            val current = db.reelControllerDao().getGlobalConfig() ?: ReelGlobalConfigEntity()
            db.reelControllerDao().upsertGlobalConfig(
                current.copy(isHardBlockEnabled = enabled, isSynced = false, updatedAt = System.currentTimeMillis())
            )
            Log.d(TAG, "setHardBlockEnabled($enabled) → Room+Prefs saved")
        }
    }

    // ─── Restore from Room → SharedPreferences ────────────────────────────────

    /**
     * Reads all saved config from Room (SQLite) and writes it back to SharedPreferences.
     *
     * Call this at app startup / after a SharedPreferences wipe is detected,
     * BEFORE the AccessibilityService initialises, so all limits are already in
     * SharedPreferences when the service starts reading them.
     */
    suspend fun restoreLocalFromRoom() {
        try {
            val globalConfig = db.reelControllerDao().getGlobalConfig()
            val appConfigs   = db.reelControllerDao().getAllApps()

            if (globalConfig == null && appConfigs.isEmpty()) {
                Log.d(TAG, "No Room data to restore — first install or fresh DB")
                return
            }

            globalConfig?.let {
                prefs.isFeatureEnabled   = it.isFeatureEnabled
                prefs.isBlockModeEnabled = it.isBlockModeEnabled
                prefs.isHardBlockEnabled = it.isHardBlockEnabled
            }

            appConfigs.forEach { app ->
                prefs.setReelLimit(app.pkg, app.reelLimit)
                prefs.setTimeLimit(app.pkg, app.timeLimit)
                prefs.setAppEnabled(app.pkg, app.isEnabled)
            }

            Log.d(TAG, "✅ Restored ${appConfigs.size} app configs + global from Room")
        } catch (e: Exception) {
            Log.e(TAG, "restoreLocalFromRoom failed: ${e.message}", e)
        }
    }

    /**
     * Called by SyncRepository after successfully fetching reel controller config from server.
     * Writes server data → Room (isSynced=true) + SharedPreferences.
     */
    suspend fun applyServerConfig(
        isFeatureEnabled: Boolean,
        isBlockModeEnabled: Boolean,
        isHardBlockEnabled: Boolean,
        appSettings: List<ReelControllerEntity>
    ) {
        try {
            // Write to Room
            db.reelControllerDao().upsertGlobalConfig(
                ReelGlobalConfigEntity(
                    isFeatureEnabled   = isFeatureEnabled,
                    isBlockModeEnabled = isBlockModeEnabled,
                    isHardBlockEnabled = isHardBlockEnabled,
                    isSynced = true
                )
            )
            val syncedApps = appSettings.map { it.copy(isSynced = true) }
            if (syncedApps.isNotEmpty()) db.reelControllerDao().upsertApps(syncedApps)

            // Propagate to SharedPreferences
            prefs.isFeatureEnabled   = isFeatureEnabled
            prefs.isBlockModeEnabled = isBlockModeEnabled
            prefs.isHardBlockEnabled = isHardBlockEnabled
            appSettings.forEach { app ->
                prefs.setReelLimit(app.pkg, app.reelLimit)
                prefs.setTimeLimit(app.pkg, app.timeLimit)
                prefs.setAppEnabled(app.pkg, app.isEnabled)
            }

            Log.d(TAG, "✅ Applied server config: ${appSettings.size} apps updated")
        } catch (e: Exception) {
            Log.e(TAG, "applyServerConfig failed: ${e.message}", e)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun defaultEntity(pkg: String) = ReelControllerEntity(
        pkg       = pkg,
        reelLimit = prefs.getReelLimit(pkg),
        timeLimit = prefs.getTimeLimit(pkg),
        isEnabled = prefs.isAppEnabled(pkg),
        isSynced  = false
    )
}
