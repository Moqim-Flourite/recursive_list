package com.moqim.list.widget

data class CurrentSegmentWidgetTaskItem(
    val text: String,
    val taskId: Long? = null,
    val completed: Boolean = false,
)

data class CurrentSegmentWidgetContent(
    val title: String,
    val summary: String,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<CurrentSegmentWidgetTaskItem>,
    val task1: CurrentSegmentWidgetTaskItem,
    val task2: CurrentSegmentWidgetTaskItem,
    val task3: CurrentSegmentWidgetTaskItem,
)
