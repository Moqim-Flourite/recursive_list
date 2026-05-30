package com.moqim.list.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun MorningDashboardCard(
    dateText: String,
    weeklySummary: String,
    habitsSummary: String,
    carryOverSummary: String,
    focusItems: List<String>,
    onClose: () -> Unit,
    onStartToday: () -> Unit,
    onViewAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSectionCard(
        title = "晨间视图",
        eyebrow = "Focus Notes",
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClose) {
                    Text("X")
                }
            }

            SummaryPill("本周推进", weeklySummary)
            SummaryPill("打卡概览", habitsSummary)
            SummaryPill("昨日延续", carryOverSummary)

            focusItems.forEachIndexed { index, item ->
                Text(
                    text = "${index + 1}. $item",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(onClick = onStartToday) {
                    Text("开始今天")
                }
                TextButton(onClick = onViewAll) {
                    Text("查看全部")
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
