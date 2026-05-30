package com.moqim.list.domain.model

data class HabitSummary(
    val totalCount: Int,
    val completedCount: Int,
    val summaryText: String,
    val items: List<HabitSummaryItem> = emptyList(),
)

data class HabitSummaryItem(
    val templateId: Long,
    val title: String,
    val status: String,
    val estimatedMinutes: Int? = null,
    val iconLabel: String? = null,
    val iconUri: String? = null,
    val targetAppPackageName: String? = null,
    val dailyTargetCount: Int = 1,
    val completedCount: Int = 0,
    val preferredTimeSegment: String? = null,
    val totalCompletedDays: Int = 0,
    val currentStreakDays: Int = 0,
    val showTotalCompletedDays: Boolean = true,
    val baseCompletedDays: Int = 0,
)
