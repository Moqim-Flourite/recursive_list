package com.moqim.list.domain.model

data class DailyPlanSummary(
    val id: Long,
    val weeklyPlanId: Long?,
    val date: String,
    val summary: String?,
    val energyLevel: String?,
    val review: String?,
    val status: String,
)
