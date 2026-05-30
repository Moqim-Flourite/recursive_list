package com.moqim.list.domain.repository

import com.moqim.list.domain.model.ExecutionTaskSummary
import kotlinx.coroutines.flow.Flow

interface ExecutionTaskRepository {
    suspend fun seedForTodayIfNeeded()
    suspend fun addQuickTask()
    suspend fun addTaskForDate(
        date: String,
        title: String,
        note: String,
        estimatedMinutes: Int?,
        timeSegment: String?,
        specificTime: String?,
    )
    suspend fun quickEditTask(taskId: Long)
    suspend fun updateTask(
        taskId: Long,
        title: String,
        note: String,
        estimatedMinutes: Int?,
        timeSegment: String?,
        specificTime: String?,
    )
    suspend fun getTask(taskId: Long): ExecutionTaskSummary?
    suspend fun toggleTaskStatus(taskId: Long)
    suspend fun moveTaskToNextSegment(taskId: Long)
    suspend fun deleteTask(taskId: Long)
    suspend fun addInboxTask(title: String, note: String = "")
    suspend fun addTemporaryTask(
        title: String,
        note: String = "",
        listKey: String = "inbox",
    )
    suspend fun assignTaskToDateSegment(
        taskId: Long,
        date: String,
        timeSegment: String,
    )
    suspend fun moveTaskToWeekPool(taskId: Long)
    fun observeTodayTasks(): Flow<List<ExecutionTaskSummary>>
    fun observeTasksForDate(date: String): Flow<List<ExecutionTaskSummary>>
    fun observeUnscheduledTasks(): Flow<List<ExecutionTaskSummary>>
    fun observeTemporaryTasksBySource(sourceType: String): Flow<List<ExecutionTaskSummary>>
}
