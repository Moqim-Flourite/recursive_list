package com.moqim.list.data.local.provider

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.moqim.list.data.local.database.AppDatabase

object DatabaseProvider {

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_plans_monthly_plan_id` ON `weekly_plans` (`monthly_plan_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_plans_weekly_plan_id` ON `daily_plans` (`weekly_plan_id`)")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 清理孤儿记录（外键约束生效前必须保证引用完整性）
            db.execSQL("DELETE FROM weekly_plans WHERE monthly_plan_id NOT IN (SELECT id FROM monthly_plans)")
            db.execSQL("DELETE FROM daily_plans WHERE weekly_plan_id IS NOT NULL AND weekly_plan_id NOT IN (SELECT id FROM weekly_plans)")
            db.execSQL("DELETE FROM execution_tasks WHERE daily_plan_id IS NOT NULL AND daily_plan_id NOT IN (SELECT id FROM daily_plans)")
            db.execSQL("DELETE FROM execution_tasks WHERE weekly_plan_id IS NOT NULL AND weekly_plan_id NOT IN (SELECT id FROM weekly_plans)")
            db.execSQL("DELETE FROM execution_tasks WHERE monthly_plan_id IS NOT NULL AND monthly_plan_id NOT IN (SELECT id FROM monthly_plans)")

            // 2. 重建 weekly_plans（加外键约束）
            db.execSQL("""
                CREATE TABLE `weekly_plans_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `monthly_plan_id` INTEGER NOT NULL,
                    `title` TEXT NOT NULL,
                    `goal` TEXT,
                    `week_start_date` TEXT NOT NULL,
                    `week_end_date` TEXT NOT NULL,
                    `capacity` INTEGER,
                    `review` TEXT,
                    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    FOREIGN KEY (`monthly_plan_id`) REFERENCES `monthly_plans`(`id`) ON DELETE CASCADE
                )
            """)
            db.execSQL("INSERT INTO `weekly_plans_new` SELECT * FROM `weekly_plans`")
            db.execSQL("DROP TABLE `weekly_plans`")
            db.execSQL("ALTER TABLE `weekly_plans_new` RENAME TO `weekly_plans`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_weekly_plans_monthly_plan_id` ON `weekly_plans` (`monthly_plan_id`)")

            // 3. 重建 daily_plans（加外键约束）
            db.execSQL("""
                CREATE TABLE `daily_plans_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `weekly_plan_id` INTEGER,
                    `date` TEXT NOT NULL,
                    `summary` TEXT,
                    `energy_level` TEXT,
                    `generated_from_previous_day` INTEGER NOT NULL DEFAULT 0,
                    `review` TEXT,
                    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    FOREIGN KEY (`weekly_plan_id`) REFERENCES `weekly_plans`(`id`) ON DELETE CASCADE
                )
            """)
            db.execSQL("INSERT INTO `daily_plans_new` SELECT * FROM `daily_plans`")
            db.execSQL("DROP TABLE `daily_plans`")
            db.execSQL("ALTER TABLE `daily_plans_new` RENAME TO `daily_plans`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_plans_weekly_plan_id` ON `daily_plans` (`weekly_plan_id`)")

            // 4. 重建 execution_tasks（加外键约束）
            db.execSQL("""
                CREATE TABLE `execution_tasks_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `note` TEXT,
                    `type` TEXT NOT NULL DEFAULT 'PLAN_EXECUTION',
                    `status` TEXT NOT NULL DEFAULT 'TODO',
                    `priority` TEXT NOT NULL DEFAULT 'MEDIUM',
                    `due_at` INTEGER,
                    `sort_order` INTEGER NOT NULL DEFAULT 0,
                    `monthly_plan_id` INTEGER,
                    `weekly_plan_id` INTEGER,
                    `daily_plan_id` INTEGER,
                    `source_type` TEXT NOT NULL DEFAULT 'MANUAL',
                    `source_task_id` INTEGER,
                    `time_segment` TEXT,
                    `specific_time` TEXT,
                    `is_top_focus` INTEGER NOT NULL DEFAULT 0,
                    `estimated_minutes` INTEGER,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL,
                    FOREIGN KEY (`monthly_plan_id`) REFERENCES `monthly_plans`(`id`) ON DELETE CASCADE,
                    FOREIGN KEY (`weekly_plan_id`) REFERENCES `weekly_plans`(`id`) ON DELETE CASCADE,
                    FOREIGN KEY (`daily_plan_id`) REFERENCES `daily_plans`(`id`) ON DELETE CASCADE
                )
            """)
            db.execSQL("INSERT INTO `execution_tasks_new` SELECT * FROM `execution_tasks`")
            db.execSQL("DROP TABLE `execution_tasks`")
            db.execSQL("ALTER TABLE `execution_tasks_new` RENAME TO `execution_tasks`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_tasks_daily_plan_id` ON `execution_tasks` (`daily_plan_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_tasks_weekly_plan_id` ON `execution_tasks` (`weekly_plan_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_tasks_monthly_plan_id` ON `execution_tasks` (`monthly_plan_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_tasks_status` ON `execution_tasks` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_tasks_time_segment` ON `execution_tasks` (`time_segment`)")
        }
    }

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "moqim_list.db",
            )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}
