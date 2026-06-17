package com.moqim.list.data.repository

import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.dao.DailyPlanDao
import com.moqim.list.data.local.dao.ExecutionTaskDao
import com.moqim.list.data.local.entity.ExecutionTaskEntity
import com.moqim.list.domain.model.ExecutionTaskSummary
import com.moqim.list.domain.repository.ExecutionTaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomExecutionTaskRepository(
    private val dailyPlanDao: DailyPlanDao,
    private val executionTaskDao: ExecutionTaskDao,
) : ExecutionTaskRepository {

    companion object {
        /** 已经 seed 过的日期，防止用户删光任务后被重新生成 */
        private val seededTodayDates = mutableSetOf<String>()
    }

    override suspend fun seedForTodayIfNeeded() {
        // seed 逻辑已移除
        return

        @Suppress("UNREACHABLE_CODE")
        val today = LocalDate.now().toString()
        if (today in seededTodayDates) return
        val dailyPlan = dailyPlanDao.observeByDate(today).first() ?: return
        if (executionTaskDao.countByDailyPlanId(dailyPlan.id) > 0) {
            seededTodayDates.add(today)
            return
        }

        val now = System.currentTimeMillis()
        val yesterday = LocalDate.now().minusDays(1).toString()
        val yesterdayPlan = dailyPlanDao.observeByDate(yesterday).first()
        val rolloverTasks = yesterdayPlan
            ?.let { executionTaskDao.getByDailyPlanId(it.id) }
            .orEmpty()
            .filter { it.status != "DONE" }
            .take(2)

        val generated = mutableListOf<ExecutionTaskEntity>()

        if (dailyPlan.weeklyPlanId != null) {
            val weeklyGoalHint = dailyPlan.summary
                ?.substringAfter("本周主线：", "")
                ?.substringBefore("。")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: dailyPlan.summary
                    ?.substringAfter("承接周计划：", "")
                    ?.substringBefore("\n")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: "当前周计划"
            generated += ExecutionTaskEntity(
                title = "推进${weeklyGoalHint}主线",
                note = "优先推进「$weeklyGoalHint」最重要的一步。",
                status = "TODO",
                priority = "HIGH",
                sortOrder = generated.size,
                dailyPlanId = dailyPlan.id,
                weeklyPlanId = dailyPlan.weeklyPlanId,
                sourceType = "WEEKLY_PLAN",
                timeSegment = TimeSegment.MORNING.name,
                specificTime = null,
                isTopFocus = true,
                estimatedMinutes = 45,
                createdAt = now,
                updatedAt = now,
            )
            generated += ExecutionTaskEntity(
                title = "拆解${weeklyGoalHint}到今日",
                note = "把「$weeklyGoalHint」拆成今天可完成的动作。",
                status = "TODO",
                priority = "HIGH",
                sortOrder = generated.size,
                dailyPlanId = dailyPlan.id,
                weeklyPlanId = dailyPlan.weeklyPlanId,
                sourceType = "WEEKLY_PLAN",
                timeSegment = TimeSegment.AFTERNOON.name,
                specificTime = null,
                isTopFocus = true,
                estimatedMinutes = 35,
                createdAt = now,
                updatedAt = now,
            )
        }

        rolloverTasks.forEachIndexed { index, task ->
            generated += ExecutionTaskEntity(
                title = task.title,
                note = buildString {
                    append("昨日未完成滚动")
                    task.note?.takeIf { it.isNotBlank() }?.let { append("：$it") }
                },
                status = "TODO",
                priority = task.priority,
                sortOrder = generated.size,
                monthlyPlanId = task.monthlyPlanId,
                weeklyPlanId = dailyPlan.weeklyPlanId ?: task.weeklyPlanId,
                dailyPlanId = dailyPlan.id,
                sourceType = "DAILY_ROLLOVER",
                timeSegment = when (index) {
                    0 -> TimeSegment.NOON.name
                    else -> TimeSegment.EVENING.name
                },
                specificTime = null,
                isTopFocus = false,
                estimatedMinutes = task.estimatedMinutes ?: 20,
                createdAt = now,
                updatedAt = now,
            )
        }

        if (generated.isEmpty()) {
            generated += ExecutionTaskEntity(
                title = "梳理今天执行主线",
                note = "先明确今天最重要的一件事。",
                status = "TODO",
                priority = "HIGH",
                sortOrder = 0,
                dailyPlanId = dailyPlan.id,
                weeklyPlanId = dailyPlan.weeklyPlanId,
                sourceType = "MANUAL",
                timeSegment = TimeSegment.MORNING_START.name,
                specificTime = null,
                isTopFocus = true,
                estimatedMinutes = 20,
                createdAt = now,
                updatedAt = now,
            )
            generated += ExecutionTaskEntity(
                title = "安排一个可交付推进块",
                note = "确保今天至少有一个清晰结果。",
                status = "TODO",
                priority = "MEDIUM",
                sortOrder = 1,
                dailyPlanId = dailyPlan.id,
                weeklyPlanId = dailyPlan.weeklyPlanId,
                sourceType = "MANUAL",
                timeSegment = TimeSegment.AFTERNOON.name,
                specificTime = null,
                isTopFocus = false,
                estimatedMinutes = 40,
                createdAt = now,
                updatedAt = now,
            )
        }

        generated.forEach { executionTaskDao.upsert(it) }
        seededTodayDates.add(today)
    }

    override suspend fun addQuickTask() {
        val today = LocalDate.now().toString()
        val dailyPlan = dailyPlanDao.observeByDate(today).first() ?: return
        val existingCount = executionTaskDao.observeByDailyPlanId(dailyPlan.id).first().size
        val now = System.currentTimeMillis()

        executionTaskDao.upsert(
            ExecutionTaskEntity(
                title = "执行任务 #${existingCount + 1}",
                note = "新建一条待执行任务，可继续补充备注与时段安排。",
                status = "TODO",
                priority = "MEDIUM",
                sortOrder = existingCount + 1,
                dailyPlanId = dailyPlan.id,
                weeklyPlanId = dailyPlan.weeklyPlanId,
                sourceType = "MANUAL",
                timeSegment = "EVENING",
                specificTime = null,
                isTopFocus = false,
                estimatedMinutes = 15,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun addTaskForDate(
        date: String,
        title: String,
        note: String,
        estimatedMinutes: Int?,
        timeSegment: String?,
        specificTime: String?,
    ) {
        var dailyPlan = dailyPlanDao.observeByDate(date).first()
        if (dailyPlan == null) {
            val now = System.currentTimeMillis()
            val newPlanId = dailyPlanDao.upsert(
                com.moqim.list.data.local.entity.DailyPlanEntity(
                    date = date,
                    summary = "从计划页创建的日计划",
                    energyLevel = "MEDIUM",
                    generatedFromPreviousDay = false,
                    review = null,
                    status = "ACTIVE",
                    createdAt = now,
                    updatedAt = now,
                )
            )
            dailyPlan = dailyPlanDao.getById(newPlanId)
        }
        val targetDailyPlan = dailyPlan ?: return
        val existingCount = executionTaskDao.observeByDailyPlanId(targetDailyPlan.id).first().size
        val now = System.currentTimeMillis()
        executionTaskDao.upsert(
            ExecutionTaskEntity(
                title = title.ifBlank { "新任务" },
                note = note.ifBlank { null },
                status = "TODO",
                priority = "MEDIUM",
                sortOrder = existingCount + 1,
                dailyPlanId = targetDailyPlan.id,
                weeklyPlanId = targetDailyPlan.weeklyPlanId,
                sourceType = "MANUAL",
                timeSegment = timeSegment ?: TimeSegment.MORNING.name,
                specificTime = specificTime,
                isTopFocus = false,
                estimatedMinutes = estimatedMinutes,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun quickEditTask(taskId: Long) {
        val task = executionTaskDao.getById(taskId) ?: return
        val editedTitle = if (task.title.contains("（已调）")) {
            task.title
        } else {
            "${task.title}（已调）"
        }
        val nextMinutes = when (task.estimatedMinutes) {
            null -> 15
            15 -> 25
            25 -> 45
            45 -> 60
            else -> 15
        }

        executionTaskDao.upsert(
            task.copy(
                title = editedTitle,
                estimatedMinutes = nextMinutes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun getTask(taskId: Long): ExecutionTaskSummary? {
        val task = executionTaskDao.getById(taskId) ?: return null
        return ExecutionTaskSummary(
            id = task.id,
            dailyPlanId = task.dailyPlanId,
            title = task.title,
            note = task.note,
            status = task.status,
            timeSegment = task.timeSegment,
            specificTime = task.specificTime,
            isTopFocus = task.isTopFocus,
            estimatedMinutes = task.estimatedMinutes,
        )
    }

    override suspend fun updateTask(
        taskId: Long,
        title: String,
        note: String,
        estimatedMinutes: Int?,
        timeSegment: String?,
        specificTime: String?,
    ) {
        val task = executionTaskDao.getById(taskId) ?: return
        executionTaskDao.upsert(
            task.copy(
                title = title,
                note = note,
                estimatedMinutes = estimatedMinutes,
                timeSegment = timeSegment,
                specificTime = specificTime,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun toggleTaskStatus(taskId: Long) {
        val task = executionTaskDao.getById(taskId) ?: return
        val nextStatus = if (task.status == "DONE") "TODO" else "DONE"
        executionTaskDao.upsert(
            task.copy(
                status = nextStatus,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun moveTaskToNextSegment(taskId: Long) {
        val task = executionTaskDao.getById(taskId) ?: return
        val current = task.timeSegment
            ?.let { value -> TimeSegment.entries.firstOrNull { it.name == value } }
            ?: TimeSegment.MORNING_START
        val next = when (current) {
            TimeSegment.MORNING_START -> TimeSegment.MORNING
            TimeSegment.MORNING -> TimeSegment.NOON
            TimeSegment.NOON -> TimeSegment.AFTERNOON
            TimeSegment.AFTERNOON -> TimeSegment.EVENING
            TimeSegment.EVENING -> TimeSegment.MORNING_START
        }

        executionTaskDao.upsert(
            task.copy(
                timeSegment = next.name,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteTask(taskId: Long) {
        executionTaskDao.deleteById(taskId)
    }

    override suspend fun addInboxTask(title: String, note: String) {
        addTemporaryTask(
            title = title,
            note = note,
            listKey = "INBOX",
        )
    }

    override suspend fun addTemporaryTask(
        title: String,
        note: String,
        listKey: String,
    ) {
        val existingCount = executionTaskDao.countUnscheduled()
        val now = System.currentTimeMillis()
        executionTaskDao.upsert(
            ExecutionTaskEntity(
                title = title.ifBlank { "临时任务" },
                note = note.ifBlank { null },
                status = "TODO",
                priority = "MEDIUM",
                sortOrder = existingCount + 1,
                sourceType = listKey,
                timeSegment = null,
                specificTime = null,
                isTopFocus = false,
                estimatedMinutes = 15,
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    override suspend fun assignTaskToDateSegment(
        taskId: Long,
        date: String,
        timeSegment: String,
    ) {
        val task = executionTaskDao.getById(taskId) ?: return
        var dailyPlan = dailyPlanDao.observeByDate(date).first()
        if (dailyPlan == null) {
            val now = System.currentTimeMillis()
            val newPlanId = dailyPlanDao.upsert(
                com.moqim.list.data.local.entity.DailyPlanEntity(
                    date = date,
                    summary = "从计划中心编排的日计划",
                    energyLevel = "MEDIUM",
                    generatedFromPreviousDay = false,
                    review = null,
                    status = "ACTIVE",
                    createdAt = now,
                    updatedAt = now,
                )
            )
            dailyPlan = dailyPlanDao.getById(newPlanId)
        }

        val targetDailyPlan = dailyPlan ?: return
        val updatedSourceType = if (task.sourceType == "WEEK_POOL") "WEEK_POOL_ASSIGNED" else task.sourceType
        executionTaskDao.upsert(
            task.copy(
                dailyPlanId = targetDailyPlan.id,
                timeSegment = timeSegment,
                specificTime = null,
                sourceType = updatedSourceType,
                updatedAt = System.currentTimeMillis(),
            )
        )

        if (updatedSourceType == "WEEK_POOL_ASSIGNED") {
            val assignedWeekPoolTasks = executionTaskDao.getByDailyPlanId(targetDailyPlan.id)
                .filter { it.sourceType == "WEEK_POOL_ASSIGNED" }
                .map { it.title.trim() }
                .filter { it.isNotBlank() }

            if (assignedWeekPoolTasks.isNotEmpty()) {
                val bridgeLine = when {
                    assignedWeekPoolTasks.size == 1 -> "今日承接周池项：${assignedWeekPoolTasks.first()}"
                    else -> "今日承接周池项：${assignedWeekPoolTasks.take(2).joinToString("、")} 等 ${assignedWeekPoolTasks.size} 项"
                }
                val baseSummary = targetDailyPlan.summary
                    ?.lines()
                    ?.filterNot { it.startsWith("今日承接周池项：") }
                    ?.joinToString("\n")
                    ?.trim()
                    .orEmpty()

                dailyPlanDao.upsert(
                    targetDailyPlan.copy(
                        summary = buildString {
                            if (baseSummary.isNotBlank()) {
                                append(baseSummary)
                                append("\n")
                            }
                            append(bridgeLine)
                        }.trim(),
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    override suspend fun moveTaskToWeekPool(taskId: Long) {
        val task = executionTaskDao.getById(taskId) ?: return
        val previousDailyPlanId = task.dailyPlanId
        executionTaskDao.upsert(
            task.copy(
                dailyPlanId = null,
                timeSegment = null,
                specificTime = null,
                sourceType = "WEEK_POOL",
                updatedAt = System.currentTimeMillis(),
            )
        )

        if (previousDailyPlanId != null) {
            val previousDailyPlan = dailyPlanDao.getById(previousDailyPlanId)
            val remainingAssignedWeekPoolTasks = executionTaskDao.getByDailyPlanId(previousDailyPlanId)
                .filter { it.sourceType == "WEEK_POOL_ASSIGNED" }
                .map { it.title.trim() }
                .filter { it.isNotBlank() }

            previousDailyPlan?.let { plan ->
                val baseSummary = plan.summary
                    ?.lines()
                    ?.filterNot { it.startsWith("今日承接周池项：") }
                    ?.joinToString("\n")
                    ?.trim()
                    .orEmpty()

                val nextSummary = buildString {
                    if (baseSummary.isNotBlank()) {
                        append(baseSummary)
                    }
                    if (remainingAssignedWeekPoolTasks.isNotEmpty()) {
                        if (isNotBlank()) append("\n")
                        append(
                            when {
                                remainingAssignedWeekPoolTasks.size == 1 ->
                                    "今日承接周池项：${remainingAssignedWeekPoolTasks.first()}"
                                else ->
                                    "今日承接周池项：${remainingAssignedWeekPoolTasks.take(2).joinToString("、")} 等 ${remainingAssignedWeekPoolTasks.size} 项"
                            }
                        )
                    }
                }.trim()

                dailyPlanDao.upsert(
                    plan.copy(
                        summary = nextSummary,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    override fun observeTodayTasks(): Flow<List<ExecutionTaskSummary>> {
        return observeTasksForDate(LocalDate.now().toString())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeTasksForDate(date: String): Flow<List<ExecutionTaskSummary>> {
        return dailyPlanDao.observeByDate(date).flatMapLatest { observedDailyPlan ->
            val plansForDate = dailyPlanDao.getAllByDate(date)
            val fallbackPlan = observedDailyPlan?.let { observed ->
                plansForDate.firstOrNull { it.id == observed.id }
            }
            val targetPlans = when {
                plansForDate.isNotEmpty() -> plansForDate
                fallbackPlan != null -> listOf(fallbackPlan)
                observedDailyPlan != null -> listOf(observedDailyPlan)
                else -> emptyList()
            }
            if (targetPlans.isEmpty()) {
                flowOf(emptyList())
            } else {
                val planIds = targetPlans.map { it.id }
                if (planIds.size == 1) {
                    executionTaskDao.observeByDailyPlanId(planIds.first()).map { tasks ->
                        tasks.sortedWith(compareByDescending<ExecutionTaskEntity> { it.isTopFocus }.thenBy { it.sortOrder }.thenBy { it.id })
                            .map(::toSummary)
                    }
                } else {
                    val flows = planIds.map { id -> executionTaskDao.observeByDailyPlanId(id) }
                    combine(flows) { arrays ->
                        arrays.flatMap { it.toList() }
                            .distinctBy { it.id }
                            .sortedWith(compareByDescending<ExecutionTaskEntity> { it.isTopFocus }.thenBy { it.sortOrder }.thenBy { it.id })
                            .map(::toSummary)
                    }
                }
            }
        }
    }

    override fun observeUnscheduledTasks(): Flow<List<ExecutionTaskSummary>> {
        return executionTaskDao.observeUnscheduledTasks().map { tasks ->
            tasks.map(::toSummary)
        }
    }

    override fun observeTemporaryTasksBySource(sourceType: String): Flow<List<ExecutionTaskSummary>> {
        return executionTaskDao.observeBySourceType(sourceType).map { tasks ->
            tasks.map(::toSummary)
        }
    }

    private fun toSummary(task: ExecutionTaskEntity): ExecutionTaskSummary {
        return ExecutionTaskSummary(
            id = task.id,
            dailyPlanId = task.dailyPlanId,
            title = task.title,
            note = task.note,
            status = task.status,
            timeSegment = task.timeSegment,
            specificTime = task.specificTime,
            isTopFocus = task.isTopFocus,
            estimatedMinutes = task.estimatedMinutes,
        )
    }
}
