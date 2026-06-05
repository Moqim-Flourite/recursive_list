package com.moqim.list.feature.settings

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moqim.list.data.backup.BackupService
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.feature.home.ShizukuWallpaperSetter
import com.moqim.list.feature.settings.model.SettingsUiState
import com.moqim.list.system.LiveWallpaperStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val appContext: Context,
    private val repository: SettingsPreferencesRepository,
    private val backupService: BackupService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { preferences ->
                _uiState.value = _uiState.value.copy(
                    morningViewEnabled = preferences.morningViewEnabled,
                    appThemeLabel = preferences.appTheme.asThemeLabel(),
                    defaultHomeTab = preferences.defaultHomeTab.asHomeTabLabel(),
                    defaultHomeTabRoute = preferences.defaultHomeTab,
                    widgetDefaultStyle = preferences.widgetDefaultStyle.asWidgetStyleLabel(),
                    liveWallpaperSource = preferences.liveWallpaperSource.asLiveWallpaperSourceLabel(),
                    displayStatus = "静态壁纸 + 动态壁纸 + Widget",
                    batteryTipStatus = batteryOptimizationStatusLabel(),
                    desktopStatus = LiveWallpaperStatus.statusLabel(appContext),
                    morningStartTime = preferences.morningStartTime,
                    morningTime = preferences.morningTime,
                    noonTime = preferences.noonTime,
                    afternoonTime = preferences.afternoonTime,
                    eveningTime = preferences.eveningTime,
                )
            }
        }
    }

    fun onMorningViewEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            repository.setMorningViewEnabled(enabled)
        }
    }

    fun onDefaultHomeTabChange(value: String) {
        viewModelScope.launch {
            repository.setDefaultHomeTab(value)
        }
    }

    fun onWidgetDefaultStyleChange(value: String) {
        viewModelScope.launch {
            repository.setWidgetDefaultStyle(value)
        }
    }

    fun onAppThemeChange(value: String) {
        viewModelScope.launch {
            repository.setAppTheme(value)
        }
    }

    fun onLiveWallpaperSourceChange(value: String) {
        viewModelScope.launch {
            repository.setLiveWallpaperSource(value)
        }
    }

    fun onSegmentBoundaryTimesChange(
        morningStartTime: String,
        morningTime: String,
        noonTime: String,
        afternoonTime: String,
        eveningTime: String,
    ) {
        viewModelScope.launch {
            repository.setSegmentBoundaryTimes(
                morningStartTime = morningStartTime,
                morningTime = morningTime,
                noonTime = noonTime,
                afternoonTime = afternoonTime,
                eveningTime = eveningTime,
            )
        }
    }

    fun onExportBackup() {
        viewModelScope.launch {
            val file = backupService.exportToDownloads()
            _uiState.value = _uiState.value.copy(
                backupStatusMessage = "备份已导出：${file.absolutePath}",
            )
        }
    }

    fun onRestoreLatestBackup() {
        viewModelScope.launch {
            val file = backupService.restoreLatestFromDownloads()
            val message = if (file != null) {
                "已恢复最近备份：${file.absolutePath}"
            } else {
                "未找到可恢复备份"
            }
            _uiState.value = _uiState.value.copy(
                backupStatusMessage = message,
            )
        }
    }

    fun onBackupStatusShown() {
        _uiState.value = _uiState.value.copy(backupStatusMessage = null)
    }

    fun onLiveWallpaperSetStatusShown() {
        _uiState.value = _uiState.value.copy(liveWallpaperSetStatusMessage = null)
    }

    fun onSetLiveWallpaperByShizuku() {
        viewModelScope.launch {
            val result = ShizukuWallpaperSetter.trySetLiveWallpaper()
            _uiState.value = _uiState.value.copy(
                liveWallpaperSetStatusMessage = when (result) {
                    is ShizukuWallpaperSetter.SetResult.Applied ->
                        "已自动设为动态壁纸"
                    is ShizukuWallpaperSetter.SetResult.OpenedSystemPicker ->
                        "已打开系统动态壁纸设置页，请手动确认一次"
                    is ShizukuWallpaperSetter.SetResult.Failed ->
                        "自动设置失败：${result.detail.ifBlank { "未知错误" }}"
                }
            )
        }
    }

    private val updateChecker by lazy { com.moqim.list.data.update.GitHubUpdateChecker(appContext) }

    fun onCheckForUpdate() {
        if (_uiState.value.isCheckingUpdate) return
        _uiState.value = _uiState.value.copy(isCheckingUpdate = true, updateStatus = "检查中...")
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate()

            result.fold(
                onSuccess = { info ->
                    if (info != null) {
                        _uiState.value = _uiState.value.copy(
                            isCheckingUpdate = false,
                            updateStatus = "发现新版本 ${info.version}",
                            updateDialogInfo = com.moqim.list.feature.settings.model.UpdateDialogInfo(
                                versionName = info.version,
                                releaseName = info.releaseName,
                                releaseNotes = info.releaseBody,
                                publishedAt = info.publishedAt,
                                apkDownloadUrl = info.apkDownloadUrl,
                                apkFileName = info.apkFileName,
                                apkSizeBytes = info.apkSizeBytes,
                            ),
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isCheckingUpdate = false,
                            updateStatus = "已是最新版本 ✓",
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateStatus = "检查失败: ${e.message}",
                    )
                },
            )
        }
    }

    fun onDismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(updateDialogInfo = null)
    }

    fun onInstallUpdate() {
        val dialogInfo = _uiState.value.updateDialogInfo ?: return
        val apkUrl = dialogInfo.apkDownloadUrl ?: return

        // 构造 UpdateInfo 传给下载器，文件名用 API 返回的 asset name
        val updateInfo = com.moqim.list.data.update.GitHubUpdateChecker.UpdateInfo(
            version = dialogInfo.versionName,
            releaseName = dialogInfo.releaseName,
            releaseBody = dialogInfo.releaseNotes,
            apkDownloadUrl = apkUrl,
            apkFileName = dialogInfo.apkFileName ?: apkUrl.substringAfterLast('/').substringBefore('?'),
            apkSizeBytes = dialogInfo.apkSizeBytes,
            publishedAt = dialogInfo.publishedAt,
        )

        _uiState.value = _uiState.value.copy(updateStatus = "下载中...")
        viewModelScope.launch {
            val result = com.moqim.list.data.update.ApkDownloader.download(
                context = appContext,
                updateInfo = updateInfo,
                onProgress = { pct ->
                    _uiState.value = _uiState.value.copy(updateStatus = "下载中 $pct%")
                },
            )

            result.fold(
                onSuccess = { file ->
                    _uiState.value = _uiState.value.copy(
                        updateStatus = "下载完成，准备安装...",
                        updateDialogInfo = null,
                    )
                    // 打开 APK 安装
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            androidx.core.content.FileProvider.getUriForFile(
                                appContext,
                                "${appContext.packageName}.fileprovider",
                                file,
                            ),
                            "application/vnd.android.package-archive",
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { appContext.startActivity(intent) }
                    _uiState.value = _uiState.value.copy(updateStatus = "检查更新")
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(updateStatus = "下载失败: ${e.message}")
                },
            )
        }
    }

    fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { appContext.startActivity(intent) }
    }

    private fun batteryOptimizationStatusLabel(): String {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val ignored = powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        return if (ignored) "已忽略优化" else "待处理"
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            val repository = SettingsPreferencesRepository(appContext)
            val backupService = BackupService(appContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(appContext, repository, backupService) as T
                }
            }
        }
    }
}

private fun String.asHomeTabLabel(): String = when (this) {
    "today" -> "今日"
    "plans" -> "计划"
    "surface" -> "展示"
    "settings" -> "设置"
    else -> "今日"
}

private fun String.asWidgetStyleLabel(): String = when (this) {
    "compact" -> "卡片"
    "focus" -> "专注"
    else -> "极简"
}

private fun String.asThemeLabel(): String = when (this) {
    "light" -> "极简白"
    "dark" -> "深邃黑"
    else -> "通透玻璃"
}

private fun String.asLiveWallpaperSourceLabel(): String = when (this) {
    "focus" -> "当前时段"
    "weekly" -> "本周"
    "habit" -> "打卡"
    else -> "今日"
}
