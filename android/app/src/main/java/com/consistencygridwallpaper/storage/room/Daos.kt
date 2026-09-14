package com.consistencygridwallpaper.storage.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE is_deleted = 0 AND is_active = 1 ORDER BY created_at DESC")
    fun getActiveHabitsFlow(): Flow<List<HabitEntity>>

    /** One-shot list for wallpaper data builder */
    @Query("SELECT * FROM habits WHERE is_deleted = 0 AND is_active = 1 ORDER BY created_at DESC")
    suspend fun getActiveHabitsList(): List<HabitEntity>

    /** Synchronous list for RemoteViewsFactory widget update (avoids runBlocking deadlock) */
    @Query("SELECT * FROM habits WHERE is_deleted = 0 AND is_active = 1 ORDER BY created_at DESC")
    fun getActiveHabitsListSync(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    /** Returns habits that have local changes not yet pushed to the server. */
    @Query("SELECT * FROM habits WHERE is_synced = 0")
    suspend fun getUnsyncedHabits(): List<HabitEntity>

    @Query("UPDATE habits SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markHabitsSynced(ids: List<String>)

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabitById(id: String): HabitEntity?

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: String)

    @Query("UPDATE habits SET is_deleted = 1, is_synced = 0, updated_at = :timestamp WHERE id = :habitId")
    suspend fun softDeleteHabit(habitId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM habits")
    suspend fun clearAll()
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs WHERE habit_id = :habitId AND date >= :startDate ORDER BY date ASC")
    fun getLogsForHabit(habitId: String, startDate: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getLogsForDateFlow(date: String): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    suspend fun getLogsForDate(date: String): List<HabitLogEntity>

    /** Synchronous logs for RemoteViewsFactory widget update */
    @Query("SELECT * FROM habit_logs WHERE date = :date")
    fun getLogsForDateSync(date: String): List<HabitLogEntity>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: HabitLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<HabitLogEntity>)

    @Query("SELECT * FROM habit_logs WHERE is_synced = 0")
    suspend fun getUnsyncedLogs(): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE date >= :startDate ORDER BY date DESC")
    fun getAllLogsFrom(startDate: String): Flow<List<HabitLogEntity>>

    /** All done logs — used by wallpaper data builder to compute activityMap */
    @Query("SELECT * FROM habit_logs WHERE done = 1")
    suspend fun getAllDoneLogs(): List<HabitLogEntity>

    @Query("UPDATE habit_logs SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markLogsSynced(ids: List<String>)
    
    @Query("DELETE FROM habit_logs")
    suspend fun clearAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE is_deleted = 0 ORDER BY is_pinned DESC, id ASC")
    fun getActiveGoalsFlow(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE is_deleted = 0 ORDER BY is_pinned DESC, id ASC")
    suspend fun getActiveGoalsList(): List<GoalEntity>

    /** Synchronous list for RemoteViewsFactory widget update */
    @Query("SELECT * FROM goals WHERE is_deleted = 0 ORDER BY is_pinned DESC, id ASC")
    fun getActiveGoalsListSync(): List<GoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<GoalEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: GoalEntity)

    @Query("UPDATE goals SET is_deleted = 1, is_synced = 0, updated_at = :timestamp WHERE id = :goalId")
    suspend fun softDeleteGoal(goalId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM goals WHERE is_synced = 0")
    suspend fun getUnsyncedGoals(): List<GoalEntity>

    /** Returns only the local IDs of goals that have pending local changes not yet pushed. */
    @Query("SELECT id FROM goals WHERE is_synced = 0")
    suspend fun getUnsyncedGoalIds(): List<String>

    @Query("UPDATE goals SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markGoalsSynced(ids: List<String>)

    @Query("SELECT * FROM goals WHERE id = :goalId LIMIT 1")
    suspend fun getGoalById(goalId: String): GoalEntity?

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoalById(goalId: String)

    @Query("DELETE FROM goals")
    suspend fun clearAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE is_deleted = 0 AND is_completed = 0 ORDER BY priority DESC, start_date ASC")
    fun getRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE is_deleted = 0 AND is_completed = 0 ORDER BY priority DESC, start_date ASC")
    suspend fun getRemindersList(): List<ReminderEntity>

    /** Synchronous list for RemoteViewsFactory widget update */
    @Query("SELECT * FROM reminders WHERE is_deleted = 0 AND is_completed = 0 ORDER BY priority DESC, start_date ASC")
    fun getRemindersListSync(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE is_deleted = 0 AND is_completed = 1 ORDER BY updated_at DESC")
    fun getCompletedRemindersFlow(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET is_completed = 1, is_synced = 0, updated_at = :timestamp WHERE id = :reminderId")
    suspend fun completeReminder(reminderId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET is_completed = 0, is_synced = 0, updated_at = :timestamp WHERE id = :reminderId")
    suspend fun uncompleteReminder(reminderId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET is_deleted = 1, is_synced = 0, updated_at = :timestamp WHERE id = :reminderId")
    suspend fun softDeleteReminder(reminderId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM reminders WHERE is_synced = 0")
    suspend fun getUnsyncedReminders(): List<ReminderEntity>

    @Query("SELECT id FROM reminders WHERE is_synced = 0")
    suspend fun getUnsyncedReminderIds(): List<String>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: String): ReminderEntity?

    @Query("UPDATE reminders SET is_synced = 1 WHERE id IN (:ids)")
    suspend fun markRemindersSynced(ids: List<String>)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: String)

    @Query("DELETE FROM reminders")
    suspend fun clearAll()
}

@Dao
interface ReelControllerDao {

    // ── Per-app settings ──────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(entity: ReelControllerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(entities: List<ReelControllerEntity>)

    @Query("SELECT * FROM reel_controller")
    suspend fun getAllApps(): List<ReelControllerEntity>

    @Query("SELECT * FROM reel_controller WHERE pkg = :pkg LIMIT 1")
    suspend fun getApp(pkg: String): ReelControllerEntity?

    @Query("SELECT * FROM reel_controller WHERE is_synced = 0")
    suspend fun getUnsyncedApps(): List<ReelControllerEntity>

    @Query("UPDATE reel_controller SET is_synced = 1 WHERE pkg IN (:pkgs)")
    suspend fun markAppsSynced(pkgs: List<String>)

    @Query("DELETE FROM reel_controller")
    suspend fun clearAll()

    // ── Global config (singleton row id=1) ───────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlobalConfig(config: ReelGlobalConfigEntity)

    @Query("SELECT * FROM reel_global_config WHERE id = 1 LIMIT 1")
    suspend fun getGlobalConfig(): ReelGlobalConfigEntity?

    @Query("UPDATE reel_global_config SET is_synced = 1 WHERE id = 1")
    suspend fun markGlobalSynced()
}

@Dao
interface UserProfileDao {

    /** Upsert the singleton profile row (id = 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)

    /** Returns the cached profile, or null if the user has never synced. */
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun get(): UserProfileEntity?

    /** Live flow for the UI to observe. */
    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getFlow(): Flow<UserProfileEntity?>
}
