package com.moqim.list.feature.plans.model

data class MonthlyPlanItemUiModel(
    val id: Long,
    val title: String,
    val theme: String,
    val goal: String?,
    val startDate: String,
    val endDate: String,
    val periodText: String,
    val status: String,
    val review: String = "",
    val progressText: String = "",
    val childWeekCount: Int = 0,
    val taskPoolSummary: String = "",
    val taskPoolItems: List<PlanningPoolItemUiModel> = emptyList(),
)
