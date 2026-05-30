package com.moqim.list.domain.repository

import com.moqim.list.domain.model.DailyPlanSummary
import kotlinx.coroutines.flow.Flow

interface DailyPlanRepository {
    suspend fun seedTodayIfNeeded()
    suspend fun getDailyPlan(planId: Long): DailyPlanSummary?
    suspend fun createDailyPlan(
        date: String,
        weeklyPlanId: Long?,
        summary: String,
    ): Long
    suspend fun updateDailyPlan(
        planId: Long,
        summary: String,
        energyLevel: String,
        review: String,
    )
    suspend fun updateDailyPlanWeeklyLink(planId: Long, weeklyPlanId: Long?)
    suspend fun deleteDailyPlan(planId: Long)
    suspend fun deleteDailyPlanCascade(planId: Long)
    fun observeTodayPlan(date: String): Flow<DailyPlanSummary?>
}
