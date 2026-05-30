package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.moqim.list.data.local.entity.HabitTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitTemplateDao {
    @Query("SELECT * FROM habit_templates WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")
    fun observeEnabled(): Flow<List<HabitTemplateEntity>>

    @Query("SELECT COUNT(*) FROM habit_templates")
    suspend fun countAll(): Int

    @Query("SELECT * FROM habit_templates WHERE enabled = 1 ORDER BY sort_order ASC, id ASC")
    suspend fun getEnabledList(): List<HabitTemplateEntity>

    @Query("SELECT * FROM habit_templates WHERE id = :templateId LIMIT 1")
    suspend fun getById(templateId: Long): HabitTemplateEntity?

    @Query("SELECT * FROM habit_templates ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<HabitTemplateEntity>

    @Query("DELETE FROM habit_templates")
    suspend fun clearAll()

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM habit_templates")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HabitTemplateEntity): Long

    @Query("DELETE FROM habit_templates WHERE id = :templateId")
    suspend fun deleteById(templateId: Long)

    @Transaction
    suspend fun replaceEnabledOrder(templateIdsInOrder: List<Long>) {
        val current = getEnabledList().associateBy { it.id }
        templateIdsInOrder.forEachIndexed { index, templateId ->
            current[templateId]?.let { entity ->
                upsert(entity.copy(sortOrder = index))
            }
        }
    }
}
