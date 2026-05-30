package com.moqim.list.widget

import android.content.Context
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.core.time.TimeSegmentResolver
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomExecutionTaskRepository
import com.moqim.list.data.repository.RoomHabitRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class CurrentSegmentWidgetContentBuilder(
    private val context: Context,
) {
    suspend fun build(): CurrentSegmentWidgetContent {
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

        val today = LocalDate.now().toString()
        habitRepository.seedDefaultsIfNeeded()
        habitRepository.ensureTodayRecords(today)
        dailyPlanRepository.seedTodayIfNeeded()
        executionTaskRepository.seedForTodayIfNeeded()

        val settings = SettingsPreferencesRepository(context).settingsFlow.first()
        val currentSegment = TimeSegmentResolver.resolveNow(settings)
        val tasks = executionTaskRepository.observeTodayTasks().first()
            .filter { it.timeSegment == currentSegment.name }

        val pendingCount = tasks.count { it.status != "DONE" }
        val completedCount = tasks.count { it.status == "DONE" }

        val allItems = tasks
            .sortedWith(
                compareByDescending<com.moqim.list.domain.model.ExecutionTaskSummary> { it.status != "DONE" }
                    .thenBy { it.id }
            )
            .mapIndexed { index, task ->
                val minutesText = task.estimatedMinutes?.let { "（${it}分钟）" }.orEmpty()
                CurrentSegmentWidgetTaskItem(
                    text = "${index + 1}. ${task.title}$minutesText",
                    taskId = task.id,
                    completed = task.status == "DONE",
                )
            }

        return CurrentSegmentWidgetContent(
            title = "当前时段 · ${currentSegment.label}",
            summary = if (tasks.isEmpty()) {
                "当前时段暂无任务，适合机动调整。"
            } else {
                "当前时段还有 ${pendingCount} 项待办"
            },
            completedCount = completedCount,
            totalCount = tasks.size,
            items = allItems,
            task1 = allItems.getOrElse(0) { CurrentSegmentWidgetTaskItem("") },
            task2 = allItems.getOrElse(1) { CurrentSegmentWidgetTaskItem("") },
            task3 = allItems.getOrElse(2) { CurrentSegmentWidgetTaskItem("") },
        )
    }
}
