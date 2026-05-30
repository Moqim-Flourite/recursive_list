package com.moqim.list.widget

data class MorningWidgetTaskItem(
    val text: String,
    val taskId: Long? = null,
    val completed: Boolean = false,
)

data class MorningWidgetContent(
    val title: String,
    val summary: String,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<MorningWidgetTaskItem>,
    val top1: MorningWidgetTaskItem,
    val top2: MorningWidgetTaskItem,
    val top3: MorningWidgetTaskItem,
)
