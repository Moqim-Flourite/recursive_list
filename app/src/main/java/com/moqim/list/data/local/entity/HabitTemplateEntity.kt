package com.moqim.list.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habit_templates")
data class HabitTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    @ColumnInfo(name = "estimated_minutes")
    val estimatedMinutes: Int? = null,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    @ColumnInfo(name = "frequency_type")
    val frequencyType: String = "DAILY",
    @ColumnInfo(name = "weekdays_mask")
    val weekdaysMask: Int? = null,
    @ColumnInfo(name = "preferred_time_segment")
    val preferredTimeSegment: String? = null,
    @ColumnInfo(name = "target_app_package_name")
    val targetAppPackageName: String? = null,
    @ColumnInfo(name = "icon_uri")
    val iconUri: String? = null,
    @ColumnInfo(name = "icon_label")
    val iconLabel: String? = null,
    @ColumnInfo(name = "daily_target_count")
    val dailyTargetCount: Int = 1,
    @ColumnInfo(name = "streak_enabled")
    val streakEnabled: Boolean = true,
    @ColumnInfo(name = "base_completed_days")
    val baseCompletedDays: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
