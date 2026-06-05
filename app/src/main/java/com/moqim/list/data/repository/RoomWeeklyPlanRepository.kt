package com.moqim.list.data.repository

import com.moqim.list.data.local.dao.DailyPlanDao
import com.moqim.list.data.local.dao.ExecutionTaskDao
import com.moqim.list.data.local.dao.MonthlyPlanDao
import com.moqim.list.data.local.dao.WeeklyPlanDao
import com.moqim.list.data.local.database.AppDatabase
import com.moqim.list.data.local.entity.WeeklyPlanEntity
import com.moqim.list.domain.model.WeeklyPlanSummary
import com.moqim.list.domain.repository.WeeklyPlanRepository
import com.moqim.list.feature.plans.model.WeeklyPlanItemUiModel
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RoomWeeklyPlanRepository(
    private val weeklyPlanDao: WeeklyPlanDao,
    private val monthlyPlanDao: MonthlyPlanDao,
    private val dailyPlanDao: DailyPlanDao? = null,
    private val executionTaskDao: ExecutionTaskDao? = null,
    private val db: AppDatabase? = null,
) : WeeklyPlanRepository {

    override suspend fun seedDefaultsIfNeeded() {
        if (weeklyPlanDao.countAll() > 0) return

        val monthlyPlans = monthlyPlanDao.observeAll().first()
        val firstMonthlyPlan = monthlyPlans.firstOrNull() ?: return

        insertWeeklyPlan(
            monthlyPlanId = firstMonthlyPlan.id,
            title = "第一周推进",
            goal = "完成首页、打卡闭环与计划页联动。",
        )
    }

    override suspend fun addQuickWeeklyPlan() {
        val monthlyPlans = monthlyPlanDao.observeAll().first()
        val firstMonthlyPlan = monthlyPlans.firstOrNull() ?: return
        val count = weeklyPlanDao.countAll() + 1

        insertWeeklyPlan(
            monthlyPlanId = firstMonthlyPlan.id,
            title = "周计划 #$count",
            goal = "明确本周要推进的重点事项与阶段目标。",
        )
    }

    override suspend fun getWeeklyPlan(planId: Long): WeeklyPlanSummary? {
        val entity = weeklyPlanDao.getById(planId) ?: return null
        return WeeklyPlanSummary(
            id = entity.id,
            monthlyPlanId = entity.monthlyPlanId,
            title = entity.title,
            goal = entity.goal,
            weekStartDate = entity.weekStartDate,
            weekEndDate = entity.weekEndDate,
            periodText = formatPeriod(entity.weekStartDate, entity.weekEndDate),
            status = entity.status,
        )
    }

    override suspend fun getWeeklyPlanDetail(planId: Long): WeeklyPlanItemUiModel? {
        val entity = weeklyPlanDao.getById(planId) ?: return null
        val goalText = entity.goal.orEmpty()
        val focusSummary = goalText.substringAfter("重点任务池：", "").takeIf { "重点任务池：" in goalText }.orEmpty()
        return WeeklyPlanItemUiModel(
            id = entity.id,
            monthlyPlanId = entity.monthlyPlanId,
            title = entity.title,
            goal = entity.goal,
            weekStartDate = entity.weekStartDate,
            weekEndDate = entity.weekEndDate,
            periodText = formatPeriod(entity.weekStartDate, entity.weekEndDate),
            status = entity.status,
            capacity = entity.capacity,
            review = entity.review.orEmpty(),
            focusSummary = focusSummary,
            focusItems = focusSummary
                .split("；", "、", "\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { com.moqim.list.feature.plans.model.PlanningPoolItemUiModel(title = it) },
        )
    }

    override suspend fun updateWeeklyPlan(
        planId: Long,
        title: String,
        goal: String,
        weekStartDate: String,
        weekEndDate: String,
        monthlyPlanId: Long?,
        capacity: Int?,
        review: String?,
    ) {
        val entity = weeklyPlanDao.getById(planId) ?: return
        weeklyPlanDao.upsert(
            entity.copy(
                monthlyPlanId = monthlyPlanId ?: entity.monthlyPlanId,
                title = title,
                goal = goal,
                weekStartDate = weekStartDate,
                weekEndDate = weekEndDate,
                capacity = capacity,
                review = review,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun createWeeklyPlan(
        monthlyPlanId: Long,
        title: String,
        goal: String,
        weekStartDate: String,
        weekEndDate: String,
        capacity: Int?,
        review: String?,
    ): Long {
        val now = System.currentTimeMillis()
        return weeklyPlanDao.upsert(
            WeeklyPlanEntity(
                monthlyPlanId = monthlyPlanId,
                title = title,
                goal = goal,
                weekStartDate = weekStartDate,
                weekEndDate = weekEndDate,
                capacity = capacity,
                review = review,
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun deleteWeeklyPlan(planId: Long) {
        weeklyPlanDao.deleteById(planId)
    }

    override suspend fun deleteWeeklyPlanCascade(planId: Long) {
        val execute: suspend () -> Unit = {
            executionTaskDao?.deleteByWeeklyPlanId(planId)
            dailyPlanDao?.deleteByWeeklyPlanId(planId)
            weeklyPlanDao.deleteById(planId)
        }
        if (db != null) {
            db.withTransaction { execute() }
        } else {
            execute()
        }
    }

    override fun observeWeeklyPlans(): Flow<List<WeeklyPlanSummary>> {
        return weeklyPlanDao.observeAll().map { entities ->
            entities.map { entity ->
                WeeklyPlanSummary(
                    id = entity.id,
                    monthlyPlanId = entity.monthlyPlanId,
                    title = entity.title,
                    goal = entity.goal,
                    weekStartDate = entity.weekStartDate,
                    weekEndDate = entity.weekEndDate,
                    periodText = formatPeriod(entity.weekStartDate, entity.weekEndDate),
                    status = entity.status,
                )
            }
        }
    }

    private suspend fun insertWeeklyPlan(
        monthlyPlanId: Long,
        title: String,
        goal: String,
    ) {
        val today = LocalDate.now()
        val start = today.with(DayOfWeek.MONDAY)
        val end = today.with(DayOfWeek.SUNDAY)
        val now = System.currentTimeMillis()

        weeklyPlanDao.upsert(
            WeeklyPlanEntity(
                monthlyPlanId = monthlyPlanId,
                title = title,
                goal = goal,
                weekStartDate = start.toString(),
                weekEndDate = end.toString(),
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private fun formatPeriod(startDate: String, endDate: String): String {
        return try {
            val start = LocalDate.parse(startDate)
            val end = LocalDate.parse(endDate)
            val formatter = DateTimeFormatter.ofPattern("M月d日")
            "${start.format(formatter)} - ${end.format(formatter)}"
        } catch (_: Exception) {
            "$startDate - $endDate"
        }
    }
}
