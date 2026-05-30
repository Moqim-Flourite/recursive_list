package com.moqim.list.domain.model

data class MonthlyPlanSummary(
    val id: Long,
    val title: String,
    val theme: String,
    val goal: String?,
    val startDate: String,
    val endDate: String,
    val periodText: String,
    val status: String,
)
