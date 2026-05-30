package com.moqim.list.domain.repository

import com.moqim.list.domain.model.HabitSummary
import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    suspend fun seedDefaultsIfNeeded()
    suspend fun ensureTodayRecords(date: String)
    suspend fun incrementHabitProgress(templateId: Long, date: String)
    suspend fun decrementHabitProgress(templateId: Long, date: String)
    suspend fun skipHabit(templateId: Long, date: String)
    suspend fun deleteHabit(templateId: Long)
    fun observeTodayHabitSummary(date: String): Flow<HabitSummary>
}
