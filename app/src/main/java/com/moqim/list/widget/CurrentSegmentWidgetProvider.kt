package com.moqim.list.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.moqim.list.MainActivity
import com.moqim.list.R
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomExecutionTaskRepository
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.worker.WidgetRefreshScheduler
import kotlinx.coroutines.runBlocking

class CurrentSegmentWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> refreshAll(context)
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
                val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (taskId > 0L) {
                    if (consumeToggleConfirmation(context, taskId)) {
                        runBlocking {
                            val db = DatabaseProvider.get(context)
                            val repository = RoomExecutionTaskRepository(
                                dailyPlanDao = db.dailyPlanDao(),
                                executionTaskDao = db.executionTaskDao(),
                            )
                            repository.toggleTaskStatus(taskId)
                        }
                        clearPendingConfirmation(context)
                        MorningWidgetProvider.refreshAll(context)
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                            WidgetSurfaceConfigStore.isAggressiveRefreshPolicy(context, appWidgetId)
                        ) {
                            refresh(context, appWidgetId)
                        } else {
                            CurrentSegmentWidgetProvider.refreshAll(context)
                        }
                        WallpaperRefreshNotifier.notifyRefresh(context)
                        WidgetRefreshScheduler.enqueueAggressiveRefreshIfNeeded(context, "current_toggle_confirm")
                    } else {
                        markPendingConfirmation(context, taskId)
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                            WidgetSurfaceConfigStore.isAggressiveRefreshPolicy(context, appWidgetId)
                        ) {
                            refresh(context, appWidgetId)
                        } else {
                            CurrentSegmentWidgetProvider.refreshAll(context)
                        }
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            WidgetSurfaceConfigStore.deleteWidgetInstanceConfig(context, appWidgetId)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            WidgetSurfaceConfigStore.ensureWidgetInstanceConfig(context, appWidgetId, "WIDGET_CURRENT_SEGMENT")
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
        }
    }

    companion object {
        private const val ACTION_TOGGLE_TASK = "com.moqim.list.widget.CURRENT_TOGGLE_TASK"
        private const val EXTRA_TASK_ID = "extra_task_id"
        private const val PREFS_NAME = "widget_task_confirmation"
        private const val KEY_PENDING_TASK_ID = "current_pending_task_id"
        private const val KEY_PENDING_AT = "current_pending_at"
        private const val CONFIRM_WINDOW_MS = 1200L

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, CurrentSegmentWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(componentName)
            ids.forEach { id ->
                WidgetSurfaceConfigStore.ensureWidgetInstanceConfig(context, id, "WIDGET_CURRENT_SEGMENT")
                manager.updateAppWidget(id, buildRemoteViews(context, id))
            }
        }

        fun refresh(context: Context, appWidgetId: Int) {
            WidgetSurfaceConfigStore.ensureWidgetInstanceConfig(context, appWidgetId, "WIDGET_CURRENT_SEGMENT")
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetId))
        }

        private fun taskTogglePendingIntent(context: Context, requestCode: Int, taskId: Long?): PendingIntent {
            if (taskId == null) {
                val intent = Intent(context, MainActivity::class.java)
                return PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val intent = Intent(context, CurrentSegmentWidgetProvider::class.java).apply {
                action = ACTION_TOGGLE_TASK
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, requestCode / 10)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun RemoteViews.bindTaskItem(
            context: Context,
            viewId: Int,
            item: CurrentSegmentWidgetTaskItem,
        ) {
            val pendingTaskId = getPendingTaskId(context)
            val isPendingConfirm = item.taskId != null && item.taskId == pendingTaskId
            val displayText = when {
                isPendingConfirm -> item.text.replaceFirst(Regex("""^(\d+\.)\s*"""), "$1 再点确认 ")
                item.completed && item.taskId != null -> item.text.replaceFirst(Regex("""^(\d+\.)\s*"""), "$1 ✓ ")
                else -> item.text
            }
            setTextViewText(viewId, displayText)
            setInt(
                viewId,
                "setPaintFlags",
                if (item.completed && !isPendingConfirm) {
                    android.graphics.Paint.STRIKE_THRU_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG
                } else {
                    android.graphics.Paint.ANTI_ALIAS_FLAG
                }
            )
            setTextColor(
                viewId,
                when {
                    isPendingConfirm -> 0xFFFFC857.toInt()
                    item.completed -> 0x99FFFFFF.toInt()
                    else -> 0xFFFFFFFF.toInt()
                },
            )
        }

        private fun confirmationPrefs(context: Context) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        private fun getPendingTaskId(context: Context): Long? {
            val prefs = confirmationPrefs(context)
            val taskId = prefs.getLong(KEY_PENDING_TASK_ID, -1L)
            val pendingAt = prefs.getLong(KEY_PENDING_AT, 0L)
            if (taskId <= 0L || System.currentTimeMillis() - pendingAt > CONFIRM_WINDOW_MS) {
                clearPendingConfirmation(context)
                return null
            }
            return taskId
        }

        private fun markPendingConfirmation(context: Context, taskId: Long) {
            confirmationPrefs(context).edit()
                .putLong(KEY_PENDING_TASK_ID, taskId)
                .putLong(KEY_PENDING_AT, System.currentTimeMillis())
                .apply()
        }

        private fun clearPendingConfirmation(context: Context) {
            confirmationPrefs(context).edit()
                .remove(KEY_PENDING_TASK_ID)
                .remove(KEY_PENDING_AT)
                .apply()
        }

        private fun consumeToggleConfirmation(context: Context, taskId: Long): Boolean {
            val pendingTaskId = getPendingTaskId(context)
            return pendingTaskId != null && pendingTaskId == taskId
        }
        private fun buildRemoteViews(context: Context, appWidgetId: Int): RemoteViews {
            val surfaceConfig = WidgetSurfaceConfigStore.loadSurfaceConfig(context, appWidgetId)
            val renderConfig = WidgetRenderStyle.fromSurfaceConfig(surfaceConfig)
            val showCompleted = renderConfig.showCompleted
            val maxItems = renderConfig.maxItems
            val showProgress = renderConfig.showProgress
            val textScale = renderConfig.textScale
            val theme = renderConfig.theme
            val opacity = renderConfig.opacity

            val content = runBlocking {
                CurrentSegmentWidgetContentBuilder(context).build()
            }
            val visibleItems = if (showCompleted) {
                content.items
            } else {
                content.items.filterNot { it.completed && it.taskId != null }
            }
            val limitedItems = visibleItems.take(maxItems)
            val displayItems = if (limitedItems.isNotEmpty()) {
                listOf(
                    limitedItems.getOrElse(0) { CurrentSegmentWidgetTaskItem("") },
                    limitedItems.getOrElse(1) { CurrentSegmentWidgetTaskItem("") },
                    limitedItems.getOrElse(2) { CurrentSegmentWidgetTaskItem("") },
                )
            } else {
                listOf(
                    CurrentSegmentWidgetTaskItem("当前时段暂无任务，可机动安排轻量事务或提前准备下个时段。"),
                    CurrentSegmentWidgetTaskItem(""),
                    CurrentSegmentWidgetTaskItem(""),
                )
            }

            val palette = WidgetRenderStyle.currentSegmentPalette(theme, opacity)
            val progressText = if (content.totalCount > 0) {
                "已完成 ${content.completedCount}/${content.totalCount}"
            } else {
                "当前时段暂无任务"
            }
            val progressPercent = if (content.totalCount > 0) {
                (content.completedCount * 100 / content.totalCount).coerceIn(0, 100)
            } else {
                0
            }
            val summaryText = when {
                !showProgress -> "当前时段已就绪 · 直接开始执行"
                surfaceConfig?.refreshPolicy == "BATTERY_SAVER" -> "$progressText · 省电模式"
                else -> "${content.summary} · $progressText"
            }
            val gravity = when (surfaceConfig?.anchorPosition) {
                "CENTER" -> android.view.Gravity.CENTER_VERTICAL
                "BOTTOM" -> android.view.Gravity.BOTTOM
                else -> android.view.Gravity.TOP
            }

            Log.d(
                "CurrentWidget",
                "buildRemoteViews appWidgetId=$appWidgetId theme=$theme maxItems=$maxItems showCompleted=$showCompleted showProgress=$showProgress opacity=$opacity textScale=$textScale anchorPosition=${surfaceConfig?.anchorPosition} refreshPolicy=${surfaceConfig?.refreshPolicy} progress=${content.completedCount}/${content.totalCount}",
            )

            return RemoteViews(context.packageName, R.layout.widget_current_segment).apply {
                setInt(R.id.currentSegmentRoot, "setBackgroundColor", palette.backgroundColor)
                setInt(R.id.currentSegmentContentContainer, "setGravity", gravity)
                setInt(R.id.currentSegmentTitle, "setTextColor", palette.titleColor)
                setInt(R.id.currentSegmentSummary, "setTextColor", palette.summaryColor)
                setInt(R.id.currentSegmentTask1, "setTextColor", palette.contentColor)
                setInt(R.id.currentSegmentTask2, "setTextColor", palette.contentColor)
                setInt(R.id.currentSegmentTask3, "setTextColor", palette.contentColor)

                setFloat(R.id.currentSegmentTitle, "setTextSize", 18f * textScale)
                setFloat(R.id.currentSegmentSummary, "setTextSize", 14f * textScale)
                setFloat(R.id.currentSegmentTask1, "setTextSize", 14f * textScale)
                setFloat(R.id.currentSegmentTask2, "setTextSize", 14f * textScale)
                setFloat(R.id.currentSegmentTask3, "setTextSize", 14f * textScale)
                setViewVisibility(R.id.currentSegmentProgress, if (showProgress) android.view.View.VISIBLE else android.view.View.GONE)
                setProgressBar(R.id.currentSegmentProgress, 100, progressPercent, false)

                setTextViewText(R.id.currentSegmentTitle, content.title)
                setTextViewText(R.id.currentSegmentSummary, summaryText)
                bindTaskItem(context, R.id.currentSegmentTask1, displayItems[0])
                bindTaskItem(context, R.id.currentSegmentTask2, displayItems[1])
                bindTaskItem(context, R.id.currentSegmentTask3, displayItems[2])

                val isEmptyState = limitedItems.isEmpty()
                setViewVisibility(R.id.currentSegmentTask1, android.view.View.VISIBLE)
                setViewVisibility(
                    R.id.currentSegmentTask2,
                    if (!isEmptyState && maxItems >= 2 && displayItems[1].text.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE,
                )
                setViewVisibility(
                    R.id.currentSegmentTask3,
                    if (!isEmptyState && maxItems >= 3 && displayItems[2].text.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE,
                )

                val intent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    1,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.currentSegmentTitle, pendingIntent)
                setOnClickPendingIntent(R.id.currentSegmentSummary, pendingIntent)
                setOnClickPendingIntent(
                    R.id.currentSegmentTask1,
                    if (displayItems[0].taskId != null) taskTogglePendingIntent(context, 201 + appWidgetId * 10, displayItems[0].taskId) else pendingIntent,
                )
                setOnClickPendingIntent(
                    R.id.currentSegmentTask2,
                    if (displayItems[1].taskId != null) taskTogglePendingIntent(context, 202 + appWidgetId * 10, displayItems[1].taskId) else pendingIntent,
                )
                setOnClickPendingIntent(
                    R.id.currentSegmentTask3,
                    if (displayItems[2].taskId != null) taskTogglePendingIntent(context, 203 + appWidgetId * 10, displayItems[2].taskId) else pendingIntent,
                )
            }
        }

        private fun widgetPalette(theme: String, opacity: Float): WidgetPalette {
            val alpha = (opacity * 255).toInt().coerceIn(0, 255)
            return when (theme) {
                "MINIMAL" -> WidgetPalette(
                    backgroundColor = android.graphics.Color.argb(alpha, 18, 18, 18),
                    titleColor = android.graphics.Color.WHITE,
                    summaryColor = android.graphics.Color.parseColor("#D0D0D0"),
                    contentColor = android.graphics.Color.WHITE,
                )
                "MICROSOFT" -> WidgetPalette(
                    backgroundColor = android.graphics.Color.argb(alpha, 232, 238, 249),
                    titleColor = android.graphics.Color.parseColor("#1B2A41"),
                    summaryColor = android.graphics.Color.parseColor("#355070"),
                    contentColor = android.graphics.Color.parseColor("#122033"),
                )
                "FOCUS" -> WidgetPalette(
                    backgroundColor = android.graphics.Color.argb(alpha, 23, 32, 43),
                    titleColor = android.graphics.Color.parseColor("#8FD3FF"),
                    summaryColor = android.graphics.Color.parseColor("#C9E7FF"),
                    contentColor = android.graphics.Color.WHITE,
                )
                "PIXEL" -> WidgetPalette(
                    backgroundColor = android.graphics.Color.argb(alpha, 45, 24, 84),
                    titleColor = android.graphics.Color.parseColor("#FFE082"),
                    summaryColor = android.graphics.Color.parseColor("#FFD6F5"),
                    contentColor = android.graphics.Color.WHITE,
                )
                else -> WidgetPalette(
                    backgroundColor = android.graphics.Color.argb(alpha, 21, 32, 51),
                    titleColor = android.graphics.Color.WHITE,
                    summaryColor = android.graphics.Color.parseColor("#D6E4FF"),
                    contentColor = android.graphics.Color.WHITE,
                )
            }
        }

        private data class WidgetPalette(
            val backgroundColor: Int,
            val titleColor: Int,
            val summaryColor: Int,
            val contentColor: Int,
        )
    }
}
