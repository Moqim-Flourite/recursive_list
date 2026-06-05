package com.moqim.list.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.moqim.list.data.local.dao.DailyPlanDao
import com.moqim.list.data.local.dao.ExecutionTaskDao
import com.moqim.list.data.local.dao.HabitRecordDao
import com.moqim.list.data.local.dao.HabitTemplateDao
import com.moqim.list.data.local.dao.MonthlyPlanDao
import com.moqim.list.data.local.dao.SurfaceConfigDao
import com.moqim.list.data.local.dao.WeeklyPlanDao
import com.moqim.list.data.local.dao.WidgetInstanceConfigDao
import com.moqim.list.data.local.entity.DailyPlanEntity
import com.moqim.list.data.local.entity.ExecutionTaskEntity
import com.moqim.list.data.local.entity.HabitRecordEntity
import com.moqim.list.data.local.entity.HabitTemplateEntity
import com.moqim.list.data.local.entity.MonthlyPlanEntity
import com.moqim.list.data.local.entity.SurfaceConfigEntity
import com.moqim.list.data.local.entity.WeeklyPlanEntity
import com.moqim.list.data.local.entity.WidgetInstanceConfigEntity

@Database(
    entities = [
        MonthlyPlanEntity::class,
        WeeklyPlanEntity::class,
        DailyPlanEntity::class,
        ExecutionTaskEntity::class,
        HabitTemplateEntity::class,
        HabitRecordEntity::class,
        SurfaceConfigEntity::class,
        WidgetInstanceConfigEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun monthlyPlanDao(): MonthlyPlanDao
    abstract fun weeklyPlanDao(): WeeklyPlanDao
    abstract fun dailyPlanDao(): DailyPlanDao
    abstract fun executionTaskDao(): ExecutionTaskDao
    abstract fun habitTemplateDao(): HabitTemplateDao
    abstract fun habitRecordDao(): HabitRecordDao
    abstract fun surfaceConfigDao(): SurfaceConfigDao
    abstract fun widgetInstanceConfigDao(): WidgetInstanceConfigDao
}
