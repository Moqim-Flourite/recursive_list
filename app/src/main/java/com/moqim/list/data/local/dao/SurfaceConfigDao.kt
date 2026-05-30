package com.moqim.list.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.moqim.list.data.local.entity.SurfaceConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SurfaceConfigDao {
    @Query("SELECT * FROM surface_configs ORDER BY id DESC")
    fun observeAll(): Flow<List<SurfaceConfigEntity>>

    @Query("SELECT * FROM surface_configs ORDER BY id DESC")
    suspend fun getAll(): List<SurfaceConfigEntity>

    @Query("SELECT * FROM surface_configs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SurfaceConfigEntity?

    @Query("DELETE FROM surface_configs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM surface_configs")
    suspend fun clearAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SurfaceConfigEntity): Long
}
