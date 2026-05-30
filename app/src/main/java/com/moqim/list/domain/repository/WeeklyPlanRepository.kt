package com.moqim.list.domain.repository

import com.moqim.list.domain.model.WeeklyPlanSummary
import kotlinx.coroutines.flow.Flow

interface WeeklyPlanRepository {
    suspend fun seedDefaultsIfNeeded()
    suspend fun addQuickWeeklyPlan()
    suspend fun getWeeklyPlan(planId: Long): WeeklyPlanSummary?
    suspend fun getWeeklyPlanDetail(planId: Long): com.moqim.list.feature.plans.model.WeeklyPlanItemUiModel?
    suspend fun updateWeeklyPlan(
        planId: Long,
        title: String,
        goal: String,
        weekStartDate: String,
        weekEndDate: String,
        monthlyPlanId: Long? = null,
        capacity: Int? = null,
        review: String? = null,
    )
    suspend fun createWeeklyPlan(
        monthlyPlanId: Long,
        title: String,
        goal: String,
        weekStartDate: String,
        weekEndDate: String,
        capacity: Int? = null,
        review: String? = null,
    ): Long
    suspend fun deleteWeeklyPlan(planId: Long)
    suspend fun deleteWeeklyPlanCascade(planId: Long)
    fun observeWeeklyPlans(): Flow<List<WeeklyPlanSummary>>
}
