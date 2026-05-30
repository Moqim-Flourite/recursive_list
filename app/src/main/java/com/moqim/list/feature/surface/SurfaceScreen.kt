package com.moqim.list.feature.surface

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import com.moqim.list.feature.home.components.HomeSectionCard
import com.moqim.list.system.LiveWallpaperLauncher
import com.moqim.list.feature.surface.model.SurfaceCapabilityState
import com.moqim.list.feature.surface.model.SurfaceControlPanelMode
import com.moqim.list.feature.surface.model.SurfacePreviewContent
import com.moqim.list.feature.surface.model.SurfacePreviewMode
import com.moqim.list.feature.surface.model.SurfaceSourceType
import com.moqim.list.feature.surface.model.SurfaceStyleControlMode
import com.moqim.list.feature.surface.model.SurfaceThemeStyle
import com.moqim.list.feature.surface.model.SurfaceUiState

@Composable
fun SurfaceScreen() {
    val context = LocalContext.current
    val viewModel: SurfaceViewModel = viewModel(
        factory = SurfaceViewModel.factory(context),
    )
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    fun exportedWallpaperUri(path: String): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            File(path),
        )
    }

    fun openExportedWallpaper(path: String) {
        val uri = exportedWallpaperUri(path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "预览静态壁纸"))
    }

    fun shareExportedWallpaper(path: String) {
        val uri = exportedWallpaperUri(path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享静态壁纸"))
    }

    fun setExportedWallpaper(path: String) {
        val uri = exportedWallpaperUri(path)
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, "image/png")
            putExtra("mimeType", "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "设为壁纸")
        runCatching { context.startActivity(chooser) }
            .recoverCatching {
                val wallpaperIntent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(wallpaperIntent)
            }
    }

    fun openLiveWallpaperPicker() {
        LiveWallpaperLauncher.open(context)
    }

    LaunchedEffect(uiState.exportFeedbackMessage) {
        uiState.exportFeedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onExportFeedbackShown()
        }
    }

    LaunchedEffect(uiState.liveWallpaperSetFeedbackMessage) {
        uiState.liveWallpaperSetFeedbackMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onLiveWallpaperSetFeedbackShown()
        }
    }

    LaunchedEffect(uiState.liveWallpaperDebugMessage) {
        uiState.liveWallpaperDebugMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onLiveWallpaperDebugShown()
        }
    }

    SurfaceContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onPreviewModeSelected = viewModel::onPreviewModeSelected,
        onControlPanelModeSelected = viewModel::onControlPanelModeSelected,
        onStyleControlModeSelected = viewModel::onStyleControlModeSelected,
        onSourceSelected = viewModel::onSourceSelected,
        onThemeSelected = viewModel::onThemeSelected,
        onGlassAlphaChanged = viewModel::onGlassAlphaChanged,
        onFontScaleChanged = viewModel::onFontScaleChanged,
        onShowCompletedChanged = viewModel::onShowCompletedChanged,
        onShowProgressChanged = viewModel::onShowProgressChanged,
        onAmbientChanged = viewModel::onAmbientChanged,
        onWidgetGuideToggle = viewModel::onWidgetGuideToggle,
        onExportStaticWallpaper = viewModel::onExportStaticWallpaper,
        onOpenExportedWallpaper = ::openExportedWallpaper,
        onShareExportedWallpaper = ::shareExportedWallpaper,
        onSetExportedWallpaper = ::setExportedWallpaper,
        onOpenLiveWallpaperPicker = ::openLiveWallpaperPicker,
        onSetLiveWallpaperByShizuku = viewModel::onSetLiveWallpaperByShizuku,
        onDebugWallpaperManagerByShizuku = viewModel::onDebugWallpaperManagerByShizuku,
    )
}

