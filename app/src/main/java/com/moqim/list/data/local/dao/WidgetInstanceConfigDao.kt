package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.WidgetInstanceConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetInstanceConfigDao {
    @Query("SELECT * FROM widget_instance_configs ORDER BY app_widget_id ASC")
    fun observeAll(): Flow<List<WidgetInstanceConfigEntity>>

    @Query("SELECT * FROM widget_instance_configs ORDER BY app_widget_id ASC")
    suspend fun getAll(): List<WidgetInstanceConfigEntity>

    @Query("SELECT * FROM widget_instance_configs WHERE app_widget_id = :appWidgetId LIMIT 1")
    suspend fun getByAppWidgetId(appWidgetId: Int): WidgetInstanceConfigEntity?

    @Query("DELETE FROM widget_instance_configs WHERE app_widget_id = :appWidgetId")
    suspend fun deleteByAppWidgetId(appWidgetId: Int)

    @Query("DELETE FROM widget_instance_configs")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WidgetInstanceConfigEntity): Long
}
