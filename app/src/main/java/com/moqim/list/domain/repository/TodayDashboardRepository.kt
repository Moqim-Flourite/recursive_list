package com.moqim.list.domain.repository

import com.moqim.list.feature.home.model.DailyUiState
import kotlinx.coroutines.flow.Flow

interface TodayDashboardRepository {
    suspend fun seedIfNeeded()
    fun observeTodayDashboard(): Flow<DailyUiState>
}
