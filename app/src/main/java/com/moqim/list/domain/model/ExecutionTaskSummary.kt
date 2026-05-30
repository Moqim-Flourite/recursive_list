package com.moqim.list.domain.model

data class ExecutionTaskSummary(
    val id: Long,
    val dailyPlanId: Long?,
    val title: String,
    val note: String?,
    val status: String,
    val timeSegment: String?,
    val specificTime: String?,
    val isTopFocus: Boolean,
    val estimatedMinutes: Int?,
)
