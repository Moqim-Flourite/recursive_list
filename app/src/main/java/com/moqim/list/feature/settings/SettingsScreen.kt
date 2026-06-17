package com.moqim.list.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moqim.list.feature.exportsummary.ExportExecutionSummaryActivity
import com.moqim.list.feature.home.components.HomeSectionCard
import com.moqim.list.feature.importplan.ImportPlanActivity
import com.moqim.list.feature.settings.model.SettingsUiState
import com.moqim.list.system.LiveWallpaperLauncher
import com.moqim.list.feature.settings.model.UpdateDialogInfo
import androidx.compose.material3.AlertDialog

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(context),
    )

    fun openLiveWallpaperPicker() {
        LiveWallpaperLauncher.open(context)
    }

    fun openImportPlan() {
        context.startActivity(Intent(context, ImportPlanActivity::class.java))
    }

    fun openExecutionSummaryExport() {
        context.startActivity(Intent(context, ExportExecutionSummaryActivity::class.java))
    }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.backupStatusMessage) {
        uiState.backupStatusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onBackupStatusShown()
        }
    }

    LaunchedEffect(uiState.liveWallpaperSetStatusMessage) {
        uiState.liveWallpaperSetStatusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onLiveWallpaperSetStatusShown()
        }
    }

    LaunchedEffect(uiState.syncResultMessage) {
        uiState.syncResultMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSyncResultMessageShown()
        }
    }

    SettingsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onMorningViewEnabledChange = viewModel::onMorningViewEnabledChange,
        onDefaultHomeTabChange = viewModel::onDefaultHomeTabChange,
        onAppThemeChange = viewModel::onAppThemeChange,
        onWidgetDefaultStyleChange = viewModel::onWidgetDefaultStyleChange,
        onLiveWallpaperSourceChange = viewModel::onLiveWallpaperSourceChange,
        onSegmentBoundaryTimesChange = viewModel::onSegmentBoundaryTimesChange,
        onExportBackup = viewModel::onExportBackup,
        onRestoreLatestBackup = viewModel::onRestoreLatestBackup,
        onOpenImportPlan = ::openImportPlan,
        onOpenExecutionSummaryExport = ::openExecutionSummaryExport,
        onOpenLiveWallpaperPicker = ::openLiveWallpaperPicker,
        onOpenBatteryOptimizationSettings = viewModel::openBatteryOptimizationSettings,
        onSetLiveWallpaperByShizuku = viewModel::onSetLiveWallpaperByShizuku,
        onCheckForUpdate = viewModel::onCheckForUpdate,
        onInstallUpdate = viewModel::onInstallUpdate,
        onDismissUpdateDialog = viewModel::onDismissUpdateDialog,
        onSyncEnabledChange = viewModel::onSyncEnabledChange,
        onSyncPair = viewModel::onSyncPair,
        onSyncUnpair = viewModel::onSyncUnpair,
        onTriggerManualSync = viewModel::onTriggerManualSync,
        onSyncResultMessageShown = viewModel::onSyncResultMessageShown,
        onAutoDiscoverServer = viewModel::onAutoDiscoverServer,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onMorningViewEnabledChange: (Boolean) -> Unit,
    onDefaultHomeTabChange: (String) -> Unit,
    onAppThemeChange: (String) -> Unit,
    onWidgetDefaultStyleChange: (String) -> Unit,
    onLiveWallpaperSourceChange: (String) -> Unit,
    onSegmentBoundaryTimesChange: (String, String, String, String, String) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreLatestBackup: () -> Unit,
    onOpenImportPlan: () -> Unit,
    onOpenExecutionSummaryExport: () -> Unit,
    onOpenLiveWallpaperPicker: () -> Unit,
    onOpenBatteryOptimizationSettings: () -> Unit,
    onSetLiveWallpaperByShizuku: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdateDialog: () -> Unit,
    onSyncEnabledChange: (Boolean) -> Unit,
    onSyncPair: (String, Int, String) -> Unit,
    onSyncUnpair: () -> Unit,
    onTriggerManualSync: () -> Unit,
    onSyncResultMessageShown: () -> Unit,
    onAutoDiscoverServer: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(42.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HomeSectionCard(
                    title = "设置",
                    eyebrow = "Settings",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "把外观、同步、系统适配和桌面展示能力收进更轻盈的玻璃卡片里。",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            HeaderBadge("HyperOS Ready", true)
                            HeaderBadge("Glass System")
                            HeaderBadge("Grouped Cards")
                        }
                    }
                }
            }

            item {
                HomeSectionCard(title = "个性化与外观", eyebrow = "Appearance", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ThemePaletteRow(
                            selected = uiState.appThemeLabel,
                            onSelect = { label ->
                                onAppThemeChange(
                                    when (label) {
                                        "极简白" -> "light"
                                        "深邃黑" -> "dark"
                                        else -> "system"
                                    }
                                )
                            },
                        )
                        PreferenceBlock(
                            title = "Widget 默认样式",
                            summary = "控制展示层默认风格，和展示中心中的样式语言保持一致。",
                        ) {
                            RichOptionChips(
                                options = listOf(
                                    "极简" to "干净线条",
                                    "卡片" to "微软层次",
                                    "专注" to "更强聚焦",
                                ),
                                selected = uiState.widgetDefaultStyle,
                                onSelect = { label ->
                                    onWidgetDefaultStyleChange(
                                        when (label) {
                                            "卡片" -> "compact"
                                            "专注" -> "focus"
                                            else -> "standard"
                                        }
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                HomeSectionCard(title = "偏好与启动", eyebrow = "Preference", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PreferenceBlock(
                            title = "默认首页",
                            summary = "决定 App 冷启动后首先进入的主 Tab，减少重复跳转成本。",
                        ) {
                            RichOptionChips(
                                options = listOf(
                                    "今日" to "启动即执行",
                                    "计划" to "月周结构",
                                    "展示" to "桌面配置",
                                    "设置" to "偏好入口",
                                ),
                                selected = uiState.defaultHomeTab,
                                onSelect = { label ->
                                    onDefaultHomeTabChange(
                                        when (label) {
                                            "今日" -> "today"
                                            "计划" -> "plans"
                                            "展示" -> "surface"
                                            else -> "settings"
                                        }
                                    )
                                },
                            )
                        }

                        SettingSwitchTile(
                            title = "晨间视图开关",
                            summary = "控制首页是否优先展示晨间视图卡片与今日启动信息。",
                            checked = uiState.morningViewEnabled,
                            onCheckedChange = onMorningViewEnabledChange,
                        )

                        PreferenceBlock(
                            title = "时段时间范围",
                            summary = "定义各时段切换时间：晨从“晨开始”起，早从“早开始”起；凌晨会归到“晚”。Today 与当前时段 Widget 共用同一套规则。",
                        ) {
                            SegmentBoundaryEditor(
                                morningStartTime = uiState.morningStartTime,
                                morningTime = uiState.morningTime,
                                noonTime = uiState.noonTime,
                                afternoonTime = uiState.afternoonTime,
                                eveningTime = uiState.eveningTime,
                                onSave = onSegmentBoundaryTimesChange,
                            )
                        }
                    }
                }
            }

            item {
                HomeSectionCard(title = "WiFi 同步", eyebrow = "Sync", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingSwitchTile(
                            title = "启用同步",
                            summary = "通过局域网将计划数据同步到电脑端，每天自动同步一次。",
                            checked = uiState.syncEnabled,
                            onCheckedChange = onSyncEnabledChange,
                        )

                        if (uiState.syncEnabled) {
                            if (!uiState.syncPaired) {
                                // 配对表单
                                SyncPairForm(
                                    host = uiState.syncServerHost,
                                    port = uiState.syncServerPort,
                                    token = uiState.syncAuthToken,
                                    onPair = onSyncPair,
                                    onAutoDiscover = onAutoDiscoverServer,
                                )
                            } else {
                                // 已配对状态
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SettingValueTile(
                                        title = "服务器",
                                        value = "${uiState.syncServerHost}:${uiState.syncServerPort}",
                                    )
                                    SettingValueTile(
                                        title = "状态",
                                        value = uiState.syncStatus,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        TextButton(
                                            onClick = onTriggerManualSync,
                                            enabled = !uiState.isSyncing,
                                        ) {
                                            Text(if (uiState.isSyncing) "同步中..." else "立即同步")
                                        }
                                        TextButton(onClick = onSyncUnpair) {
                                            Text("解除配对")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                HomeSectionCard(title = "展示与壁纸", eyebrow = "Display", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PreferenceBlock(
                            title = "动态壁纸数据源",
                            summary = "直接决定 live wallpaper 默认展示哪条主线，和 Surface 页切换保持同步。",
                        ) {
                            RichOptionChips(
                                options = listOf(
                                    "今日" to "晨间主线",
                                    "当前时段" to "只看此刻",
                                    "本周" to "承接周目标",
                                    "打卡" to "固定节律",
                                ),
                                selected = uiState.liveWallpaperSource,
                                onSelect = { label ->
                                    onLiveWallpaperSourceChange(
                                        when (label) {
                                            "当前时段" -> "focus"
                                            "本周" -> "weekly"
                                            "打卡" -> "habit"
                                            else -> "today"
                                        }
                                    )
                                },
                            )
                        }

                        PreferenceBlock(
                            title = "展示层默认风格",
                            summary = "Widget、Surface、动态壁纸共用同一套默认风格语言。",
                        ) {
                            RichOptionChips(
                                options = listOf(
                                    "极简" to "干净线条",
                                    "卡片" to "微软层次",
                                    "专注" to "更强聚焦",
                                ),
                                selected = uiState.widgetDefaultStyle,
                                onSelect = { label ->
                                    onWidgetDefaultStyleChange(
                                        when (label) {
                                            "卡片" -> "compact"
                                            "专注" -> "focus"
                                            else -> "standard"
                                        }
                                    )
                                },
                            )
                        }

                        SettingValueTile(
                            title = "当前展示状态",
                            value = uiState.displayStatus,
                        )
                        SettingValueTile(
                            title = "动态壁纸接管状态",
                            value = uiState.desktopStatus,
                        )

                        ActionInfoTile(
                            title = "自动设为动态壁纸（Shizuku）",
                            summary = "优先通过 Shizuku 直接触发系统动态壁纸切换流程。",
                            actionLabel = "立即尝试",
                            onClick = onSetLiveWallpaperByShizuku,
                        )
                        ActionInfoTile(
                            title = "打开动态壁纸（手动设一次）",
                            summary = "进入系统动态壁纸入口后，手动选择 Recursive List 一次；之后 app 内容变化会自动同步到主页壁纸。",
                            actionLabel = "立即打开",
                            onClick = onOpenLiveWallpaperPicker,
                        )
                    }
                }
            }

            item {
                HomeSectionCard(title = "数据与同步", eyebrow = "Data", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionInfoTile(
                            title = "本地数据备份",
                            summary = "导出当前任务、习惯、展示与偏好配置到 Download/moqim_list。",
                            actionLabel = "立即导出",
                            onClick = onExportBackup,
                        )
                        ActionInfoTile(
                            title = "恢复最近备份",
                            summary = "从 Download/moqim_list 中恢复最近一个备份文件，适合快速回迁。",
                            actionLabel = "立即恢复",
                            onClick = onRestoreLatestBackup,
                        )
                        ActionInfoTile(
                            title = "导入 Operit AI 计划",
                            summary = "打开计划导入页，可手动粘贴 JSON，也可承接 Operit AI 通过 Intent 传入的结构化计划。",
                            actionLabel = "立即打开",
                            onClick = onOpenImportPlan,
                        )
                        ActionInfoTile(
                            title = "导出执行摘要",
                            summary = "导出某天真实执行结果 JSON，供 Operit 读取后递归生成下一天计划。",
                            actionLabel = "立即打开",
                            onClick = onOpenExecutionSummaryExport,
                        )
                    }
                }
            }

            item {
                HomeSectionCard(title = "系统适配与常驻", eyebrow = "System Fit", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ActionInfoTile(
                            title = "电池优化引导",
                            summary = "若未加入电池优化白名单，Widget 刷新与常驻能力可能被系统回收。",
                            actionLabel = uiState.batteryTipStatus,
                            onClick = onOpenBatteryOptimizationSettings,
                        )
                        NavInfoTile(
                            title = "HyperOS 兼容提示",
                            summary = "查看如何避免系统清理后台、限制自启动与小组件刷新。",
                            onClick = { /* 跳转展示页 */ },
                        )
                        NavInfoTile(
                            title = "桌面展示说明",
                            summary = "引导添加 Widget、说明刷新机制，并覆盖静态壁纸导出与动态壁纸配置入口。",
                            onClick = { /* 跳转展示页 */ },
                        )
                    }
                }
            }

            item {
                HomeSectionCard(title = "关于", eyebrow = "About", collapsible = true, initiallyExpanded = false) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AboutHeroCard(
                            title = "Recursive List",
                            summary = uiState.aboutText,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AboutMetric(
                                modifier = Modifier.weight(1f),
                                label = "当前阶段",
                                value = uiState.desktopStatus,
                            )
                            AboutMetric(
                                modifier = Modifier.weight(1f),
                                label = "核心方向",
                                value = uiState.focusSummary,
                            )
                        }
                        SettingValueTile(
                            title = "当前重点能力",
                            value = "晨间视图、当前时段 Widget、展示配置、HyperOS 兼容说明持续迭代中。",
                        )
                        ActionInfoTile(
                            title = "检查更新",
                            summary = "从 GitHub Release 检查是否有新版本可用。",
                            actionLabel = if (uiState.isCheckingUpdate) "检查中..." else uiState.updateStatus,
                            onClick = onCheckForUpdate,
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 更新对话框
        uiState.updateDialogInfo?.let { info ->
            UpdateAvailableDialog(
                info = info,
                onDismiss = onDismissUpdateDialog,
                onInstall = onInstallUpdate,
            )
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: UpdateDialogInfo,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("🆕 发现新版本", fontWeight = FontWeight.Bold)
                Text(
                    text = info.releaseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "版本 ${info.versionName}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (info.apkSizeBytes > 0) {
                    val sizeMb = info.apkSizeBytes / 1024.0 / 1024.0
                    Text(
                        text = "安装包大小：${"%.1f".format(sizeMb)} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (info.apkDownloadUrl != null) {
                TextButton(onClick = onInstall) {
                    Text("下载并安装")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后再说")
            }
        },
    )
}

@Composable
private fun HeaderBadge(text: String, active: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.52f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ThemePaletteRow(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ThemePaletteButton("极简白", "Light", selected == "极简白", listOf(Color.White, Color(0xFFDDE7F5)), onClick = { onSelect("极简白") })
        ThemePaletteButton("深邃黑", "Dark", selected == "深邃黑", listOf(Color(0xFF1F2330), Color(0xFF4B5263)), onClick = { onSelect("深邃黑") })
        ThemePaletteButton("通透玻璃", "System", selected == "通透玻璃", listOf(Color(0xFFF9FCFF), Color(0xFF8FC4FF)), onClick = { onSelect("通透玻璃") })
    }
}

@Composable
private fun ThemePaletteButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    colors: List<Color>,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.surface.copy(alpha = 0.44f),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        ),
        shadowElevation = if (selected) 8.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(18.dp)
                    .height(18.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(colors)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PreferenceBlock(
    title: String,
    summary: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RichOptionChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (title, desc) ->
            Surface(
                modifier = Modifier.clickable { onSelect(title) },
                shape = RoundedCornerShape(18.dp),
                color = if (title == selected) MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                border = BorderStroke(
                    1.dp,
                    if (title == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                ),
                shadowElevation = if (title == selected) 6.dp else 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchTile(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NavInfoTile(
    title: String,
    summary: String,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f),
        )
    }
}

@Composable
private fun ActionInfoTile(
    title: String,
    summary: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun DisabledInfoTile(
    title: String,
    summary: String,
    tag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HeaderBadge(tag)
    }
}

@Composable
private fun StatusActionTile(
    title: String,
    summary: String,
    action: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = Color(0xFFF2BE4C).copy(alpha = 0.18f),
        ) {
            Text(
                text = action,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A6110),
            )
        }
    }
}

@Composable
private fun AboutHeroCard(
    title: String,
    summary: String,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                Color(0xFF7FE2C2).copy(alpha = 0.18f),
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("R", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeaderBadge("v1 MVP", true)
        }
    }
}

@Composable
private fun AboutMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SegmentBoundaryEditor(
    morningStartTime: String,
    morningTime: String,
    noonTime: String,
    afternoonTime: String,
    eveningTime: String,
    onSave: (String, String, String, String, String) -> Unit,
) {
    var morningStart by remember(morningStartTime) { mutableStateOf(morningStartTime) }
    var morning by remember(morningTime) { mutableStateOf(morningTime) }
    var noon by remember(noonTime) { mutableStateOf(noonTime) }
    var afternoon by remember(afternoonTime) { mutableStateOf(afternoonTime) }
    var evening by remember(eveningTime) { mutableStateOf(eveningTime) }

    val values = listOf(morningStart, morning, noon, afternoon, evening)
    val isFormatValid = values.all { it.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) }
    val isOrderValid = if (isFormatValid) {
        morningStart < morning && morning < noon && noon < afternoon && afternoon < evening
    } else {
        false
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SegmentBoundaryRow("晨开始", morningStart) { morningStart = it }
        SegmentBoundaryRow("早开始", morning) { morning = it }
        SegmentBoundaryRow("中开始", noon) { noon = it }
        SegmentBoundaryRow("下午开始", afternoon) { afternoon = it }
        SegmentBoundaryRow("晚开始", evening) { evening = it }

        Text(
            text = if (!isFormatValid) {
                "格式使用 24 小时制，例如 07:00、14:00。"
            } else if (!isOrderValid) {
                "时间顺序需要满足：晨开始 < 早开始 < 中开始 < 下午开始 < 晚开始。"
            } else {
                "规则为：晨开始~早开始=晨，早开始~中开始=早，其余跨日尾段归到晚；Today 与当前时段 Widget 共用这套规则。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(
            onClick = {
                if (isFormatValid && isOrderValid) {
                    onSave(
                        morningStart,
                        morning,
                        noon,
                        afternoon,
                        evening,
                    )
                }
            },
            enabled = isFormatValid && isOrderValid,
        ) {
            Text(
                when {
                    !isFormatValid -> "请输入合法时间"
                    !isOrderValid -> "时间顺序不合法"
                    else -> "保存时段范围"
                }
            )
        }
    }
}

@Composable
private fun SegmentBoundaryRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == ':' }.take(5)
            onValueChange(filtered)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        supportingText = {
            Text("HH:mm")
        },
    )
}

@Composable
private fun SettingValueTile(
    title: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncPairForm(
    host: String,
    port: Int,
    token: String,
    onPair: (String, Int, String) -> Unit,
    onAutoDiscover: () -> Unit,
) {
    var hostInput by remember { mutableStateOf(host) }
    var portInput by remember { mutableStateOf(if (port > 0) port.toString() else "8080") }
    var tokenInput by remember { mutableStateOf(token) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = hostInput,
            onValueChange = { hostInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("服务器 IP") },
            singleLine = true,
            supportingText = { Text("电脑端局域网 IP 地址") },
        )
        OutlinedTextField(
            value = portInput,
            onValueChange = { portInput = it.filter { c -> c.isDigit() }.take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("端口") },
            singleLine = true,
            supportingText = { Text("默认 8080") },
        )
        OutlinedTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("配对 Token") },
            singleLine = true,
            supportingText = { Text("电脑端启动时显示的 Token") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAutoDiscover) {
                Text("自动搜索")
            }
            TextButton(
                onClick = {
                    val p = portInput.toIntOrNull() ?: 8080
                    onPair(hostInput, p, tokenInput)
                },
                enabled = hostInput.isNotBlank() && tokenInput.isNotBlank(),
            ) {
                Text("配对")
            }
        }
    }
}
