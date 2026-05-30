package com.moqim.list.data.importplan

data class PlanImportPayload(
    val source: String = "manual",
    val mode: String = "merge",
    val monthlyPlan: ImportedMonthlyPlan? = null,
    val weeklyPlan: ImportedWeeklyPlan? = null,
    val dailyPlan: ImportedDailyPlan? = null,
    val tasks: List<ImportedTask> = emptyList(),
    val weekPoolTasks: List<ImportedTask> = emptyList(),
)

data class ImportedMonthlyPlan(
    val title: String,
    val theme: String = "",
    val goal: String = "",
    val startDate: String,
    val endDate: String,
)

data class ImportedWeeklyPlan(
    val title: String,
    val goal: String = "",
    val weekStartDate: String,
    val weekEndDate: String,
    val capacity: Int? = null,
    val review: String = "",
)

data class ImportedDailyPlan(
    val date: String,
    val summary: String = "",
    val summaryMode: String = "overwrite",
    val energyLevel: String = "MEDIUM",
    val review: String = "",
)

data class ImportedTask(
    val title: String,
    val note: String = "",
    val estimatedMinutes: Int? = null,
    val timeSegment: String? = null,
    val isTopFocus: Boolean = false,
)