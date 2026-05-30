package com.moqim.list.feature.home.model

data class TaskItemUiModel(
    val id: Long,
    val title: String,
    val note: String = "",
    val status: String,
    val estimatedMinutes: Int?,
    val timeSegment: String? = null,
    val specificTime: String? = null,
)
