package com.moqim.list.data.importplan

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 将用户从 AI 复制的自然语言计划文本，解析成标准 plan_json。
 */
object PlanTextParser {

    private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    private val SEGMENT_VALUES = setOf(
        "MORNING_START", "MORNING", "NOON", "AFTERNOON", "EVENING"
    )

    fun parseToPlanJson(text: String): String? {
        val sections = splitSections(text)
        if (sections.isEmpty()) return null

        val monthlyPlan = parseMonthlyPlan(sections["月计划"])
        val weeklyPlan = parseWeeklyPlan(sections["本周计划"])
        val tasks = parseTasks(sections["今日任务"])

        if (monthlyPlan == null && weeklyPlan == null && tasks.isEmpty()) return null

        val root = JSONObject().apply {
            put("source", "text_import")
            put("mode", "merge")
            monthlyPlan?.let { put("monthlyPlan", it) }
            weeklyPlan?.let { put("weeklyPlan", it) }
            put("dailyPlan", JSONObject().apply {
                put("date", LocalDate.now().toString())
                put("summaryMode", "append")
            })
            put("tasks", JSONArray().apply { tasks.forEach { put(it) } })
            put("weekPoolTasks", JSONArray())
        }
        return root.toString()
    }

    fun parseToPreview(text: String): String? {
        val json = parseToPlanJson(text) ?: return null
        val root = JSONObject(json)

        return buildString {
            appendLine("=== 导入预览 ===")
            root.optJSONObject("monthlyPlan")?.let { mp ->
                appendLine("📋 月计划：${mp.optString("title")}")
                appendLine("   时间：${mp.optString("startDate")} ~ ${mp.optString("endDate")}")
            }
            root.optJSONObject("weeklyPlan")?.let { wp ->
                appendLine("📅 周计划：${wp.optString("title")}")
                appendLine("   时间：${wp.optString("weekStartDate")} ~ ${wp.optString("weekEndDate")}")
            }
            val tasks = root.optJSONArray("tasks")
            if (tasks != null && tasks.length() > 0) {
                appendLine("✅ 今日任务（${tasks.length()} 条）：")
                for (i in 0 until tasks.length()) {
                    val task = tasks.getJSONObject(i)
                    val seg = task.optString("timeSegment", "")
                    val segLabel = if (seg.isNotBlank()) "[$seg] " else ""
                    val mins = task.optInt("estimatedMinutes", 0).let { if (it > 0) "（${it}分钟）" else "" }
                    appendLine("   ${i + 1}. $segLabel${task.optString("title")}$mins")
                }
            }
        }
    }

