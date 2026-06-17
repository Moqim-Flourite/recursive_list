package com.moqim.list.data.repository

import com.moqim.list.data.local.dao.DailyPlanDao
import com.moqim.list.data.local.dao.WeeklyPlanDao
import com.moqim.list.data.local.database.AppDatabase
import com.moqim.list.data.local.entity.DailyPlanEntity
import com.moqim.list.domain.model.DailyPlanSummary
import com.moqim.list.domain.repository.DailyPlanRepository
import com.moqim.list.data.local.entity.WeeklyPlanEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class RoomDailyPlanRepository(
    private val dailyPlanDao: DailyPlanDao,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val executionTaskDao: com.moqim.list.data.local.dao.ExecutionTaskDao,
    private val db: AppDatabase? = null,
) : DailyPlanRepository {

    override suspend fun seedTodayIfNeeded() {
        // seed 逻辑已移除
        return

        @Suppress("UNREACHABLE_CODE")
        val today = LocalDate.now()
        val todayKey = today.toString()
        if (dailyPlanDao.countByDate(todayKey) > 0) return

        val activeWeeklyPlan = weeklyPlanDao.getActiveWeeklyPlan(todayKey)
        val fallbackWeeklyPlan = weeklyPlanDao.observeAll().first().firstOrNull()
        val selectedWeeklyPlan = activeWeeklyPlan ?: fallbackWeeklyPlan
        val now = System.currentTimeMillis()

        val summary = selectedWeeklyPlan?.let { weeklyPlan ->
            buildDailySummary(
                date = today,
                weeklyPlan = weeklyPlan,
            )
        } ?: buildFallbackDailySummary(today)

        dailyPlanDao.upsert(
            DailyPlanEntity(
                weeklyPlanId = selectedWeeklyPlan?.id,
                date = todayKey,
                summary = summary,
                energyLevel = inferEnergyLevel(today),
                generatedFromPreviousDay = false,
                review = null,
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun getDailyPlan(planId: Long): DailyPlanSummary? {
        val entity = dailyPlanDao.getById(planId) ?: return null
        return DailyPlanSummary(
            id = entity.id,
            weeklyPlanId = entity.weeklyPlanId,
            date = entity.date,
            summary = entity.summary,
            energyLevel = entity.energyLevel,
            review = entity.review,
            status = entity.status,
        )
    }

    override suspend fun createDailyPlan(
        date: String,
        weeklyPlanId: Long?,
        summary: String,
    ): Long {
        val now = System.currentTimeMillis()
        return dailyPlanDao.upsert(
            DailyPlanEntity(
                weeklyPlanId = weeklyPlanId,
                date = date,
                summary = summary,
                energyLevel = "MEDIUM",
                generatedFromPreviousDay = false,
                review = "",
                status = "ACTIVE",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun updateDailyPlan(
        planId: Long,
        summary: String,
        energyLevel: String,
        review: String,
    ) {
        val entity = dailyPlanDao.getById(planId) ?: return
        dailyPlanDao.upsert(
            entity.copy(
                summary = summary,
                energyLevel = energyLevel,
                review = review,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun updateDailyPlanWeeklyLink(planId: Long, weeklyPlanId: Long?) {
        val entity = dailyPlanDao.getById(planId) ?: return
        dailyPlanDao.upsert(
            entity.copy(
                weeklyPlanId = weeklyPlanId,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteDailyPlan(planId: Long) {
        dailyPlanDao.deleteById(planId)
    }

    override suspend fun deleteDailyPlanCascade(planId: Long) {
        val execute: suspend () -> Unit = {
            executionTaskDao.deleteByDailyPlanId(planId)
            dailyPlanDao.deleteById(planId)
        }
        if (db != null) {
            db.withTransaction { execute() }
        } else {
            execute()
        }
    }

    override fun observeTodayPlan(date: String): Flow<DailyPlanSummary?> {
        return dailyPlanDao.observeByDate(date).map { entity ->
            entity?.let {
                DailyPlanSummary(
                    id = it.id,
                    weeklyPlanId = it.weeklyPlanId,
                    date = it.date,
                    summary = it.summary,
                    energyLevel = it.energyLevel,
                    review = it.review,
                    status = it.status,
                )
            }
        }
    }

    private fun buildDailySummary(
        date: LocalDate,
        weeklyPlan: WeeklyPlanEntity,
    ): String {
        val weeklyGoal = weeklyPlan.goal?.trim().orEmpty()
        val stageText = when (resolveWeekStage(date, weeklyPlan)) {
            WeekStage.START -> "今天适合先完成启动、拆解和主线对齐"
            WeekStage.MIDDLE -> "今天适合持续推进主任务，并完成一个明确里程碑"
            WeekStage.END -> "今天适合收口交付、查漏补缺，并为下一周做整理"
        }

        val focusText = if (weeklyGoal.isNotBlank()) {
            "重点围绕「$weeklyGoal」推进。"
        } else {
            "重点围绕本周主线做实质推进。"
        }

        val rhythmText = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "优先把本周任务拆到今天，先拿下最重要的一块。"
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> "适合安排连续深度工作，避免目标分散。"
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> "适合推进输出、沟通同步和结果收口。"
            DayOfWeek.SATURDAY -> "适合补齐遗漏事项，并保留轻量调整空间。"
            DayOfWeek.SUNDAY -> "适合做本周复盘、收尾和下周预热。"
        }

        return buildString {
            append("本周主线：${weeklyPlan.title}。")
            append(stageText)
            append("。")
            append(focusText)
            append(rhythmText)
        }
    }

    private fun buildFallbackDailySummary(date: LocalDate): String {
        val weekdayText = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "先完成本周主线梳理，并确定今天最重要的推进事项。"
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> "优先保障一段连续专注时间，推进核心任务。"
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> "适合把任务做出可见结果，并同步处理收尾事项。"
            DayOfWeek.SATURDAY -> "适合补缺、整理和处理轻量推进事项。"
            DayOfWeek.SUNDAY -> "适合复盘本周并为下一周做准备。"
        }
        return "今天的主线是稳步推进当前计划系统迭代。$weekdayText"
    }

    private fun inferEnergyLevel(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> "HIGH"
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY -> "MEDIUM"
        DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> "LOW"
    }

    private fun resolveWeekStage(
        date: LocalDate,
        weeklyPlan: WeeklyPlanEntity,
    ): WeekStage {
        val start = runCatching { LocalDate.parse(weeklyPlan.weekStartDate) }.getOrDefault(date)
        val end = runCatching { LocalDate.parse(weeklyPlan.weekEndDate) }.getOrDefault(date)
        val totalDays = ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(0) + 1
        val passedDays = ChronoUnit.DAYS.between(start, date).toInt().coerceAtLeast(0) + 1
        val progress = passedDays.toFloat() / totalDays.toFloat()

        return when {
            progress <= 0.34f -> WeekStage.START
            progress <= 0.75f -> WeekStage.MIDDLE
            else -> WeekStage.END
        }
    }

    private enum class WeekStage {
        START,
        MIDDLE,
        END,
    }
}