@Composable
private fun SurfaceContent(
    uiState: SurfaceUiState,
    snackbarHostState: SnackbarHostState,
    onPreviewModeSelected: (SurfacePreviewMode) -> Unit,
    onControlPanelModeSelected: (SurfaceControlPanelMode) -> Unit,
    onStyleControlModeSelected: (SurfaceStyleControlMode) -> Unit,
    onSourceSelected: (SurfaceSourceType) -> Unit,
    onThemeSelected: (SurfaceThemeStyle) -> Unit,
    onGlassAlphaChanged: (Float) -> Unit,
    onFontScaleChanged: (Float) -> Unit,
    onShowCompletedChanged: (Boolean) -> Unit,
    onShowProgressChanged: (Boolean) -> Unit,
    onAmbientChanged: (Boolean) -> Unit,
    onWidgetGuideToggle: () -> Unit,
    onExportStaticWallpaper: () -> Unit,
    onOpenExportedWallpaper: (String) -> Unit,
    onShareExportedWallpaper: (String) -> Unit,
    onSetExportedWallpaper: (String) -> Unit,
    onOpenLiveWallpaperPicker: () -> Unit,
    onSetLiveWallpaperByShizuku: () -> Unit,
    onDebugWallpaperManagerByShizuku: () -> Unit,
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
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f)),
        )

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            item {
                HomeSectionCard(
                    title = uiState.title,
                    eyebrow = "Surface Center",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CapabilityPill("LIVE UI", SurfaceCapabilityState.LIVE)
                            CapabilityPill("PREVIEW LOGIC", SurfaceCapabilityState.PREVIEW)
                        }
                        Text(
                            text = uiState.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NeutralPill("HyperOS × To-Do")
                        }
                    }
                }
            }

            item {
                HomeSectionCard(
                    title = "当前能力概览",
                    eyebrow = "Current Scope",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "展示中心的视觉骨架与交互语言已基本可用，壁纸导出与动态壁纸均已接入。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRowLike {
                            CapabilityPill("已接入：晨间视图 Widget", SurfaceCapabilityState.LIVE)
                            CapabilityPill("已接入：当前时段 Widget", SurfaceCapabilityState.LIVE)
                            CapabilityPill("已接入：样式切换", SurfaceCapabilityState.LIVE)
                            CapabilityPill("已接入：静态壁纸导出", SurfaceCapabilityState.LIVE)
                            CapabilityPill("已接入：动态壁纸", SurfaceCapabilityState.LIVE)
                        }
                    }
                }
            }

            item {
                HomeSectionCard(
                    title = "实时预览窗口",
                    eyebrow = "Previewer",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        PreviewModeRow(
                            selected = uiState.selectedPreviewMode,
                            onSelected = onPreviewModeSelected,
                        )
                        PhonePreviewCard(
                            uiState = uiState,
                            content = uiState.previewContent,
                        )
                    }
                }
            }

            item {
                HomeSectionCard(
                    title = "",
                    eyebrow = "控制区 · Source & Style",
                    compactHeader = true,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                ControlModeButton(
                                    title = "数据源选择",
                                    selected = uiState.selectedControlPanelMode == SurfaceControlPanelMode.SOURCE,
                                    onClick = { onControlPanelModeSelected(SurfaceControlPanelMode.SOURCE) },
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ControlModeButton(
                                    title = "视觉样式定制",
                                    selected = uiState.selectedControlPanelMode == SurfaceControlPanelMode.STYLE,
                                    onClick = { onControlPanelModeSelected(SurfaceControlPanelMode.STYLE) },
                                )
                            }
                        }

                        if (uiState.selectedControlPanelMode == SurfaceControlPanelMode.SOURCE) {
                            OptionBarRow {
                                SourceChip(
                                    label = "今日计划",
                                    state = SurfaceCapabilityState.LIVE,
                                    selected = uiState.selectedSource == SurfaceSourceType.TODAY,
                                    onClick = { onSourceSelected(SurfaceSourceType.TODAY) },
                                )
                                SourceChip(
                                    label = "当前时段聚焦",
                                    state = SurfaceCapabilityState.LIVE,
                                    selected = uiState.selectedSource == SurfaceSourceType.FOCUS,
                                    onClick = { onSourceSelected(SurfaceSourceType.FOCUS) },
                                )
                                SourceChip(
                                    label = "本周摘要",
                                    state = SurfaceCapabilityState.PREVIEW,
                                    selected = uiState.selectedSource == SurfaceSourceType.WEEKLY,
                                    onClick = { onSourceSelected(SurfaceSourceType.WEEKLY) },
                                )
                                SourceChip(
                                    label = "打卡进度",
                                    state = SurfaceCapabilityState.PREVIEW,
                                    selected = uiState.selectedSource == SurfaceSourceType.HABIT,
                                    onClick = { onSourceSelected(SurfaceSourceType.HABIT) },
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OptionBarRow {
                                    SmallControlButton(
                                        title = "主题",
                                        selected = uiState.selectedStyleControlMode == SurfaceStyleControlMode.THEME,
                                        onClick = { onStyleControlModeSelected(SurfaceStyleControlMode.THEME) },
                                    )
                                    SmallControlButton(
                                        title = "透明度",
                                        selected = uiState.selectedStyleControlMode == SurfaceStyleControlMode.GLASS,
                                        onClick = { onStyleControlModeSelected(SurfaceStyleControlMode.GLASS) },
                                    )
                                    SmallControlButton(
                                        title = "字体",
                                        selected = uiState.selectedStyleControlMode == SurfaceStyleControlMode.FONT,
                                        onClick = { onStyleControlModeSelected(SurfaceStyleControlMode.FONT) },
                                    )
                                }

                                when (uiState.selectedStyleControlMode) {
                                    SurfaceStyleControlMode.THEME -> {
                                        OptionBarRow {
                                            ThemeChip(
                            label = "极简线条",
                            state = SurfaceCapabilityState.LIVE,
                            selected = uiState.selectedTheme == SurfaceThemeStyle.MINIMAL,
                            onClick = { onThemeSelected(SurfaceThemeStyle.MINIMAL) },
                        )
                        ThemeChip(
                            label = "微软卡片",
                            state = SurfaceCapabilityState.LIVE,
                            selected = uiState.selectedTheme == SurfaceThemeStyle.MICROSOFT,
                            onClick = { onThemeSelected(SurfaceThemeStyle.MICROSOFT) },
                        )
                        ThemeChip(
                            label = "专注模式",
                            state = SurfaceCapabilityState.LIVE,
                            selected = uiState.selectedTheme == SurfaceThemeStyle.FOCUS,
                            onClick = { onThemeSelected(SurfaceThemeStyle.FOCUS) },
                        )
                                            ThemeChip(
                                                label = "像素风 / 游戏感",
                                                state = SurfaceCapabilityState.SOON,
                                                selected = uiState.selectedTheme == SurfaceThemeStyle.PIXEL,
                                                onClick = { onThemeSelected(SurfaceThemeStyle.PIXEL) },
                                            )
                                        }
                                    }

                                    SurfaceStyleControlMode.GLASS -> {
                                        SliderSetting(
                                            title = "透明度 / 毛玻璃深度",
                                            summary = "拖动时直接观察预览窗口变化",
                                            value = uiState.glassAlpha,
                                            valueRange = 0.18f..0.70f,
                                            onValueChange = onGlassAlphaChanged,
                                        )
                                    }

                                    SurfaceStyleControlMode.FONT -> {
                                        SliderSetting(
                                            title = "字体大小",
                                            summary = "拖动时同步调整预览中的标题与正文比例",
                                            value = uiState.previewFontScale,
                                            valueRange = 14f..22f,
                                            onValueChange = onFontScaleChanged,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                HomeSectionCard(
                    title = "刷新与同步策略",
                    eyebrow = "Refresh & Sync",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingSwitchRow(
                            title = "显示已完成任务",
                            summary = "让桌面上展示完整进度感",
                            checked = uiState.showCompleted,
                            onCheckedChange = onShowCompletedChanged,
                        )
                        SettingSwitchRow(
                            title = "显示进度条",
                            summary = "为 Widget 增加完成度反馈",
                            checked = uiState.showProgress,
                            onCheckedChange = onShowProgressChanged,
                        )
                        SettingSwitchRow(
                            title = "熄屏保持常亮",
                            summary = "适合锁屏展示 / StandBy 场景",
                            checked = uiState.ambientOn,
                            onCheckedChange = onAmbientChanged,
                        )
                        InfoRow(
                            title = "电池优化豁免状态",
                            value = if (uiState.batteryOptimizationIgnored) "已豁免" else "待处理",
                            summary = "未豁免可能影响刷新频率",
                            highlight = if (uiState.batteryOptimizationIgnored) MaterialTheme.colorScheme.primary else Color(0xFFB86A18),
                        )
                    }
                }
            }

            item {
                HomeSectionCard(
                    title = "壁纸主线入口",
                    eyebrow = "Wallpaper Roadmap",
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoRow(
                            title = "静态壁纸导出",
                            value = "已就绪",
                            summary = "将当前 Surface 预览导出为可设置的静态壁纸图像。",
                            highlight = MaterialTheme.colorScheme.primary,
                        )
                        InfoRow(
                            title = "动态壁纸状态",
                            value = uiState.liveWallpaperStatusLabel,
                            summary = "首次需要你在系统里手动设为 Recursive List；设好一次后，后续内容会自动同步刷新。",
                            highlight = if (uiState.liveWallpaperStatusLabel.contains("已接管")) MaterialTheme.colorScheme.primary else Color(0xFFB86A18),
                        )
                        ActionButton(
                            title = "导出静态壁纸 PNG",
                            state = SurfaceCapabilityState.LIVE,
                            primary = true,
                            onClick = onExportStaticWallpaper,
                        )
                        uiState.exportedWallpaperPath?.let { exportedPath ->
                            ActionButton(
                                title = "预览已导出的壁纸",
                                state = SurfaceCapabilityState.LIVE,
                                primary = false,
                                onClick = { onOpenExportedWallpaper(exportedPath) },
                            )
                            ActionButton(
                                title = "分享已导出的壁纸",
                                state = SurfaceCapabilityState.LIVE,
                                primary = false,
                                onClick = { onShareExportedWallpaper(exportedPath) },
                            )
                            ActionButton(
                                title = "调起设为壁纸",
                                state = SurfaceCapabilityState.LIVE,
                                primary = false,
                                onClick = { onSetExportedWallpaper(exportedPath) },
                            )
                        }
                        ActionButton(
                            title = "自动设为动态壁纸（Shizuku）",
                            state = SurfaceCapabilityState.LIVE,
                            primary = true,
                            onClick = onSetLiveWallpaperByShizuku,
                        )
                        ActionButton(
                            title = "调试 WallpaperManager（Shizuku）",
                            state = SurfaceCapabilityState.LIVE,
                            primary = false,
                            onClick = onDebugWallpaperManagerByShizuku,
                        )
                        ActionButton(
                            title = "打开动态壁纸入口（手动设一次）",
                            state = SurfaceCapabilityState.LIVE,
                            primary = false,
                            onClick = onOpenLiveWallpaperPicker,
                        )
                        ActionButton(
                            title = "添加小组件引导",
                            state = SurfaceCapabilityState.LIVE,
                            primary = false,
                            onClick = onWidgetGuideToggle,
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Surface Build · v2026.04.06-verify-1",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }

            if (uiState.widgetGuideExpanded) {
                item {
                    HomeSectionCard(
                        title = "HyperOS 添加小组件",
                        eyebrow = "Widget Guide",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GuideStep(1, "桌面双指内捏，进入桌面编辑态。")
                            GuideStep(2, "点击“小组件”，找到 Recursive List 展示卡片。")
                            GuideStep(3, "拖到桌面后返回 App 调整数据源、透明度和主题。")
                        }
                    }
                }
            }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun PreviewModeRow(
    selected: SurfacePreviewMode,
    onSelected: (SurfacePreviewMode) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PreviewModeChip(
            label = "锁屏预览",
            stateLabel = "Preview",
            state = SurfaceCapabilityState.PREVIEW,
            selected = selected == SurfacePreviewMode.LOCKSCREEN,
            onClick = { onSelected(SurfacePreviewMode.LOCKSCREEN) },
        )
        PreviewModeChip(
            label = "主屏 Widget",
            stateLabel = "Live First",
            state = SurfaceCapabilityState.LIVE,
            selected = selected == SurfacePreviewMode.WIDGET,
            onClick = { onSelected(SurfacePreviewMode.WIDGET) },
        )
        PreviewModeChip(
            label = "全屏壁纸",
            stateLabel = "Soon",
            state = SurfaceCapabilityState.SOON,
            selected = selected == SurfacePreviewMode.WALLPAPER,
            onClick = { onSelected(SurfacePreviewMode.WALLPAPER) },
        )
    }
}

@Composable
private fun PhonePreviewCard(
    uiState: SurfaceUiState,
    content: SurfacePreviewContent,
) {
    val titleSize = uiState.previewFontScale.sp
    val bodySize = (uiState.previewFontScale - 4f).coerceAtLeast(12f).sp
    val glassColor = Color.White.copy(alpha = uiState.glassAlpha)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f),
        shape = RoundedCornerShape(34.dp),
        color = Color(0xE1161C28),
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(92.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.75f)),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFD9E6FF),
                                Color(0xFFB9D0FF),
                                Color(0xFFEFE7DC),
                            ),
                        ),
                    )
                    .padding(18.dp),
            ) {
                when (uiState.selectedPreviewMode) {
                    SurfacePreviewMode.LOCKSCREEN -> {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "09:41",
                                fontSize = 50.sp,
                                color = Color(0xFFF6FBFF),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "周二 · 适合专注推进的一天",
                                color = Color.White.copy(alpha = 0.84f),
                                fontSize = 13.sp,
                            )
                            Spacer(modifier = Modifier.height(22.dp))
                            GlassPreviewCard(glassColor = glassColor) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "当前时段聚焦",
                                        color = Color(0xFF2D6CFF),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    MiniCapabilityPill("Lock Preview", SurfaceCapabilityState.PREVIEW)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = content.title,
                                    fontSize = titleSize,
                                    lineHeight = (titleSize.value * 1.15f).sp,
                                    color = Color(0xFF142033),
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = content.meta,
                                    fontSize = bodySize,
                                    color = Color(0x99142033),
                                )
                            }
                        }
                    }

                    SurfacePreviewMode.WIDGET -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            GlassPreviewCard(glassColor = glassColor) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = content.label,
                                            modifier = Modifier.weight(1f),
                                            fontSize = bodySize,
                                            color = Color(0x99142033),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        MiniCapabilityPill("Widget MVP", SurfaceCapabilityState.LIVE)
                                    }
                                    Text(
                                        text = themeLabel(uiState.selectedTheme),
                                        fontSize = bodySize,
                                        color = Color(0x99142033),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = content.title,
                                    fontSize = titleSize,
                                    lineHeight = (titleSize.value * 1.15f).sp,
                                    color = Color(0xFF142033),
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                val items = if (uiState.showCompleted) content.items else content.items.take(2)
                                items.forEachIndexed { index, item ->
                                    PreviewItemRow(
                                        text = item,
                                        color = when (index) {
                                            1 -> Color(0xFF64CF9F)
                                            2 -> Color(0xFFFFB347)
                                            else -> Color(0xFF2D6CFF)
                                        },
                                        bodySize = bodySize,
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                                if (uiState.showProgress) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(Color.White.copy(alpha = 0.4f)),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.67f)
                                                    .height(8.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(
                                                        Brush.horizontalGradient(
                                                            listOf(Color(0xFF61A8FF), Color(0xFF89F0D0)),
                                                        ),
                                                    ),
                                            )
                                        }
                                        Text(
                                            text = "67%",
                                            fontSize = 12.sp,
                                            color = Color(0x99142033),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SurfacePreviewMode.WALLPAPER -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            GlassPreviewCard(
                                glassColor = glassColor,
                                modifier = Modifier.padding(top = 80.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "动态壁纸主线预览",
                                        color = Color(0xFF142033),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp,
                                    )
                                    MiniCapabilityPill("Live Preview", SurfaceCapabilityState.PREVIEW)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "通过 App 内部逻辑控制桌面外观，实时投影出你的节奏与主线。当前可导出静态壁纸或通过 Shizuku 设置动态壁纸。",
                                    fontSize = bodySize,
                                    color = Color(0x99142033),
                                )
                            }
                        }
                    }
                }

                if (uiState.ambientOn) {
                    Text(
                        text = "Always On",
                        modifier = Modifier.align(Alignment.BottomEnd),
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassPreviewCard(
    glassColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(glassColor)
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(26.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

@Composable
private fun PreviewItemRow(
    text: String,
    color: Color,
    bodySize: androidx.compose.ui.unit.TextUnit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.36f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.26f))
                .border(1.dp, color.copy(alpha = 0.38f), CircleShape),
        )
        Text(
            text = text,
            fontSize = bodySize,
            color = Color(0xFF142033),
        )
    }
}

