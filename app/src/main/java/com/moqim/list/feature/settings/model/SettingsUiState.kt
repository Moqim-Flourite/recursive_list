package com.moqim.list.feature.settings.model

data class SettingsUiState(
    val morningViewEnabled: Boolean = true,
    val defaultHomeTab: String = "今日",
    val defaultHomeTabRoute: String = "today",
    val widgetDefaultStyle: String = "极简",
    val liveWallpaperSource: String = "今日",
    val displayStatus: String = "静态壁纸 + 动态壁纸 + Widget",
    val batteryTipStatus: String = "未配置",
    val aboutText: String = "按月、周、日、时段组织执行，并把重点计划持续展示在主界面的 Android 执行系统。",
    val appThemeLabel: String = "极简白",
    val desktopStatus: String = "Wallpaper + Widget",
    val focusSummary: String = "执行 + 展示",
    val morningStartTime: String = "05:00",
    val morningTime: String = "08:00",
    val noonTime: String = "11:00",
    val afternoonTime: String = "14:00",
    val eveningTime: String = "18:00",
    val backupStatusMessage: String? = null,
    val liveWallpaperSetStatusMessage: String? = null,
    // 检查更新状态
    val updateStatus: String = "检查更新",
    val isCheckingUpdate: Boolean = false,
    val updateDialogInfo: UpdateDialogInfo? = null,
)

data class UpdateDialogInfo(
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
)
