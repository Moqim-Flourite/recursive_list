package com.moqim.list.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.moqim.list.ui.theme.MyApplicationTheme
import com.moqim.list.data.local.entity.SurfaceConfigEntity
import com.moqim.list.data.local.entity.WidgetInstanceConfigEntity
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.worker.WidgetRefreshScheduler
import kotlinx.coroutines.launch

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                WidgetConfigRoute(
                    appWidgetId = appWidgetId,
                    onCancel = { finish() },
                    onSave = { draft ->
                        lifecycleScope.launch {
                            saveConfig(draft)
                        }
                    },
                )
            }
        }
    }

    private suspend fun saveConfig(draft: WidgetConfigDraft) {
        val db = DatabaseProvider.get(this)
        val widgetDao = db.widgetInstanceConfigDao()
        val surfaceDao = db.surfaceConfigDao()
        val existingWidget = widgetDao.getByAppWidgetId(appWidgetId)

        val targetType = when {
            isMorningWidget(appWidgetId) -> "WIDGET_MORNING"
            isCurrentSegmentWidget(appWidgetId) -> "WIDGET_CURRENT_SEGMENT"
            else -> "WIDGET_UNKNOWN"
        }

        val surfaceId = surfaceDao.upsert(
            SurfaceConfigEntity(
                id = existingWidget?.surfaceConfigId ?: 0,
                targetType = targetType,
                surfaceType = "APP_WIDGET",
                theme = draft.theme,
                maxItems = draft.maxItems,
                showCompleted = draft.showCompleted,
                showProgress = draft.showProgress,
                opacity = draft.opacity,
                anchorPosition = draft.anchorPosition,
                textScale = draft.textScale,
                refreshPolicy = draft.refreshPolicy,
            ),
        )

        widgetDao.upsert(
            WidgetInstanceConfigEntity(
                appWidgetId = appWidgetId,
                surfaceConfigId = surfaceId,
            ),
        )

        Log.d(
            "WidgetConfig",
            "saveConfig appWidgetId=$appWidgetId targetType=$targetType theme=${draft.theme} maxItems=${draft.maxItems} showCompleted=${draft.showCompleted} showProgress=${draft.showProgress} opacity=${draft.opacity} anchorPosition=${draft.anchorPosition} textScale=${draft.textScale} refreshPolicy=${draft.refreshPolicy}",
        )

        when {
            isMorningWidget(appWidgetId) -> {
                if (draft.refreshPolicy == "AGGRESSIVE") {
                    MorningWidgetProvider.refresh(this, appWidgetId)
                } else {
                    MorningWidgetProvider.refreshAll(this)
                }
            }
            isCurrentSegmentWidget(appWidgetId) -> {
                if (draft.refreshPolicy == "AGGRESSIVE") {
                    CurrentSegmentWidgetProvider.refresh(this, appWidgetId)
                } else {
                    CurrentSegmentWidgetProvider.refreshAll(this)
                }
            }
            else -> {
                MorningWidgetProvider.refreshAll(this)
                CurrentSegmentWidgetProvider.refreshAll(this)
            }
        }
        WidgetRefreshScheduler.enqueueAggressiveRefreshIfNeeded(this, "config_saved")

        val result = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun isMorningWidget(appWidgetId: Int): Boolean {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, MorningWidgetProvider::class.java))
        return ids.contains(appWidgetId)
    }

    private fun isCurrentSegmentWidget(appWidgetId: Int): Boolean {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, CurrentSegmentWidgetProvider::class.java))
        return ids.contains(appWidgetId)
    }
}

private data class WidgetConfigDraft(
    val widgetKind: String,
    val theme: String,
    val maxItems: Int,
    val showCompleted: Boolean,
    val showProgress: Boolean,
    val opacity: Float,
    val anchorPosition: String,
    val textScale: Float,
    val refreshPolicy: String,
)