@Composable
private fun SliderSetting(
    title: String,
    summary: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(
    title: String,
    value: String,
    summary: String,
    highlight: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = highlight)
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionButton(
    title: String,
    state: SurfaceCapabilityState,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (primary) {
        Brush.linearGradient(listOf(Color(0xD1FFFFFF), Color(0xC6D1EAFF)))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.30f)))
    }
    val textColor = if (primary) Color(0xFF204079) else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            MiniCapabilityPill(
                text = when (state) {
                    SurfaceCapabilityState.LIVE -> "Live Flow"
                    SurfaceCapabilityState.PREVIEW -> "Preview"
                    SurfaceCapabilityState.SOON -> "Soon"
                },
                state = state,
            )
        }
    }
}

@Composable
private fun GuideStep(
    index: Int,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.24f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(Color(0x292D6CFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                color = Color(0xFF2D6CFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewModeChip(
    label: String,
    stateLabel: String,
    state: SurfaceCapabilityState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableChip(
        selected = selected,
        onClick = onClick,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(6.dp))
        MiniCapabilityPill(text = stateLabel, state = state)
    }
}

@Composable
private fun SourceChip(
    label: String,
    state: SurfaceCapabilityState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableChip(selected = selected, onClick = onClick) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(6.dp))
        MiniCapabilityPill(
            text = when (state) {
                SurfaceCapabilityState.LIVE -> "Live"
                SurfaceCapabilityState.PREVIEW -> "Preview"
                SurfaceCapabilityState.SOON -> "Soon"
            },
            state = state,
        )
    }
}

@Composable
private fun ThemeChip(
    label: String,
    state: SurfaceCapabilityState,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SelectableChip(selected = selected, onClick = onClick) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.width(6.dp))
        MiniCapabilityPill(
            text = when (state) {
                SurfaceCapabilityState.LIVE -> "Live"
                SurfaceCapabilityState.PREVIEW -> "Preview"
                SurfaceCapabilityState.SOON -> "Soon"
            },
            state = state,
        )
    }
}

