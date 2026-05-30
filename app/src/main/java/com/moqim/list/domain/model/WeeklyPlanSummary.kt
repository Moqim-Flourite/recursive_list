package com.moqim.list.domain.model

data class WeeklyPlanSummary(
    val id: Long,
    val monthlyPlanId: Long,
    val title: String,
    val goal: String?,
    val weekStartDate: String,
    val weekEndDate: String,
    val periodText: String,
    val status: String,
)
