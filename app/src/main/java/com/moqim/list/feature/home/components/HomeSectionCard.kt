package com.moqim.list.feature.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun HomeSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    compactHeader: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (compactHeader) 12.dp else 14.dp,
                vertical = if (compactHeader) 10.dp else 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (compactHeader) 6.dp else 8.dp),
        ) {
            val hasHeader = eyebrow != null || title.isNotBlank()

            if (hasHeader) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (title.isNotBlank()) 4.dp else 0.dp),
                ) {
                    eyebrow?.let {
                        Text(
                            text = it,
                            style = if (compactHeader) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                                .padding(
                                    horizontal = if (compactHeader) 5.dp else 6.dp,
                                    vertical = if (compactHeader) 1.dp else 2.dp,
                                ),
                        )
                    }

                    if (title.isNotBlank()) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                if (!compactHeader) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                }
            }

            content()
        }
    }
}
