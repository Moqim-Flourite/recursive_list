package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habit_records",
    indices = [
        Index(value = ["habit_template_id", "date"], unique = true),
    ],
)
data class HabitRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "habit_template_id")
    val habitTemplateId: Long,
    val date: String,
    val status: String = "TODO",
    @ColumnInfo(name = "completed_at")
    val completedAt: Long? = null,
    val note: String? = null,
    @ColumnInfo(name = "completed_count")
    val completedCount: Int = 0,
)
