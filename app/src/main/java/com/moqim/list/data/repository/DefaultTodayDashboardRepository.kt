package com.moqim.list.data.repository

import com.moqim.list.core.model.TimeSegment
import com.moqim.list.domain.repository.DailyPlanRepository
import com.moqim.list.domain.repository.ExecutionTaskRepository
import com.moqim.list.domain.repository.HabitRepository
import com.moqim.list.domain.repository.TodayDashboardRepository
import com.moqim.list.feature.home.model.DailyUiState
import com.moqim.list.feature.home.model.HabitItemUiModel
import com.moqim.list.feature.home.model.SegmentSummaryUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DefaultTodayDashboardRepository(
    private val habitRepository: HabitRepository,
    private val dailyPlanRepository: DailyPlanRepository,
    private val executionTaskRepository: ExecutionTaskRepository,
) : TodayDashboardRepository {

    override suspend fun seedIfNeeded() {
        val dateKey = LocalDate.now().toString()
        habitRepository.seedDefaultsIfNeeded()
        habitRepository.ensureTodayRecords(dateKey)
        dailyPlanRepository.seedTodayIfNeeded()
        executionTaskRepository.seedForTodayIfNeeded()
    }

    override fun observeTodayDashboard(): Flow<DailyUiState> {
        val today = LocalDate.now()
        val dateFormatter = DateTimeFormatter.ofPattern("M月d日")
        val dateText = "${today.format(dateFormatter)} · ${today.dayOfWeek.toChineseWeekday()}"
        val dateKey = today.toString()

        return combine(
            habitRepository.observeTodayHabitSummary(dateKey),
            dailyPlanRepository.observeTodayPlan(dateKey),
            executionTaskRepository.observeTodayTasks(),
        ) { habitSummary, dailyPlan, tasks ->
            val pendingTasks = tasks.filter { it.status != "DONE" }
            val topFocusTasks = pendingTasks
                .sortedWith(
                    compareByDescending<com.moqim.list.domain.model.ExecutionTaskSummary> { it.isTopFocus }
                        .thenBy { it.id }
                )
                .take(3)

            val carryOverTasks = pendingTasks.filter { task ->
                task.note?.contains("昨日未完成滚动") == true
            }

            DailyUiState(
                title = "今日",
                dateText = dateText,
                dailyPlanId = dailyPlan?.id,
                weeklySummary = buildString {
                    append(dailyPlan?.summary ?: "今日计划待生成")
                    if (pendingTasks.isNotEmpty()) {
                        append(" · 今日剩余 ${pendingTasks.size} 项待办")
                    }
                },
                habitsSummary = habitSummary.summaryText,
                carryOverSummary = if (carryOverTasks.isEmpty()) {
                    "昨日无延续任务"
                } else {
                    "昨日延续 ${carryOverTasks.size} 项：${carryOverTasks.take(2).joinToString("、") { it.title }}"
                },
                energyLevel = dailyPlan?.energyLevel ?: "MEDIUM",
                review = dailyPlan?.review.orEmpty(),
                morningFocusItems = if (topFocusTasks.isNotEmpty()) {
                    topFocusTasks.map { task ->
                        val segmentText = task.timeSegment
                            ?.let { value -> TimeSegment.entries.firstOrNull { it.name == value }?.label }
                            ?.let { "【建议$it】" }
                            .orEmpty()
                        val minutesText = task.estimatedMinutes?.let { "（${it}分钟）" }.orEmpty()
                        "$segmentText ${task.title}$minutesText".trim()
                    }
                } else {
                    listOf(
                        "确认今天的主线目标",
                        "给任务分配到合适时段",
                        "完成至少一个固定打卡项",
                    )
                },
                segmentSummaries = TimeSegment.entries.map { segment ->
                    val matchedTasks = pendingTasks
                        .filter { it.timeSegment == segment.name }
                    SegmentSummaryUiModel(
                        segment = segment,
                        summary = if (matchedTasks.isEmpty()) {
                            segment.defaultSummary()
                        } else {
                            "本时段 ${matchedTasks.size} 项待办"
                        },
                        tasks = matchedTasks.map {
                            com.moqim.list.feature.home.model.TaskItemUiModel(
                                id = it.id,
                                title = it.title,
                                note = it.note.orEmpty(),
                                status = it.status,
                                estimatedMinutes = it.estimatedMinutes,
                                timeSegment = it.timeSegment,
                            )
                        },
                    )
                },
                completedTasks = tasks.filter { it.status == "DONE" }.map {
                    com.moqim.list.feature.home.model.TaskItemUiModel(
                        id = it.id,
                        title = it.title,
                        note = it.note.orEmpty(),
                        status = it.status,
                        estimatedMinutes = it.estimatedMinutes,
                        timeSegment = it.timeSegment,
                    )
                },
                habitItems = habitSummary.items.map { item ->
                    HabitItemUiModel(
                        templateId = item.templateId,
                        title = item.title,
                        status = item.status,
                        estimatedMinutes = item.estimatedMinutes,
                        dailyTargetCount = item.dailyTargetCount,
                        completedCount = item.completedCount,
                        iconLabel = item.iconLabel ?: "◉",
                        iconUri = item.iconUri,
                        targetAppPackageName = item.targetAppPackageName,
                        preferredTimeSegment = item.preferredTimeSegment,
                        totalCompletedDays = item.totalCompletedDays,
                        currentStreakDays = item.currentStreakDays,
                        showTotalCompletedDays = item.showTotalCompletedDays,
                        baseCompletedDays = item.baseCompletedDays,
                    )
                },
            )
        }
    }
}

private fun java.time.DayOfWeek.toChineseWeekday(): String = when (this) {
    java.time.DayOfWeek.MONDAY -> "周一"
    java.time.DayOfWeek.TUESDAY -> "周二"
    java.time.DayOfWeek.WEDNESDAY -> "周三"
    java.time.DayOfWeek.THURSDAY -> "周四"
    java.time.DayOfWeek.FRIDAY -> "周五"
    java.time.DayOfWeek.SATURDAY -> "周六"
    java.time.DayOfWeek.SUNDAY -> "周日"
}

private fun TimeSegment.defaultSummary(): String = when (this) {
    TimeSegment.MORNING_START -> "适合安排晨间浏览、轻打卡和启动任务。"
    TimeSegment.MORNING -> "适合安排最重要的深度任务。"
    TimeSegment.NOON -> "适合安排轻量事务和缓冲任务。"
    TimeSegment.AFTERNOON -> "适合安排推进型任务与沟通事项。"
    TimeSegment.EVENING -> "适合安排收尾、打卡补完和明日规划。"
}