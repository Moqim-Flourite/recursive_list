package com.moqim.list.data.importplan

import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.local.entity.ExecutionTaskEntity
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomMonthlyPlanRepository
import com.moqim.list.data.repository.RoomWeeklyPlanRepository
import kotlinx.coroutines.flow.first

class PlanImportService {
    suspend fun import(json: String, appContext: android.content.Context): PlanImportResult {
        val payload = PlanImportParser.parse(json)
        require(payload.mode in SUPPORTED_MODES) { "当前仅支持模式：${SUPPORTED_MODES.joinToString()}" }

        val db = DatabaseProvider.get(appContext)
        val monthlyRepository = RoomMonthlyPlanRepository(
            monthlyPlanDao = db.monthlyPlanDao(),
            weeklyPlanDao = db.weeklyPlanDao(),
            dailyPlanDao = db.dailyPlanDao(),
            executionTaskDao = db.executionTaskDao(),
        )
        val weeklyRepository = RoomWeeklyPlanRepository(
            weeklyPlanDao = db.weeklyPlanDao(),
            monthlyPlanDao = db.monthlyPlanDao(),
            dailyPlanDao = db.dailyPlanDao(),
            executionTaskDao = db.executionTaskDao(),
        )
        val dailyRepository = RoomDailyPlanRepository(
            dailyPlanDao = db.dailyPlanDao(),
            weeklyPlanDao = db.weeklyPlanDao(),
            executionTaskDao = db.executionTaskDao(),
        )

        val existingMonthlyPlan = payload.monthlyPlan?.let { monthly ->
            db.monthlyPlanDao().getAll().firstOrNull {
                it.startDate == monthly.startDate && it.endDate == monthly.endDate
            }
        }
        val targetMonthlyPlanId = payload.monthlyPlan?.let { monthly ->
            existingMonthlyPlan?.id?.also { planId ->
                monthlyRepository.updateMonthlyPlan(
                    planId = planId,
                    title = monthly.title,
                    theme = monthly.theme,
                    goal = monthly.goal,
                    startDate = monthly.startDate,
                    endDate = monthly.endDate,
                )
            } ?: monthlyRepository.createMonthlyPlan(
                title = monthly.title,
                theme = monthly.theme,
                goal = monthly.goal,
                startDate = monthly.startDate,
                endDate = monthly.endDate,
            )
        }

        val existingWeeklyPlan = payload.weeklyPlan?.let { weekly ->
            val candidateMonthlyPlanId = targetMonthlyPlanId
            db.weeklyPlanDao().getAll().firstOrNull {
                it.weekStartDate == weekly.weekStartDate &&
                    it.weekEndDate == weekly.weekEndDate &&
                    (candidateMonthlyPlanId == null || it.monthlyPlanId == candidateMonthlyPlanId)
            }
        }
        val targetWeeklyPlanId = payload.weeklyPlan?.let { weekly ->
            val monthlyPlanId = targetMonthlyPlanId
                ?: existingWeeklyPlan?.monthlyPlanId
                ?: db.monthlyPlanDao().observeAll()
                    .first()
                    .firstOrNull()
                    ?.id
                ?: throw IllegalArgumentException("导入周计划前需要已有月计划，或同时传 monthlyPlan")

            if (payload.mode == MODE_REPLACE_WEEK && existingWeeklyPlan != null) {
                weeklyRepository.deleteWeeklyPlanCascade(existingWeeklyPlan.id)
            }

            existingWeeklyPlan?.takeUnless { payload.mode == MODE_REPLACE_WEEK }?.id?.also { planId ->
                weeklyRepository.updateWeeklyPlan(
                    planId = planId,
                    title = weekly.title,
                    goal = weekly.goal,
                    weekStartDate = weekly.weekStartDate,
                    weekEndDate = weekly.weekEndDate,
                    monthlyPlanId = monthlyPlanId,
                    capacity = weekly.capacity,
                    review = weekly.review.ifBlank { null },
                )
            } ?: weeklyRepository.createWeeklyPlan(
                monthlyPlanId = monthlyPlanId,
                title = weekly.title,
                goal = weekly.goal,
                weekStartDate = weekly.weekStartDate,
                weekEndDate = weekly.weekEndDate,
                capacity = weekly.capacity,
                review = weekly.review.ifBlank { null },
            )
        }

        val existingDailyPlan = payload.dailyPlan?.let { daily ->
            when {
                targetWeeklyPlanId != null -> db.dailyPlanDao()
                    .getByWeeklyPlanIdAndDate(targetWeeklyPlanId, daily.date)
                else -> db.dailyPlanDao().observeByDate(daily.date).first()
            }
        }

        val targetDailyPlanId = payload.dailyPlan?.let { daily ->
            if (payload.mode == MODE_REPLACE_DAY && existingDailyPlan != null) {
                dailyRepository.deleteDailyPlanCascade(existingDailyPlan.id)
            }

            val reusedPlanId = existingDailyPlan?.takeUnless { payload.mode == MODE_REPLACE_DAY }?.id
            val dailyPlanId = reusedPlanId ?: dailyRepository.createDailyPlan(
                date = daily.date,
                weeklyPlanId = targetWeeklyPlanId,
                summary = daily.summary,
            )
            val mergedSummary = mergeDailySummary(
                existingSummary = existingDailyPlan?.takeUnless { payload.mode == MODE_REPLACE_DAY }?.summary,
                importedSummary = daily.summary,
                summaryMode = daily.summaryMode,
            )
            dailyRepository.updateDailyPlan(
                planId = dailyPlanId,
                summary = mergedSummary,
                energyLevel = daily.energyLevel,
                review = daily.review,
            )
            if (targetWeeklyPlanId != null) {
                dailyRepository.updateDailyPlanWeeklyLink(dailyPlanId, targetWeeklyPlanId)
            }
            dailyPlanId
        }

        val targetDailyPlan = targetDailyPlanId?.let { db.dailyPlanDao().getById(it) }
        val now = System.currentTimeMillis()
        val existingDailyTasks = targetDailyPlanId?.let { db.executionTaskDao().getByDailyPlanId(it) }.orEmpty()
        var createdTaskCount = 0
        var mergedTaskCount = 0
        payload.tasks.forEachIndexed { index, task ->
            val normalizedSegment = task.timeSegment
                ?.takeIf { segment -> TimeSegment.entries.any { it.name == segment } }
            val duplicate = existingDailyTasks.firstOrNull { existing ->
                existing.title.trim() == task.title.trim() &&
                    (existing.timeSegment ?: "") == (normalizedSegment ?: "") &&
                    existing.status != "DONE"
            }
            if (duplicate == null) {
                db.executionTaskDao().upsert(
                    ExecutionTaskEntity(
                        title = task.title,
                        note = task.note.ifBlank { null },
                        status = "TODO",
                        priority = if (task.isTopFocus) "HIGH" else "MEDIUM",
                        sortOrder = existingDailyTasks.size + index,
                        monthlyPlanId = targetMonthlyPlanId,
                        weeklyPlanId = targetWeeklyPlanId ?: targetDailyPlan?.weeklyPlanId,
                        dailyPlanId = targetDailyPlanId,
                        sourceType = "OPERIT_IMPORT",
                        timeSegment = normalizedSegment,
                        isTopFocus = task.isTopFocus,
                        estimatedMinutes = task.estimatedMinutes,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                createdTaskCount += 1
            } else {
                mergedTaskCount += 1
            }
        }

        val existingWeekPoolTasks = db.executionTaskDao().observeBySourceType("WEEK_POOL").first()
        var createdWeekPoolTaskCount = 0
        var mergedWeekPoolTaskCount = 0
        payload.weekPoolTasks.forEachIndexed { index, task ->
            val duplicate = existingWeekPoolTasks.firstOrNull { existing ->
                existing.title.trim() == task.title.trim() && existing.status != "DONE"
            }
            if (duplicate == null) {
                db.executionTaskDao().upsert(
                    ExecutionTaskEntity(
                        title = task.title,
                        note = task.note.ifBlank { null },
                        status = "TODO",
                        priority = if (task.isTopFocus) "HIGH" else "MEDIUM",
                        sortOrder = existingWeekPoolTasks.size + index,
                        monthlyPlanId = targetMonthlyPlanId,
                        weeklyPlanId = targetWeeklyPlanId,
                        dailyPlanId = null,
                        sourceType = "WEEK_POOL",
                        timeSegment = null,
                        isTopFocus = task.isTopFocus,
                        estimatedMinutes = task.estimatedMinutes,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                createdWeekPoolTaskCount += 1
            } else {
                mergedWeekPoolTaskCount += 1
            }
        }

        return PlanImportResult(
            source = payload.source,
            mode = payload.mode,
            monthlyPlanCreated = targetMonthlyPlanId != null && existingMonthlyPlan == null,
            weeklyPlanCreated = targetWeeklyPlanId != null && (existingWeeklyPlan == null || payload.mode == MODE_REPLACE_WEEK),
            dailyPlanCreated = targetDailyPlanId != null && (existingDailyPlan == null || payload.mode == MODE_REPLACE_DAY),
            dailyPlanMerged = existingDailyPlan != null && payload.mode == MODE_MERGE,
            createdTaskCount = createdTaskCount,
            mergedTaskCount = mergedTaskCount,
            createdWeekPoolTaskCount = createdWeekPoolTaskCount,
            mergedWeekPoolTaskCount = mergedWeekPoolTaskCount,
        )
    }

    companion object {
        const val MODE_MERGE = "merge"
        const val MODE_REPLACE_DAY = "replace_day"
        const val MODE_REPLACE_WEEK = "replace_week"

        val SUPPORTED_MODES = setOf(
            MODE_MERGE,
            MODE_REPLACE_DAY,
            MODE_REPLACE_WEEK,
        )
    }
}

data class PlanImportResult(
    val source: String,
    val mode: String,
    val monthlyPlanCreated: Boolean,
    val weeklyPlanCreated: Boolean,
    val dailyPlanCreated: Boolean,
    val dailyPlanMerged: Boolean,
    val createdTaskCount: Int,
    val mergedTaskCount: Int,
    val createdWeekPoolTaskCount: Int,
    val mergedWeekPoolTaskCount: Int,
)

private fun mergeDailySummary(
    existingSummary: String?,
    importedSummary: String,
    summaryMode: String,
): String {
    val existing = existingSummary?.trim().orEmpty()
    val incoming = importedSummary.trim()
    return when (summaryMode) {
        "keep_existing" -> if (existing.isNotBlank()) existing else incoming
        "append" -> when {
            existing.isBlank() -> incoming
            incoming.isBlank() -> existing
            existing.contains(incoming) -> existing
            else -> "$existing\n—— Operit 补充 ——\n$incoming"
        }
        else -> incoming
    }
}