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

    fun onCheckForUpdate() {
        if (_uiState.value.isCheckingUpdate) return
        _uiState.value = _uiState.value.copy(isCheckingUpdate = true, updateStatus = "检查中...")
        viewModelScope.launch {
            val currentVersionCode = try {
                appContext.packageManager
                    .getPackageInfo(appContext.packageName, 0)
                    .longVersionCode.toInt()
            } catch (_: Exception) { 1 }

            // 网络操作放到 IO
            val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.moqim.list.data.update.GitHubUpdateChecker.checkForUpdate(currentVersionCode)
            }

            when (result) {
                is com.moqim.list.data.update.UpdateResult.Available -> {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateStatus = "发现新版本 ${result.versionName}",
                        updateDialogInfo = com.moqim.list.feature.settings.model.UpdateDialogInfo(
                            versionName = result.versionName,
                            releaseName = result.releaseName,
                            releaseNotes = result.releaseNotes,
                            publishedAt = result.publishedAt,
                            apkDownloadUrl = result.apkDownloadUrl,
                            apkSizeBytes = result.apkSizeBytes,
                        ),
                    )
                }
                is com.moqim.list.data.update.UpdateResult.UpToDate -> {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateStatus = "已是最新版本 ✓",
                    )
                }
                is com.moqim.list.data.update.UpdateResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isCheckingUpdate = false,
                        updateStatus = "检查失败: ${result.message}",
                    )
                }
            }
        }
    }

    fun onDismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(updateDialogInfo = null)
    }

    fun onInstallUpdate() {
        val info = _uiState.value.updateDialogInfo ?: return
        val apkUrl = info.apkDownloadUrl ?: return
        _uiState.value = _uiState.value.copy(updateStatus = "下载中...")
        com.moqim.list.data.update.ApkDownloader.download(
            context = appContext,
            apkUrl = apkUrl,
            onProgress = { read, total ->
                val pct = if (total > 0) (read * 100 / total) else -1
                _uiState.value = _uiState.value.copy(
                    updateStatus = if (pct >= 0) "下载中 $pct%" else "下载中..."
                )
            },
            onComplete = { file ->
                if (file != null) {
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
                } else {
                    _uiState.value = _uiState.value.copy(updateStatus = "下载失败，请重试")
                }
            },
        )
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
