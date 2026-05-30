package com.moqim.list.widget

import android.content.Context
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomExecutionTaskRepository
import com.moqim.list.data.repository.RoomHabitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MorningWidgetContentBuilder(
    private val context: Context,
) {
    suspend fun build(): MorningWidgetContent {
        val db = DatabaseProvider.get(context)
        val habitRepository = RoomHabitRepository(
            habitTemplateDao = db.habitTemplateDao(),
            habitRecordDao = db.habitRecordDao(),
        )
        val dailyPlanRepository = RoomDailyPlanRepository(
        dailyPlanDao = db.dailyPlanDao(),
        weeklyPlanDao = db.weeklyPlanDao(),
        executionTaskDao = db.executionTaskDao(),
    )
        val executionTaskRepository = RoomExecutionTaskRepository(
            dailyPlanDao = db.dailyPlanDao(),
            executionTaskDao = db.executionTaskDao(),
        )

        val today = LocalDate.now()
        val dateKey = today.toString()
        val dateText = today.format(DateTimeFormatter.ofPattern("M月d日"))

        habitRepository.seedDefaultsIfNeeded()
        habitRepository.ensureTodayRecords(dateKey)
        dailyPlanRepository.seedTodayIfNeeded()
        executionTaskRepository.seedForTodayIfNeeded()

        val dailyPlan = dailyPlanRepository.observeTodayPlan(dateKey).first()
        val tasks = executionTaskRepository.observeTodayTasks().first()
        val sortedTasks = tasks
            .sortedWith(
                compareByDescending<com.moqim.list.domain.model.ExecutionTaskSummary> { it.status != "DONE" }
                    .thenByDescending { it.isTopFocus }
                    .thenBy { it.id }
            )

        val pendingCount = tasks.count { it.status != "DONE" }
        val completedCount = tasks.count { it.status == "DONE" }

        val allItems = sortedTasks.mapIndexed { index, task ->
            val segmentText = task.timeSegment
                ?.let { value -> TimeSegment.entries.firstOrNull { it.name == value }?.label }
                ?.let { "【建议$it】" }
                .orEmpty()
            val minutesText = task.estimatedMinutes?.let { "（${it}分钟）" }.orEmpty()
            MorningWidgetTaskItem(
                text = "${index + 1}. $segmentText ${task.title}$minutesText".trim(),
                taskId = task.id,
                completed = task.status == "DONE",
            )
        }

        return MorningWidgetContent(
            title = "晨间视图 · $dateText",
            summary = buildString {
                append(dailyPlan?.summary ?: "今日计划待生成")
                if (tasks.isNotEmpty()) {
                    append(" · 今日剩余 ${pendingCount} 项待办")
                }
            },
            completedCount = completedCount,
            totalCount = tasks.size,
            items = allItems,
            top1 = allItems.getOrElse(0) { MorningWidgetTaskItem("1. 确认今天的主线目标") },
            top2 = allItems.getOrElse(1) { MorningWidgetTaskItem("2. 给任务分配到合适时段") },
            top3 = allItems.getOrElse(2) { MorningWidgetTaskItem("3. 完成至少一个固定打卡项") },
        )
    }
}
