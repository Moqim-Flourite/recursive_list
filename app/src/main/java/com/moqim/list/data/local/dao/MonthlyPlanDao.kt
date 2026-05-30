package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.MonthlyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MonthlyPlanDao {
    @Query("SELECT * FROM monthly_plans ORDER BY start_date DESC, id DESC")
    fun observeAll(): Flow<List<MonthlyPlanEntity>>

    @Query("SELECT COUNT(*) FROM monthly_plans")
    suspend fun countAll(): Int

    @Query("SELECT * FROM monthly_plans WHERE id = :planId LIMIT 1")
    suspend fun getById(planId: Long): MonthlyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MonthlyPlanEntity): Long

    @Query("SELECT * FROM monthly_plans ORDER BY start_date DESC, id DESC")
    suspend fun getAll(): List<MonthlyPlanEntity>

    @Query("DELETE FROM monthly_plans")
    suspend fun clearAll()

    @Query("DELETE FROM monthly_plans WHERE id = :planId")
    suspend fun deleteById(planId: Long)
}