@Composable
private fun SelectableChip(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.24f),
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.22f),
                RoundedCornerShape(999.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun CapabilityPill(
    text: String,
    state: SurfaceCapabilityState,
) {
    val colors = capabilityColors(state)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.first)
            .border(1.dp, colors.second, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = colors.third,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NeutralPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.40f))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MiniCapabilityPill(
    text: String,
    state: SurfaceCapabilityState,
) {
    val colors = capabilityColors(state)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.first)
            .border(1.dp, colors.second, RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = colors.third,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun ControlModeButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.24f),
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.22f),
                RoundedCornerShape(18.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SmallControlButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.24f),
            )
            .border(
                1.dp,
                if (selected) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.22f),
                RoundedCornerShape(999.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun OptionBarRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun FlowRowLike(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun capabilityColors(state: SurfaceCapabilityState): Triple<Color, Color, Color> {
    return when (state) {
        SurfaceCapabilityState.LIVE -> Triple(Color(0x2461A8FF), Color(0x2E61A8FF), Color(0xFF1F6FFF))
        SurfaceCapabilityState.PREVIEW -> Triple(Color(0x1F7A58FF), Color(0x297A58FF), Color(0xFF7A58FF))
        SurfaceCapabilityState.SOON -> Triple(Color(0x24FFBA62), Color(0x2EFFBA62), Color(0xFFA36A17))
    }
}

private fun themeLabel(theme: SurfaceThemeStyle): String {
    return when (theme) {
        SurfaceThemeStyle.MINIMAL -> "极简线条"
        SurfaceThemeStyle.MICROSOFT -> "微软卡片"
        SurfaceThemeStyle.FOCUS -> "专注模式"
        SurfaceThemeStyle.PIXEL -> "像素风"
    }
}

