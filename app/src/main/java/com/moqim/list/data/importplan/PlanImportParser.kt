package com.moqim.list.data.importplan

import org.json.JSONArray
import org.json.JSONObject

object PlanImportParser {
    fun parse(json: String): PlanImportPayload {
        val root = JSONObject(json)
        return PlanImportPayload(
            source = root.optString("source", "manual"),
            mode = root.optString("mode", "merge"),
            monthlyPlan = root.optJSONObject("monthlyPlan")?.toMonthlyPlan(),
            weeklyPlan = root.optJSONObject("weeklyPlan")?.toWeeklyPlan(),
            dailyPlan = root.optJSONObject("dailyPlan")?.toDailyPlan(),
            tasks = root.optJSONArray("tasks").toTaskList(),
            weekPoolTasks = root.optJSONArray("weekPoolTasks").toTaskList(),
        )
    }

    private fun JSONObject.toMonthlyPlan(): ImportedMonthlyPlan {
        return ImportedMonthlyPlan(
            title = requireString("title"),
            theme = optString("theme", ""),
            goal = optString("goal", ""),
            startDate = requireString("startDate"),
            endDate = requireString("endDate"),
        )
    }

    private fun JSONObject.toWeeklyPlan(): ImportedWeeklyPlan {
        return ImportedWeeklyPlan(
            title = requireString("title"),
            goal = optString("goal", ""),
            weekStartDate = requireString("weekStartDate"),
            weekEndDate = requireString("weekEndDate"),
            capacity = if (has("capacity") && !isNull("capacity")) optInt("capacity") else null,
            review = optString("review", ""),
        )
    }

    private fun JSONObject.toDailyPlan(): ImportedDailyPlan {
        return ImportedDailyPlan(
            date = requireString("date"),
            summary = optString("summary", ""),
            summaryMode = optString("summaryMode", "overwrite"),
            energyLevel = optString("energyLevel", "MEDIUM"),
            review = optString("review", ""),
        )
    }

    private fun JSONArray?.toTaskList(): List<ImportedTask> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    ImportedTask(
                        title = item.requireString("title"),
                        note = item.optString("note", ""),
                        estimatedMinutes = if (item.has("estimatedMinutes") && !item.isNull("estimatedMinutes")) item.optInt("estimatedMinutes") else null,
                        timeSegment = item.optString("timeSegment").takeIf { it.isNotBlank() },
                        isTopFocus = item.optBoolean("isTopFocus", false),
                    )
                )
            }
        }
    }

    private fun JSONObject.requireString(key: String): String {
        val value = optString(key).trim()
        require(value.isNotBlank()) { "字段 $key 不能为空" }
        return value
    }
}