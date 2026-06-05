package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.DailyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyPlanDao {
    @Query("SELECT * FROM daily_plans WHERE date = :date LIMIT 1")
    fun observeByDate(date: String): Flow<DailyPlanEntity?>

    @Query("SELECT COUNT(*) FROM daily_plans WHERE date = :date")
    suspend fun countByDate(date: String): Int

    @Query("SELECT * FROM daily_plans WHERE date = :date ORDER BY updated_at DESC, id DESC")
    suspend fun getAllByDate(date: String): List<DailyPlanEntity>

    @Query("SELECT * FROM daily_plans WHERE id = :planId LIMIT 1")
    suspend fun getById(planId: Long): DailyPlanEntity?

    @Query("SELECT * FROM daily_plans WHERE weekly_plan_id = :weeklyPlanId ORDER BY date ASC")
    suspend fun getByWeeklyPlanId(weeklyPlanId: Long): List<DailyPlanEntity>

    @Query("SELECT * FROM daily_plans WHERE weekly_plan_id = :weeklyPlanId AND date = :date LIMIT 1")
    suspend fun getByWeeklyPlanIdAndDate(weeklyPlanId: Long, date: String): DailyPlanEntity?

    @Query("DELETE FROM daily_plans WHERE id = :planId")
    suspend fun deleteById(planId: Long)

    @Query("DELETE FROM daily_plans WHERE weekly_plan_id = :weeklyPlanId")
    suspend fun deleteByWeeklyPlanId(weeklyPlanId: Long)

    @Query("DELETE FROM daily_plans WHERE weekly_plan_id IN (:weeklyPlanIds)")
    suspend fun deleteByWeeklyPlanIds(weeklyPlanIds: List<Long>)

    @Query("SELECT * FROM daily_plans ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<DailyPlanEntity>

    @Query("DELETE FROM daily_plans")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DailyPlanEntity): Long
}
