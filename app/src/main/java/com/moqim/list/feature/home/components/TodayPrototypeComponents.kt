package com.moqim.list.feature.home.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.feature.home.model.HabitItemUiModel
import com.moqim.list.feature.home.model.SegmentSummaryUiModel
import com.moqim.list.feature.home.model.TaskItemUiModel

@Composable
fun TodayHeroCard(
    title: String,
    target: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "我的一天",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "当前目标：$target",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "72%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun FocusedTaskListCard(
    title: String,
    timeLeft: String,
    tasks: List<TaskItemUiModel>,
    onTaskClick: (Long) -> Unit,
    onTaskEdit: (Long) -> Unit,
    onTaskMoveNext: (Long) -> Unit = {},
    onTaskMoveToSegment: (Long, String) -> Unit = { _, _ -> },
    onTaskDelete: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                shape = RoundedCornerShape(30.dp),
            ),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Current Segment",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        ) {
                            Text(
                                text = "进行中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = timeLeft,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (tasks.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
                ) {
                    Text(
                        text = "当前时段还没有任务。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    )
                }
            } else {
                tasks.forEach { task ->
                    FocusedTaskRow(
                        title = task.title,
                        meta = task.estimatedMinutes?.let { "所属周计划 · ${it} 分钟" } ?: "所属周计划 · 未设置时长",
                        currentSegment = task.timeSegment,
                        onToggle = { onTaskClick(task.id) },
                        onOpen = { onTaskEdit(task.id) },
                        onMoveNext = { onTaskMoveNext(task.id) },
                        onMoveToSegment = { target -> onTaskMoveToSegment(task.id, target) },
                        onDelete = { onTaskDelete(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusedTaskRow(
    title: String,
    meta: String,
    currentSegment: String?,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onMoveNext: () -> Unit,
    onMoveToSegment: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(22.dp),
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.50f),
                        shape = CircleShape,
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "○",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "当前主线 · $meta",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpen),
                )
                Text(
                    text = "下一段",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onMoveNext),
                )
                TimeSegment.entries.forEach { segment ->
                    if (segment.name != currentSegment) {
                        Text(
                            text = "转到${segment.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onMoveToSegment(segment.name) },
                        )
                    }
                }
                Text(
                    text = "删除",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(onClick = onDelete),
                )
            }
        }
    }
}

