package com.consistencygridwallpaper.auth

import android.util.Log
import com.consistencygridwallpaper.storage.room.AppDatabase

/**
 * DataMigrationHelper — Utility to detect and clear local-only (guest) data.
 *
 * "Local-only" means records that were created while the user was NOT logged in
 * and therefore have serverId == null (they were never pushed to the server).
 */
object DataMigrationHelper {

    private const val TAG = "DataMigrationHelper"

    /**
     * Returns true if the Room DB has any records that belong to the guest session
     * (i.e., habits / goals / reminders with no serverId — never pushed to server).
     */
    suspend fun hasLocalOnlyData(db: AppDatabase): Boolean {
        return try {
            val localHabits    = db.habitDao().getUnsyncedHabits()
                .count { it.serverId == null && !it.isDeleted }
            val localGoals     = db.goalDao().getUnsyncedGoals()
                .count { it.serverId == null && !it.isDeleted }
            val localReminders = db.reminderDao().getUnsyncedReminders()
                .count { it.serverId == null && !it.isDeleted }

            val total = localHabits + localGoals + localReminders
            Log.d(TAG, "Local-only data: $localHabits habits, $localGoals goals, $localReminders reminders")
            total > 0
        } catch (e: Exception) {
            Log.e(TAG, "hasLocalOnlyData error: ${e.message}")
            false
        }
    }

    /**
     * Clears all user-specific tables so the fresh server pull is clean.
     * Called when the user chooses "No, start fresh" on the migration dialog.
     */
    suspend fun clearAllLocalUserData(db: AppDatabase) {
        try {
            db.habitDao().clearAll()
            db.habitLogDao().clearAll()
            db.goalDao().clearAll()
            db.reminderDao().clearAll()
            db.userProfileDao().upsert(
                com.consistencygridwallpaper.storage.room.UserProfileEntity(id = 1)
            )
            Log.d(TAG, "✅ All local user data cleared for fresh start")
        } catch (e: Exception) {
            Log.e(TAG, "clearAllLocalUserData error: ${e.message}")
        }
    }
}
