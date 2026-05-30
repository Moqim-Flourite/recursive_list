package com.moqim.list.feature.home

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.core.time.TimeSegmentResolver
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.feature.home.components.CompletedTasksCard
import com.moqim.list.feature.home.components.FloatingHabitBar
import com.moqim.list.feature.home.components.FocusedTaskListCard
import com.moqim.list.feature.home.components.FullDayOverviewCard
import com.moqim.list.feature.home.components.MorningDashboardCard
import com.moqim.list.feature.home.components.TodayHeroCard
import com.moqim.list.feature.home.model.TaskItemUiModel
import rikka.shizuku.Shizuku

@Composable
fun TodayScreen() {
    val context = LocalContext.current
    val viewModel: TodayViewModel = viewModel(
        factory = TodayViewModel.factory(context),
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val morningViewVisible by viewModel.morningViewVisible.collectAsStateWithLifecycle()
    val availableApps by viewModel.availableApps.collectAsStateWithLifecycle()
    val availableAppsLoading by viewModel.availableAppsLoading.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var allDayExpanded by rememberSaveable { mutableStateOf(false) }
    val settingsRepository = remember(context) { SettingsPreferencesRepository(context.applicationContext) }
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.moqim.list.data.preferences.SettingsPreferences(),
    )

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onFeedbackShown()
        }
    }

    val currentSegment = TimeSegmentResolver.resolveNow(settings)
    val currentSegmentSummary = uiState.segmentSummaries.firstOrNull { it.segment == currentSegment }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 132.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (morningViewVisible) {
                item {
                    MorningDashboardCard(
                        dateText = uiState.dateText,
                        weeklySummary = uiState.weeklySummary,
                        habitsSummary = uiState.habitsSummary,
                        carryOverSummary = uiState.carryOverSummary,
                        focusItems = uiState.morningFocusItems,
                        onClose = {
                            viewModel.dismissMorningViewForToday()
                            allDayExpanded = true
                        },
                        onStartToday = {
                            viewModel.dismissMorningViewForToday()
                            allDayExpanded = true
                        },
                        onViewAll = {
                            allDayExpanded = true
                        },
                    )
                }
            }

            item {
                TodayHeroCard(
                    title = "${currentSegment.label} · 深度执行",
                    target = uiState.weeklySummary,
                )
            }

            item {
                SnackbarHost(hostState = snackbarHostState)
            }

            currentSegmentSummary?.let { segmentSummary ->
                item {
                    FocusedTaskListCard(
                    title = "当前时段任务",
                    timeLeft = currentSegmentTimeHint(currentSegment),
                    tasks = segmentSummary.tasks,
                    onTaskClick = viewModel::onTaskClick,
                    onTaskEdit = viewModel::onTaskEdit,
                    onTaskMoveNext = viewModel::onTaskMoveNext,
                    onTaskMoveToSegment = viewModel::onTaskMoveToSegment,
                    onTaskDelete = viewModel::onTaskDelete,
                )
                }
            }

            item {
                TextButton(onClick = { allDayExpanded = !allDayExpanded }) {
                    Text(if (allDayExpanded) "收起全天计划" else "查看全天计划")
                }
            }

            if (allDayExpanded) {
                item {
                    FullDayOverviewCard(
                        segments = uiState.segmentSummaries.filterNot { it.segment == currentSegment },
                        onTaskClick = viewModel::onTaskClick,
                        onTaskEdit = viewModel::onTaskEdit,
                    )
                }

                items(
                    uiState.segmentSummaries.filterNot { it.segment == currentSegment },
                    key = { it.segment.name },
                ) { segmentSummary ->
                    com.moqim.list.feature.home.components.SegmentSectionCard(
                        title = segmentSummary.segment.label,
                        summary = segmentSummary.summary,
                        tasks = segmentSummary.tasks,
                        loadLabel = segmentSummary.loadLabel,
                        isCurrentSegment = false,
                        onTaskClick = viewModel::onTaskClick,
                        onTaskQuickEdit = viewModel::onTaskQuickEdit,
                        onTaskEdit = viewModel::onTaskEdit,
                        onTaskMoveNext = viewModel::onTaskMoveNext,
                        onTaskMoveToSegment = viewModel::onTaskMoveToSegment,
                        onTaskDelete = viewModel::onTaskDelete,
                    )
                }

                if (!morningViewVisible) {
                    item {
                        MorningDashboardCard(
                    dateText = uiState.dateText,
                    weeklySummary = uiState.weeklySummary,
                    habitsSummary = uiState.habitsSummary,
                    carryOverSummary = uiState.carryOverSummary,
                    focusItems = uiState.morningFocusItems,
                    onClose = {},
                    onStartToday = {
                        allDayExpanded = true
                    },
                    onViewAll = {
                        allDayExpanded = true
                    },
                )
                    }
                }
            }

            item {
                CompletedTasksCard(
                    tasks = uiState.completedTasks,
                    onTaskClick = viewModel::onTaskClick,
                    onTaskDelete = viewModel::onTaskDelete,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .align(androidx.compose.ui.Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = viewModel::showHabitManager) {
                    Text("管理打卡")
                }
            }
            FloatingHabitBar(
                items = uiState.habitItems,
                reorderMode = uiState.habitReorderMode,
                deleteMode = uiState.habitDeleteMode,
                onToggleReorderMode = {
                    if (uiState.habitReorderMode) viewModel.persistHabitOrder()
                    else viewModel.showHabitOrderDialog()
                },
                onToggleDeleteMode = {
                    if (uiState.habitDeleteMode) viewModel.exitHabitDeleteMode()
                    else viewModel.enterHabitDeleteMode()
                },
                onHabitMove = viewModel::onHabitMove,
                onHabitClick = viewModel::onHabitClick,
                onHabitUndo = viewModel::onHabitUndo,
                onHabitDelete = viewModel::onHabitDelete,
                onHabitLongPress = viewModel::onHabitLongPress,
                onAddHabit = viewModel::onAddHabitTemplate,
            )
        }
    }

    uiState.editingTask?.let { task ->
        TaskEditDialog(
            task = task,
            onDismiss = viewModel::onTaskEditDismiss,
            onSave = { title, note, estimatedMinutes, timeSegment, specificTime ->
                viewModel.onTaskEditSave(
                    taskId = task.id,
                    title = title,
                    note = note,
                    estimatedMinutes = estimatedMinutes,
                    timeSegment = timeSegment,
                    specificTime = specificTime,
                )
            },
        )
    }

    uiState.editingDailyPlan?.let { plan ->
        DailyPlanEditDialog(
            plan = plan,
            onDismiss = viewModel::onDailyPlanEditDismiss,
            onSave = { summary, energyLevel, review ->
                viewModel.onDailyPlanEditSave(
                    planId = plan.id,
                    summary = summary,
                    energyLevel = energyLevel,
                    review = review,
                )
            },
        )
    }

    uiState.editingHabit?.let { habit ->
        HabitEditDialog(
            habit = habit,
            availableApps = availableApps.map {
                InstalledAppOption(
                    label = it.label,
                    packageName = it.packageName,
                    iconBitmap = it.iconBitmap,
                )
            },
            availableAppsLoading = availableAppsLoading,
            onLoadApps = viewModel::loadAvailableApps,
            onDismiss = viewModel::onHabitEditDismiss,
            onSave = { title, iconLabel, dailyTargetCount, completedCount, iconUri, targetAppPackageName, showTotalCompletedDays, baseCompletedDays ->
                if (habit.templateId == 0L) {
                    viewModel.onCreateHabitSave(
                        title = title,
                        iconLabel = iconLabel,
                        dailyTargetCount = dailyTargetCount,
                        completedCount = completedCount,
                        iconUri = iconUri,
                        targetAppPackageName = targetAppPackageName,
                        showTotalCompletedDays = showTotalCompletedDays,
                        baseCompletedDays = baseCompletedDays,
                    )
                } else {
                    viewModel.onHabitEditSave(
                        templateId = habit.templateId,
                        title = title,
                        iconLabel = iconLabel,
                        dailyTargetCount = dailyTargetCount,
                        completedCount = completedCount,
                        iconUri = iconUri,
                        targetAppPackageName = targetAppPackageName,
                        showTotalCompletedDays = showTotalCompletedDays,
                        baseCompletedDays = baseCompletedDays,
                    )
                }
            },
            onDelete = if (habit.templateId == 0L) null else { { viewModel.onHabitDelete(habit.templateId) } },
        )
    }

    if (uiState.habitManagerVisible) {
        HabitManagerDialog(
            items = uiState.habitItems,
            onDismiss = viewModel::dismissHabitManager,
            onAddHabit = viewModel::onAddHabitTemplate,
            onEditHabit = viewModel::onHabitLongPress,
            onSkipHabit = viewModel::onHabitSkip,
            onDeleteHabit = viewModel::onHabitDelete,
            onOpenApp = viewModel::onOpenHabitTargetApp,
        )
    }

    if (uiState.habitOrderDialogVisible) {
        HabitReorderDialog(
            items = uiState.habitItems,
            onMove = viewModel::onHabitMove,
            onDismiss = viewModel::dismissHabitOrderDialog,
            onSave = viewModel::persistHabitOrder,
        )
    }
}

