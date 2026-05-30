package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_plans")
data class WeeklyPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "monthly_plan_id")
    val monthlyPlanId: Long,
    val title: String,
    val goal: String? = null,
    @ColumnInfo(name = "week_start_date")
    val weekStartDate: String,
    @ColumnInfo(name = "week_end_date")
    val weekEndDate: String,
    val capacity: Int? = null,
    val review: String? = null,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
