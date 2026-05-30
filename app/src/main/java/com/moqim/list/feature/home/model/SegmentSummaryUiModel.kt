package com.moqim.list.feature.home.model

import com.moqim.list.core.model.TimeSegment

data class SegmentSummaryUiModel(
    val segment: TimeSegment,
    val summary: String,
    val tasks: List<TaskItemUiModel> = emptyList(),
    val loadLabel: String = "0 项",
)
