package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_plans")
data class DailyPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "weekly_plan_id")
    val weeklyPlanId: Long? = null,
    val date: String,
    val summary: String? = null,
    @ColumnInfo(name = "energy_level")
    val energyLevel: String? = null,
    @ColumnInfo(name = "generated_from_previous_day")
    val generatedFromPreviousDay: Boolean = false,
    val review: String? = null,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
