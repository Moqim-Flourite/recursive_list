package com.moqim.list.feature.plans.model

import com.moqim.list.core.model.TimeSegment

enum class PlanningLayer {
    MONTH,
    WEEK,
    DAY,
}
data class PlanningDateChipUiModel(
    val date: String,
    val dayLabel: String,
    val dayNumber: String,
    val isSelected: Boolean,
    val isToday: Boolean,
)

data class PlanningPoolItemUiModel(
    val title: String,
    val kind: String = "INFO",
    val source: String = "MANUAL",
    val status: String = "ACTIVE",
    val referenceKey: String? = null,
)

data class PlanningTaskUiModel(
    val id: Long,
    val title: String,
    val note: String = "",
    val status: String,
    val estimatedMinutes: Int?,
    val timeSegment: String? = null,
    val specificTime: String? = null,
    val sourceType: String = "MANUAL",
    val isTopFocus: Boolean = false,
)

data class PlanningSegmentUiModel(
    val segment: TimeSegment,
    val summary: String,
    val tasks: List<PlanningTaskUiModel> = emptyList(),
)

data class TemporaryListUiModel(
    val key: String,
    val title: String,
    val caption: String,
    val accent: Long,
    val tasks: List<PlanningTaskUiModel> = emptyList(),
)

data class WeeklyPlanProgressUiModel(
    val weeklyPlanId: Long,
    val linkedDates: List<String> = emptyList(),
    val totalTaskCount: Int = 0,
    val doneTaskCount: Int = 0,
)
