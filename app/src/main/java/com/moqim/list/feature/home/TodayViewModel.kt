package com.moqim.list.feature.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.data.repository.DefaultTodayDashboardRepository
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomExecutionTaskRepository
import com.moqim.list.data.repository.RoomHabitRepository
import com.moqim.list.domain.repository.TodayDashboardRepository
import com.moqim.list.feature.home.model.DailyPlanEditorUiModel
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.feature.home.model.DailyUiState
import com.moqim.list.feature.home.model.HabitEditorUiModel
import com.moqim.list.widget.CurrentSegmentWidgetProvider
import com.moqim.list.widget.MorningWidgetProvider
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TodayViewModel(
    private val appContext: Context,
    private val repository: TodayDashboardRepository,
    private val dailyPlanRepository: RoomDailyPlanRepository,
    private val habitRepository: RoomHabitRepository,
    private val executionTaskRepository: RoomExecutionTaskRepository,
    private val settingsRepository: SettingsPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyUiState())
    val uiState: StateFlow<DailyUiState> = _uiState.asStateFlow()

    private val _morningViewVisible = MutableStateFlow(false)
    val morningViewVisible: StateFlow<Boolean> = _morningViewVisible.asStateFlow()

    private val _availableApps = MutableStateFlow<List<ShizukuInstalledApp>>(emptyList())
    val availableApps: StateFlow<List<ShizukuInstalledApp>> = _availableApps.asStateFlow()

    private val _availableAppsLoading = MutableStateFlow(false)
    val availableAppsLoading: StateFlow<Boolean> = _availableAppsLoading.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedIfNeeded()
            launch { observeDashboard() }
            launch { observeMorningVisibility() }
        }
    }

    fun onHabitClick(templateId: Long) {
        if (_uiState.value.habitReorderMode || _uiState.value.habitDeleteMode) return
        viewModelScope.launch {
            val dateKey = LocalDate.now().toString()
            habitRepository.incrementHabitProgress(templateId, dateKey)
            refreshWidget()
        }
    }

    fun onHabitUndo(templateId: Long) {
        if (_uiState.value.habitReorderMode || _uiState.value.habitDeleteMode) return
        viewModelScope.launch {
            val dateKey = LocalDate.now().toString()
            habitRepository.decrementHabitProgress(templateId, dateKey)
            refreshWidget()
        }
    }

    fun onHabitSkip(templateId: Long) {
        if (_uiState.value.habitReorderMode || _uiState.value.habitDeleteMode) return
        viewModelScope.launch {
            val dateKey = LocalDate.now().toString()
            habitRepository.skipHabit(templateId, dateKey)
            _uiState.update {
                it.copy(feedbackMessage = "已标记今日跳过")
            }
            refreshWidget()
        }
    }

    fun onHabitLongPress(templateId: Long) {
        if (_uiState.value.habitReorderMode || _uiState.value.habitDeleteMode) return
        val target = _uiState.value.habitItems.firstOrNull { it.templateId == templateId } ?: return
        _uiState.value = _uiState.value.copy(
            editingHabit = HabitEditorUiModel(
                templateId = target.templateId,
                title = target.title,
                iconLabel = target.iconLabel,
                dailyTargetCount = target.dailyTargetCount,
                completedCount = target.completedCount,
                iconUri = target.iconUri,
                targetAppPackageName = target.targetAppPackageName,
                showTotalCompletedDays = target.showTotalCompletedDays,
                baseCompletedDays = target.baseCompletedDays,
            ),
        )
    }

    fun onOpenHabitTargetApp(templateId: Long) {
        val target = _uiState.value.habitItems.firstOrNull { it.templateId == templateId }
        val packageName = target?.targetAppPackageName?.trim().orEmpty()
        if (packageName.isBlank()) {
            _uiState.update { it.copy(feedbackMessage = "这个打卡项还没有绑定目标 App") }
            return
        }
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            _uiState.update { it.copy(feedbackMessage = "未安装目标 App：$packageName") }
            return
        }
        runCatching {
            appContext.startActivity(
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onSuccess {
            _uiState.update { it.copy(feedbackMessage = "已打开目标 App") }
        }.onFailure {
            _uiState.update { it.copy(feedbackMessage = "打开目标 App 失败") }
        }
    }

    fun onHabitEditDismiss() {
        _uiState.update { it.copy(editingHabit = null) }
    }

    fun onHabitDelete(templateId: Long) {
        viewModelScope.launch {
            habitRepository.deleteHabit(templateId)
            _uiState.update { state ->
                state.copy(
                    editingHabit = null,
                    habitItems = state.habitItems.filterNot { it.templateId == templateId },
                    feedbackMessage = "打卡项已删除",
                )
            }
            refreshWidget()
        }
    }

    fun loadAvailableApps(forceRefresh: Boolean = false) {
        if (!forceRefresh && _availableApps.value.isNotEmpty()) return
        viewModelScope.launch {
            _availableAppsLoading.value = true
            val result = withContext(Dispatchers.IO) {
                ShizukuAppScanner.loadThirdPartyApps(appContext)
            }
            _availableApps.value = result
            _availableAppsLoading.value = false
        }
    }

    fun onHabitEditSave(
        templateId: Long,
        title: String,
        iconLabel: String,
        dailyTargetCount: Int,
        completedCount: Int,
        iconUri: String?,
        targetAppPackageName: String?,
        showTotalCompletedDays: Boolean,
        baseCompletedDays: Int,
    ) {
        viewModelScope.launch {
            val normalizedCompleted = completedCount.coerceAtLeast(0)
            val normalizedTarget = dailyTargetCount.coerceAtLeast(1)
            val today = LocalDate.now().toString()
            habitRepository.updateHabitTemplate(
                templateId = templateId,
                title = title,
                iconLabel = iconLabel,
                iconUri = iconUri,
                dailyTargetCount = normalizedTarget,
                targetAppPackageName = targetAppPackageName,
                streakEnabled = showTotalCompletedDays,
                baseCompletedDays = baseCompletedDays,
            )
            habitRepository.updateHabitCompletedCount(
                templateId = templateId,
                date = today,
                completedCount = normalizedCompleted.coerceAtMost(normalizedTarget),
            )
            _uiState.update { state ->
                state.copy(
                    habitItems = state.habitItems.map { item ->
                        if (item.templateId == templateId) {
                            item.copy(
                                title = title.trim().ifBlank { item.title },
                                iconLabel = iconLabel.trim().ifBlank { "◉" },
                                dailyTargetCount = normalizedTarget,
                                completedCount = normalizedCompleted.coerceAtMost(normalizedTarget),
                                iconUri = iconUri,
                                targetAppPackageName = targetAppPackageName,
                                showTotalCompletedDays = showTotalCompletedDays,
                            )
                        } else {
                            item
                        }
                    },
                    editingHabit = null,
                    feedbackMessage = "打卡项已保存",
                )
            }
            refreshWidget()
        }
    }

    fun onAddHabitTemplate() {
        _uiState.value = _uiState.value.copy(
            editingHabit = HabitEditorUiModel(
                templateId = 0L,
                title = "",
                iconLabel = "◉",
                dailyTargetCount = 1,
                completedCount = 0,
                iconUri = null,
                targetAppPackageName = null,
                showTotalCompletedDays = true,
                baseCompletedDays = 0,
            ),
        )
    }

    fun onCreateHabitSave(
        title: String,
        iconLabel: String,
        dailyTargetCount: Int,
        completedCount: Int,
        iconUri: String?,
        targetAppPackageName: String?,
        showTotalCompletedDays: Boolean,
        baseCompletedDays: Int,
    ) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val normalizedTarget = dailyTargetCount.coerceAtLeast(1)
            val normalizedCompleted = completedCount.coerceAtLeast(0).coerceAtMost(normalizedTarget)
            val newId = habitRepository.createHabitTemplate(
                title = title,
                iconLabel = iconLabel,
                iconUri = iconUri,
                dailyTargetCount = normalizedTarget,
                targetAppPackageName = targetAppPackageName,
                streakEnabled = showTotalCompletedDays,
                baseCompletedDays = baseCompletedDays,
            )
            habitRepository.ensureTodayRecords(today)
            habitRepository.updateHabitCompletedCount(
                templateId = newId,
                date = today,
                completedCount = normalizedCompleted,
            )
            _uiState.update { state ->
                state.copy(
                    editingHabit = null,
                    habitItems = state.habitItems + com.moqim.list.feature.home.model.HabitItemUiModel(
                        templateId = newId,
                        title = title.trim().ifBlank { "新打卡" },
                        status = if (normalizedCompleted > 0) "IN_PROGRESS" else "TODO",
                        dailyTargetCount = normalizedTarget,
                        completedCount = normalizedCompleted,
                        iconLabel = iconLabel.trim().ifBlank { "◉" },
                        iconUri = iconUri,
                        targetAppPackageName = targetAppPackageName,
                        showTotalCompletedDays = showTotalCompletedDays,
                        baseCompletedDays = baseCompletedDays.coerceAtLeast(0),
                    ),
                    feedbackMessage = "已新增打卡项",
                )
            }
            refreshWidget()
        }
    }

    fun showHabitManager() {
        _uiState.update { it.copy(habitManagerVisible = true) }
    }

    fun dismissHabitManager() {
        _uiState.update { it.copy(habitManagerVisible = false) }
    }

    fun showHabitOrderDialog() {
        _uiState.update { it.copy(habitReorderMode = true, habitDeleteMode = false, habitOrderDialogVisible = true) }
    }

    fun enterHabitDeleteMode() {
        _uiState.update { it.copy(habitDeleteMode = true, habitReorderMode = false) }
    }

    fun exitHabitDeleteMode() {
        _uiState.update { it.copy(habitDeleteMode = false) }
    }

    fun dismissHabitOrderDialog() {
        _uiState.update { it.copy(habitReorderMode = false, habitOrderDialogVisible = false) }
    }

    fun onHabitMove(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val items = state.habitItems.toMutableList()
            if (fromIndex !in items.indices || toIndex !in items.indices) return@update state
            val moved = items.removeAt(fromIndex)
            items.add(toIndex, moved)
            state.copy(habitItems = items)
        }
    }

    fun persistHabitOrder() {
        viewModelScope.launch {
            habitRepository.replaceHabitOrder(_uiState.value.habitItems.map { it.templateId })
            _uiState.update {
                it.copy(
                    habitReorderMode = false,
                    habitOrderDialogVisible = false,
                    feedbackMessage = "Habit 顺序已更新",
                )
            }
        }
    }

    fun dismissMorningViewForToday() {
        viewModelScope.launch {
            settingsRepository.dismissMorningViewForToday()
            _morningViewVisible.value = false
        }
    }

    fun onTaskClick(taskId: Long) {
        viewModelScope.launch {
            executionTaskRepository.toggleTaskStatus(taskId)
            refreshWidget()
        }
    }

    fun onTaskQuickEdit(taskId: Long) {
        viewModelScope.launch {
            executionTaskRepository.quickEditTask(taskId)
            refreshWidget()
        }
    }

    fun onTaskEdit(taskId: Long) {
        viewModelScope.launch {
            val task = executionTaskRepository.getTask(taskId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                    editingTask = com.moqim.list.feature.home.model.TaskItemUiModel(
                        id = task.id,
                        title = task.title,
                        note = task.note.orEmpty(),
                        status = task.status,
                        estimatedMinutes = task.estimatedMinutes,
                        timeSegment = task.timeSegment,
                        specificTime = task.specificTime,
                    )
            )
        }
    }

    fun onTaskEditDismiss() {
        _uiState.value = _uiState.value.copy(editingTask = null)
    }

    fun onDailyPlanEdit() {
        viewModelScope.launch {
            val planId = _uiState.value.dailyPlanId ?: return@launch
            val plan = dailyPlanRepository.getDailyPlan(planId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                editingDailyPlan = DailyPlanEditorUiModel(
                    id = plan.id,
                    summary = plan.summary.orEmpty(),
                    energyLevel = plan.energyLevel ?: "MEDIUM",
                    review = plan.review.orEmpty(),
                )
            )
        }
    }

    fun onDailyPlanEditDismiss() {
        _uiState.value = _uiState.value.copy(editingDailyPlan = null)
    }

    fun onDailyPlanEditSave(
        planId: Long,
        summary: String,
        energyLevel: String,
        review: String,
    ) {
        viewModelScope.launch {
            dailyPlanRepository.updateDailyPlan(
                planId = planId,
                summary = summary,
                energyLevel = energyLevel,
                review = review,
            )
            _uiState.value = _uiState.value.copy(
                editingDailyPlan = null,
                feedbackMessage = "日计划已保存",
            )
            refreshWidget()
        }
    }

    fun onFeedbackShown() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }

    fun onTaskEditSave(
        taskId: Long,
        title: String,
        note: String,
        estimatedMinutes: Int?,
        timeSegment: String?,
        specificTime: String?,
    ) {
        viewModelScope.launch {
            executionTaskRepository.updateTask(
                taskId = taskId,
                title = title,
                note = note,
                estimatedMinutes = estimatedMinutes,
                timeSegment = timeSegment,
                specificTime = specificTime,
            )
            _uiState.value = _uiState.value.copy(
                editingTask = null,
                feedbackMessage = "执行任务已保存",
            )
            refreshWidget()
        }
    }

    fun onAddQuickTask() {
        viewModelScope.launch {
            executionTaskRepository.addQuickTask()
            _uiState.value = _uiState.value.copy(feedbackMessage = "已新增一条执行任务")
            refreshWidget()
        }
    }

    fun onTaskDelete(taskId: Long) {
        viewModelScope.launch {
            executionTaskRepository.deleteTask(taskId)
            refreshWidget()
        }
    }

    fun onTaskMoveNext(taskId: Long) {
        viewModelScope.launch {
            val task = executionTaskRepository.getTask(taskId)
            executionTaskRepository.moveTaskToNextSegment(taskId)
            val nextLabel = task?.timeSegment
                ?.let { current -> nextSegmentName(current) }
                ?.let { segmentNameToLabel(it) }
                ?: "下一时段"
            _uiState.update {
                it.copy(feedbackMessage = "已移到$nextLabel")
            }
            refreshWidget()
        }
    }

    fun onTaskMoveToSegment(taskId: Long, timeSegment: String) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            executionTaskRepository.assignTaskToDateSegment(
                taskId = taskId,
                date = today,
                timeSegment = timeSegment,
            )
            _uiState.update {
                it.copy(feedbackMessage = "已移到${segmentNameToLabel(timeSegment)}")
            }
            refreshWidget()
        }
    }

    private fun segmentNameToLabel(name: String): String =
        TimeSegment.entries.firstOrNull { it.name == name }?.label ?: name

    private fun nextSegmentName(current: String): String? {
        val index = TimeSegment.entries.indexOfFirst { it.name == current }
        if (index == -1) return null
        return TimeSegment.entries[(index + 1) % TimeSegment.entries.size].name
    }

    private suspend fun observeMorningVisibility() {
        settingsRepository.settingsFlow.collect { settings ->
            val today = LocalDate.now().toString()
            val currentHour = java.time.LocalTime.now().hour
            val shouldShowMorningView = settings.morningViewEnabled &&
                currentHour in 5..10 &&
                settings.morningViewDismissedDate != today
            _morningViewVisible.value = shouldShowMorningView
        }
    }

    private fun refreshWidget() {
        MorningWidgetProvider.refreshAll(appContext)
        CurrentSegmentWidgetProvider.refreshAll(appContext)
        WallpaperRefreshNotifier.notifyRefresh(appContext)
    }

    private suspend fun observeDashboard() {
        repository.observeTodayDashboard().collect { state ->
            _uiState.value = state
        }
    }

    companion object {
        fun factory(
            context: Context,
        ): ViewModelProvider.Factory {
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
            val settingsRepository = SettingsPreferencesRepository(context.applicationContext)
            val repository = DefaultTodayDashboardRepository(
                habitRepository = habitRepository,
                dailyPlanRepository = dailyPlanRepository,
                executionTaskRepository = executionTaskRepository,
            )

            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TodayViewModel(
                        appContext = context.applicationContext,
                        repository = repository,
                        dailyPlanRepository = dailyPlanRepository,
                        habitRepository = habitRepository,
                        executionTaskRepository = executionTaskRepository,
                        settingsRepository = settingsRepository,
                    ) as T
                }
            }
        }
    }
}
