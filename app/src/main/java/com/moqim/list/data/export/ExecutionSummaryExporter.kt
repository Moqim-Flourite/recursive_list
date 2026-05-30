package com.moqim.list.data.export

import android.content.Context
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomHabitRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class ExecutionSummaryExporter(
    private val appContext: Context,
) {
    suspend fun exportForDate(date: String): String {
        val db = DatabaseProvider.get(appContext)
        val dailyPlan = db.dailyPlanDao().observeByDate(date).first()
        val tasks = dailyPlan?.let { db.executionTaskDao().getByDailyPlanId(it.id) }.orEmpty()
        val habitRepository = RoomHabitRepository(
            habitTemplateDao = db.habitTemplateDao(),
            habitRecordDao = db.habitRecordDao(),
        )
        val habitSummary = habitRepository.observeTodayHabitSummary(date).first()

        val totalTaskCount = tasks.size
        val doneTaskCount = tasks.count { it.status == "DONE" }
        val todoTaskCount = tasks.count { it.status != "DONE" }
        val temporaryTaskCount = tasks.count { it.sourceType in setOf("INBOX", "QUICK_WINS", "DEEP_WORK") }
        val topFocusDoneCount = tasks.count { it.isTopFocus && it.status == "DONE" }
        val unscheduledTaskCount = tasks.count { it.timeSegment.isNullOrBlank() }
        val totalEstimatedMinutes = tasks.sumOf { it.estimatedMinutes ?: 0 }
        val doneEstimatedMinutes = tasks.filter { it.status == "DONE" }.sumOf { it.estimatedMinutes ?: 0 }
        val completionRate = if (totalTaskCount == 0) 0.0 else doneTaskCount.toDouble() / totalTaskCount.toDouble()
        val rolloverCandidates = tasks
            .filter { it.status != "DONE" && it.sourceType != "INBOX" && it.sourceType != "QUICK_WINS" && it.sourceType != "DEEP_WORK" }
            .map { it.title }
            .take(5)

        val temporaryInterruptionLevel = when {
            temporaryTaskCount >= 4 -> "HIGH"
            temporaryTaskCount >= 2 -> "MEDIUM"
            temporaryTaskCount >= 1 -> "LOW"
            else -> "NONE"
        }
        val manualAdjustmentHints = buildList {
            if (unscheduledTaskCount > 0) add("存在 $unscheduledTaskCount 个未分配时段任务")
            if (temporaryTaskCount >= 2) add("临时事务偏多，说明当天计划被插单打断")
            if (topFocusDoneCount == 0 && tasks.any { it.isTopFocus }) add("Top Focus 未完成，次日应谨慎继续追加主线")
            if (completionRate < 0.4 && totalTaskCount >= 4) add("完成率偏低，建议次日降低任务量")
        }

        val segmentStatsArray = JSONArray().apply {
            TimeSegment.entries.forEach { segment ->
                val segmentTasks = tasks.filter { it.timeSegment == segment.name }
                put(
                    JSONObject().apply {
                        put("segment", segment.name)
                        put("label", segment.label)
                        put("taskCount", segmentTasks.size)
                        put("doneCount", segmentTasks.count { it.status == "DONE" })
                        put("estimatedMinutes", segmentTasks.sumOf { it.estimatedMinutes ?: 0 })
                    }
                )
            }
        }

        val taskArray = JSONArray().apply {
            tasks.forEach { task ->
                put(
                    JSONObject().apply {
                        put("title", task.title)
                        put("status", task.status)
                        put("timeSegment", task.timeSegment)
                        put("estimatedMinutes", task.estimatedMinutes)
                        put("isTopFocus", task.isTopFocus)
                        put("sourceType", task.sourceType)
                        put("note", task.note)
                    }
                )
            }
        }

        val habitItemsArray = JSONArray().apply {
            habitSummary.items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("title", item.title)
                        put("status", item.status)
                        put("completedCount", item.completedCount)
                        put("dailyTargetCount", item.dailyTargetCount)
                        put("preferredTimeSegment", item.preferredTimeSegment)
                        put("estimatedMinutes", item.estimatedMinutes)
                    }
                )
            }
        }

        val packageInfo = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        }.getOrNull()
        val appVersion = packageInfo?.versionName ?: "unknown"

        return JSONObject().apply {
            put("schemaVersion", 1)
            put("generatedAt", System.currentTimeMillis())
            put("sourceApp", appContext.packageName)
            put("appVersion", appVersion)
            put("date", date)
            put(
                "dailyPlan",
                JSONObject().apply {
                    put("summary", dailyPlan?.summary ?: "")
                    put("energyLevel", dailyPlan?.energyLevel ?: "")
                    put("review", dailyPlan?.review ?: "")
                    put("weeklyPlanId", dailyPlan?.weeklyPlanId)
                }
            )
            put(
                "taskStats",
                JSONObject().apply {
                    put("total", totalTaskCount)
                    put("done", doneTaskCount)
                    put("todo", todoTaskCount)
                    put("temporary", temporaryTaskCount)
                    put("topFocusDone", topFocusDoneCount)
                    put("unscheduled", unscheduledTaskCount)
                    put("completionRate", completionRate)
                    put("totalEstimatedMinutes", totalEstimatedMinutes)
                    put("doneEstimatedMinutes", doneEstimatedMinutes)
                }
            )
            put("tasks", taskArray)
            put("segmentStats", segmentStatsArray)
            put(
                "habits",
                JSONObject().apply {
                    put("summaryText", habitSummary.summaryText)
                    put("done", habitSummary.completedCount)
                    put("total", habitSummary.totalCount)
                    put("items", habitItemsArray)
                }
            )
            put(
                "insight",
                JSONObject().apply {
                    put("rolloverCandidates", JSONArray(rolloverCandidates))
                    put("temporaryInterruptionLevel", temporaryInterruptionLevel)
                    put("manualAdjustmentHints", JSONArray(manualAdjustmentHints))
                }
            )
        }.toString(2)
    }
}