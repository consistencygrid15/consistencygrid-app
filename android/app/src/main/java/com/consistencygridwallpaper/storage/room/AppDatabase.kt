package com.consistencygridwallpaper.storage.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class,
        GoalEntity::class,
        ReminderEntity::class,
        ReelControllerEntity::class,
        ReelGlobalConfigEntity::class,
        UserProfileEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun goalDao(): GoalDao
    abstract fun reminderDao(): ReminderDao
    abstract fun reelControllerDao(): ReelControllerDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE goals ADD COLUMN server_id TEXT")
                database.execSQL("ALTER TABLE goals ADD COLUMN sub_goals TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE goals ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE goals ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE goals ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_is_deleted_server_id` ON `goals` (`is_deleted`, `server_id`)")
            }
        }

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN server_id TEXT")
                database.execSQL("ALTER TABLE reminders ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE reminders ADD COLUMN is_deleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE reminders ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_is_deleted_server_id` ON `reminders` (`is_deleted`, `server_id`)")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Per-app reel controller settings
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reel_controller` (
                        `pkg` TEXT NOT NULL PRIMARY KEY,
                        `reel_limit` INTEGER NOT NULL DEFAULT 30,
                        `time_limit` INTEGER NOT NULL DEFAULT 20,
                        `is_enabled` INTEGER NOT NULL DEFAULT 1,
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reel_controller_is_synced` ON `reel_controller` (`is_synced`)"
                )
                // Global reel controller config (singleton)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `reel_global_config` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `is_feature_enabled` INTEGER NOT NULL DEFAULT 1,
                        `is_block_mode_enabled` INTEGER NOT NULL DEFAULT 0,
                        `is_hard_block_enabled` INTEGER NOT NULL DEFAULT 0,
                        `is_synced` INTEGER NOT NULL DEFAULT 0,
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add is_synced column to habits so pending creates/updates can be queued
                database.execSQL("ALTER TABLE habits ADD COLUMN is_synced INTEGER NOT NULL DEFAULT 1")

                // Create the user_profile cache table (singleton row id=1)
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_profile` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL DEFAULT '',
                        `email` TEXT NOT NULL DEFAULT '',
                        `plan` TEXT NOT NULL DEFAULT 'free',
                        `is_premium` INTEGER NOT NULL DEFAULT 0,
                        `current_streak` INTEGER NOT NULL DEFAULT 0,
                        `total_habits` INTEGER NOT NULL DEFAULT 0,
                        `today_completion` INTEGER NOT NULL DEFAULT 0,
                        `days_tracked` INTEGER NOT NULL DEFAULT 0,
                        `public_token` TEXT NOT NULL DEFAULT '',
                        `updated_at` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reminders ADD COLUMN is_completed INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "consistency_grid_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration() // Reset DB if migrating from 1 -> 2
                    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                INSTANCE = instance
                instance
                instance
            }
        }
    }
}
