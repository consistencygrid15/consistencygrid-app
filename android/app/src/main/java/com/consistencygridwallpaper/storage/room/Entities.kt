package com.consistencygridwallpaper.storage.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo

@Entity(
    tableName = "habits",
    indices = [
        Index(value = ["is_active", "server_id"])
    ]
)
data class HabitEntity(
    @PrimaryKey val id: String, // Treat this as the primary unique key, which will be the server's cuid() or a temporary local ID
    @ColumnInfo(name = "server_id") val serverId: String? = null, // If null, the habit hasn't been synced to server yet
    val title: String,
    @ColumnInfo(name = "scheduled_time") val scheduledTime: String? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,    // Soft delete for sync
    @ColumnInfo(name = "is_synced")  val isSynced:  Boolean = true       // false = local change not yet pushed
)

@Entity(
    tableName = "habit_logs",
    indices = [
        Index(value = ["habit_id", "date"], unique = true)
    ]
)
data class HabitLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val date: String, // "YYYY-MM-DD"
    val done: Boolean,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = true // False if the tick was made offline and needs to be pushed
)

@Entity(
    tableName = "goals",
    indices = [
        Index(value = ["is_deleted", "server_id"])
    ]
)
data class GoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    val title: String,
    val progress: Int,        // 0-100 derived from subgoals
    val category: String = "General",
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "sub_goals") val subGoalsJson: String = "[]",
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = true,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reminders",
    indices = [
        Index(value = ["is_deleted", "server_id"])
    ]
)
data class ReminderEntity(
    @PrimaryKey val id: String, // Local UUID
    @ColumnInfo(name = "server_id") val serverId: String? = null,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "start_date") val startDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "end_date") val endDate: String, // YYYY-MM-DD
    @ColumnInfo(name = "start_time") val startTime: String?,
    @ColumnInfo(name = "end_time") val endTime: String?,
    @ColumnInfo(name = "is_full_day") val isFullDay: Boolean,
    val priority: Int,
    @ColumnInfo(name = "marker_color") val markerColor: String,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean = false,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
)

// ─── Reel Controller ──────────────────────────────────────────────────────────

/**
 * Per-app reel tracking config (one row per tracked package).
 * isSynced = false means this row has local changes not yet pushed to server.
 */
@Entity(
    tableName = "reel_controller",
    indices = [Index(value = ["is_synced"])]
)
data class ReelControllerEntity(
    @PrimaryKey val pkg: String,                     // e.g. "com.instagram.android"
    @ColumnInfo(name = "reel_limit")  val reelLimit: Int     = 30,
    @ColumnInfo(name = "time_limit")  val timeLimit: Int     = 20, // minutes
    @ColumnInfo(name = "is_enabled")  val isEnabled: Boolean = true,
    @ColumnInfo(name = "is_synced")   val isSynced:  Boolean = false,
    @ColumnInfo(name = "updated_at")  val updatedAt: Long    = System.currentTimeMillis()
)

/**
 * Singleton row (id=1) for global reel controller toggles.
 */
@Entity(tableName = "reel_global_config")
data class ReelGlobalConfigEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "is_feature_enabled")    val isFeatureEnabled:    Boolean = true,
    @ColumnInfo(name = "is_block_mode_enabled") val isBlockModeEnabled:  Boolean = false,
    @ColumnInfo(name = "is_hard_block_enabled") val isHardBlockEnabled:  Boolean = false,
    @ColumnInfo(name = "is_synced")             val isSynced:            Boolean = false,
    @ColumnInfo(name = "updated_at")            val updatedAt:           Long    = System.currentTimeMillis()
)

// ─── User Profile Cache ───────────────────────────────────────────────────────

/**
 * Singleton row (id=1) caching the server user profile.
 * Populated on every successful sync — gives the app fully offline access
 * to user name, email, plan, and streak without any network call.
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,                                      // Always 1 — singleton
    @ColumnInfo(name = "name")               val name: String = "",
    @ColumnInfo(name = "email")              val email: String = "",
    @ColumnInfo(name = "plan")               val plan: String = "free",
    @ColumnInfo(name = "is_premium")         val isPremium: Boolean = false,
    @ColumnInfo(name = "current_streak")     val currentStreak: Int = 0,
    @ColumnInfo(name = "total_habits")       val totalHabits: Int = 0,
    @ColumnInfo(name = "today_completion")   val todayCompletion: Int = 0,  // 0-100 %
    @ColumnInfo(name = "days_tracked")       val daysTracked: Int = 0,
    @ColumnInfo(name = "public_token")       val publicToken: String = "",
    @ColumnInfo(name = "updated_at")         val updatedAt: Long = 0L
)
