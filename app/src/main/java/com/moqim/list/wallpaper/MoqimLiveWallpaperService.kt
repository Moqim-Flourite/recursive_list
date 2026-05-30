package com.moqim.list.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import com.moqim.list.widget.CurrentSegmentWidgetTaskItem
import com.moqim.list.widget.MorningWidgetTaskItem
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomHabitRepository
import com.moqim.list.widget.CurrentSegmentWidgetContentBuilder
import com.moqim.list.widget.MorningWidgetContentBuilder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private data class WallpaperTaskItem(
    val text: String,
    val completed: Boolean = false,
) {
    val displayText: String
        get() = if (completed) {
            text.replaceFirst(Regex("""^(\d+\.)\s*"""), "$1 ✓ ")
        } else {
            text
        }

    companion object {
        fun fromMorning(item: MorningWidgetTaskItem): WallpaperTaskItem =
            WallpaperTaskItem(text = item.text, completed = item.completed)

        fun fromCurrentSegment(item: CurrentSegmentWidgetTaskItem): WallpaperTaskItem =
            WallpaperTaskItem(text = item.text, completed = item.completed)
    }
}

class MoqimLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        appendDebug("service onCreateEngine")
        return MoqimWallpaperEngine()
    }

    private fun appendDebug(message: String) {
        runCatching {
            val file = File("/sdcard/Download/moqim_list/live_wallpaper_runtime.txt")
            file.parentFile?.mkdirs()
            file.appendText("${System.currentTimeMillis()}  $message\n")
        }
    }

    inner class MoqimWallpaperEngine : Engine() {

        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val refreshHandler = Handler(Looper.getMainLooper())
        private val refreshRunnable = object : Runnable {
            override fun run() {
                if (isVisible) {
                    refreshContentAndDraw()
                    refreshHandler.postDelayed(this, 60_000L)
                }
            }
        }
        private val refreshReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WallpaperRefreshNotifier.ACTION_REFRESH_WALLPAPER) {
                    refreshContentAndDraw()
                }
            }
        }

        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#142033")
            textSize = 64f
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD)
        }
        private val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#355070")
            textSize = 34f
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E2F45")
            textSize = 42f
        }

        @Volatile
        private var wallpaperSource: String = "today"
        @Volatile
        private var wallpaperStyle: String = "standard"
        @Volatile
        private var wallpaperLabel: String = "Recursive List live"
    @Volatile
    private var wallpaperTitle: String = "Recursive List"
        @Volatile
        private var wallpaperSummary: String = "动态壁纸加载中…"
        @Volatile
        private var wallpaperItems: List<WallpaperTaskItem> = listOf(
            WallpaperTaskItem("正在准备今日展示内容")
        )

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            appendDebug("engine onCreate")
            super.onCreate(surfaceHolder)
            runCatching {
                registerReceiver(
                    refreshReceiver,
                    IntentFilter(WallpaperRefreshNotifier.ACTION_REFRESH_WALLPAPER),
                    Context.RECEIVER_NOT_EXPORTED,
                )
                appendDebug("engine receiver registered")
            }.onFailure {
                appendDebug("engine registerReceiver failed: ${it.javaClass.simpleName}: ${it.message}")
            }
            refreshContentAndDraw()
        }

        override fun onDestroy() {
            appendDebug("engine onDestroy")
            refreshHandler.removeCallbacks(refreshRunnable)
            runCatching { unregisterReceiver(refreshReceiver) }
                .onFailure { appendDebug("engine unregisterReceiver failed: ${it.javaClass.simpleName}: ${it.message}") }
            engineScope.cancel()
            super.onDestroy()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            appendDebug("engine onSurfaceCreated")
            super.onSurfaceCreated(holder)
            refreshContentAndDraw()
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            drawFrame(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            appendDebug("engine onVisibilityChanged visible=$visible")
            if (visible) {
                refreshContentAndDraw()
                refreshHandler.removeCallbacks(refreshRunnable)
                refreshHandler.postDelayed(refreshRunnable, 60_000L)
            } else {
                refreshHandler.removeCallbacks(refreshRunnable)
            }
        }

        private fun refreshContentAndDraw() {
            appendDebug("refreshContentAndDraw start")
            engineScope.launch(Dispatchers.IO) {
                runCatching {
                    val settings = SettingsPreferencesRepository(applicationContext).settingsFlow.first()
                    appendDebug("refresh settings loaded source=${settings.liveWallpaperSource} style=${settings.widgetDefaultStyle}")
                    wallpaperStyle = settings.widgetDefaultStyle
                    val fontScale = when {
                        settings.wallpaperFontScale <= 18f -> 0.8f + ((settings.wallpaperFontScale - 14f) / 4f) * 0.2f
                        else -> 1.0f + ((settings.wallpaperFontScale - 18f) / 4f) * 0.8f
                    }.coerceIn(0.8f, 1.8f)
                    titlePaint.textSize = 64f * fontScale
                    metaPaint.textSize = 34f * fontScale
                    bodyPaint.textSize = 42f * fontScale
                    when (settings.liveWallpaperSource) {
                        "focus" -> {
                            wallpaperSource = "focus"
                            val content = CurrentSegmentWidgetContentBuilder(applicationContext).build()
                            wallpaperLabel = "当前时段聚焦"
                            wallpaperTitle = styleTitle("当前时段", content.title, settings.widgetDefaultStyle)
                            wallpaperSummary = content.summary
                            wallpaperItems = listOf(content.task1, content.task2, content.task3).map {
                                WallpaperTaskItem.fromCurrentSegment(it)
                            }
                        }
                        "weekly" -> {
                            wallpaperSource = "weekly"
                            val db = DatabaseProvider.get(applicationContext)
                            val dailyPlanRepository = RoomDailyPlanRepository(
                                dailyPlanDao = db.dailyPlanDao(),
                                weeklyPlanDao = db.weeklyPlanDao(),
                                executionTaskDao = db.executionTaskDao(),
                            )
                            dailyPlanRepository.seedTodayIfNeeded()
                            val todayPlan = dailyPlanRepository.observeTodayPlan(LocalDate.now().toString()).first()
                            val summary = todayPlan?.summary ?: "本周主线正在准备中"
                            wallpaperLabel = "本周摘要"
                            wallpaperTitle = styleTitle("本周摘要", summary, settings.widgetDefaultStyle)
                            wallpaperSummary = "今日计划承接本周主线"
                            wallpaperItems = buildWeeklyItems(summary).map { WallpaperTaskItem(it) }
                        }
                        "habit" -> {
                            wallpaperSource = "habit"
                            val db = DatabaseProvider.get(applicationContext)
                            val habitRepository = RoomHabitRepository(
                                habitTemplateDao = db.habitTemplateDao(),
                                habitRecordDao = db.habitRecordDao(),
                            )
                            val today = LocalDate.now().toString()
                            habitRepository.seedDefaultsIfNeeded()
                            habitRepository.ensureTodayRecords(today)
                            val habitSummary = habitRepository.observeTodayHabitSummary(today).first()
                            wallpaperLabel = "固定打卡"
                            wallpaperTitle = styleTitle("打卡进度", habitSummary.summaryText, settings.widgetDefaultStyle)
                            wallpaperSummary = "固定打卡不打断主线，但必须保持连续"
                            wallpaperItems = buildHabitItems(habitSummary).map { WallpaperTaskItem(it) }
                        }
                        else -> {
                            wallpaperSource = "today"
                            val content = MorningWidgetContentBuilder(applicationContext).build()
                            wallpaperLabel = "晨间视图"
                            wallpaperTitle = styleTitle("晨间视图", content.title, settings.widgetDefaultStyle)
                            wallpaperSummary = content.summary
                            wallpaperItems = listOf(content.top1, content.top2, content.top3).map {
                                WallpaperTaskItem.fromMorning(it)
                            }
                        }
                    }
                }.onFailure {
                    appendDebug("refresh failed: ${it.javaClass.simpleName}: ${it.message}")
                }
                launch(Dispatchers.Main) {
                    appendDebug("refresh drawFrame dispatch")
                    runCatching { drawFrame(surfaceHolder) }
                        .onFailure { appendDebug("drawFrame dispatch failed: ${it.javaClass.simpleName}: ${it.message}") }
                }
            }
        }

        private fun drawFrame(holder: SurfaceHolder) {
            appendDebug("drawFrame start")
            val canvas = runCatching { holder.lockCanvas() }
                .onFailure { appendDebug("lockCanvas failed: ${it.javaClass.simpleName}: ${it.message}") }
                .getOrNull() ?: return
            try {
                val bounds = Rect(0, 0, canvas.width, canvas.height)
                backgroundPaint.shader = LinearGradient(
                    0f,
                    0f,
                    bounds.width().toFloat(),
                    bounds.height().toFloat(),
                    wallpaperGradientColors(wallpaperStyle),
                    null,
                    Shader.TileMode.CLAMP,
                )
                canvas.drawRect(bounds, backgroundPaint)
                drawContent(canvas)
                appendDebug("drawFrame success width=${canvas.width} height=${canvas.height} source=$wallpaperSource")
            } catch (t: Throwable) {
                appendDebug("drawFrame failed: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                runCatching { holder.unlockCanvasAndPost(canvas) }
                    .onFailure { appendDebug("unlockCanvasAndPost failed: ${it.javaClass.simpleName}: ${it.message}") }
            }
        }

        private fun drawContent(canvas: Canvas) {
            when (wallpaperSource) {
                "focus" -> drawFocusLayout(canvas)
                "weekly" -> drawWeeklyLayout(canvas)
                "habit" -> drawHabitLayout(canvas)
                else -> drawTodayLayout(canvas)
            }
        }

        private fun drawTodayLayout(canvas: Canvas) {
            drawBaseCard(canvas)
            drawHeaderLabel(canvas, wallpaperLabel)
            var y = 380f
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperTitle,
                paint = titlePaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = titlePaint.textSize * 1.18f,
                maxLines = 3,
            ) + 20f
            y = drawMetaLine(canvas, y)
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperSummary,
                paint = metaPaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = metaPaint.textSize * 1.35f,
                maxLines = 3,
            ) + 36f
            wallpaperItems
                .filter { it.text.isNotBlank() }
                .take(3)
                .forEach { item ->
                    y = drawItemBlock(canvas, item, 104f, y, canvas.width - 208f) + 24f
                }
        }

        private fun drawFocusLayout(canvas: Canvas) {
            drawBaseCard(canvas)
            drawHeaderLabel(canvas, wallpaperLabel)
            var y = 360f
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperTitle,
                paint = titlePaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = titlePaint.textSize * 1.22f,
                maxLines = 2,
            ) + 18f
            y = drawMetaLine(canvas, y)
            val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(90, 45, 108, 255)
            }
            val summaryCardHeight = bodyPaint.textSize * 3.6f
            canvas.drawRoundRect(104f, y, canvas.width - 104f, y + summaryCardHeight, 30f, 30f, focusPaint)
            drawWrappedText(
                canvas = canvas,
                text = wallpaperSummary,
                paint = bodyPaint,
                x = 132f,
                startY = y + bodyPaint.textSize * 1.45f,
                maxWidth = canvas.width - 264f,
                lineHeight = bodyPaint.textSize * 1.3f,
                maxLines = 2,
            )
            var itemY = y + summaryCardHeight + 72f
            wallpaperItems
            .filter { it.text.isNotBlank() }
            .take(2)
            .forEach { item ->
                itemY = drawItemBlock(canvas, item, 104f, itemY, canvas.width - 208f) + 24f
            }
        }

        private fun drawWeeklyLayout(canvas: Canvas) {
            drawBaseCard(canvas)
            drawHeaderLabel(canvas, wallpaperLabel)
            var y = 360f
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperTitle,
                paint = titlePaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = titlePaint.textSize * 1.18f,
                maxLines = 3,
            ) + 16f
            y = drawMetaLine(canvas, y)
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperSummary,
                paint = metaPaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = metaPaint.textSize * 1.3f,
                maxLines = 2,
            ) + 30f

            val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(70, 255, 255, 255)
            }
            wallpaperItems
            .filter { it.text.isNotBlank() }
            .take(3)
            .forEachIndexed { index, item ->
                val top = y + index * 170f
                canvas.drawRoundRect(104f, top, canvas.width - 104f, top + 140f, 28f, 28f, lanePaint)
                drawStyledWrappedText(
                    canvas = canvas,
                    item = item,
                    basePaint = bodyPaint,
                    x = 132f,
                    startY = top + 62f,
                    maxWidth = canvas.width - 264f,
                    lineHeight = 50f,
                    maxLines = 2,
                )
            }
        }

        private fun drawHabitLayout(canvas: Canvas) {
            drawBaseCard(canvas)
            drawHeaderLabel(canvas, wallpaperLabel)
            var y = 360f
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperTitle,
                paint = titlePaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = titlePaint.textSize * 1.2f,
                maxLines = 2,
            ) + 16f
            y = drawMetaLine(canvas, y)
            y = drawWrappedText(
                canvas = canvas,
                text = wallpaperSummary,
                paint = metaPaint,
                x = 104f,
                startY = y,
                maxWidth = canvas.width - 208f,
                lineHeight = metaPaint.textSize * 1.3f,
                maxLines = 2,
            ) + 24f

            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(75, 125, 226, 168)
            }
            wallpaperItems
            .filter { it.text.isNotBlank() }
            .take(3)
            .forEachIndexed { index, item ->
                val top = y + index * 160f
                canvas.drawRoundRect(104f, top, canvas.width - 104f, top + 128f, 26f, 26f, progressPaint)
                drawStyledWrappedText(
                    canvas = canvas,
                    item = item,
                    basePaint = bodyPaint,
                    x = 132f,
                    startY = top + 56f,
                    maxWidth = canvas.width - 264f,
                    lineHeight = 48f,
                    maxLines = 2,
                )
            }
        }

        private fun drawBaseCard(canvas: Canvas) {
            val width = canvas.width.toFloat()
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(122, 255, 255, 255)
            }
            canvas.drawRoundRect(72f, 280f, width - 72f, canvas.height - 180f, 42f, 42f, cardPaint)
        }

        private fun drawHeaderLabel(canvas: Canvas, label: String) {
            drawWrappedText(
                canvas = canvas,
                text = label,
                paint = metaPaint,
                x = 96f,
                startY = 220f,
                maxWidth = canvas.width - 192f,
                lineHeight = metaPaint.textSize * 1.2f,
                maxLines = 1,
            )
        }

        private fun drawMetaLine(canvas: Canvas, startY: Float): Float {
            return drawWrappedText(
                canvas = canvas,
                text = "当前时间：${LocalTime.now().withSecond(0).withNano(0)}",
                paint = metaPaint,
                x = 104f,
                startY = startY,
                maxWidth = canvas.width - 208f,
                lineHeight = metaPaint.textSize * 1.2f,
                maxLines = 1,
            ) + (metaPaint.textSize * 0.45f)
        }

        private fun styleTitle(prefix: String, title: String, widgetStyle: String): String = when (widgetStyle) {
            "compact" -> "$prefix · 卡片"
            "focus" -> "$prefix · 专注"
            else -> title
        }

        private fun wallpaperGradientColors(widgetStyle: String): IntArray = when (widgetStyle) {
            "compact" -> intArrayOf(
                Color.parseColor("#E8EEF9"),
                Color.parseColor("#C9D8F0"),
                Color.parseColor("#F5EFE6"),
            )
            "focus" -> intArrayOf(
                Color.parseColor("#DDEBFF"),
                Color.parseColor("#B8D4FF"),
                Color.parseColor("#DCEFE3"),
            )
            else -> intArrayOf(
                Color.parseColor("#D9E6FF"),
                Color.parseColor("#B9D0FF"),
                Color.parseColor("#EFE7DC"),
            )
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

        private fun drawItemBlock(
            canvas: Canvas,
            item: WallpaperTaskItem,
            x: Float,
            startY: Float,
            maxWidth: Float,
        ): Float {
            val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (item.completed) Color.parseColor("#8EA4C8") else Color.parseColor("#2D6CFF")
            }
            canvas.drawCircle(x + 10f, startY - 18f, 8f, markerPaint)
            return drawStyledWrappedText(
                canvas = canvas,
                item = item,
                basePaint = bodyPaint,
                x = x + 34f,
                startY = startY,
                maxWidth = maxWidth - 34f,
                lineHeight = bodyPaint.textSize * 1.28f,
                maxLines = 3,
            )
        }

        private fun drawStyledWrappedText(
            canvas: Canvas,
            item: WallpaperTaskItem,
            basePaint: Paint,
            x: Float,
            startY: Float,
            maxWidth: Float,
            lineHeight: Float,
            maxLines: Int,
        ): Float {
            val paint = Paint(basePaint).apply {
                color = if (item.completed) Color.parseColor("#8FA1B8") else basePaint.color
                flags = if (item.completed) {
                    Paint.ANTI_ALIAS_FLAG or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    Paint.ANTI_ALIAS_FLAG
                }
            }
            val displayText = item.displayText
            return drawWrappedText(
                canvas = canvas,
                text = displayText,
                paint = paint,
                x = x,
                startY = startY,
                maxWidth = maxWidth,
                lineHeight = lineHeight,
                maxLines = maxLines,
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
    }
}
