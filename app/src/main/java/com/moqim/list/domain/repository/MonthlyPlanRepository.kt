package com.moqim.list.domain.repository

import com.moqim.list.domain.model.MonthlyPlanSummary
import kotlinx.coroutines.flow.Flow

interface MonthlyPlanRepository {
    suspend fun seedDefaultsIfNeeded()
    suspend fun addQuickMonthlyPlan()
    suspend fun getMonthlyPlan(planId: Long): MonthlyPlanSummary?
    suspend fun getMonthlyPlanDetail(planId: Long): com.moqim.list.feature.plans.model.MonthlyPlanItemUiModel?
    suspend fun updateMonthlyPlan(
        planId: Long,
        title: String,
        theme: String,
        goal: String,
        startDate: String,
        endDate: String,
    )
    suspend fun createMonthlyPlan(
        title: String,
        theme: String,
        goal: String,
        startDate: String,
        endDate: String,
    ): Long
    suspend fun deleteMonthlyPlan(planId: Long)
    suspend fun deleteMonthlyPlanCascade(planId: Long)
    fun observeMonthlyPlans(): Flow<List<MonthlyPlanSummary>>
}
