package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "execution_tasks",
    indices = [
        Index(value = ["daily_plan_id"]),
        Index(value = ["weekly_plan_id"]),
        Index(value = ["monthly_plan_id"]),
        Index(value = ["status"]),
        Index(value = ["time_segment"]),
    ],
)
data class ExecutionTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val type: String = "PLAN_EXECUTION",
    val status: String = "TODO",
    val priority: String = "MEDIUM",
    @ColumnInfo(name = "due_at")
    val dueAt: Long? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    @ColumnInfo(name = "monthly_plan_id")
    val monthlyPlanId: Long? = null,
    @ColumnInfo(name = "weekly_plan_id")
    val weeklyPlanId: Long? = null,
    @ColumnInfo(name = "daily_plan_id")
    val dailyPlanId: Long? = null,
    @ColumnInfo(name = "source_type")
    val sourceType: String = "MANUAL",
    @ColumnInfo(name = "source_task_id")
    val sourceTaskId: Long? = null,
    @ColumnInfo(name = "time_segment")
    val timeSegment: String? = null,
    @ColumnInfo(name = "specific_time")
    val specificTime: String? = null,
    @ColumnInfo(name = "is_top_focus")
    val isTopFocus: Boolean = false,
    @ColumnInfo(name = "estimated_minutes")
    val estimatedMinutes: Int? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
