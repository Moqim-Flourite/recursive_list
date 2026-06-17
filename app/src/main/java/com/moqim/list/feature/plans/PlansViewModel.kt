package com.moqim.list.feature.plans

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomExecutionTaskRepository
import com.moqim.list.data.repository.RoomHabitRepository
import com.moqim.list.data.repository.RoomMonthlyPlanRepository
import com.moqim.list.data.repository.RoomWeeklyPlanRepository
import com.moqim.list.domain.model.MonthlyPlanSummary
import com.moqim.list.domain.model.WeeklyPlanSummary
import com.moqim.list.domain.repository.ExecutionTaskRepository
import com.moqim.list.domain.repository.HabitRepository
import com.moqim.list.domain.repository.MonthlyPlanRepository
import com.moqim.list.domain.repository.WeeklyPlanRepository
import com.moqim.list.feature.home.model.DailyPlanEditorUiModel
import com.moqim.list.feature.home.model.TaskItemUiModel
import com.moqim.list.feature.plans.model.MonthlyPlanItemUiModel
import com.moqim.list.feature.plans.model.PlanningDateChipUiModel
import com.moqim.list.feature.plans.model.PlanningLayer
import com.moqim.list.feature.plans.model.PlanningSegmentUiModel
import com.moqim.list.feature.plans.model.PlanningTaskUiModel
import com.moqim.list.feature.plans.model.PlansUiState
import com.moqim.list.feature.plans.model.TemporaryListUiModel
import com.moqim.list.feature.plans.model.WeeklyPlanItemUiModel
import com.moqim.list.feature.plans.model.WeeklyPlanProgressUiModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlansViewModel(
    private val appContext: Context,
    private val monthlyPlanRepository: MonthlyPlanRepository,
    private val weeklyPlanRepository: WeeklyPlanRepository,
    private val dailyPlanRepository: RoomDailyPlanRepository,
    private val executionTaskRepository: ExecutionTaskRepository,
    private val habitRepository: HabitRepository,
) : ViewModel() {

    private val selectedDateFlow = MutableStateFlow(LocalDate.now())
    private val currentLayerFlow = MutableStateFlow(PlanningLayer.DAY)
    private val selectedTemporaryListKeyFlow = MutableStateFlow("INBOX")

    private val _uiState = MutableStateFlow(
        PlansUiState(
            selectedDate = LocalDate.now().toString(),
            dateChips = buildDateChips(LocalDate.now()),
        )
    )
    val uiState: StateFlow<PlansUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            monthlyPlanRepository.seedDefaultsIfNeeded()
            weeklyPlanRepository.seedDefaultsIfNeeded()
            dailyPlanRepository.seedTodayIfNeeded()
            executionTaskRepository.seedForTodayIfNeeded()
            habitRepository.seedDefaultsIfNeeded()
            habitRepository.ensureTodayRecords(LocalDate.now().toString())
            seedPlanningCenterTemporaryListsIfNeeded()

            combine(
                monthlyPlanRepository.observeMonthlyPlans(),
                weeklyPlanRepository.observeWeeklyPlans(),
                selectedDateFlow,
                currentLayerFlow,
                selectedTemporaryListKeyFlow,
            ) { monthlyPlans, weeklyPlans, selectedDate, currentLayer, selectedTemporaryListKey ->
                PlanningBaseState(
                    monthlyPlans = monthlyPlans,
                    weeklyPlans = weeklyPlans,
                    selectedDate = selectedDate,
                    currentLayer = currentLayer,
                    selectedTemporaryListKey = selectedTemporaryListKey,
                )
            }.collect { baseState ->
                val selectedDateKey = baseState.selectedDate.toString()
                val selectedDailyPlan = dailyPlanRepository.observeTodayPlan(selectedDateKey).first()
                val selectedDayTasks = executionTaskRepository.observeTasksForDate(selectedDateKey).first()
                val weekPoolTasks = executionTaskRepository.observeTemporaryTasksBySource("WEEK_POOL").first()
                val inboxTasks = executionTaskRepository.observeTemporaryTasksBySource("INBOX").first()
                val deepWorkTasks = executionTaskRepository.observeTemporaryTasksBySource("DEEP_WORK").first()
                val quickWinTasks = executionTaskRepository.observeTemporaryTasksBySource("QUICK_WINS").first()
                habitRepository.ensureTodayRecords(selectedDateKey)
                val habitSummary = habitRepository.observeTodayHabitSummary(selectedDateKey).first()

                val db = DatabaseProvider.get(appContext)
                val monthlyUi = baseState.monthlyPlans.map { plan ->
                    toMonthlyPlanItemUiModel(
                        plan = plan,
                        childWeekCount = runCatching { db.weeklyPlanDao().getByMonthlyPlanId(plan.id).size }.getOrDefault(0),
                    )
                }
                val weeklyUi = baseState.weeklyPlans.map { plan ->
                    toWeeklyPlanItemUiModel(
                        plan = plan,
                        capacity = runCatching { db.weeklyPlanDao().getById(plan.id)?.capacity }.getOrNull(),
                        review = runCatching { db.weeklyPlanDao().getById(plan.id)?.review }.getOrNull(),
                    )
                }
                val weeklyProgress = weeklyUi.map { week ->
                    val db = DatabaseProvider.get(appContext)
                    val linkedPlans = runCatching {
                        db.dailyPlanDao().getByWeeklyPlanId(week.id)
                    }.getOrDefault(emptyList())
                    val taskEntities = runCatching {
                        db.executionTaskDao().getByWeeklyPlanId(week.id)
                    }.getOrDefault(emptyList())
                    val taskDailyPlans = taskEntities.mapNotNull { task ->
                        task.dailyPlanId?.let { dailyPlanId -> runCatching { db.dailyPlanDao().getById(dailyPlanId) }.getOrNull() }
                    }
                    val linkedDates = (linkedPlans.map { it.date } + taskDailyPlans.map { it.date })
                        .distinct()
                        .sorted()
                    val doneTaskCount = taskEntities.count { it.status == "DONE" }
                    WeeklyPlanProgressUiModel(
                        weeklyPlanId = week.id,
                        linkedDates = linkedDates,
                        totalTaskCount = taskEntities.size,
                        doneTaskCount = doneTaskCount,
                    )
                }

                val inboxUi = inboxTasks.map(::toPlanningTask)
                val weekPoolUi = weekPoolTasks.map(::toPlanningTask)
                val selectedDayUi = selectedDayTasks.map(::toPlanningTask)
                val pendingDayUi = selectedDayUi.filter { it.status != "DONE" && it.sourceType != "INBOX" && it.sourceType != "QUICK_WINS" && it.sourceType != "DEEP_WORK" }
                val completedDayUi = selectedDayUi.filter { it.status == "DONE" }
                val dayTemporaryUi = selectedDayUi.filter { it.sourceType in setOf("INBOX", "QUICK_WINS", "DEEP_WORK") }
                val weekPoolAssignedTasks = selectedDayUi.filter { it.sourceType == "WEEK_POOL_ASSIGNED" }
                val selectedWeek = baseState.weeklyPlans.firstOrNull { it.id == selectedDailyPlan?.weeklyPlanId }
                val selectedWeekCapacity = weeklyUi.firstOrNull { it.id == selectedWeek?.id }?.capacity
                val topFocusTitles = pendingDayUi
                    .sortedWith(compareByDescending<PlanningTaskUiModel> { it.isTopFocus }.thenBy { it.estimatedMinutes ?: Int.MAX_VALUE })
                    .take(3)
                    .map { it.title }
                val totalPlannedMinutes = pendingDayUi.sumOf { it.estimatedMinutes ?: 0 }
                val assignedMinutes = pendingDayUi.filter { !it.timeSegment.isNullOrBlank() }.sumOf { it.estimatedMinutes ?: 0 }
                val loadSummary = buildDayLoadSummary(pendingDayUi)
                val capacitySummary = buildCapacitySummary(
                    totalPlannedMinutes = totalPlannedMinutes,
                    assignedMinutes = assignedMinutes,
                    weekCapacity = selectedWeekCapacity,
                )
                val dayWeekPoolBridgeText = when {
                    weekPoolAssignedTasks.isEmpty() -> ""
                    weekPoolAssignedTasks.size == 1 -> "今日承接周池项：${weekPoolAssignedTasks.first().title}"
                    else -> "今日承接周池项：${weekPoolAssignedTasks.take(2).joinToString("、") { it.title }} 等 ${weekPoolAssignedTasks.size} 项"
                }
                val daySuggestionItems = buildList {
                    if (weekPoolAssignedTasks.isNotEmpty()) {
                        add("优先完成周池下沉项，避免周→日链路断开。")
                    }
                    val weeklyTitle = selectedDailyPlanWeeklyTitle(baseState, selectedDailyPlan?.weeklyPlanId)
                    if (weeklyTitle.isNotBlank()) {
                        add("围绕「$weeklyTitle」安排 1 条主推进任务。")
                        add("为「$weeklyTitle」预留 1 条可交付或收口任务。")
                    }
                    if (selectedDayTemporaryUiCount(dayTemporaryUi = dayTemporaryUi) >= 3) {
                        add("临时事务偏多，先清掉 1-2 条碎片事项，再留主线时间。")
                    }
                    if (isEmpty()) {
                        add("先明确今天唯一主线，再新增一条匹配当前能量档的任务。")
                        add("优先选择 25-45 分钟内可完成的一步，降低启动成本。")
                    }
                }.take(3)
                val temporaryLists = listOf(
                    TemporaryListUiModel(
                        key = "INBOX",
                        title = "Inbox",
                        caption = "稍后再编排",
                        accent = 0xFF7C8CF8,
                        tasks = inboxUi,
                    ),
                    TemporaryListUiModel(
                        key = "DEEP_WORK",
                        title = "Deep Work",
                        caption = "高专注候选",
                        accent = 0xFF6DAA7F,
                        tasks = deepWorkTasks.map(::toPlanningTask),
                    ),
                    TemporaryListUiModel(
                        key = "QUICK_WINS",
                        title = "Quick Wins",
                        caption = "碎片时间处理",
                        accent = 0xFFFFB457,
                        tasks = quickWinTasks.map(::toPlanningTask),
                    ),
                )

                _uiState.value = PlansUiState(
                    currentLayer = baseState.currentLayer,
                    selectedDate = selectedDateKey,
                    dateChips = buildDateChips(baseState.selectedDate),
                    monthlyPlans = monthlyUi,
                    weeklyPlans = weeklyUi,
                    weeklyProgress = weeklyProgress,
                    inboxTasks = inboxUi,
                    weekPoolTasks = weekPoolUi,
                    weekPoolHint = if (weekPoolUi.isEmpty()) "" else "当前周池还有 ${weekPoolUi.size} 项，可直接下沉到 ${selectedDateKey} 的具体时段",
                    daySegments = buildSegmentUi(pendingDayUi),
                    completedDayTasks = completedDayUi,
                    dayTemporaryTasks = dayTemporaryUi,
                    temporaryLists = temporaryLists,
                    selectedTemporaryListKey = baseState.selectedTemporaryListKey,
                    selectedDailyPlanId = selectedDailyPlan?.id,
                    selectedDailyPlanWeeklyPlanId = selectedDailyPlan?.weeklyPlanId,
                    selectedDailyPlanWeeklyPlanTitle = baseState.weeklyPlans
                        .firstOrNull { it.id == selectedDailyPlan?.weeklyPlanId }
                        ?.title
                        .orEmpty(),
                    selectedDailyPlanSummary = selectedDailyPlan?.summary.orEmpty(),
                    selectedDailyPlanEnergy = selectedDailyPlan?.energyLevel ?: "MEDIUM",
                    selectedDailyPlanReview = selectedDailyPlan?.review.orEmpty(),
                    selectedDayWeekPoolBridgeText = dayWeekPoolBridgeText,
                    selectedDaySuggestionItems = daySuggestionItems,
                    selectedDayTopFocusTitles = topFocusTitles,
                    selectedDayLoadSummary = loadSummary,
                    selectedDayCapacitySummary = capacitySummary,
                    selectedDayCompletedCount = completedDayUi.size,
                    selectedDayTemporaryCount = dayTemporaryUi.size,
                    selectedDayHabitProgressText = habitSummary.summaryText,
                    editingMonthlyPlan = _uiState.value.editingMonthlyPlan,
                    editingWeeklyPlan = _uiState.value.editingWeeklyPlan,
                    creatingMonthlyPlan = _uiState.value.creatingMonthlyPlan,
                    creatingWeeklyPlan = _uiState.value.creatingWeeklyPlan,
                    editingDailyPlan = _uiState.value.editingDailyPlan,
                    editingTask = _uiState.value.editingTask,
                    creatingTask = _uiState.value.creatingTask,
                    feedbackMessage = _uiState.value.feedbackMessage,
                    selectedMonthlyPlan = _uiState.value.selectedMonthlyPlan,
                    selectedWeeklyPlan = _uiState.value.selectedWeeklyPlan,
                    showingTextImportDialog = _uiState.value.showingTextImportDialog,
                    showingImportPreview = _uiState.value.showingImportPreview,
                    importPreviewText = _uiState.value.importPreviewText,
                    importInputText = _uiState.value.importInputText,
                )
            }
        }
    }

    fun onLayerSelected(layer: PlanningLayer) {
        currentLayerFlow.value = layer
    }

    fun onDateSelected(date: String) {
        runCatching { LocalDate.parse(date) }.getOrNull()?.let {
            selectedDateFlow.value = it
        }
    }

    fun onSelectTemporaryList(listKey: String) {
        selectedTemporaryListKeyFlow.value = listKey
    }

    fun onAssignTaskToSegment(taskId: Long, segment: TimeSegment) {
        viewModelScope.launch {
            val weekPoolTask = _uiState.value.weekPoolTasks.firstOrNull { it.id == taskId }
            executionTaskRepository.assignTaskToDateSegment(
                taskId = taskId,
                date = selectedDateFlow.value.toString(),
                timeSegment = segment.name,
            )
            _uiState.value = _uiState.value.copy(
                feedbackMessage = if (weekPoolTask != null) {
                    "已将周池任务「${weekPoolTask.title}」下沉到${segment.label}"
                } else {
                    "已放入${segment.label}时段"
                },
            )
        }
    }

    fun onMoveTaskBackToWeekPool(taskId: Long) {
        viewModelScope.launch {
            val task = executionTaskRepository.getTask(taskId)
            executionTaskRepository.moveTaskToWeekPool(taskId)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "已将任务「${task?.title ?: "未命名任务"}」放回周池",
            )
        }
    }

    fun onAddInboxTask() {
        viewModelScope.launch {
            executionTaskRepository.addInboxTask(
                title = "Inbox #${uiState.value.inboxTasks.size + 1}",
                note = "等待安排到周池或日计划。",
            )
            _uiState.value = _uiState.value.copy(feedbackMessage = "已加入 Inbox")
        }
    }

    fun onLinkSelectedDateToWeeklyPlan(weeklyPlanId: Long) {
        viewModelScope.launch {
            val targetWeek = weeklyPlanRepository.getWeeklyPlan(weeklyPlanId) ?: return@launch
            val date = selectedDateFlow.value.toString()
            val currentPlan = dailyPlanRepository.observeTodayPlan(date).first()
            if (currentPlan == null) {
                dailyPlanRepository.seedTodayIfNeeded()
            }
            val refreshedPlan = dailyPlanRepository.observeTodayPlan(date).first()
            val planId = refreshedPlan?.id ?: return@launch
            dailyPlanRepository.updateDailyPlan(
                planId = planId,
                summary = refreshedPlan.summary ?: "承接周计划：${targetWeek.title}",
                energyLevel = refreshedPlan.energyLevel ?: "MEDIUM",
                review = refreshedPlan.review.orEmpty(),
            )
            dailyPlanRepository.updateDailyPlanWeeklyLink(planId, weeklyPlanId)
            _uiState.value = _uiState.value.copy(
                feedbackMessage = "已让 ${date} 承接周计划：${targetWeek.title}",
            )
        }
    }

    fun onEditSelectedDailyPlan() {
        viewModelScope.launch {
            val date = selectedDateFlow.value.toString()
            var plan = dailyPlanRepository.observeTodayPlan(date).first()
            if (plan == null) {
                val week = weeklyPlanRepository.getWeeklyPlan(_uiState.value.selectedDailyPlanWeeklyPlanId ?: _uiState.value.weeklyPlans.firstOrNull()?.id ?: 0L)
                val createdId = dailyPlanRepository.createDailyPlan(
                    date = date,
                    weeklyPlanId = week?.id,
                    summary = "",
                )
                plan = dailyPlanRepository.getDailyPlan(createdId)
            }
            val target = plan ?: return@launch
            _uiState.value = _uiState.value.copy(
                editingDailyPlan = DailyPlanEditorUiModel(
                    id = target.id,
                    summary = target.summary.orEmpty(),
                    energyLevel = target.energyLevel ?: "MEDIUM",
                    review = target.review.orEmpty(),
                    weeklyPlanId = target.weeklyPlanId,
                )
            )
        }
    }

    fun onDismissDailyPlanEdit() {
        _uiState.value = _uiState.value.copy(editingDailyPlan = null)
    }

    fun onSaveDailyPlanEdit(
        planId: Long,
        summary: String,
        energyLevel: String,
        review: String,
        weeklyPlanId: Long?,
    ) {
        viewModelScope.launch {
            val bridgeText = _uiState.value.selectedDayWeekPoolBridgeText.trim()
            val normalizedSummary = buildString {
                append(summary.trim())
                if (bridgeText.isNotBlank() && !summary.contains(bridgeText)) {
                    if (isNotBlank()) append("\n")
                    append(bridgeText)
                }
            }.ifBlank { summary.trim() }

            dailyPlanRepository.updateDailyPlan(
                planId = planId,
                summary = normalizedSummary,
                energyLevel = energyLevel,
                review = review,
            )
            dailyPlanRepository.updateDailyPlanWeeklyLink(planId, weeklyPlanId)
            _uiState.value = _uiState.value.copy(
                editingDailyPlan = null,
                feedbackMessage = "日计划已保存并更新周关联",
            )
        }
    }

    fun onDeleteDailyPlan(planId: Long) {
        viewModelScope.launch {
            dailyPlanRepository.deleteDailyPlanCascade(planId)
            _uiState.value = _uiState.value.copy(
                editingDailyPlan = null,
                feedbackMessage = "日计划及其任务已删除",
            )
        }
    }

    fun onAddTaskForSelectedDate() {
        _uiState.value = _uiState.value.copy(
            creatingTask = TaskItemUiModel(
                id = 0L,
                title = "",
                note = "",
                status = "TODO",
                estimatedMinutes = 25,
                timeSegment = TimeSegment.MORNING.name,
                specificTime = null,
            )
        )
    }

    fun onDismissTaskCreate() {
        _uiState.value = _uiState.value.copy(creatingTask = null)
    }

    fun onCreateTaskSave(
        title: String,
        note: String,
        estimatedMinutes: Int?,
        timeSegment: String?,
        specificTime: String?,
    ) {
        viewModelScope.launch {
            executionTaskRepository.addTaskForDate(
                date = selectedDateFlow.value.toString(),
                title = title,
                note = note,
                estimatedMinutes = estimatedMinutes,
                timeSegment = timeSegment,
                specificTime = specificTime,
            )
            _uiState.value = _uiState.value.copy(
                creatingTask = null,
                feedbackMessage = "已新增日任务",
            )
        }
    }

    fun onEditTask(taskId: Long) {
        viewModelScope.launch {
            val task = executionTaskRepository.getTask(taskId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                editingTask = TaskItemUiModel(
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

    fun onDismissTaskEdit() {
        _uiState.value = _uiState.value.copy(editingTask = null)
    }

    fun onSaveTaskEdit(
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
                feedbackMessage = "任务已保存",
            )
        }
    }

    fun onDeleteTask(taskId: Long) {
        viewModelScope.launch {
            executionTaskRepository.deleteTask(taskId)
            _uiState.value = _uiState.value.copy(
                editingTask = null,
                feedbackMessage = "任务已删除",
            )
        }
    }

    fun onAddQuickMonthlyPlan() {
        val today = LocalDate.now()
        val start = today.withDayOfMonth(1).toString()
        val end = today.withDayOfMonth(today.lengthOfMonth()).toString()
        _uiState.value = _uiState.value.copy(
            creatingMonthlyPlan = MonthlyPlanItemUiModel(
                id = 0L,
                title = "",
                theme = "",
                goal = "",
                startDate = start,
                endDate = end,
                periodText = "$start - $end",
                status = "ACTIVE",
            )
        )
    }

    fun onDismissMonthlyPlanCreate() {
        _uiState.value = _uiState.value.copy(creatingMonthlyPlan = null)
    }

    fun onCreateMonthlyPlanSave(
        title: String,
        theme: String,
        goal: String,
        startDate: String,
        endDate: String,
        taskPoolSummary: String,
        review: String,
    ) {
        viewModelScope.launch {
            monthlyPlanRepository.createMonthlyPlan(
                title = title,
                theme = theme,
                goal = buildString {
                    append(goal)
                    if (taskPoolSummary.isNotBlank()) append("\n月任务池：$taskPoolSummary")
                    if (review.isNotBlank()) append("\n月复盘：$review")
                }.trim(),
                startDate = startDate,
                endDate = endDate,
            )
            _uiState.value = _uiState.value.copy(
                creatingMonthlyPlan = null,
                feedbackMessage = "已新增月计划",
            )
        }
    }

    fun onAddQuickWeeklyPlan() {
        val today = LocalDate.now()
        val start = today.with(DayOfWeek.MONDAY).toString()
        val end = today.with(DayOfWeek.SUNDAY).toString()
        val fallbackMonthlyId = _uiState.value.monthlyPlans.firstOrNull()?.id ?: 1L
        _uiState.value = _uiState.value.copy(
            creatingWeeklyPlan = WeeklyPlanItemUiModel(
                id = 0L,
                monthlyPlanId = fallbackMonthlyId,
                title = "",
                goal = "",
                weekStartDate = start,
                weekEndDate = end,
                periodText = "$start - $end",
                status = "ACTIVE",
            )
        )
    }

    fun onDismissWeeklyPlanCreate() {
        _uiState.value = _uiState.value.copy(creatingWeeklyPlan = null)
    }

    fun onCreateWeeklyPlanSave(
        title: String,
        goal: String,
        weekStartDate: String,
        weekEndDate: String,
        monthlyPlanId: Long,
        capacity: Int?,
        review: String,
        focusSummary: String,
    ) {
        viewModelScope.launch {
            weeklyPlanRepository.createWeeklyPlan(
                monthlyPlanId = monthlyPlanId,
                title = title,
                goal = buildString {
                    append(goal)
                    if (focusSummary.isNotBlank()) append("\n重点任务池：$focusSummary")
                }.trim(),
                weekStartDate = weekStartDate,
                weekEndDate = weekEndDate,
                capacity = capacity,
                review = review.ifBlank { null },
            )
            _uiState.value = _uiState.value.copy(
                creatingWeeklyPlan = null,
                feedbackMessage = "已新增周计划",
            )
        }
    }

    fun onSelectMonthlyPlan(planId: Long) {
        viewModelScope.launch {
            val plan = monthlyPlanRepository.getMonthlyPlanDetail(planId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                selectedMonthlyPlan = plan,
                editingMonthlyPlan = null,
                editingWeeklyPlan = null,
            )
        }
    }

    fun onDismissMonthlyPlanDetail() {
        _uiState.value = _uiState.value.copy(selectedMonthlyPlan = null)
    }

    fun onEditMonthlyPlan(planId: Long) {
        viewModelScope.launch {
            val plan = monthlyPlanRepository.getMonthlyPlanDetail(planId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                selectedMonthlyPlan = null,
                editingMonthlyPlan = plan,
                editingWeeklyPlan = null,
            )
        }
    }

    fun onDismissMonthlyPlanEdit() {
        _uiState.value = _uiState.value.copy(editingMonthlyPlan = null)
    }

    fun onDeleteMonthlyPlan(planId: Long) {
        viewModelScope.launch {
            monthlyPlanRepository.deleteMonthlyPlanCascade(planId)
            _uiState.value = _uiState.value.copy(
                editingMonthlyPlan = null,
                feedbackMessage = "月计划及其周/日/任务已删除",
            )
        }
    }

    fun onSaveMonthlyPlanEdit(
        planId: Long,
        title: String,
        theme: String,
        goal: String,
        startDate: String,
        endDate: String,
        taskPoolSummary: String,
        review: String,
    ) {
        viewModelScope.launch {
            monthlyPlanRepository.updateMonthlyPlan(
                planId = planId,
                title = title,
                theme = theme,
                goal = buildString {
                    append(goal)
                    if (taskPoolSummary.isNotBlank()) append("\n月任务池：$taskPoolSummary")
                    if (review.isNotBlank()) append("\n月复盘：$review")
                }.trim(),
                startDate = startDate,
                endDate = endDate,
            )
            _uiState.value = _uiState.value.copy(
                editingMonthlyPlan = null,
                feedbackMessage = "月计划已保存",
            )
        }
    }

    fun onSelectWeeklyPlan(planId: Long) {
        viewModelScope.launch {
            val plan = weeklyPlanRepository.getWeeklyPlanDetail(planId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                selectedWeeklyPlan = plan,
                editingWeeklyPlan = null,
                editingMonthlyPlan = null,
            )
        }
    }

    fun onDismissWeeklyPlanDetail() {
        _uiState.value = _uiState.value.copy(selectedWeeklyPlan = null)
    }

    fun onEditWeeklyPlan(planId: Long) {
        viewModelScope.launch {
            val plan = weeklyPlanRepository.getWeeklyPlanDetail(planId) ?: return@launch
            _uiState.value = _uiState.value.copy(
                selectedWeeklyPlan = null,
                editingWeeklyPlan = plan,
                editingMonthlyPlan = null,
            )
        }
    }

    fun onDismissWeeklyPlanEdit() {
        _uiState.value = _uiState.value.copy(editingWeeklyPlan = null)
    }

    fun onDeleteWeeklyPlan(planId: Long) {
        viewModelScope.launch {
            weeklyPlanRepository.deleteWeeklyPlanCascade(planId)
            _uiState.value = _uiState.value.copy(
                editingWeeklyPlan = null,
                feedbackMessage = "周计划及其日计划/任务已删除",
            )
        }
    }

    fun onPoolItemClick(item: com.moqim.list.feature.plans.model.PlanningPoolItemUiModel) {
        val targetLayer = when (item.source) {
            "WEEK" -> PlanningLayer.WEEK
            "MONTH" -> if (_uiState.value.monthlyPlans.any { it.childWeekCount > 0 }) PlanningLayer.WEEK else PlanningLayer.MONTH
            else -> currentLayerFlow.value
        }
        currentLayerFlow.value = targetLayer
        _uiState.value = _uiState.value.copy(
            feedbackMessage = when (item.source) {
                "MONTH" -> "池项：${item.title}，已切到${if (targetLayer == PlanningLayer.WEEK) "周层查看该月拆解" else "月层"}"
                "WEEK" -> "池项：${item.title}，已切到周层"
                else -> "池项：${item.title}（${item.source}/${item.status}）"
            },
        )
    }

    fun onFeedbackShown() {
        _uiState.value = _uiState.value.copy(feedbackMessage = null)
    }

    // ===== AI 文本导入相关 =====

    /** 打开文本导入对话框 */
    fun onShowTextImportDialog() {
        _uiState.value = _uiState.value.copy(
            showingTextImportDialog = true,
            importInputText = "",
            importPreviewText = "",
            showingImportPreview = false,
        )
    }

    /** 关闭文本导入对话框 */
    fun onDismissTextImportDialog() {
        _uiState.value = _uiState.value.copy(
            showingTextImportDialog = false,
            showingImportPreview = false,
            importInputText = "",
            importPreviewText = "",
        )
    }

    /** 更新输入框文本 */
    fun onImportInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(importInputText = text)
    }

    /** 点击"预览"：解析文本并显示预览 */
    fun onPreviewImport() {
        val input = _uiState.value.importInputText.trim()
        if (input.isBlank()) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "请先粘贴计划文本")
            return
        }
        val preview = com.moqim.list.data.importplan.PlanTextParser.parseToPreview(input)
        if (preview == null) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "无法识别计划格式，请检查文本")
            return
        }
        _uiState.value = _uiState.value.copy(
            importPreviewText = preview,
            showingImportPreview = true,
        )
    }

    /** 确认导入 */
    fun onConfirmImport() {
        val input = _uiState.value.importInputText.trim()
        val planJson = com.moqim.list.data.importplan.PlanTextParser.parseToPlanJson(input)
        if (planJson == null) {
            _uiState.value = _uiState.value.copy(feedbackMessage = "解析失败，请检查文本格式")
            return
        }
        viewModelScope.launch {
            try {
                val result = com.moqim.list.data.importplan.PlanImportService().import(planJson, appContext)
                _uiState.value = _uiState.value.copy(
                    showingTextImportDialog = false,
                    showingImportPreview = false,
                    importInputText = "",
                    importPreviewText = "",
                    feedbackMessage = "导入成功！新增 ${result.createdTaskCount} 条任务",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    feedbackMessage = "导入失败：${e.message}",
                )
            }
        }
    }

    /** 返回编辑输入（从预览状态回到输入状态） */
    fun onBackToImportEdit() {
        _uiState.value = _uiState.value.copy(showingImportPreview = false)
    }

    /** 模板已复制到剪贴板 */
    fun onTemplateCopied() {
        _uiState.value = _uiState.value.copy(feedbackMessage = "模板已复制到剪贴板，去豆包/Kimi等AI里粘贴")
    }

    fun onSaveWeeklyPlanEdit(
        planId: Long,
        title: String,
        goal: String,
        weekStartDate: String,
        weekEndDate: String,
        monthlyPlanId: Long,
        capacity: Int?,
        review: String,
        focusSummary: String,
    ) {
        viewModelScope.launch {
            weeklyPlanRepository.updateWeeklyPlan(
                planId = planId,
                title = title,
                goal = buildString {
                    append(goal)
                    if (focusSummary.isNotBlank()) append("\n重点任务池：$focusSummary")
                }.trim(),
                weekStartDate = weekStartDate,
                weekEndDate = weekEndDate,
                monthlyPlanId = monthlyPlanId,
                capacity = capacity,
                review = review.ifBlank { null },
            )
            _uiState.value = _uiState.value.copy(
                editingWeeklyPlan = null,
                feedbackMessage = "周计划已保存并更新月关联",
            )
        }
    }

    private fun toMonthlyPlanItemUiModel(
        plan: MonthlyPlanSummary,
        childWeekCount: Int,
    ): MonthlyPlanItemUiModel {
        val goalText = plan.goal.orEmpty()
        val taskPoolSummary = extractTaggedLine(goalText, "月任务池：")
        val review = extractTaggedTail(goalText, "月复盘：")
        return MonthlyPlanItemUiModel(
            id = plan.id,
            title = plan.title,
            theme = plan.theme,
            goal = plan.goal,
            startDate = plan.startDate,
            endDate = plan.endDate,
            periodText = plan.periodText,
            status = plan.status,
            review = review,
            progressText = when {
                childWeekCount <= 0 -> "本月尚未拆出周计划"
                else -> "已拆解 ${childWeekCount} 个周计划，继续向日计划推进"
            },
            childWeekCount = childWeekCount,
            taskPoolSummary = taskPoolSummary,
            taskPoolItems = splitSummaryItems(taskPoolSummary),
        )
    }

    private fun toWeeklyPlanItemUiModel(
        plan: WeeklyPlanSummary,
        capacity: Int?,
        review: String?,
    ): WeeklyPlanItemUiModel {
        val goalText = plan.goal.orEmpty()
        val focusSummary = extractTaggedTail(goalText, "重点任务池：")
        return WeeklyPlanItemUiModel(
            id = plan.id,
            monthlyPlanId = plan.monthlyPlanId,
            title = plan.title,
            goal = plan.goal,
            weekStartDate = plan.weekStartDate,
            weekEndDate = plan.weekEndDate,
            periodText = plan.periodText,
            status = plan.status,
            capacity = capacity,
            review = review.orEmpty(),
            focusSummary = focusSummary,
            focusItems = splitSummaryItems(focusSummary),
        )
    }

    private fun extractTaggedLine(text: String, tag: String): String {
        return text.substringAfter(tag, "").substringBefore("\n").trim()
    }

    private fun extractTaggedTail(text: String, tag: String): String {
        return text.substringAfter(tag, "").trim()
    }

    private fun splitSummaryItems(summary: String): List<com.moqim.list.feature.plans.model.PlanningPoolItemUiModel> {
        return summary
            .split("；", "、", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapIndexed { index, text ->
                com.moqim.list.feature.plans.model.PlanningPoolItemUiModel(
                    title = text,
                    kind = when {
                        index == 0 -> "FOCUS"
                        text.contains("复盘") || text.contains("反馈") -> "REVIEW"
                        else -> "INFO"
                    },
                    source = when {
                        text.contains("周") -> "WEEK"
                        text.contains("月") -> "MONTH"
                        else -> "MANUAL"
                    },
                    status = when {
                        text.contains("完成") || text.contains("已做") -> "DONE"
                        else -> "ACTIVE"
                    },
                    referenceKey = "${index}_${text.take(12)}",
                )
            }
    }

    private suspend fun seedPlanningCenterTemporaryListsIfNeeded() {
        // seed 逻辑已移除
    }

    private fun buildSegmentUi(tasks: List<PlanningTaskUiModel>): List<PlanningSegmentUiModel> {
        return TimeSegment.entries.map { segment ->
            val segmentTasks = tasks.filter { it.timeSegment == segment.name }
            val minutes = segmentTasks.sumOf { it.estimatedMinutes ?: 0 }
            PlanningSegmentUiModel(
                segment = segment,
                summary = when {
                    segmentTasks.isEmpty() -> "留空，给真正重要的事。"
                    segmentTasks.any { it.isTopFocus } -> "这个时段有主线任务 · ${segmentTasks.size} 项 · ${minutes} 分钟"
                    else -> "安排 ${segmentTasks.size} 项推进事项 · ${minutes} 分钟"
                },
                tasks = segmentTasks,
            )
        }
    }

    private fun buildDayLoadSummary(tasks: List<PlanningTaskUiModel>): String {
        if (tasks.isEmpty()) return "今日负载：暂无待执行任务"
        val segmentSummaries = TimeSegment.entries.mapNotNull { segment ->
            val segmentTasks = tasks.filter { it.timeSegment == segment.name }
            if (segmentTasks.isEmpty()) {
                null
            } else {
                val minutes = segmentTasks.sumOf { it.estimatedMinutes ?: 0 }
                "${segment.label}${segmentTasks.size}项/${minutes}分钟"
            }
        }
        return if (segmentSummaries.isEmpty()) {
            "今日负载：${tasks.size} 项待执行，尚未分配到具体时段"
        } else {
            "今日负载：" + segmentSummaries.joinToString(" · ")
        }
    }

    private fun buildCapacitySummary(
        totalPlannedMinutes: Int,
        assignedMinutes: Int,
        weekCapacity: Int?,
    ): String {
        return if (weekCapacity != null && weekCapacity > 0) {
            val percent = ((totalPlannedMinutes.toFloat() / weekCapacity.toFloat()) * 100).toInt()
            "容量视图：今日计划 ${totalPlannedMinutes} 分钟，已分配 ${assignedMinutes} 分钟，占周容量 ${percent}%"
        } else {
            "容量视图：今日计划 ${totalPlannedMinutes} 分钟，已分配 ${assignedMinutes} 分钟，周容量未设置"
        }
    }

    private fun selectedDailyPlanWeeklyTitle(
        baseState: PlanningBaseState,
        weeklyPlanId: Long?,
    ): String = baseState.weeklyPlans.firstOrNull { it.id == weeklyPlanId }?.title.orEmpty()

    private fun selectedDayTemporaryUiCount(
        dayTemporaryUi: List<PlanningTaskUiModel>,
    ): Int = dayTemporaryUi.size

    private fun buildDateChips(selectedDate: LocalDate): List<PlanningDateChipUiModel> {
        val start = selectedDate.with(DayOfWeek.MONDAY)
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("E")
        return (0..6).map { offset ->
            val date = start.plusDays(offset.toLong())
            PlanningDateChipUiModel(
                date = date.toString(),
                dayLabel = date.format(formatter),
                dayNumber = date.dayOfMonth.toString(),
                isSelected = date == selectedDate,
                isToday = date == today,
            )
        }
    }

    private fun toPlanningTask(task: com.moqim.list.domain.model.ExecutionTaskSummary): PlanningTaskUiModel {
        return PlanningTaskUiModel(
            id = task.id,
            title = task.title,
            note = task.note.orEmpty(),
            status = task.status,
            estimatedMinutes = task.estimatedMinutes,
            timeSegment = task.timeSegment,
            sourceType = "",
            isTopFocus = task.isTopFocus,
        )
    }

    private data class PlanningBaseState(
        val monthlyPlans: List<MonthlyPlanSummary>,
        val weeklyPlans: List<WeeklyPlanSummary>,
        val selectedDate: LocalDate,
        val currentLayer: PlanningLayer,
        val selectedTemporaryListKey: String,
    )

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val db = DatabaseProvider.get(context)
            val monthlyPlanRepository = RoomMonthlyPlanRepository(
                monthlyPlanDao = db.monthlyPlanDao(),
                weeklyPlanDao = db.weeklyPlanDao(),
                dailyPlanDao = db.dailyPlanDao(),
                executionTaskDao = db.executionTaskDao(),
            )
            val weeklyPlanRepository = RoomWeeklyPlanRepository(
                weeklyPlanDao = db.weeklyPlanDao(),
                monthlyPlanDao = db.monthlyPlanDao(),
                dailyPlanDao = db.dailyPlanDao(),
                executionTaskDao = db.executionTaskDao(),
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
            val habitRepository = RoomHabitRepository(
                habitTemplateDao = db.habitTemplateDao(),
                habitRecordDao = db.habitRecordDao(),
            )

            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlansViewModel(
                        appContext = context.applicationContext,
                        monthlyPlanRepository = monthlyPlanRepository,
                        weeklyPlanRepository = weeklyPlanRepository,
                        dailyPlanRepository = dailyPlanRepository,
                        executionTaskRepository = executionTaskRepository,
                        habitRepository = habitRepository,
                    ) as T
                }
            }
        }
    }
}
