package com.moqim.list.feature.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moqim.list.feature.home.model.HabitItemUiModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitsSummaryCard(
    summary: String,
    items: List<HabitItemUiModel>,
    onHabitClick: (Long) -> Unit,
    onHabitLongPress: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    HomeSectionCard(
        title = "固定打卡",
        eyebrow = "Habit Bar",
        modifier = modifier,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            items.forEach { item ->
                val done = item.completedCount >= item.dailyTargetCount
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (done) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)
                    },
                    modifier = Modifier.combinedClickable(
                        onClick = { onHabitClick(item.templateId) },
                        onLongClick = { onHabitLongPress(item.templateId) },
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = item.iconLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${item.completedCount}/${item.dailyTargetCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text(
            text = "点击记为今日完成，长按进入编辑态。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
