package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.WeeklyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyPlanDao {
    @Query("SELECT * FROM weekly_plans WHERE monthly_plan_id = :monthlyPlanId ORDER BY week_start_date DESC, id DESC")
    fun observeByMonthlyPlanId(monthlyPlanId: Long): Flow<List<WeeklyPlanEntity>>

    @Query("SELECT * FROM weekly_plans ORDER BY week_start_date DESC, id DESC")
    fun observeAll(): Flow<List<WeeklyPlanEntity>>

    @Query("SELECT COUNT(*) FROM weekly_plans")
    suspend fun countAll(): Int

    @Query("""
        SELECT * FROM weekly_plans
        WHERE week_start_date <= :date AND week_end_date >= :date
        ORDER BY id DESC
        LIMIT 1
    """)
    suspend fun getActiveWeeklyPlan(date: String): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plans WHERE id = :planId LIMIT 1")
    suspend fun getById(planId: Long): WeeklyPlanEntity?

    @Query("SELECT * FROM weekly_plans WHERE monthly_plan_id = :monthlyPlanId ORDER BY week_start_date DESC, id DESC")
    suspend fun getByMonthlyPlanId(monthlyPlanId: Long): List<WeeklyPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeeklyPlanEntity): Long

    @Query("SELECT * FROM weekly_plans ORDER BY week_start_date DESC, id DESC")
    suspend fun getAll(): List<WeeklyPlanEntity>

    @Query("DELETE FROM weekly_plans")
    suspend fun clearAll()

    @Query("DELETE FROM weekly_plans WHERE id = :planId")
    suspend fun deleteById(planId: Long)
}
