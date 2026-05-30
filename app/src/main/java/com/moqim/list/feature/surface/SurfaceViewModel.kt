package com.moqim.list.feature.surface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomHabitRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.feature.home.ShizukuWallpaperSetter
import com.moqim.list.feature.surface.model.SurfaceCapabilityState
import com.moqim.list.system.LiveWallpaperStatus
import com.moqim.list.feature.surface.model.SurfacePreviewContent
import com.moqim.list.feature.surface.model.SurfaceControlPanelMode
import com.moqim.list.feature.surface.model.SurfacePreviewMode
import com.moqim.list.feature.surface.model.SurfaceSourceType
import com.moqim.list.feature.surface.model.SurfaceStyleControlMode
import com.moqim.list.feature.surface.model.SurfaceThemeStyle
import com.moqim.list.feature.surface.model.SurfaceUiState
import com.moqim.list.widget.CurrentSegmentWidgetContentBuilder
import com.moqim.list.widget.MorningWidgetContentBuilder
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SurfaceViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurfaceUiState())
    val uiState: StateFlow<SurfaceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { preferences ->
                _uiState.update {
                    it.copy(
                        selectedTheme = when (preferences.widgetDefaultStyle) {
                            "compact" -> SurfaceThemeStyle.MICROSOFT
                            "focus" -> SurfaceThemeStyle.FOCUS
                            else -> SurfaceThemeStyle.MINIMAL
                        },
                        selectedSource = when (preferences.liveWallpaperSource) {
                            "focus" -> SurfaceSourceType.FOCUS
                            "weekly" -> SurfaceSourceType.WEEKLY
                            "habit" -> SurfaceSourceType.HABIT
                            else -> SurfaceSourceType.TODAY
                        },
                        liveWallpaperStatusLabel = LiveWallpaperStatus.statusLabel(appContext),
                        previewFontScale = preferences.wallpaperFontScale,
                    )
                }
                refreshPreviewContent(_uiState.value.selectedSource)
            }
        }

        viewModelScope.launch {
            refreshPreviewContent(_uiState.value.selectedSource)
        }
    }

    fun onPreviewModeSelected(mode: SurfacePreviewMode) {
        _uiState.update { it.copy(selectedPreviewMode = mode) }
    }

    fun onControlPanelModeSelected(mode: SurfaceControlPanelMode) {
        _uiState.update { it.copy(selectedControlPanelMode = mode) }
    }

    fun onStyleControlModeSelected(mode: SurfaceStyleControlMode) {
        _uiState.update { it.copy(selectedStyleControlMode = mode) }
    }

    fun onSourceSelected(source: SurfaceSourceType) {
        _uiState.update { it.copy(selectedSource = source) }
        viewModelScope.launch {
            settingsRepository.setLiveWallpaperSource(
                when (source) {
                    SurfaceSourceType.FOCUS -> "focus"
                    SurfaceSourceType.WEEKLY -> "weekly"
                    SurfaceSourceType.HABIT -> "habit"
                    SurfaceSourceType.TODAY -> "today"
                },
            )
            refreshPreviewContent(source)
        }
    }

    fun onThemeSelected(theme: SurfaceThemeStyle) {
        _uiState.update { it.copy(selectedTheme = theme) }
        viewModelScope.launch {
            settingsRepository.setWidgetDefaultStyle(
                when (theme) {
                    SurfaceThemeStyle.MICROSOFT -> "compact"
                    SurfaceThemeStyle.FOCUS -> "focus"
                    else -> "standard"
                },
            )
        }
    }

    fun onGlassAlphaChanged(value: Float) {
        _uiState.update { it.copy(glassAlpha = value.coerceIn(0.18f, 0.70f)) }
    }

    fun onFontScaleChanged(value: Float) {
        val newValue = value.coerceIn(14f, 22f)
        _uiState.update { it.copy(previewFontScale = newValue) }
        viewModelScope.launch {
            settingsRepository.setWallpaperFontScale(newValue)
        }
    }

    fun onShowCompletedChanged(enabled: Boolean) {
        _uiState.update { it.copy(showCompleted = enabled) }
    }

    fun onShowProgressChanged(enabled: Boolean) {
        _uiState.update { it.copy(showProgress = enabled) }
    }

    fun onAmbientChanged(enabled: Boolean) {
        _uiState.update { it.copy(ambientOn = enabled) }
    }

    fun onWidgetGuideToggle() {
        _uiState.update { it.copy(widgetGuideExpanded = !it.widgetGuideExpanded) }
    }

    fun onExportStaticWallpaper() {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                exportStaticWallpaper(_uiState.value)
            }
            _uiState.update {
                it.copy(
                    exportFeedbackMessage = "静态壁纸已导出：${file.absolutePath}",
                    exportedWallpaperPath = file.absolutePath,
                )
            }
        }
    }

    fun onExportFeedbackShown() {
        _uiState.update { it.copy(exportFeedbackMessage = null) }
    }

    fun onLiveWallpaperSetFeedbackShown() {
        _uiState.update { it.copy(liveWallpaperSetFeedbackMessage = null) }
    }

    fun onLiveWallpaperDebugShown() {
        _uiState.update { it.copy(liveWallpaperDebugMessage = null) }
    }

    fun onSetLiveWallpaperByShizuku() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = ShizukuWallpaperSetter.trySetLiveWallpaper()
            _uiState.update {
                it.copy(
                    liveWallpaperSetFeedbackMessage = when (result) {
                        is ShizukuWallpaperSetter.SetResult.Applied ->
                            "已自动设为动态壁纸"
                        is ShizukuWallpaperSetter.SetResult.OpenedSystemPicker ->
                            "已打开系统动态壁纸设置页，请手动确认一次"
                        is ShizukuWallpaperSetter.SetResult.Failed ->
                            "自动设置未成功，已记录调试信息"
                    },
                    liveWallpaperDebugMessage = when (result) {
                        is ShizukuWallpaperSetter.SetResult.Applied -> result.detail
                        is ShizukuWallpaperSetter.SetResult.OpenedSystemPicker -> result.detail
                        is ShizukuWallpaperSetter.SetResult.Failed -> result.detail
                    },
                )
            }
        }
    }

    fun onDebugWallpaperManagerByShizuku() {
        viewModelScope.launch(Dispatchers.IO) {
            ShizukuWallpaperSetter.reflectWallpaperManagerMethods()
            _uiState.update {
                it.copy(
                    liveWallpaperDebugMessage = "调试结果已写入：${ShizukuWallpaperSetter.getDebugFilePath()}",
                )
            }
        }
    }

    private suspend fun refreshPreviewContent(source: SurfaceSourceType) {
        val content = when (source) {
            SurfaceSourceType.TODAY -> {
                val widget = MorningWidgetContentBuilder(appContext).build()
                SurfacePreviewContent(
                    label = "今日计划",
                    title = widget.title,
                    items = listOf(widget.top1, widget.top2, widget.top3).map {
                        if (it.completed) it.text.replaceFirst(Regex("""^(\d+\.)\s*"""), "$1 ✓ ") else it.text
                    },
                    meta = widget.summary,
                    capabilityState = SurfaceCapabilityState.LIVE,
                )
            }

            SurfaceSourceType.FOCUS -> {
                val widget = CurrentSegmentWidgetContentBuilder(appContext).build()
                SurfacePreviewContent(
                    label = "当前时段聚焦",
                    title = widget.title,
                    items = listOf(widget.task1, widget.task2, widget.task3).map {
                        if (it.completed) it.text.replaceFirst(Regex("""^(\d+\.)\s*"""), "$1 ✓ ") else it.text
                    },
                    meta = widget.summary,
                    capabilityState = SurfaceCapabilityState.LIVE,
                )
            }

            SurfaceSourceType.WEEKLY -> {
                val db = DatabaseProvider.get(appContext)
                val dailyPlanRepository = RoomDailyPlanRepository(
                    dailyPlanDao = db.dailyPlanDao(),
                    weeklyPlanDao = db.weeklyPlanDao(),
                    executionTaskDao = db.executionTaskDao(),
                )
                dailyPlanRepository.seedTodayIfNeeded()
                val todayPlan = dailyPlanRepository.observeTodayPlan(LocalDate.now().toString()).first()
                val summary = todayPlan?.summary ?: "本周主线正在准备中"
                SurfacePreviewContent(
                    label = "本周摘要",
                    title = summary,
                    items = buildWeeklyItems(summary),
                    meta = "今日计划承接本周主线",
                    capabilityState = SurfaceCapabilityState.LIVE,
                )
            }

            SurfaceSourceType.HABIT -> {
                val db = DatabaseProvider.get(appContext)
                val habitRepository = RoomHabitRepository(
                    habitTemplateDao = db.habitTemplateDao(),
                    habitRecordDao = db.habitRecordDao(),
                )
                val today = LocalDate.now().toString()
                habitRepository.seedDefaultsIfNeeded()
                habitRepository.ensureTodayRecords(today)
                val habitSummary = habitRepository.observeTodayHabitSummary(today).first()
                SurfacePreviewContent(
                    label = "打卡进度",
                    title = habitSummary.summaryText,
                    items = buildHabitItems(habitSummary),
                    meta = "固定打卡不打断主线，但必须保持连续",
                    capabilityState = SurfaceCapabilityState.LIVE,
                )
            }
        }

        _uiState.update { it.copy(previewContent = content) }
    }

    private fun exportStaticWallpaper(state: SurfaceUiState): File {
        val width = 1440
        val height = 3200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                gradientColorsForExport(state.selectedTheme),
                null,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(122, 255, 255, 255)
        }
        canvas.drawRoundRect(72f, 180f, width - 72f, height - 180f, 42f, 42f, cardPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#142033")
            textSize = 92f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#5A6B84")
            textSize = 42f
        }
        val itemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1C2B44")
            textSize = 52f
        }

        val content = state.previewContent
        drawWrappedText(
            canvas = canvas,
            text = content.label,
            paint = metaPaint,
            x = 96f,
            startY = 130f,
            maxWidth = width - 192f,
            lineHeight = 44f,
            maxLines = 1,
        )

        var y = 260f
        y = drawWrappedText(
            canvas = canvas,
            text = content.title,
            paint = titlePaint,
            x = 104f,
            startY = y,
            maxWidth = width - 208f,
            lineHeight = 104f,
            maxLines = 3,
        ) + 12f

        y = drawWrappedText(
            canvas = canvas,
            text = "当前时间：${LocalTime.now().withSecond(0).withNano(0)}",
            paint = metaPaint,
            x = 104f,
            startY = y,
            maxWidth = width - 208f,
            lineHeight = 44f,
            maxLines = 1,
        ) + 10f

        y = drawWrappedText(
            canvas = canvas,
            text = themeLabelForExport(state.selectedTheme),
            paint = metaPaint,
            x = 104f,
            startY = y,
            maxWidth = width - 208f,
            lineHeight = 44f,
            maxLines = 1,
        ) + 18f

        y = drawWrappedText(
            canvas = canvas,
            text = content.meta,
            paint = metaPaint,
            x = 104f,
            startY = y,
            maxWidth = width - 208f,
            lineHeight = 52f,
            maxLines = 3,
        ) + 40f

        content.items.take(5).forEach { item ->
            y = drawItemBlock(
                canvas = canvas,
                text = item,
                paint = itemPaint,
                x = 104f,
                startY = y,
                maxWidth = width - 208f,
            ) + 24f
        }

        val exportDir = File("/sdcard/Download/moqim_list")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val outputFile = File(exportDir, "surface_wallpaper_$timestamp.png")
        FileOutputStream(outputFile).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.flush()
        }
        bitmap.recycle()
        return outputFile
    }

    private fun themeLabelForExport(theme: SurfaceThemeStyle): String = when (theme) {
        SurfaceThemeStyle.MINIMAL -> "极简线条"
        SurfaceThemeStyle.MICROSOFT -> "微软卡片"
        SurfaceThemeStyle.FOCUS -> "专注模式"
        SurfaceThemeStyle.PIXEL -> "像素风 / 游戏感"
    }

    private fun gradientColorsForExport(theme: SurfaceThemeStyle): IntArray = when (theme) {
        SurfaceThemeStyle.MICROSOFT -> intArrayOf(
            android.graphics.Color.parseColor("#E8EEF9"),
            android.graphics.Color.parseColor("#C9D8F0"),
            android.graphics.Color.parseColor("#F5EFE6"),
        )
        SurfaceThemeStyle.FOCUS -> intArrayOf(
            android.graphics.Color.parseColor("#DDEBFF"),
            android.graphics.Color.parseColor("#B8D4FF"),
            android.graphics.Color.parseColor("#DCEFE3"),
        )
        else -> intArrayOf(
            android.graphics.Color.parseColor("#D9E6FF"),
            android.graphics.Color.parseColor("#B9D0FF"),
            android.graphics.Color.parseColor("#EFE7DC"),
        )
    }

    private fun drawItemBlock(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        startY: Float,
        maxWidth: Float,
    ): Float {
        val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#2D6CFF")
        }
        canvas.drawCircle(x + 10f, startY - 18f, 8f, markerPaint)
        return drawWrappedText(
            canvas = canvas,
            text = text,
            paint = paint,
            x = x + 34f,
            startY = startY,
            maxWidth = maxWidth - 34f,
            lineHeight = 66f,
            maxLines = 3,
        )
    }

    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        maxLines: Int,
    ): Float {
        val lines = wrapText(text, paint, maxWidth).take(maxLines)
        var y = startY
        lines.forEach { line ->
            canvas.drawText(line, x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        var current = ""
        text.forEach { ch ->
            val candidate = current + ch
            if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                current = candidate
            } else {
                result += current.trim()
                current = ch.toString()
            }
        }
        if (current.isNotBlank()) {
            result += current.trim()
        }
        return result.ifEmpty { listOf(text.take(1)) }
    }

    private fun buildWeeklyItems(summary: String): List<String> {
        val compact = summary
            .replace("。", "。|")
            .split("|")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
        return listOf(
            compact.getOrElse(0) { "1. 本周主线继续推进" },
            compact.getOrElse(1) { "2. 今天继续承接周目标" },
            compact.getOrElse(2) { "3. 及时收口并为下一步铺路" },
        ).mapIndexed { index, text -> "${index + 1}. ${text.removePrefix("${index + 1}. ")}" }
    }

    private fun buildHabitItems(habitSummary: com.moqim.list.domain.model.HabitSummary): List<String> {
        val realItems = habitSummary.items.take(3).mapIndexed { index, item ->
            val streakText = if (item.showTotalCompletedDays) " · 连续${item.currentStreakDays}天" else ""
            "${index + 1}. ${item.title} ${item.completedCount}/${item.dailyTargetCount}$streakText".trim()
        }
        return listOf(
            realItems.getOrElse(0) { "1. 今日保持至少一个固定打卡" },
            realItems.getOrElse(1) { "2. 打卡是节律，不是负担" },
            realItems.getOrElse(2) { "3. 晚上记得把固定项补完" },
        )
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val repository = SettingsPreferencesRepository(context.applicationContext)
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SurfaceViewModel(
                        appContext = context.applicationContext,
                        settingsRepository = repository,
                    ) as T
                }
            }
        }
    }
}