package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.HabitRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitRecordDao {
    @Query("SELECT * FROM habit_records WHERE date = :date ORDER BY id ASC")
    fun observeByDate(date: String): Flow<List<HabitRecordEntity>>

    @Query("SELECT COUNT(*) FROM habit_records WHERE date = :date")
    suspend fun countByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM habit_records WHERE habit_template_id = :habitTemplateId AND date = :date")
    suspend fun countByTemplateIdAndDate(habitTemplateId: Long, date: String): Int

    @Query("SELECT * FROM habit_records WHERE habit_template_id = :habitTemplateId AND date = :date LIMIT 1")
    suspend fun getByTemplateIdAndDate(habitTemplateId: Long, date: String): HabitRecordEntity?

    @Query("SELECT COUNT(DISTINCT date) FROM habit_records WHERE habit_template_id = :habitTemplateId AND completed_count > 0")
    suspend fun countCompletedDaysByTemplateId(habitTemplateId: Long): Int

    @Query("DELETE FROM habit_records WHERE habit_template_id = :habitTemplateId")
    suspend fun deleteByTemplateId(habitTemplateId: Long)

    @Query("SELECT * FROM habit_records ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<HabitRecordEntity>

    @Query("DELETE FROM habit_records")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HabitRecordEntity): Long
}
