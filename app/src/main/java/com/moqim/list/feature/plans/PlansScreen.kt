package com.moqim.list.feature.plans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.feature.home.model.DailyPlanEditorUiModel
import com.moqim.list.feature.home.model.TaskItemUiModel
import com.moqim.list.feature.plans.components.MonthlyPlanEditDialog
import com.moqim.list.feature.plans.components.WeeklyPlanEditDialog
import com.moqim.list.feature.plans.model.MonthlyPlanItemUiModel
import com.moqim.list.feature.plans.model.PlanningLayer
import com.moqim.list.feature.plans.model.PlanningSegmentUiModel
import com.moqim.list.feature.plans.model.PlanningTaskUiModel
import com.moqim.list.feature.plans.model.PlansUiState
import com.moqim.list.feature.plans.model.WeeklyPlanItemUiModel
import com.moqim.list.feature.plans.model.WeeklyPlanProgressUiModel

@Composable
fun PlansScreen() {
    val context = LocalContext.current
    val viewModel: PlansViewModel = viewModel(
        factory = PlansViewModel.factory(context),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onFeedbackShown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(46.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                PlanningHeroCard(
                    title = uiState.title,
                    description = uiState.description,
                )
            }

            item {
                SnackbarHost(hostState = snackbarHostState)
            }

            item {
                LayerSwitcher(
                    currentLayer = uiState.currentLayer,
                    onLayerSelected = viewModel::onLayerSelected,
                )
            }

            item {
                QuickActionsRow(
                    currentLayer = uiState.currentLayer,
                    onAddMonthly = viewModel::onAddQuickMonthlyPlan,
                    onAddWeekly = viewModel::onAddQuickWeeklyPlan,
                    onAddDaily = viewModel::onEditSelectedDailyPlan,
                    onAddTask = viewModel::onAddTaskForSelectedDate,
                    onAiImport = viewModel::onShowTextImportDialog,
                    onCopyTemplate = {
                        val template = com.moqim.list.data.importplan.PlanTextTemplate.generateTemplateWithCurrentWeek()
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("moqim_template", template))
                        viewModel.onTemplateCopied()
                    },
                )
            }

            item {
                DateChipRow(
                    uiState = uiState,
                    onDateSelected = viewModel::onDateSelected,
                )
            }

            when (uiState.currentLayer) {
                PlanningLayer.MONTH -> {
                    item {
                        SectionTitle(
                            title = "月计划 → 周计划",
                            subtitle = "月计划定义主线，周计划负责拆解和推进",
                        )
                    }
                    if (uiState.monthlyPlans.isEmpty()) {
                        item { EmptyCard("还没有月计划", "先建立这个月真正要推进的主线。") }
                    } else {
                        items(uiState.monthlyPlans) { month ->
                            LinkedMonthPlanCard(
                                plan = month,
                                weeklyPlans = uiState.weeklyPlans.filter { it.monthlyPlanId == month.id },
                                weeklyProgress = uiState.weeklyProgress,
                                onMonthClick = { viewModel.onSelectMonthlyPlan(month.id) },
                                onWeekClick = { weekId -> viewModel.onSelectWeeklyPlan(weekId) },
                                onPoolItemClick = viewModel::onPoolItemClick,
                            )
                        }
                    }
                }

                PlanningLayer.WEEK -> {
                    item {
                        SectionTitle(
                            title = "周计划 → 日计划",
                            subtitle = "周计划应该明确拆到哪几天，并持续推进",
                        )
                    }
                    if (uiState.weeklyPlans.isEmpty()) {
                        item { EmptyCard("还没有周计划", "先把月目标拆到本周。") }
                    } else {
                        items(uiState.weeklyPlans) { week ->
                            WeeklyPlanWithDayBridgeCard(
                                plan = week,
                                progress = uiState.weeklyProgress.firstOrNull { it.weeklyPlanId == week.id },
                                selectedDate = uiState.selectedDate,
                                onClick = { viewModel.onSelectWeeklyPlan(week.id) },
                                onLinkToSelectedDate = { viewModel.onLinkSelectedDateToWeeklyPlan(week.id) },
                                onPoolItemClick = viewModel::onPoolItemClick,
                            )
                        }
                    }

                    item {
                        SectionTitle(
                            title = "周任务池",
                            subtitle = "从周层任务下沉到具体日时段",
                        )
                    }
                    if (uiState.weekPoolTasks.isEmpty()) {
                        item { EmptyCard("周池暂时为空", "这里后续应该承接周计划拆解项。") }
                    } else {
                        item {
                            EmptyInlineHint(uiState.weekPoolHint)
                        }
                        items(uiState.weekPoolTasks) { task ->
                            AssignableTaskCard(
                                task = task,
                                onAssign = { segment -> viewModel.onAssignTaskToSegment(task.id, segment) },
                                onEdit = { viewModel.onEditTask(task.id) },
                            )
                        }
                    }
                }

                PlanningLayer.DAY -> {
                    item {
                        SectionTitle(
                            title = "日计划 → 执行任务",
                            subtitle = "每日计划承接周计划，再由执行任务真正落地",
                        )
                    }

                    item {
                        DailyPlanSummaryCard(
                            uiState = uiState,
                            onEditDailyPlan = viewModel::onEditSelectedDailyPlan,
                            onAddTask = viewModel::onAddTaskForSelectedDate,
                        )
                    }

                    item {
                        SectionTitle(
                            title = "日时段任务",
                            subtitle = "所有任务都必须能编辑、删除、调整时段",
                        )
                    }

                    items(uiState.daySegments) { segment ->
                        SegmentCard(
                            segment = segment,
                            onTaskClick = { taskId -> viewModel.onEditTask(taskId) },
                            onMoveBackToWeekPool = { taskId -> viewModel.onMoveTaskBackToWeekPool(taskId) },
                        )
                    }

                    item {
                        SectionTitle(
                            title = "临时事务",
                            subtitle = "当天插入但默认不进入主线推导",
                        )
                    }

                    if (uiState.dayTemporaryTasks.isEmpty()) {
                        item { EmptyCard("暂无临时事务", "临时事务出现后会在这里集中展示。") }
                    } else {
                        items(uiState.dayTemporaryTasks) { task ->
                            PlannedTaskRow(
                                task = task,
                                onClick = { viewModel.onEditTask(task.id) },
                            )
                        }
                    }

                    item {
                        SectionTitle(
                            title = "已完成",
                            subtitle = "已经完成的任务收在这里，避免干扰主线",
                        )
                    }

                    if (uiState.completedDayTasks.isEmpty()) {
                        item { EmptyCard("今天还没有已完成任务", "完成后会自动进入这个区块。") }
                    } else {
                        items(uiState.completedDayTasks) { task ->
                            PlannedTaskRow(
                                task = task,
                                onClick = { viewModel.onEditTask(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.selectedMonthlyPlan?.let { plan ->
        MonthlyPlanDetailDialog(
            plan = plan,
            linkedWeeks = uiState.weeklyPlans.filter { it.monthlyPlanId == plan.id },
            onDismiss = viewModel::onDismissMonthlyPlanDetail,
            onEdit = {
                viewModel.onEditMonthlyPlan(plan.id)
            },
        )
    }

    uiState.editingMonthlyPlan?.let { plan ->
        MonthlyPlanEditDialog(
            plan = plan,
            onDismiss = viewModel::onDismissMonthlyPlanEdit,
            onSave = { title, theme, goal, startDate, endDate, taskPoolSummary, review ->
                viewModel.onSaveMonthlyPlanEdit(
                    planId = plan.id,
                    title = title,
                    theme = theme,
                    goal = goal,
                    startDate = startDate,
                    endDate = endDate,
                    taskPoolSummary = taskPoolSummary,
                    review = review,
                )
            },
            onDelete = { viewModel.onDeleteMonthlyPlan(plan.id) },
        )
    }

    uiState.selectedWeeklyPlan?.let { plan ->
        WeeklyPlanDetailDialog(
            plan = plan,
            linkedDates = uiState.weeklyProgress.firstOrNull { it.weeklyPlanId == plan.id }?.linkedDates.orEmpty(),
            onDismiss = viewModel::onDismissWeeklyPlanDetail,
            onEdit = {
                viewModel.onEditWeeklyPlan(plan.id)
            },
        )
    }

    uiState.editingWeeklyPlan?.let { plan ->
        WeeklyPlanEditDialog(
            plan = plan,
            monthlyPlans = uiState.monthlyPlans,
            onDismiss = viewModel::onDismissWeeklyPlanEdit,
            onSave = { title, goal, weekStartDate, weekEndDate, monthlyPlanId, capacity, review, focusSummary ->
                viewModel.onSaveWeeklyPlanEdit(
                    planId = plan.id,
                    title = title,
                    goal = goal,
                    weekStartDate = weekStartDate,
                    weekEndDate = weekEndDate,
                    monthlyPlanId = monthlyPlanId,
                    capacity = capacity,
                    review = review,
                    focusSummary = focusSummary,
                )
            },
            onDelete = { viewModel.onDeleteWeeklyPlan(plan.id) },
        )
    }

    uiState.creatingMonthlyPlan?.let { plan ->
        MonthlyPlanEditDialog(
            plan = plan,
            onDismiss = viewModel::onDismissMonthlyPlanCreate,
            onSave = { title, theme, goal, startDate, endDate, taskPoolSummary, review ->
                viewModel.onCreateMonthlyPlanSave(
                    title = title,
                    theme = theme,
                    goal = goal,
                    startDate = startDate,
                    endDate = endDate,
                    taskPoolSummary = taskPoolSummary,
                    review = review,
                )
            },
            onDelete = viewModel::onDismissMonthlyPlanCreate,
        )
    }

    uiState.creatingWeeklyPlan?.let { plan ->
        WeeklyPlanEditDialog(
            plan = plan,
            monthlyPlans = uiState.monthlyPlans,
            onDismiss = viewModel::onDismissWeeklyPlanCreate,
            onSave = { title, goal, weekStartDate, weekEndDate, monthlyPlanId, capacity, review, focusSummary ->
                viewModel.onCreateWeeklyPlanSave(
                    title = title,
                    goal = goal,
                    weekStartDate = weekStartDate,
                    weekEndDate = weekEndDate,
                    monthlyPlanId = monthlyPlanId,
                    capacity = capacity,
                    review = review,
                    focusSummary = focusSummary,
                )
            },
            onDelete = viewModel::onDismissWeeklyPlanCreate,
        )
    }

    uiState.editingDailyPlan?.let { plan ->
        DailyPlanEditDialog(
            plan = plan,
            weeklyPlans = uiState.weeklyPlans,
            onDismiss = viewModel::onDismissDailyPlanEdit,
            onSave = { summary, energyLevel, review, weeklyPlanId ->
                viewModel.onSaveDailyPlanEdit(
                    planId = plan.id,
                    summary = summary,
                    energyLevel = energyLevel,
                    review = review,
                    weeklyPlanId = weeklyPlanId,
                )
            },
            onDelete = { viewModel.onDeleteDailyPlan(plan.id) },
        )
    }

    uiState.editingTask?.let { task ->
        TaskEditDialog(
            task = task,
            onDismiss = viewModel::onDismissTaskEdit,
            onSave = { title, note, minutes, segment, specificTime ->
                viewModel.onSaveTaskEdit(
                    taskId = task.id,
                    title = title,
                    note = note,
                    estimatedMinutes = minutes,
                    timeSegment = segment,
                    specificTime = specificTime,
                )
            },
            onDelete = { viewModel.onDeleteTask(task.id) },
            isCreateMode = false,
        )
    }

    uiState.creatingTask?.let { task ->
        TaskEditDialog(
            task = task,
            onDismiss = viewModel::onDismissTaskCreate,
            onSave = { title, note, minutes, segment, specificTime ->
                viewModel.onCreateTaskSave(
                    title = title,
                    note = note,
                    estimatedMinutes = minutes,
                    timeSegment = segment,
                    specificTime = specificTime,
                )
            },
            onDelete = viewModel::onDismissTaskCreate,
            isCreateMode = true,
        )
    }

    if (uiState.showingTextImportDialog) {
        AiImportDialog(
            inputText = uiState.importInputText,
            previewText = uiState.importPreviewText,
            showPreview = uiState.showingImportPreview,
            onInputChange = viewModel::onImportInputChanged,
            onPreview = viewModel::onPreviewImport,
            onConfirm = viewModel::onConfirmImport,
            onBack = viewModel::onBackToImportEdit,
            onDismiss = viewModel::onDismissTextImportDialog,
        )
    }
}

@Composable
private fun PlanningHeroCard(
    title: String,
    description: String,
) {
    GlassCard {
        Text(
            text = "Planning Center",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LayerSwitcher(
    currentLayer: PlanningLayer,
    onLayerSelected: (PlanningLayer) -> Unit,
) {
    GlassCard {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanningLayer.entries.forEach { layer ->
                val selected = layer == currentLayer
                AssistChip(
                    onClick = { onLayerSelected(layer) },
                    label = {
                        Text(
                            when (layer) {
                                PlanningLayer.MONTH -> "月"
                                PlanningLayer.WEEK -> "周"
                                PlanningLayer.DAY -> "日"
                            }
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    currentLayer: PlanningLayer,
    onAddMonthly: () -> Unit,
    onAddWeekly: () -> Unit,
    onAddDaily: () -> Unit,
    onAddTask: () -> Unit,
    onAiImport: () -> Unit,
    onCopyTemplate: () -> Unit,
) {
    GlassCard {
        Text(
            text = "Quick actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            when (currentLayer) {
                PlanningLayer.MONTH -> {
                    Button(onClick = onAddMonthly, shape = RoundedCornerShape(20.dp)) { Text("新增月计划") }
                    Button(onClick = onAddWeekly, shape = RoundedCornerShape(20.dp)) { Text("新增周计划") }
                }
                PlanningLayer.WEEK -> {
                    Button(onClick = onAddWeekly, shape = RoundedCornerShape(20.dp)) { Text("新增周计划") }
                    Button(onClick = onAddDaily, shape = RoundedCornerShape(20.dp)) { Text("新增日计划") }
                }
                PlanningLayer.DAY -> {
                    Button(onClick = onAddDaily, shape = RoundedCornerShape(20.dp)) { Text("编辑日计划") }
                    Button(onClick = onAddTask, shape = RoundedCornerShape(20.dp)) { Text("新增任务") }
                }
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                modifier = Modifier.clickable(onClick = onAiImport),
            ) {
                Text(
                    text = "🤖 AI导入",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                modifier = Modifier.clickable(onClick = onCopyTemplate),
            ) {
                Text(
                    text = "📋 复制模板",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun DateChipRow(
    uiState: PlansUiState,
    onDateSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        uiState.dateChips.forEach { chip ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (chip.isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
                modifier = Modifier.clickable { onDateSelected(chip.date) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(chip.dayLabel, style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = chip.dayNumber + if (chip.isToday) " · 今" else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemporaryListSwitcher(
    uiState: PlansUiState,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        uiState.temporaryLists.forEach { list ->
            val selected = list.key == uiState.selectedTemporaryListKey
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(list.accent).copy(alpha = if (selected) 0.26f else 0.16f),
                modifier = Modifier.clickable { onSelect(list.key) },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = list.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${list.tasks.size} 项 · ${list.caption}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentCard(
    segment: PlanningSegmentUiModel,
    onTaskClick: (Long) -> Unit,
    onMoveBackToWeekPool: (Long) -> Unit,
) {
    GlassCard {
        Text(
            text = "${segment.segment.label}时段",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = segment.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (segment.tasks.isEmpty()) {
            EmptyInlineHint("暂无任务，可直接新增，或从周任务池分配进来。")
        } else {
                segment.tasks.forEach { task ->
                    PlannedTaskRow(
                        task = task,
                        onClick = { onTaskClick(task.id) },
                        onMoveBackToWeekPool = if (task.sourceType == "WEEK_POOL_ASSIGNED") {
                            { onMoveBackToWeekPool(task.id) }
                        } else {
                            null
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
        }
    }
}

@Composable
private fun AssignableTaskCard(
    task: PlanningTaskUiModel,
    onAssign: (TimeSegment) -> Unit,
    onEdit: () -> Unit,
) {
    GlassCard {
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (task.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = task.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("编辑") }
            TimeSegment.entries.forEach { segment ->
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onAssign(segment) },
                ) {
                    Text(
                        text = segment.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlannedTaskRow(
    task: PlanningTaskUiModel,
    onClick: () -> Unit,
    onMoveBackToWeekPool: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (task.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            task.estimatedMinutes?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${it} 分钟",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            onMoveBackToWeekPool?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "放回周池",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.clickable(onClick = it),
                )
            }
        }
    }
}

@Composable
private fun PlanPoolSummaryBlock(
    title: String,
    summary: String,
    items: List<com.moqim.list.feature.plans.model.PlanningPoolItemUiModel>,
    onItemClick: ((com.moqim.list.feature.plans.model.PlanningPoolItemUiModel) -> Unit)? = null,
) {
    if (summary.isBlank() && items.isEmpty()) return
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    if (summary.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items.take(4).forEach {
        val prefix = when (it.kind) {
            "FOCUS" -> "◎"
            "REVIEW" -> "↺"
            else -> "•"
        }
        val sourceLabel = when (it.source) {
            "WEEK" -> "周"
            "MONTH" -> "月"
            else -> "手动"
        }
        Text(
            text = "$prefix [$sourceLabel/${if (it.status == "DONE") "已完成" else "进行中"}] ${it.title}",
            style = MaterialTheme.typography.bodySmall,
            color = when (it.kind) {
                "FOCUS" -> MaterialTheme.colorScheme.primary
                "REVIEW" -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = if (onItemClick != null) Modifier.clickable { onItemClick(it) } else Modifier,
        )
        it.referenceKey?.let { key ->
            Text(
                text = "key: $key",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun MonthlyPlanCard(
    plan: MonthlyPlanItemUiModel,
    onClick: () -> Unit,
) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = plan.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = plan.periodText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "主题：${plan.theme}",
            style = MaterialTheme.typography.bodyMedium,
        )
        plan.goal?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("查看详情") }
    }
}

@Composable
private fun MonthlyPlanDetailDialog(
    plan: MonthlyPlanItemUiModel,
    linkedWeeks: List<WeeklyPlanItemUiModel>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("月计划详情") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "月度主题",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = plan.theme.ifBlank { "未设置主题" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "周期：${plan.periodText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "状态：${plan.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                plan.progressText.takeIf { it.isNotBlank() }?.let {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                    ) {
                        Text(
                            text = "当前进度：$it",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }

                plan.goal?.takeIf { it.isNotBlank() }?.let {
                    DetailBlock(
                        title = "本月目标",
                        content = it,
                    )
                }

                if (plan.taskPoolItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "月任务池",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        plan.taskPoolItems.forEachIndexed { index, item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${index + 1}. ${item.title}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val meta = listOfNotNull(
                                        item.kind.takeIf { it.isNotBlank() },
                                        item.source.takeIf { it.isNotBlank() },
                                        item.status.takeIf { it.isNotBlank() },
                                        item.referenceKey?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (plan.taskPoolSummary.isNotBlank()) {
                    DetailBlock(
                        title = "月任务池",
                        content = plan.taskPoolSummary,
                    )
                }

                plan.review.takeIf { it.isNotBlank() }?.let {
                    DetailBlock(
                        title = "月复盘",
                        content = it,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "关联周计划 · ${linkedWeeks.size} 个",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (linkedWeeks.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                        ) {
                            Text(
                                text = "这个月计划还没有拆解到周，可以下一步继续细化。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        linkedWeeks.forEach { week ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = week.title,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = week.periodText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    week.focusSummary.takeIf { it.isNotBlank() }?.let { summary ->
                                        Text(
                                            text = "重点：$summary",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("编辑月计划") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun DetailBlock(
    title: String,
    content: String,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun WeeklyPlanCard(
    plan: WeeklyPlanItemUiModel,
    onClick: () -> Unit,
) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = plan.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = plan.periodText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        plan.goal?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        plan.focusSummary.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "重点任务池：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        plan.capacity?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "周容量：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        plan.review.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "周复盘：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text("查看详情") }
    }
}

@Composable
private fun WeeklyPlanDetailDialog(
    plan: WeeklyPlanItemUiModel,
    linkedDates: List<String>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周计划详情") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "本周摘要",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "周期：${plan.periodText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "状态：${plan.status}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = plan.capacity?.let { "周容量：$it" } ?: "周容量：未设置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                plan.goal?.takeIf { it.isNotBlank() }?.let {
                    DetailBlock(
                        title = "本周目标",
                        content = it,
                    )
                }

                if (plan.focusItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "重点任务池",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        plan.focusItems.forEachIndexed { index, item ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "${index + 1}. ${item.title}",
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    val meta = listOfNotNull(
                                        item.kind.takeIf { it.isNotBlank() },
                                        item.source.takeIf { it.isNotBlank() },
                                        item.status.takeIf { it.isNotBlank() },
                                        item.referenceKey?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (plan.focusSummary.isNotBlank()) {
                    DetailBlock(
                        title = "重点任务池",
                        content = plan.focusSummary,
                    )
                }

                plan.review.takeIf { it.isNotBlank() }?.let {
                    DetailBlock(
                        title = "周复盘",
                        content = it,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "已承接到 ${linkedDates.size} 天",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (linkedDates.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                        ) {
                            Text(
                                text = "这周计划还没有承接到具体日期，可以下一步继续拆到天。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            )
                        }
                    } else {
                        linkedDates.forEach { date ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            ) {
                                Text(
                                    text = date,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("编辑周计划") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun LinkedMonthPlanCard(
    plan: MonthlyPlanItemUiModel,
    weeklyPlans: List<WeeklyPlanItemUiModel>,
    weeklyProgress: List<WeeklyPlanProgressUiModel>,
    onMonthClick: () -> Unit,
    onWeekClick: (Long) -> Unit,
    onPoolItemClick: (com.moqim.list.feature.plans.model.PlanningPoolItemUiModel) -> Unit,
) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plan.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = plan.periodText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "主题：${plan.theme}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                plan.goal?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PlanPoolSummaryBlock(
                    title = "月任务池",
                    summary = plan.taskPoolSummary,
                    items = plan.taskPoolItems,
                    onItemClick = onPoolItemClick,
                )
                plan.progressText.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = plan.progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                plan.review.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "月复盘：$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onMonthClick) { Text("编辑月计划") }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "拆解到周",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (weeklyPlans.isEmpty()) {
            EmptyInlineHint("这个月计划还没有拆出周计划。")
        } else {
            weeklyPlans.forEach { week ->
                val progress = weeklyProgress.firstOrNull { it.weeklyPlanId == week.id }
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onWeekClick(week.id) },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = week.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = week.periodText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        week.goal?.takeIf { it.isNotBlank() }?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        PlanPoolSummaryBlock(
                            title = "周重点任务池",
                            summary = week.focusSummary,
                            items = week.focusItems,
                        )
                        week.capacity?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "周容量：$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        week.review.takeIf { it.isNotBlank() }?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "周复盘：$it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        progress?.let {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "已下沉 ${it.linkedDates.size} 天 · 完成 ${it.doneTaskCount}/${it.totalTaskCount} 项",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun WeeklyPlanWithDayBridgeCard(
    plan: WeeklyPlanItemUiModel,
    progress: WeeklyPlanProgressUiModel?,
    selectedDate: String,
    onClick: () -> Unit,
    onLinkToSelectedDate: () -> Unit,
    onPoolItemClick: (com.moqim.list.feature.plans.model.PlanningPoolItemUiModel) -> Unit,
) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = plan.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = plan.periodText,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "当前查看日期：$selectedDate",
            style = MaterialTheme.typography.bodyMedium,
        )
        plan.goal?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "本周目标：$it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        plan.focusSummary.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "重点任务池：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        plan.capacity?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "周容量：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        plan.review.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "周复盘：$it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "这里应该继续把周计划拆到某一天。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        progress?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "已关联日期：${if (it.linkedDates.isEmpty()) "暂无" else it.linkedDates.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "周任务推进：${it.doneTaskCount}/${it.totalTaskCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = onLinkToSelectedDate, shape = RoundedCornerShape(18.dp)) {
            Text("让 ${selectedDate} 承接这个周计划")
        }
    }
}

@Composable
private fun DailyPlanSummaryCard(
    uiState: PlansUiState,
    onEditDailyPlan: () -> Unit,
    onAddTask: () -> Unit,
) {
    GlassCard {
        Text(
            text = "日计划摘要",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (uiState.selectedDailyPlanSummary.isBlank()) "这一天还没有日计划摘要。" else uiState.selectedDailyPlanSummary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "承接周计划：${uiState.selectedDailyPlanWeeklyPlanTitle.ifBlank { "暂未关联" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "能量：${uiState.selectedDailyPlanEnergy}  ·  复盘：${uiState.selectedDailyPlanReview.ifBlank { "暂无" }}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.selectedDayWeekPoolBridgeText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = uiState.selectedDayWeekPoolBridgeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (uiState.selectedDayTopFocusTitles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "今日 Top 3",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            uiState.selectedDayTopFocusTitles.forEachIndexed { index, item ->
                Text(
                    text = "${index + 1}. $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        if (uiState.selectedDaySuggestionItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "参考建议",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            uiState.selectedDaySuggestionItems.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
        if (uiState.selectedDayLoadSummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = uiState.selectedDayLoadSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (uiState.selectedDayCapacitySummary.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = uiState.selectedDayCapacitySummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "今日反馈：已完成 ${uiState.selectedDayCompletedCount} 项 · 临时事务 ${uiState.selectedDayTemporaryCount} 项",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = uiState.selectedDayHabitProgressText.ifBlank { "今日打卡：暂无数据" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEditDailyPlan, shape = RoundedCornerShape(20.dp)) {
                Text("编辑日计划")
            }
            Button(onClick = onAddTask, shape = RoundedCornerShape(20.dp)) {
                Text("新增任务")
            }
        }
    }
}

@Composable
private fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyCard(
    title: String,
    subtitle: String,
) {
    GlassCard {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyInlineHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TaskEditDialog(
    task: TaskItemUiModel,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?, String?, String?) -> Unit,
    onDelete: () -> Unit,
    isCreateMode: Boolean,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var note by remember(task.id) { mutableStateOf(task.note) }
    var minutesText by remember(task.id) { mutableStateOf(task.estimatedMinutes?.toString().orEmpty()) }
    var selectedSegment by remember(task.id) { mutableStateOf(task.timeSegment ?: TimeSegment.MORNING.name) }
    var specificTimeText by remember(task.id) { mutableStateOf(task.specificTime.orEmpty()) }

    val trimmedTitle = title.trim()
    val parsedMinutes = minutesText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreateMode) "新增任务" else "编辑任务") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("预计分钟数") },
                )
                Text(
                    text = "时段：${selectedSegment.toSegmentLabel()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = specificTimeText,
                    onValueChange = { input ->
                        specificTimeText = input
                            .filter { it.isDigit() || it == ':' }
                            .take(5)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("具体时间点（可选，HH:mm）") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeSegment.entries.forEach { segment ->
                        TextButton(onClick = { selectedSegment = segment.name }) {
                            Text(if (selectedSegment == segment.name) "✓ ${segment.label}" else segment.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (trimmedTitle.isNotBlank()) {
                        onSave(
                            trimmedTitle,
                            note.trim(),
                            parsedMinutes,
                            selectedSegment,
                            specificTimeText.trim().ifBlank { null },
                        )
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(if (isCreateMode) "取消" else "删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DailyPlanEditDialog(
    plan: DailyPlanEditorUiModel,
    weeklyPlans: List<WeeklyPlanItemUiModel>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Long?) -> Unit,
    onDelete: () -> Unit,
) {
    var summary by remember(plan.id) { mutableStateOf(plan.summary) }
    var energyLevel by remember(plan.id) { mutableStateOf(plan.energyLevel) }
    var review by remember(plan.id) { mutableStateOf(plan.review) }
    var weeklyPlanIdText by remember(plan.id) { mutableStateOf(plan.weeklyPlanId?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑日计划") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (weeklyPlans.isNotEmpty()) {
                    Text(
                        text = "选择关联周计划",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        weeklyPlans.forEach { week ->
                            val selected = weeklyPlanIdText == week.id.toString()
                            AssistChip(
                                onClick = { weeklyPlanIdText = week.id.toString() },
                                label = { Text(week.title) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                            )
                        }
                        AssistChip(
                            onClick = { weeklyPlanIdText = "" },
                            label = { Text("不关联") },
                        )
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("日计划摘要") },
                )
                OutlinedTextField(
                    value = energyLevel,
                    onValueChange = { energyLevel = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("能量等级") },
                )
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("复盘/备注") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        summary.trim(),
                        energyLevel.trim().ifBlank { "MEDIUM" },
                        review.trim(),
                        weeklyPlanIdText.trim().toLongOrNull(),
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}

private fun String.toSegmentLabel(): String = TimeSegment.entries
    .firstOrNull { it.name == this }
    ?.label
    ?: this

@Composable
private fun AiImportDialog(
    inputText: String,
    previewText: String,
    showPreview: Boolean,
    onInputChange: (String) -> Unit,
    onPreview: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🤖 AI 导入计划") },
        text = {
            if (showPreview) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "从豆包、Kimi 等 AI 复制计划文本，粘贴到下方：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        label = { Text("粘贴计划文本") },
                        placeholder = { Text("## 月计划\n- 标题：...\n\n## 今日任务\n1. [MORNING] 任务标题") },
                    )
                }
            }
        },
        confirmButton = {
            if (showPreview) {
                Button(onClick = onConfirm, shape = RoundedCornerShape(20.dp)) {
                    Text("确认导入")
                }
            } else {
                Button(
                    onClick = onPreview,
                    shape = RoundedCornerShape(20.dp),
                    enabled = inputText.isNotBlank(),
                ) {
                    Text("预览")
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showPreview) {
                    TextButton(onClick = onBack) { Text("返回修改") }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
