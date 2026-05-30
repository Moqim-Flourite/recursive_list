package com.moqim.list.feature.home.model

data class HabitItemUiModel(
    val templateId: Long,
    val title: String,
    val status: String,
    val estimatedMinutes: Int? = null,
    val dailyTargetCount: Int = 1,
    val completedCount: Int = 0,
    val iconLabel: String = "◉",
    val iconUri: String? = null,
    val targetAppPackageName: String? = null,
    val preferredTimeSegment: String? = null,
    val totalCompletedDays: Int = 0,
    val currentStreakDays: Int = 0,
    val showTotalCompletedDays: Boolean = true,
    val baseCompletedDays: Int = 0,
)
