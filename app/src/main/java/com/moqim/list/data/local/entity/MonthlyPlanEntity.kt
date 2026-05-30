package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monthly_plans")
data class MonthlyPlanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val theme: String = "",
    val goal: String? = null,
    @ColumnInfo(name = "start_date")
    val startDate: String,
    @ColumnInfo(name = "end_date")
    val endDate: String,
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
