package com.moqim.list.feature.surface.model

enum class SurfacePreviewMode {
    LOCKSCREEN,
    WIDGET,
    WALLPAPER,
}

enum class SurfaceSourceType {
    TODAY,
    FOCUS,
    WEEKLY,
    HABIT,
}

enum class SurfaceThemeStyle {
    MINIMAL,
    MICROSOFT,
    FOCUS,
    PIXEL,
}

enum class SurfaceCapabilityState {
    LIVE,
    PREVIEW,
    SOON,
}

enum class SurfaceControlPanelMode {
    SOURCE,
    STYLE,
}

enum class SurfaceStyleControlMode {
    THEME,
    GLASS,
    FONT,
}

data class SurfacePreviewContent(
    val label: String,
    val title: String,
    val items: List<String>,
    val meta: String,
    val capabilityState: SurfaceCapabilityState,
)

data class SurfaceUiState(
    val title: String = "展示中心",
    val description: String = "当前先用 Widget 验证展示体验，同时把静态壁纸导出与动态壁纸主线入口预埋到 Surface，后续直接沿这条链继续做。",
    val selectedPreviewMode: SurfacePreviewMode = SurfacePreviewMode.LOCKSCREEN,
    val selectedSource: SurfaceSourceType = SurfaceSourceType.TODAY,
    val selectedTheme: SurfaceThemeStyle = SurfaceThemeStyle.MINIMAL,
    val selectedControlPanelMode: SurfaceControlPanelMode = SurfaceControlPanelMode.SOURCE,
    val selectedStyleControlMode: SurfaceStyleControlMode = SurfaceStyleControlMode.THEME,
    val glassAlpha: Float = 0.30f,
    val previewFontScale: Float = 18f,
    val showCompleted: Boolean = true,
    val showProgress: Boolean = true,
    val ambientOn: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val widgetGuideExpanded: Boolean = false,
    val morningWidgetState: SurfaceCapabilityState = SurfaceCapabilityState.LIVE,
    val currentSegmentWidgetState: SurfaceCapabilityState = SurfaceCapabilityState.LIVE,
    val stylePreviewState: SurfaceCapabilityState = SurfaceCapabilityState.LIVE,
    val wallpaperExportState: SurfaceCapabilityState = SurfaceCapabilityState.LIVE,
    val staticWallpaperEntryState: SurfaceCapabilityState = SurfaceCapabilityState.LIVE,
    val liveWallpaperEntryState: SurfaceCapabilityState = SurfaceCapabilityState.LIVE,
    val liveWallpaperStatusLabel: String = "未设为当前主页动态壁纸（需手动设置一次）",
    val exportFeedbackMessage: String? = null,
    val liveWallpaperSetFeedbackMessage: String? = null,
    val liveWallpaperDebugMessage: String? = null,
    val exportedWallpaperPath: String? = null,
    val previewContent: SurfacePreviewContent = SurfacePreviewContent(
        label = "今日计划",
        title = "晨间视图：今天只盯住最重要的三件事",
        items = listOf(
            "Top 1 · 重画首页结构与材质层级",
            "Top 2 · 确认 Widget 文案与句式",
            "Top 3 · 晚间收口并写下明天第一步",
        ),
        meta = "已接入方向：晨间视图 Widget",
        capabilityState = SurfaceCapabilityState.LIVE,
    ),
)