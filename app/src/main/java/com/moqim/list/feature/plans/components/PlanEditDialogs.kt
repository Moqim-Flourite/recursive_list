package com.moqim.list.feature.plans.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moqim.list.feature.plans.model.MonthlyPlanItemUiModel
import com.moqim.list.feature.plans.model.WeeklyPlanItemUiModel

@Composable
fun MonthlyPlanEditDialog(
    plan: MonthlyPlanItemUiModel,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(plan.id) { mutableStateOf(plan.title) }
    var theme by remember(plan.id) { mutableStateOf(plan.theme) }
    var goal by remember(plan.id) { mutableStateOf(plan.goal.orEmpty()) }
    var startDate by remember(plan.id) { mutableStateOf(plan.startDate) }
    var endDate by remember(plan.id) { mutableStateOf(plan.endDate) }
    var taskPoolSummary by remember(plan.id) { mutableStateOf(plan.taskPoolSummary) }
    var review by remember(plan.id) { mutableStateOf(plan.review) }

    val trimmedTitle = title.trim()
    val trimmedTheme = theme.trim()
    val trimmedGoal = goal.trim()
    val trimmedStartDate = startDate.trim()
    val trimmedEndDate = endDate.trim()
    val trimmedTaskPoolSummary = taskPoolSummary.trim()
    val trimmedReview = review.trim()
    val titleError = if (trimmedTitle.isBlank()) "标题不能为空" else null
    val themeError = if (trimmedTheme.isBlank()) "主题不能为空" else null
    val startDateError = validateDate(trimmedStartDate, "开始日期")
    val endDateError = validateDate(trimmedEndDate, "结束日期")
    val rangeError = when {
        startDateError != null || endDateError != null -> null
        trimmedStartDate > trimmedEndDate -> "结束日期不能早于开始日期"
        else -> null
    }
    val canSave = titleError == null &&
        themeError == null &&
        startDateError == null &&
        endDateError == null &&
        rangeError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("月计划详情") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "把本月主题、目标和时间范围集中在一个轻面板里编辑。",
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
                    value = theme,
                    onValueChange = { theme = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("主题") },
                    singleLine = true,
                    isError = themeError != null,
                    supportingText = themeError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标") },
                )
                OutlinedTextField(
                    value = taskPoolSummary,
                    onValueChange = { taskPoolSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("月任务池摘要") },
                )
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("月复盘") },
                )
                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("开始日期（YYYY-MM-DD）") },
                    singleLine = true,
                    isError = startDateError != null || rangeError != null,
                    supportingText = (startDateError ?: rangeError)?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("结束日期（YYYY-MM-DD）") },
                    singleLine = true,
                    isError = endDateError != null || rangeError != null,
                    supportingText = (endDateError ?: rangeError)?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(
                            trimmedTitle,
                            trimmedTheme,
                            trimmedGoal,
                            trimmedStartDate,
                            trimmedEndDate,
                            trimmedTaskPoolSummary,
                            trimmedReview,
                        )
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        },
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WeeklyPlanEditDialog(
    plan: WeeklyPlanItemUiModel,
    monthlyPlans: List<MonthlyPlanItemUiModel>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Long, Int?, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var title by remember(plan.id) { mutableStateOf(plan.title) }
    var goal by remember(plan.id) { mutableStateOf(plan.goal.orEmpty()) }
    var weekStartDate by remember(plan.id) { mutableStateOf(plan.weekStartDate) }
    var weekEndDate by remember(plan.id) { mutableStateOf(plan.weekEndDate) }
    var monthlyPlanIdText by remember(plan.id) { mutableStateOf(plan.monthlyPlanId.toString()) }
    var capacityText by remember(plan.id) { mutableStateOf(plan.capacity?.toString().orEmpty()) }
    var review by remember(plan.id) { mutableStateOf(plan.review) }
    var focusSummary by remember(plan.id) { mutableStateOf(plan.focusSummary) }

    val trimmedTitle = title.trim()
    val trimmedGoal = goal.trim()
    val trimmedWeekStartDate = weekStartDate.trim()
    val trimmedWeekEndDate = weekEndDate.trim()
    val trimmedMonthlyPlanIdText = monthlyPlanIdText.trim()
    val trimmedCapacityText = capacityText.trim()
    val trimmedReview = review.trim()
    val trimmedFocusSummary = focusSummary.trim()
    val titleError = if (trimmedTitle.isBlank()) "标题不能为空" else null
    val startDateError = validateDate(trimmedWeekStartDate, "周开始日期")
    val endDateError = validateDate(trimmedWeekEndDate, "周结束日期")
    val monthlyPlanId = trimmedMonthlyPlanIdText.toLongOrNull()
    val monthlyPlanError = if (monthlyPlanId == null || monthlyPlanId <= 0L) "月计划ID不能为空" else null
    val capacity = trimmedCapacityText.toIntOrNull()
    val capacityError = if (trimmedCapacityText.isNotBlank() && (capacity == null || capacity <= 0)) "容量必须大于 0" else null
    val rangeError = when {
        startDateError != null || endDateError != null -> null
        trimmedWeekStartDate > trimmedWeekEndDate -> "周结束日期不能早于周开始日期"
        else -> null
    }
    val canSave = titleError == null &&
        startDateError == null &&
        endDateError == null &&
        monthlyPlanError == null &&
        capacityError == null &&
        rangeError == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("周计划详情") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "把本周重点和时间范围像微软 To Do 一样集中编辑。",
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
                    value = goal,
                    onValueChange = { goal = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标") },
                )
                if (monthlyPlans.isNotEmpty()) {
                    Text(
                        text = "选择关联月计划",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        monthlyPlans.forEach { month ->
                            val selected = monthlyPlanIdText == month.id.toString()
                            AssistChip(
                                onClick = { monthlyPlanIdText = month.id.toString() },
                                label = { Text(month.title) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                                ),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本周容量（可留空）") },
                    singleLine = true,
                    isError = capacityError != null,
                    supportingText = capacityError?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = focusSummary,
                    onValueChange = { focusSummary = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("本周重点任务池摘要") },
                )
                OutlinedTextField(
                    value = review,
                    onValueChange = { review = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("周复盘") },
                )
                OutlinedTextField(
                    value = weekStartDate,
                    onValueChange = { weekStartDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("周开始日期（YYYY-MM-DD）") },
                    singleLine = true,
                    isError = startDateError != null || rangeError != null,
                    supportingText = (startDateError ?: rangeError)?.let { { Text(it) } },
                )
                OutlinedTextField(
                    value = weekEndDate,
                    onValueChange = { weekEndDate = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("周结束日期（YYYY-MM-DD）") },
                    singleLine = true,
                    isError = endDateError != null || rangeError != null,
                    supportingText = (endDateError ?: rangeError)?.let { { Text(it) } },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(
                            trimmedTitle,
                            trimmedGoal,
                            trimmedWeekStartDate,
                            trimmedWeekEndDate,
                            monthlyPlanId ?: plan.monthlyPlanId,
                            capacity,
                            trimmedReview,
                            trimmedFocusSummary,
                        )
                    }
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("删除")
            }
        },
    )
}