    private fun splitSections(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = text.lines()
        var currentKey: String? = null
        val currentContent = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            val sectionKey = when {
                trimmed.matches(Regex("""^#{1,3}\s*月计划.*""")) -> "月计划"
                trimmed.matches(Regex("""^#{1,3}\s*本周计划.*""")) -> "本周计划"
                trimmed.matches(Regex("""^#{1,3}\s*今日任务.*""")) -> "今日任务"
                trimmed.matches(Regex("""^#{1,3}\s*周计划.*""")) -> "本周计划"
                trimmed.matches(Regex("""^#{1,3}\s*日计划.*""")) -> "今日任务"
                else -> null
            }

            if (sectionKey != null) {
                currentKey?.let { result[it] = currentContent.toString().trim() }
                currentKey = sectionKey
                currentContent.clear()
            } else if (currentKey != null) {
                currentContent.appendLine(line)
            }
        }
        currentKey?.let { result[it] = currentContent.toString().trim() }
        return result
    }

    private fun stripBullet(line: String): String? {
        val trimmed = line.trim()
        val match = Regex("""^[-*·•]\s+""").find(trimmed) ?: return null
        return trimmed.substring(match.range.last + 1)
    }

    private fun extractFieldValue(line: String, fieldPrefix: String): String? {
        val content = stripBullet(line) ?: return null
        if (!content.startsWith(fieldPrefix)) return null
        return content.removePrefix(fieldPrefix).trim()
    }

    private fun parseMonthlyPlan(text: String?): JSONObject? {
        text ?: return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var title = ""
        var startDate = ""
        var endDate = ""
        var theme = ""
        var goal = ""

        for (line in lines) {
            when {
                extractFieldValue(line, "标题：") != null || extractFieldValue(line, "标题:") != null -> {
                    title = extractFieldValue(line, "标题：") ?: extractFieldValue(line, "标题:") ?: ""
                }
                extractFieldValue(line, "时间：") != null || extractFieldValue(line, "时间:") != null -> {
                    val value = extractFieldValue(line, "时间：") ?: extractFieldValue(line, "时间:") ?: ""
                    val dates = DATE_PATTERN.findAll(value).toList()
                    if (dates.size >= 2) {
                        startDate = dates[0].value
                        endDate = dates[1].value
                    }
                }
                extractFieldValue(line, "主题：") != null || extractFieldValue(line, "主题:") != null -> {
                    theme = extractFieldValue(line, "主题：") ?: extractFieldValue(line, "主题:") ?: ""
                }
                extractFieldValue(line, "目标：") != null || extractFieldValue(line, "目标:") != null -> {
                    goal = extractFieldValue(line, "目标：") ?: extractFieldValue(line, "目标:") ?: ""
                }
            }
        }

        if (title.isBlank() && startDate.isBlank()) return null

        val today = LocalDate.now()
        return JSONObject().apply {
            put("title", title.ifBlank { "${today.monthValue}月主线" })
            put("startDate", startDate.ifBlank { today.withDayOfMonth(1).toString() })
            put("endDate", endDate.ifBlank { today.withDayOfMonth(today.lengthOfMonth()).toString() })
            put("theme", theme)
            put("goal", goal)
        }
    }

    private fun parseWeeklyPlan(text: String?): JSONObject? {
        text ?: return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        var title = ""
        var weekStartDate = ""
        var weekEndDate = ""
        var goal = ""
        var capacity: Int? = null

        for (line in lines) {
            when {
                extractFieldValue(line, "标题：") != null || extractFieldValue(line, "标题:") != null -> {
                    title = extractFieldValue(line, "标题：") ?: extractFieldValue(line, "标题:") ?: ""
                }
                extractFieldValue(line, "时间：") != null || extractFieldValue(line, "时间:") != null -> {
                    val value = extractFieldValue(line, "时间：") ?: extractFieldValue(line, "时间:") ?: ""
                    val dates = DATE_PATTERN.findAll(value).toList()
                    if (dates.size >= 2) {
                        weekStartDate = dates[0].value
                        weekEndDate = dates[1].value
                    }
                }
                extractFieldValue(line, "目标：") != null || extractFieldValue(line, "目标:") != null -> {
                    goal = extractFieldValue(line, "目标：") ?: extractFieldValue(line, "目标:") ?: ""
                }
                extractFieldValue(line, "容量：") != null || extractFieldValue(line, "容量:") != null -> {
                    val value = extractFieldValue(line, "容量：") ?: extractFieldValue(line, "容量:") ?: ""
                    capacity = value.filter { it.isDigit() }.toIntOrNull()
                }
            }
        }

        if (title.isBlank() && weekStartDate.isBlank()) return null

        val today = LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        val sunday = today.with(java.time.DayOfWeek.SUNDAY)

        return JSONObject().apply {
            put("title", title.ifBlank { "本周推进" })
            put("weekStartDate", weekStartDate.ifBlank { monday.toString() })
            put("weekEndDate", weekEndDate.ifBlank { sunday.toString() })
            put("goal", goal)
            capacity?.let { put("capacity", it) }
        }
    }

    private fun parseTasks(text: String?): List<JSONObject> {
        text ?: return emptyList()
        val result = mutableListOf<JSONObject>()

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val prefixMatch = Regex("""^\d+[.)、]\s*""").find(trimmed) ?: continue
            var rest = trimmed.substring(prefixMatch.range.last + 1)

            var segment: String? = null
            val segmentMatch = Regex("""^\[([A-Z_]+)\]\s*""").find(rest)
            if (segmentMatch != null) {
                val seg = segmentMatch.groupValues[1]
                if (seg in SEGMENT_VALUES) {
                    segment = seg
                }
                rest = rest.substring(segmentMatch.range.last + 1)
            }

            val minutePattern = Regex("""\（(\d+)分钟\）""")
            val minuteMatch = minutePattern.find(rest)

            if (minuteMatch != null) {
                val title = rest.substring(0, minuteMatch.range.first).trim()
                val minutes = minuteMatch.groupValues[1].toIntOrNull()
                val note = rest.substring(minuteMatch.range.last + 1).trim()

                if (title.isNotBlank()) {
                    result.add(JSONObject().apply {
                        put("title", title)
                        if (note.isNotBlank()) put("note", note)
                        if (minutes != null) put("estimatedMinutes", minutes)
                        if (segment != null) put("timeSegment", segment)
                    })
                }
            } else {
                val title = rest.trim()
                if (title.isNotBlank()) {
                    result.add(JSONObject().apply {
                        put("title", title)
                        if (segment != null) put("timeSegment", segment)
                    })
                }
            }
        }
        return result
    }
}