@Composable
private fun WidgetConfigRoute(
    appWidgetId: Int,
    onCancel: () -> Unit,
    onSave: (WidgetConfigDraft) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var widgetKind by remember { mutableStateOf("WIDGET_UNKNOWN") }
    var theme by remember { mutableStateOf("DEFAULT") }
    var maxItems by remember { mutableIntStateOf(3) }
    var showCompleted by remember { mutableStateOf(true) }
    var showProgress by remember { mutableStateOf(true) }
    var opacity by remember { mutableFloatStateOf(1f) }
    var anchorPosition by remember { mutableStateOf("TOP") }
    var textScale by remember { mutableFloatStateOf(1f) }
    var refreshPolicy by remember { mutableStateOf("AUTO") }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(appWidgetId) {
        val db = DatabaseProvider.get(context)
        val widget = db.widgetInstanceConfigDao().getByAppWidgetId(appWidgetId)
        val surface = widget?.surfaceConfigId?.let { db.surfaceConfigDao().getById(it) }

        val manager = AppWidgetManager.getInstance(context)
        widgetKind = when {
            manager.getAppWidgetIds(ComponentName(context, MorningWidgetProvider::class.java)).contains(appWidgetId) -> "WIDGET_MORNING"
            manager.getAppWidgetIds(ComponentName(context, CurrentSegmentWidgetProvider::class.java)).contains(appWidgetId) -> "WIDGET_CURRENT_SEGMENT"
            else -> surface?.targetType ?: "WIDGET_UNKNOWN"
        }

        theme = surface?.theme ?: "DEFAULT"
        maxItems = (surface?.maxItems ?: 3).coerceIn(1, 5)
        showCompleted = surface?.showCompleted ?: true
        showProgress = surface?.showProgress ?: true
        opacity = (surface?.opacity ?: 1f).coerceIn(0.35f, 1f)
        anchorPosition = surface?.anchorPosition ?: "TOP"
        textScale = (surface?.textScale ?: 1f).coerceIn(0.8f, 1.4f)
        refreshPolicy = surface?.refreshPolicy ?: "AUTO"
        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        )

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .windowInsetsPadding(WindowInsets.navigationBars),
            color = Color.Transparent,
        ) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("正在加载 Widget 配置…")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        GlassSectionCard(
                            title = "Widget 配置",
                            eyebrow = "Instance Config",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = when (widgetKind) {
                                        "WIDGET_MORNING" -> "晨间视图小组件"
                                        "WIDGET_CURRENT_SEGMENT" -> "当前时段小组件"
                                        else -> "未知小组件"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "实例 ID：$appWidgetId",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item {
                        GlassSectionCard(
                            title = "主题与版式",
                            eyebrow = "Theme & Layout",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                OptionRow(
                                    title = "主题",
                                    options = listOf("DEFAULT", "MINIMAL", "MICROSOFT", "FOCUS", "PIXEL"),
                                    selected = theme,
                                    labelMapper = {
                                        when (it) {
                                            "DEFAULT" -> "默认"
                                            "MINIMAL" -> "极简"
                                            "MICROSOFT" -> "微软"
                                            "FOCUS" -> "专注"
                                            "PIXEL" -> "像素"
                                            else -> it
                                        }
                                    },
                                    onSelected = { theme = it },
                                )
                                SliderSettingBlock(
                                    title = "最大显示数",
                                    summary = "控制当前实例最多显示多少条任务",
                                    value = maxItems.toFloat(),
                                    valueText = "${maxItems} 项",
                                    valueRange = 1f..5f,
                                    steps = 3,
                                    onValueChange = { maxItems = it.toInt().coerceIn(1, 5) },
                                )
                                SliderSettingBlock(
                                    title = "透明度",
                                    summary = "为后续不同样式主题预留，可影响视觉轻重",
                                    value = opacity,
                                    valueText = "${(opacity * 100).toInt()}%",
                                    valueRange = 0.35f..1f,
                                    steps = 12,
                                    onValueChange = { opacity = it },
                                )
                                SliderSettingBlock(
                                    title = "字体缩放",
                                    summary = "控制这个实例的文字整体比例",
                                    value = textScale,
                                    valueText = String.format("%.2fx", textScale),
                                    valueRange = 0.8f..1.4f,
                                    steps = 5,
                                    onValueChange = { textScale = it },
                                )
                                OptionRow(
                                    title = "锚点位置",
                                    options = listOf("TOP", "CENTER", "BOTTOM"),
                                    selected = anchorPosition,
                                    labelMapper = {
                                        when (it) {
                                            "TOP" -> "顶部"
                                            "CENTER" -> "居中"
                                            "BOTTOM" -> "底部"
                                            else -> it
                                        }
                                    },
                                    onSelected = { anchorPosition = it },
                                )
                            }
                        }
                    }

                    item {
                        GlassSectionCard(
                            title = "内容与反馈",
                            eyebrow = "Content & Feedback",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                SettingSwitchRow(
                                    title = "显示已完成任务",
                                    summary = "保留完整进度感；关闭后只展示未完成项",
                                    checked = showCompleted,
                                    onCheckedChange = { showCompleted = it },
                                )
                                SettingSwitchRow(
                                    title = "显示进度信息",
                                    summary = "为后续进度条/进度摘要样式预留开关",
                                    checked = showProgress,
                                    onCheckedChange = { showProgress = it },
                                )
                                OptionRow(
                                    title = "刷新策略",
                                    options = listOf("AUTO", "AGGRESSIVE", "BATTERY_SAVER"),
                                    selected = refreshPolicy,
                                    labelMapper = {
                                        when (it) {
                                            "AUTO" -> "自动"
                                            "AGGRESSIVE" -> "积极刷新"
                                            "BATTERY_SAVER" -> "省电优先"
                                            else -> it
                                        }
                                    },
                                    onSelected = { refreshPolicy = it },
                                )
                            }
                        }
                    }

                    item {
                        GlassSectionCard(
                            title = "即时预览摘要",
                            eyebrow = "Preview Summary",
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                InfoRow("主题", theme)
                                InfoRow("最大显示数", "$maxItems 项")
                                InfoRow("显示已完成", if (showCompleted) "开启" else "关闭")
                                InfoRow("显示进度", if (showProgress) "开启" else "关闭")
                                InfoRow("透明度", "${(opacity * 100).toInt()}%")
                                InfoRow("字体缩放", String.format("%.2fx", textScale))
                                InfoRow("刷新策略", refreshPolicy)
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("取消")
                            }
                            Button(
                                onClick = {
                                    onSave(
                                        WidgetConfigDraft(
                                            widgetKind = widgetKind,
                                            theme = theme,
                                            maxItems = maxItems,
                                            showCompleted = showCompleted,
                                            showProgress = showProgress,
                                            opacity = opacity,
                                            anchorPosition = anchorPosition,
                                            textScale = textScale,
                                            refreshPolicy = refreshPolicy,
                                        ),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("保存并添加")
                            }
                        }
                        Spacer(modifier = Modifier.padding(bottom = 24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSectionCard(
    title: String,
    eyebrow: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(28.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        content()
    }
}

@Composable
private fun OptionRow(
    title: String,
    options: List<String>,
    selected: String,
    labelMapper: (String) -> String = { it },
    onSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                SmallChip(
                    text = labelMapper(option),
                    selected = selected == option,
                    onClick = { onSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun SmallChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color.White.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.26f))
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.20f),
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SliderSettingBlock(
    title: String,
    summary: String,
    value: Float,
    valueText: String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = valueText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
        )
    }
}