@Composable
private fun TaskEditDialog(
    task: TaskItemUiModel,
    onDismiss: () -> Unit,
    onSave: (String, String, Int?, String?, String?) -> Unit,
) {
    var title by remember(task.id) { mutableStateOf(task.title) }
    var note by remember(task.id) { mutableStateOf(task.note) }
    var minutesText by remember(task.id) { mutableStateOf(task.estimatedMinutes?.toString().orEmpty()) }
    var selectedSegment by remember(task.id) { mutableStateOf(task.timeSegment ?: TimeSegment.MORNING.name) }
    var specificTimeText by remember(task.id) { mutableStateOf(task.specificTime.orEmpty()) }

    val trimmedTitle = title.trim()
    val parsedMinutes = minutesText.toIntOrNull()
    val titleError = if (trimmedTitle.isBlank()) "标题不能为空" else null
    val minutesError = when {
        minutesText.isBlank() -> null
        parsedMinutes == null -> "预计分钟数必须是数字"
        parsedMinutes <= 0 -> "预计分钟数必须大于 0"
        else -> null
    }
    val canSave = titleError == null && minutesError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "任务详情")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "像微软 To Do 一样，把任务信息集中在一个轻面板里编辑。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    singleLine = true,
                    isError = titleError != null,
                    supportingText = titleError?.let { { Text(it) } },
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
                    singleLine = true,
                    isError = minutesError != null,
                    supportingText = minutesError?.let { { Text(it) } },
                )
                Text(
                    text = "安排到：${selectedSegment.toSegmentLabel()}",
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
                    singleLine = true,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    TimeSegment.entries.forEach { segment ->
                        TextButton(
                            onClick = { selectedSegment = segment.name },
                        ) {
                            Text(
                                text = if (selectedSegment == segment.name) "✓ ${segment.label}" else segment.label,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
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
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}
private fun currentSegmentTimeHint(segment: TimeSegment): String = when (segment) {
    TimeSegment.MORNING_START -> "启动时段"
    TimeSegment.MORNING -> "深度推进窗口"
    TimeSegment.NOON -> "轻量缓冲窗口"
    TimeSegment.AFTERNOON -> "推进 / 沟通窗口"
    TimeSegment.EVENING -> "收尾 / 复盘窗口"
}

@Composable
private fun HabitManagerDialog(
    items: List<com.moqim.list.feature.home.model.HabitItemUiModel>,
    onDismiss: () -> Unit,
    onAddHabit: () -> Unit,
    onEditHabit: (Long) -> Unit,
    onSkipHabit: (Long) -> Unit,
    onDeleteHabit: (Long) -> Unit,
    onOpenApp: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("打卡模板管理") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "这里集中管理每日打卡模板：查看当前进度、连续情况、目标 App 与基础配置，并支持新增、编辑、跳过、删除。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (items.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
                    ) {
                        Text(
                            text = "还没有打卡模板。先新增一个，把每天重复的小事从主线任务里独立出来。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        )
                    }
                } else {
                    items.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = when (item.status) {
                                                "SKIPPED" -> "今日已跳过"
                                                "DONE" -> "今日已完成"
                                                "IN_PROGRESS" -> "今日进行中"
                                                else -> "今日待完成"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Text(
                                        text = "${item.completedCount}/${item.dailyTargetCount}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = buildString {
                                            append("预计时长：")
                                            append(item.estimatedMinutes?.let { "${it} 分钟" } ?: "未设置")
                                            item.preferredTimeSegment?.takeIf { it.isNotBlank() }?.let {
                                                append(" · 推荐时段：")
                                                append(it.toSegmentLabel())
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = buildString {
                                            append("目标 App：")
                                            append(item.targetAppPackageName ?: "未绑定")
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = buildString {
                                            if (item.showTotalCompletedDays) {
                                                append("连续 ${item.currentStreakDays} 天")
                                                append(" · 累计 ${item.totalCompletedDays + item.baseCompletedDays} 天")
                                            } else {
                                                append("已关闭总打卡天数展示")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    TextButton(onClick = { onOpenApp(item.templateId) }) {
                                        Text("打开 App")
                                    }
                                    TextButton(onClick = { onEditHabit(item.templateId) }) {
                                        Text("编辑")
                                    }
                                    TextButton(onClick = { onSkipHabit(item.templateId) }) {
                                        Text("跳过")
                                    }
                                    TextButton(onClick = { onDeleteHabit(item.templateId) }) {
                                        Text("删除")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAddHabit) { Text("新增模板") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun HabitReorderDialog(
    items: List<com.moqim.list.feature.home.model.HabitItemUiModel>,
    onMove: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("排序 Habit") },
        text = {
            var draggingTemplateId by remember(items) { mutableStateOf(-1L) }
            var draggingStartIndex by remember(items) { mutableStateOf(-1) }
            var draggingTargetIndex by remember(items) { mutableStateOf(-1) }
            var dragOffsetY by remember(items) { mutableStateOf(0f) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _: Int, item: com.moqim.list.feature.home.model.HabitItemUiModel -> item.templateId },
                ) { index: Int, item: com.moqim.list.feature.home.model.HabitItemUiModel ->
                    val isDragging = draggingTemplateId == item.templateId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = if (isDragging) dragOffsetY else 0f
                                alpha = if (isDragging) 0.94f else 1f
                                scaleX = if (isDragging) 1.02f else 1f
                                scaleY = if (isDragging) 1.02f else 1f
                                shadowElevation = if (isDragging) 18.dp.toPx() else 0f
                            }
                            .pointerInput(items.map { it.templateId }, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingTemplateId = item.templateId
                                        draggingStartIndex = index
                                        draggingTargetIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        val currentIndex = items.indexOfFirst { it.templateId == item.templateId }
                                        val targetIndex = draggingTargetIndex
                                        if (currentIndex != -1 && targetIndex != -1 && currentIndex != targetIndex) {
                                            onMove(currentIndex, targetIndex)
                                        }
                                        draggingTemplateId = -1L
                                        draggingStartIndex = -1
                                        draggingTargetIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingTemplateId = -1L
                                        draggingStartIndex = -1
                                        draggingTargetIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val estimatedIndexShift = (dragOffsetY / 68f).toInt()
                                        val rawTarget = draggingStartIndex + estimatedIndexShift
                                        draggingTargetIndex = rawTarget.coerceIn(0, items.lastIndex)
                                    },
                                )
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = when {
                            isDragging -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            index == draggingTargetIndex && draggingTemplateId != -1L -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = when {
                                        isDragging -> "拖到目标位置后松手"
                                        index == draggingTargetIndex && draggingTemplateId != -1L -> "将在这里落位"
                                        else -> "长按后拖到任意位置"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        isDragging -> MaterialTheme.colorScheme.primary
                                        index == draggingTargetIndex && draggingTemplateId != -1L -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                            Text(
                                text = "☰",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun DailyPlanEditDialog(
    plan: com.moqim.list.feature.home.model.DailyPlanEditorUiModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var summary by remember(plan.id) { mutableStateOf(plan.summary) }
    var energyLevel by remember(plan.id) { mutableStateOf(plan.energyLevel) }
    var review by remember(plan.id) { mutableStateOf(plan.review) }

    val trimmedSummary = summary.trim()
    val trimmedEnergyLevel = energyLevel.trim().ifBlank { "MEDIUM" }
    val trimmedReview = review.trim()
    val summaryError = if (trimmedSummary.isBlank()) "日计划不能为空" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑今日计划") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "这里定义今天的节奏、能量和一句简短复盘。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("日计划摘要") },
                    isError = summaryError != null,
                    supportingText = summaryError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = energyLevel,
                    onValueChange = { energyLevel = it.uppercase() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("能量等级（LOW/MEDIUM/HIGH）") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("复盘 / 备注") },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (summaryError == null) {
                        onSave(trimmedSummary, trimmedEnergyLevel, trimmedReview)
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun HabitEditDialog(
    habit: com.moqim.list.feature.home.model.HabitEditorUiModel,
    availableApps: List<InstalledAppOption>,
    availableAppsLoading: Boolean,
    onLoadApps: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, String?, String?, Boolean, Int) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var title by remember(habit.templateId) { mutableStateOf(habit.title) }
    var iconLabel by remember(habit.templateId) { mutableStateOf(habit.iconLabel) }
    var dailyTargetText by remember(habit.templateId) { mutableStateOf(habit.dailyTargetCount.toString()) }
    var completedText by remember(habit.templateId) { mutableStateOf(habit.completedCount.toString()) }
    var iconUri by remember(habit.templateId) { mutableStateOf(habit.iconUri) }
    var targetAppPackageName by remember(habit.templateId) { mutableStateOf(habit.targetAppPackageName.orEmpty()) }
    var appPickerVisible by remember(habit.templateId) { mutableStateOf(false) }
    var appSearchQuery by remember(habit.templateId) { mutableStateOf("") }
    var shizukuPermissionResult by remember(habit.templateId) { mutableIntStateOf(-1) }
    var showTotalCompletedDays by remember(habit.templateId) { mutableStateOf(habit.showTotalCompletedDays) }
    var baseCompletedDaysText by remember(habit.templateId) { mutableStateOf(habit.baseCompletedDays.toString()) }

    val permissionListener = remember {
        Shizuku.OnRequestPermissionResultListener { _, grantResult ->
            shizukuPermissionResult = grantResult
        }
    }

    LaunchedEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    LaunchedEffect(habit.templateId, shizukuPermissionResult) {
        if (ShizukuAppScanner.isShizukuAvailable() && ShizukuAppScanner.hasPermission()) {
            onLoadApps(false)
        }
    }

    val filteredApps = remember(appSearchQuery, availableApps) {
        val query = appSearchQuery.trim()
        if (query.isBlank()) {
            availableApps
        } else {
            availableApps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            iconUri = it.toString()
        }
    }

    val parsedDailyTarget = (dailyTargetText.toIntOrNull() ?: 1).coerceAtLeast(1)
    val parsedCompleted = (completedText.toIntOrNull() ?: 0).coerceIn(0, parsedDailyTarget)
    val parsedBaseCompletedDays = (baseCompletedDaysText.toIntOrNull() ?: 0).coerceAtLeast(0)
    val isCreateMode = habit.templateId == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCreateMode) "新增打卡项" else "编辑打卡项") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "支持从本机已安装应用里直接选择，例如多邻国。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = targetAppPackageName,
                    onValueChange = { targetAppPackageName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("App 包名（可选）") },
                    singleLine = true,
                )
                TextButton(onClick = { appPickerVisible = true }) {
                    Text("从已安装应用中选择")
                }
                if (!ShizukuAppScanner.hasPermission()) {
                    TextButton(
                        onClick = {
                            if (ShizukuAppScanner.isShizukuAvailable()) {
                                Shizuku.requestPermission(1001)
                            }
                        }
                    ) {
                        Text(
                            if (ShizukuAppScanner.isShizukuAvailable()) "授权 Shizuku 读取第三方应用"
                            else "未检测到 Shizuku / Sui"
                        )
                    }
                }
                OutlinedTextField(
                    value = iconLabel,
                    onValueChange = { iconLabel = it.take(2) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("图标字符") },
                    singleLine = true,
                )
                TextButton(onClick = { imagePicker.launch("image/*") }) {
                    Text(if (iconUri.isNullOrBlank()) "选择图片图标" else "更换图片图标")
                }
                if (!iconUri.isNullOrBlank()) {
                    Text(
                        text = "已选择图片图标",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = dailyTargetText,
                    onValueChange = { dailyTargetText = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("每天目标次数") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = completedText,
                    onValueChange = { completedText = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("今天已完成次数") },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "显示总打卡天数",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = showTotalCompletedDays,
                        onCheckedChange = { showTotalCompletedDays = it },
                    )
                }
                OutlinedTextField(
                    value = baseCompletedDaysText,
                    onValueChange = { baseCompletedDaysText = it.filter { ch -> ch.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("历史累计打卡天数") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        iconLabel,
                        parsedDailyTarget,
                        parsedCompleted,
                        iconUri,
                        targetAppPackageName.trim().ifBlank { null },
                        showTotalCompletedDays,
                        parsedBaseCompletedDays,
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )

    if (appPickerVisible) {
        AlertDialog(
            onDismissRequest = { appPickerVisible = false },
            title = { Text("选择已安装应用") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索应用名或包名") },
                        singleLine = true,
                    )
                    if (!ShizukuAppScanner.hasPermission()) {
                        Text(
                            text = "请先授权 Shizuku，再读取真实第三方应用列表。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (availableAppsLoading) {
                        Text(
                            text = "正在加载应用列表…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (filteredApps.isEmpty()) {
                        Text(
                            text = "Shizuku 已授权，但还没读取到第三方应用列表。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { onLoadApps(true) }) {
                            Text("重新扫描")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp),
                        ) {
                            item {
                                Text(
                                    text = "已安装第三方应用",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            items(filteredApps) { app ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            title = if (title.isBlank()) app.label else title
                                            targetAppPackageName = app.packageName
                                            appPickerVisible = false
                                        },
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val bitmap = app.iconBitmap ?: runCatching {
                                            context.packageManager.getApplicationIcon(app.packageName).toBitmapSafely()
                                        }.getOrNull()
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = app.label,
                                                modifier = Modifier.size(36.dp),
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            Text(
                                                text = app.label,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                text = app.packageName,
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
                TextButton(onClick = { appPickerVisible = false }) {
                    Text("关闭")
                }
            },
        )
    }
}

private data class InstalledAppOption(
    val label: String,
    val packageName: String,
    val iconBitmap: Bitmap? = null,
)

private fun android.graphics.drawable.Drawable.toBitmapSafely(): Bitmap? {
    return runCatching {
        val width = intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap
    }.getOrNull()
}

private fun String.toSegmentLabel(): String = TimeSegment.entries
    .firstOrNull { it.name == this }
    ?.label
    ?: this
