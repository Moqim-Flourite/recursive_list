package com.moqim.list.feature.plans.model

data class WeeklyPlanItemUiModel(
    val id: Long,
    val monthlyPlanId: Long,
    val title: String,
    val goal: String?,
    val weekStartDate: String,
    val weekEndDate: String,
    val periodText: String,
    val status: String,
    val capacity: Int? = null,
    val review: String = "",
    val focusSummary: String = "",
    val focusItems: List<PlanningPoolItemUiModel> = emptyList(),
)
