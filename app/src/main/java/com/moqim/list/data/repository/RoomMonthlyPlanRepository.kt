package com.moqim.list.data.repository

import com.moqim.list.data.local.dao.DailyPlanDao
import com.moqim.list.data.local.dao.ExecutionTaskDao
import com.moqim.list.data.local.dao.MonthlyPlanDao
import com.moqim.list.data.local.dao.WeeklyPlanDao
import com.moqim.list.data.local.entity.MonthlyPlanEntity
import com.moqim.list.domain.model.MonthlyPlanSummary
import com.moqim.list.domain.repository.MonthlyPlanRepository
import com.moqim.list.feature.plans.model.MonthlyPlanItemUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RoomMonthlyPlanRepository(
    private val monthlyPlanDao: MonthlyPlanDao,
    private val weeklyPlanDao: WeeklyPlanDao? = null,
    private val dailyPlanDao: DailyPlanDao? = null,
    private val executionTaskDao: ExecutionTaskDao? = null,
) : MonthlyPlanRepository {

    override suspend fun seedDefaultsIfNeeded() {
        if (monthlyPlanDao.countAll() > 0) return
        insertMonthlyPlan(
            title = "Recursive List MVP 开发",
            theme = "产品搭建",
            goal = "完成首页、打卡闭环和月计划主线骨架",
        )
    }

    override suspend fun addQuickMonthlyPlan() {
        val count = monthlyPlanDao.countAll() + 1
        insertMonthlyPlan(
            title = "月计划 #$count",
            theme = "阶段推进",
            goal = "明确本月的主线目标与关键结果。",
        )
    }

    override suspend fun getMonthlyPlan(planId: Long): MonthlyPlanSummary? {
        val entity = monthlyPlanDao.getById(planId) ?: return null
        return MonthlyPlanSummary(
            id = entity.id,
            title = entity.title,
            theme = entity.theme,
            goal = entity.goal,
            startDate = entity.startDate,
            endDate = entity.endDate,
            periodText = formatPeriod(entity.startDate, entity.endDate),
            status = entity.status,
        )
    }

    override suspend fun getMonthlyPlanDetail(planId: Long): MonthlyPlanItemUiModel? {
        val entity = monthlyPlanDao.getById(planId) ?: return null
        val goalText = entity.goal.orEmpty()
        val taskPoolSummary = goalText.substringAfter("月任务池：", "").substringBefore("\n").takeIf { "月任务池：" in goalText }.orEmpty()
        val review = goalText.substringAfter("月复盘：", "").takeIf { "月复盘：" in goalText }.orEmpty()
        val childWeeks = weeklyPlanDao?.getByMonthlyPlanId(planId).orEmpty()
        return MonthlyPlanItemUiModel(
            id = entity.id,
            title = entity.title,
            theme = entity.theme,
            goal = entity.goal,
            startDate = entity.startDate,
            endDate = entity.endDate,
            periodText = formatPeriod(entity.startDate, entity.endDate),
            status = entity.status,
            review = review,
            progressText = when {
                childWeeks.isEmpty() -> "本月尚未拆出周计划"
                else -> "已拆解 ${childWeeks.size} 个周计划，继续向日计划推进"
            },
            childWeekCount = childWeeks.size,
            taskPoolSummary = taskPoolSummary,
            taskPoolItems = taskPoolSummary
                .split("；", "、", "\n")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { com.moqim.list.feature.plans.model.PlanningPoolItemUiModel(title = it) },
        )
    }

    override suspend fun updateMonthlyPlan(
        planId: Long,
        title: String,
        theme: String,
        goal: String,
        startDate: String,
        endDate: String,
    ) {
        val entity = monthlyPlanDao.getById(planId) ?: return
        monthlyPlanDao.upsert(
            entity.copy(
                title = title,
                theme = theme,
                goal = goal,
                startDate = startDate,
                endDate = endDate,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun createMonthlyPlan(
        title: String,
        theme: String,
        goal: String,
        startDate: String,
        endDate: String,
    ): Long {
        val now = System.currentTimeMillis()
        return monthlyPlanDao.upsert(
            MonthlyPlanEntity(
                title = title,
                theme = theme,
                goal = goal,
                startDate = startDate,
                endDate = endDate,
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun deleteMonthlyPlan(planId: Long) {
        monthlyPlanDao.deleteById(planId)
    }

    override suspend fun deleteMonthlyPlanCascade(planId: Long) {
        val weeklyDao = weeklyPlanDao ?: run {
            monthlyPlanDao.deleteById(planId)
            return
        }
        val weeks = weeklyDao.getByMonthlyPlanId(planId)
        weeks.forEach { week ->
            val days = dailyPlanDao?.getByWeeklyPlanId(week.id).orEmpty()
            days.forEach { day ->
                executionTaskDao?.deleteByDailyPlanId(day.id)
                dailyPlanDao?.deleteById(day.id)
            }
            executionTaskDao?.deleteByWeeklyPlanId(week.id)
            weeklyDao.deleteById(week.id)
        }
        executionTaskDao?.deleteByMonthlyPlanId(planId)
        monthlyPlanDao.deleteById(planId)
    }

    override fun observeMonthlyPlans(): Flow<List<MonthlyPlanSummary>> {
        return monthlyPlanDao.observeAll().map { entities ->
            entities.map { entity ->
                MonthlyPlanSummary(
                    id = entity.id,
                    title = entity.title,
                    theme = entity.theme,
                    goal = entity.goal,
                    startDate = entity.startDate,
                    endDate = entity.endDate,
                    periodText = formatPeriod(entity.startDate, entity.endDate),
                    status = entity.status,
                )
            }
        }
    }

    private suspend fun insertMonthlyPlan(
        title: String,
        theme: String,
        goal: String,
    ) {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1)
        val end = today.withDayOfMonth(today.lengthOfMonth())
        val now = System.currentTimeMillis()

        monthlyPlanDao.upsert(
            MonthlyPlanEntity(
                title = title,
                theme = theme,
                goal = goal,
                startDate = start.toString(),
                endDate = end.toString(),
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
