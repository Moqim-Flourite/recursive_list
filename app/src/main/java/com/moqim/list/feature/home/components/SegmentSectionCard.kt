package com.moqim.list.feature.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.feature.home.model.TaskItemUiModel

@Composable
fun SegmentSectionCard(
    title: String,
    summary: String,
    tasks: List<TaskItemUiModel> = emptyList(),
    loadLabel: String = "0 项",
    isCurrentSegment: Boolean = false,
    onTaskClick: (Long) -> Unit = {},
    onTaskQuickEdit: (Long) -> Unit = {},
    onTaskEdit: (Long) -> Unit = {},
    onTaskMoveNext: (Long) -> Unit = {},
    onTaskMoveToSegment: (Long, String) -> Unit = { _, _ -> },
    onTaskDelete: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isCurrent = isCurrentSegment
    var expanded by remember(title, isCurrent) { mutableStateOf(isCurrent) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = if (isCurrent) "Current Segment" else "Full Day Overview",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "$summary · $loadLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        when {
                            isCurrent && expanded -> "收起"
                            isCurrent -> "展开"
                            expanded -> "收起"
                            else -> "展开"
                        }
                    )
                }
            }

            AnimatedVisibility(visible = isCurrent || expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (tasks.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                        ) {
                            Text(
                                text = if (isCurrent) "当前时段暂无任务，可以提前准备下一段或补一个轻量动作。" else "这个时段还没有任务。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                    } else {
                        tasks.forEach { task ->
                            TaskTodoRow(
                                title = task.title,
                                meta = task.estimatedMinutes?.let { "${it} 分钟" } ?: "未设置时长",
                                currentSegment = task.timeSegment,
                                onToggle = { onTaskClick(task.id) },
                                onEdit = { onTaskEdit(task.id) },
                                onMoveNext = { onTaskMoveNext(task.id) },
                                onMoveToSegment = { target -> onTaskMoveToSegment(task.id, target) },
                                onDelete = { onTaskDelete(task.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskTodoRow(
    title: String,
    meta: String,
    currentSegment: String?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMoveNext: () -> Unit,
    onMoveToSegment: (String) -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                modifier = Modifier.clickable(onClick = onToggle),
            ) {
                Text(
                    text = "○",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                LightAction("编辑", onEdit)
                LightAction("下一段", onMoveNext)
                TimeSegment.entries.forEach { segment ->
                    if (segment.name != currentSegment) {
                        LightAction(
                            text = "转到${segment.label}",
                            onClick = { onMoveToSegment(segment.name) },
                        )
                    }
                }
                LightAction("删除", onDelete, destructive = true)
            }
        }
    }
}

@Composable
private fun LightAction(
    text: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