@Composable
fun FullDayOverviewCard(
    segments: List<SegmentSummaryUiModel>,
    onTaskClick: (Long) -> Unit,
    onTaskEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Full Day Overview",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            segments.forEachIndexed { index, segment ->
                val faded = index >= 1
                Column(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = if (faded) 0.36f else 0.52f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = segment.segment.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (segment.tasks.isEmpty()) {
                        Text(
                            text = "暂无任务",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        segment.tasks.forEach { task ->
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable { onTaskEdit(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingHabitBar(
    items: List<HabitItemUiModel>,
    reorderMode: Boolean,
    deleteMode: Boolean,
    onToggleReorderMode: () -> Unit,
    onToggleDeleteMode: () -> Unit,
    onHabitMove: (Int, Int) -> Unit,
    onHabitClick: (Long) -> Unit,
    onHabitUndo: (Long) -> Unit,
    onHabitDelete: (Long) -> Unit,
    onHabitLongPress: (Long) -> Unit,
    onAddHabit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentSegment = currentUiTimeSegment()
    var actionMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val fullyDone = item.completedCount >= item.dailyTargetCount
                val highlighted = item.preferredTimeSegment == currentSegment.name

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (fullyDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (highlighted) 0.78f else 0.60f),
                                shape = RoundedCornerShape(22.dp),
                            )
                            .combinedClickable(
                                onClick = { onHabitClick(item.templateId) },
                                onLongClick = { onHabitLongPress(item.templateId) },
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HabitIcon(
                            iconUri = item.iconUri,
                            iconLabel = item.iconLabel,
                            targetAppPackageName = item.targetAppPackageName,
                            done = fullyDone,
                        )
                        HabitStageDots(
                            dailyTargetCount = item.dailyTargetCount,
                            completedCount = item.completedCount,
                            highlighted = highlighted,
                        )
                        if (item.showTotalCompletedDays) {
                            Text(
                                text = "${item.baseCompletedDays + item.totalCompletedDays}天",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (deleteMode) {
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { onHabitDelete(item.templateId) },
                        )
                    } else if (item.completedCount > 0) {
                        Text(
                            text = "撤销",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onHabitUndo(item.templateId) },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.width(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            color = if (reorderMode || deleteMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .clickable {
                            if (reorderMode) {
                                onToggleReorderMode()
                                actionMenuExpanded = false
                            } else if (deleteMode) {
                                onToggleDeleteMode()
                                actionMenuExpanded = false
                            } else {
                                actionMenuExpanded = !actionMenuExpanded
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (reorderMode || deleteMode) "完成" else "＋",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (reorderMode || deleteMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }

                if (actionMenuExpanded && !reorderMode && !deleteMode) {
                    Column(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(18.dp),
                            )
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "新增",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable {
                                    actionMenuExpanded = false
                                    onAddHabit()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            text = "排序",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable {
                                    actionMenuExpanded = false
                                    onToggleReorderMode()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        Text(
                            text = "删除",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .clickable {
                                    actionMenuExpanded = false
                                    onToggleDeleteMode()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitIcon(
    iconUri: String?,
    iconLabel: String,
    targetAppPackageName: String?,
    done: Boolean,
) {
    val context = LocalContext.current
    val appBitmap = remember(targetAppPackageName) {
        targetAppPackageName?.let { packageName ->
            runCatching {
                val packageManager = context.packageManager
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                val drawable = when {
                    launchIntent?.component != null -> {
                        packageManager.getActivityIcon(launchIntent.component!!)
                    }
                    else -> {
                        packageManager.getApplicationIcon(packageName)
                    }
                }
                drawable.toBitmapSafe()
            }.recoverCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmapSafe()
            }.getOrNull()
        }
    }
    val fileBitmap = remember(iconUri) {
        iconUri?.let { value ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value))?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }
    val bitmap = appBitmap ?: fileBitmap

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Habit Icon",
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                    shape = CircleShape,
                ),
            contentScale = ContentScale.Crop,
        )
    } else {
        Text(
            text = when {
                !targetAppPackageName.isNullOrBlank() && targetAppPackageName.contains("duolingo", ignoreCase = true) -> "D"
                !targetAppPackageName.isNullOrBlank() && targetAppPackageName.contains("weixin", ignoreCase = true) -> "微"
                !targetAppPackageName.isNullOrBlank() && targetAppPackageName.contains("qq", ignoreCase = true) -> "Q"
                else -> iconLabel
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun HabitStageDots(
    dailyTargetCount: Int,
    completedCount: Int,
    highlighted: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dailyTargetCount.coerceAtLeast(1)) { index ->
            val done = index < completedCount
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = when {
                            done -> MaterialTheme.colorScheme.primary
                            highlighted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                        },
                        shape = CircleShape,
                    ),
            )
        }
    }
}

private fun currentUiTimeSegment(): TimeSegment {
    val now = java.time.LocalTime.now()
    return when {
        now.isBefore(java.time.LocalTime.of(8, 0)) -> TimeSegment.MORNING_START
        now.isBefore(java.time.LocalTime.of(11, 0)) -> TimeSegment.MORNING
        now.isBefore(java.time.LocalTime.of(14, 0)) -> TimeSegment.NOON
        now.isBefore(java.time.LocalTime.of(18, 0)) -> TimeSegment.AFTERNOON
        else -> TimeSegment.EVENING
    }
}

private fun Drawable.toBitmapSafe(): Bitmap {
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}