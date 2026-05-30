package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.ExecutionTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionTaskDao {
    @Query("""
        SELECT * FROM execution_tasks
        WHERE daily_plan_id = :dailyPlanId
        ORDER BY is_top_focus DESC, sort_order ASC, id ASC
    """)
    fun observeByDailyPlanId(dailyPlanId: Long): Flow<List<ExecutionTaskEntity>>

    @Query("""
        SELECT * FROM execution_tasks
        WHERE daily_plan_id IS NULL
        ORDER BY updated_at DESC, id DESC
    """)
    fun observeUnscheduledTasks(): Flow<List<ExecutionTaskEntity>>

    @Query("""
        SELECT * FROM execution_tasks
        WHERE daily_plan_id IS NULL AND source_type = :sourceType
        ORDER BY updated_at DESC, id DESC
    """)
    fun observeBySourceType(sourceType: String): Flow<List<ExecutionTaskEntity>>

    @Query("SELECT COUNT(*) FROM execution_tasks WHERE daily_plan_id = :dailyPlanId")
    suspend fun countByDailyPlanId(dailyPlanId: Long): Int

    @Query("SELECT COUNT(*) FROM execution_tasks WHERE weekly_plan_id = :weeklyPlanId")
    suspend fun countByWeeklyPlanId(weeklyPlanId: Long): Int

    @Query("SELECT COUNT(*) FROM execution_tasks WHERE weekly_plan_id = :weeklyPlanId AND status = 'DONE'")
    suspend fun countDoneByWeeklyPlanId(weeklyPlanId: Long): Int

    @Query("SELECT COUNT(*) FROM execution_tasks WHERE daily_plan_id IS NULL")
    suspend fun countUnscheduled(): Int

    @Query("SELECT * FROM execution_tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: Long): ExecutionTaskEntity?

    @Query("DELETE FROM execution_tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: Long)

    @Query("DELETE FROM execution_tasks WHERE daily_plan_id = :dailyPlanId")
    suspend fun deleteByDailyPlanId(dailyPlanId: Long)

    @Query("DELETE FROM execution_tasks WHERE weekly_plan_id = :weeklyPlanId")
    suspend fun deleteByWeeklyPlanId(weeklyPlanId: Long)

    @Query("DELETE FROM execution_tasks WHERE monthly_plan_id = :monthlyPlanId")
    suspend fun deleteByMonthlyPlanId(monthlyPlanId: Long)

    @Query("SELECT * FROM execution_tasks WHERE daily_plan_id = :dailyPlanId ORDER BY is_top_focus DESC, sort_order ASC, id ASC")
    suspend fun getByDailyPlanId(dailyPlanId: Long): List<ExecutionTaskEntity>

    @Query("SELECT * FROM execution_tasks WHERE weekly_plan_id = :weeklyPlanId ORDER BY updated_at DESC, id DESC")
    suspend fun getByWeeklyPlanId(weeklyPlanId: Long): List<ExecutionTaskEntity>

    @Query("SELECT * FROM execution_tasks WHERE monthly_plan_id = :monthlyPlanId ORDER BY updated_at DESC, id DESC")
    suspend fun getByMonthlyPlanId(monthlyPlanId: Long): List<ExecutionTaskEntity>

    @Query("""
        SELECT * FROM execution_tasks
        WHERE status != 'DONE'
          AND daily_plan_id IS NOT NULL
        ORDER BY updated_at DESC, id DESC
    """)
    suspend fun getAllIncompleteScheduledTasks(): List<ExecutionTaskEntity>

    @Query("SELECT * FROM execution_tasks ORDER BY updated_at DESC, id DESC")
    suspend fun getAll(): List<ExecutionTaskEntity>

    @Query("DELETE FROM execution_tasks")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ExecutionTaskEntity): Long
}
