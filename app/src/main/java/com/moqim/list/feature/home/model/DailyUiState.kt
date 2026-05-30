package com.moqim.list.feature.home.model

import com.moqim.list.core.model.TimeSegment

data class DailyPlanEditorUiModel(
    val id: Long,
    val summary: String,
    val energyLevel: String,
    val review: String,
    val weeklyPlanId: Long? = null,
)

data class HabitEditorUiModel(
    val templateId: Long,
    val title: String,
    val iconLabel: String,
    val dailyTargetCount: Int,
    val completedCount: Int,
    val iconUri: String? = null,
    val targetAppPackageName: String? = null,
    val showTotalCompletedDays: Boolean = true,
    val baseCompletedDays: Int = 0,
)

data class DailyUiState(
    val title: String = "今日",
    val dateText: String = "今天",
    val dailyPlanId: Long? = null,
    val weeklySummary: String = "本周主线正在准备中",
    val habitsSummary: String = "今日打卡：0/0",
    val carryOverSummary: String = "昨日无延续任务",
    val energyLevel: String = "MEDIUM",
    val review: String = "",
    val morningFocusItems: List<String> = listOf(
        "先确认今天最重要的一件事",
        "安排一个明确的推进时段",
        "优先完成一项关键动作",
    ),
    val segmentSummaries: List<SegmentSummaryUiModel> = TimeSegment.entries.map { segment ->
        SegmentSummaryUiModel(
            segment = segment,
            summary = "暂无任务",
            tasks = emptyList(),
            loadLabel = "0 项",
        )
    },
    val completedTasks: List<TaskItemUiModel> = emptyList(),
    val habitItems: List<HabitItemUiModel> = emptyList(),
    val editingTask: TaskItemUiModel? = null,
    val editingDailyPlan: DailyPlanEditorUiModel? = null,
    val editingHabit: HabitEditorUiModel? = null,
    val habitManagerVisible: Boolean = false,
    val habitReorderMode: Boolean = false,
    val habitDeleteMode: Boolean = false,
    val habitOrderDialogVisible: Boolean = false,
    val feedbackMessage: String? = null,
)
