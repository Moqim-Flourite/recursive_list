package com.moqim.list.feature.planquery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moqim.list.data.local.entity.DailyPlanEntity
import com.moqim.list.data.local.entity.ExecutionTaskEntity
import com.moqim.list.data.local.entity.MonthlyPlanEntity
import com.moqim.list.data.local.entity.WeeklyPlanEntity
import com.moqim.list.data.local.provider.DatabaseProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class BackgroundQueryPlanTreeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        val monthlyPlanId = intent.getLongExtra(EXTRA_MONTHLY_PLAN_ID, -1L).takeIf { it > 0 }
        val weeklyPlanId = intent.getLongExtra(EXTRA_WEEKLY_PLAN_ID, -1L).takeIf { it > 0 }
        val date = intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DatabaseProvider.get(appContext)
                val allMonthlyPlans = db.monthlyPlanDao().getAll()
                val allWeeklyPlans = db.weeklyPlanDao().getAll()
                val allDailyPlans = db.dailyPlanDao().getAll()
                val allTasks = db.executionTaskDao().getAll()

                val filteredDailyPlans = when {
                    date != null -> allDailyPlans.filter { it.date == date }
                    weeklyPlanId != null -> allDailyPlans.filter { it.weeklyPlanId == weeklyPlanId }
                    else -> allDailyPlans
                }

                val inferredWeeklyIdsFromDate = filteredDailyPlans.mapNotNull { it.weeklyPlanId }.toSet()

                val filteredWeeklyPlans = when {
                    weeklyPlanId != null -> allWeeklyPlans.filter { it.id == weeklyPlanId }
                    monthlyPlanId != null -> allWeeklyPlans.filter { it.monthlyPlanId == monthlyPlanId }
                    date != null -> allWeeklyPlans.filter { it.id in inferredWeeklyIdsFromDate }
                    else -> allWeeklyPlans
                }

                val inferredMonthlyIds = filteredWeeklyPlans.map { it.monthlyPlanId }.toSet()

                val filteredMonthlyPlans = when {
                    monthlyPlanId != null -> allMonthlyPlans.filter { it.id == monthlyPlanId }
                    weeklyPlanId != null || date != null -> allMonthlyPlans.filter { it.id in inferredMonthlyIds }
                    else -> allMonthlyPlans
                }

                val relevantDailyIds = filteredDailyPlans.map { it.id }.toSet()
                val relevantWeeklyIds = filteredWeeklyPlans.map { it.id }.toSet()
                val relevantMonthlyIds = filteredMonthlyPlans.map { it.id }.toSet()

                val filteredTasks = allTasks.filter { task ->
                    when {
                        date != null -> task.dailyPlanId in relevantDailyIds || (task.dailyPlanId == null && task.weeklyPlanId in relevantWeeklyIds)
                        weeklyPlanId != null -> task.weeklyPlanId in relevantWeeklyIds
                        monthlyPlanId != null -> task.monthlyPlanId in relevantMonthlyIds
                        else -> true
                    }
                }

                val json = buildPlanTreeJson(
                    monthlyPlans = filteredMonthlyPlans,
                    weeklyPlans = filteredWeeklyPlans,
                    dailyPlans = filteredDailyPlans,
                    allTasks = filteredTasks,
                )

                val outputFile = writePlanTreeSnapshot(
                    context = appContext,
                    json = json,
                    date = date,
                    weeklyPlanId = weeklyPlanId,
                    monthlyPlanId = monthlyPlanId,
                )

                appContext.sendBroadcast(
                    Intent(ACTION_QUERY_PLAN_TREE_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, true)
                        putExtra(EXTRA_RESULT_JSON, json)
                        putExtra(EXTRA_RESULT_FILE_PATH, outputFile.absolutePath)
                    },
                )
            } catch (error: Throwable) {
                appContext.sendBroadcast(
                    Intent(ACTION_QUERY_PLAN_TREE_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, false)
                        putExtra(EXTRA_RESULT_ERROR, error.message ?: "未知错误")
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BACKGROUND_QUERY_PLAN_TREE = "com.moqim.list.action.BACKGROUND_QUERY_PLAN_TREE"
        const val ACTION_QUERY_PLAN_TREE_RESULT = "com.moqim.list.action.QUERY_PLAN_TREE_RESULT"

        const val EXTRA_MONTHLY_PLAN_ID = "monthlyPlanId"
        const val EXTRA_WEEKLY_PLAN_ID = "weeklyPlanId"
        const val EXTRA_DATE = "date"

        const val EXTRA_RESULT_OK = "result_ok"
        const val EXTRA_RESULT_JSON = "result_json"
        const val EXTRA_RESULT_FILE_PATH = "result_file_path"
        const val EXTRA_RESULT_ERROR = "result_error"
    }
}

private fun buildPlanTreeJson(
    monthlyPlans: List<MonthlyPlanEntity>,
    weeklyPlans: List<WeeklyPlanEntity>,
    dailyPlans: List<DailyPlanEntity>,
    allTasks: List<ExecutionTaskEntity>,
): String {
    return buildString {
        append("{")
        append("\"schemaVersion\":\"1.0\",")
        append("\"sourceApp\":\"moqim_list\",")
        append("\"monthlyPlans\":[")
        append(
            monthlyPlans.joinToString(",") { month ->
                buildMonthlyJson(month, weeklyPlans, dailyPlans, allTasks)
            },
        )
        append("]")
        append("}")
    }
}

private fun buildMonthlyJson(
    month: MonthlyPlanEntity,
    weeklyPlans: List<WeeklyPlanEntity>,
    dailyPlans: List<DailyPlanEntity>,
    allTasks: List<ExecutionTaskEntity>,
): String {
    val monthWeeks = weeklyPlans.filter { it.monthlyPlanId == month.id }
    val monthTasks = allTasks.filter { it.monthlyPlanId == month.id }
    val monthDoneCount = monthTasks.count { it.status == "DONE" }

    return buildString {
        append("{")
        append("\"id\":${month.id},")
        append("\"title\":${jsonString(month.title)},")
        append("\"theme\":${jsonString(month.theme)},")
        append("\"goal\":${jsonStringOrNull(month.goal)},")
        append("\"startDate\":${jsonString(month.startDate)},")
        append("\"endDate\":${jsonString(month.endDate)},")
        append("\"status\":${jsonString(month.status)},")
        append("\"taskCount\":${monthTasks.size},")
        append("\"doneTaskCount\":$monthDoneCount,")
        append("\"weeks\":[")
        append(
            monthWeeks.joinToString(",") { week ->
                buildWeeklyJson(week, dailyPlans, allTasks)
            },
        )
        append("]")
        append("}")
    }
}

private fun buildWeeklyJson(
    week: WeeklyPlanEntity,
    dailyPlans: List<DailyPlanEntity>,
    allTasks: List<ExecutionTaskEntity>,
): String {
    val weekDays = dailyPlans.filter { it.weeklyPlanId == week.id }
    val weekTasks = allTasks.filter { it.weeklyPlanId == week.id }
    val weekDoneCount = weekTasks.count { it.status == "DONE" }
    val weekPoolTasks = weekTasks.filter { it.dailyPlanId == null && it.sourceType == "WEEK_POOL" }

    return buildString {
        append("{")
        append("\"id\":${week.id},")
        append("\"monthlyPlanId\":${week.monthlyPlanId},")
        append("\"title\":${jsonString(week.title)},")
        append("\"goal\":${jsonStringOrNull(week.goal)},")
        append("\"weekStartDate\":${jsonString(week.weekStartDate)},")
        append("\"weekEndDate\":${jsonString(week.weekEndDate)},")
        append("\"capacity\":${jsonNumber(week.capacity)},")
        append("\"review\":${jsonStringOrNull(week.review)},")
        append("\"status\":${jsonString(week.status)},")
        append("\"taskCount\":${weekTasks.size},")
        append("\"doneTaskCount\":$weekDoneCount,")
        append("\"days\":[")
        append(
            weekDays.joinToString(",") { day ->
                buildDailyJson(day, allTasks)
            },
        )
        append("],")
        append("\"weekPoolTasks\":[")
        append(
            weekPoolTasks.joinToString(",") { task ->
                buildTaskJson(task)
            },
        )
        append("]")
        append("}")
    }
}

private fun buildDailyJson(
    day: DailyPlanEntity,
    allTasks: List<ExecutionTaskEntity>,
): String {
    val dayTasks = allTasks.filter { it.dailyPlanId == day.id }
    val dayDoneCount = dayTasks.count { it.status == "DONE" }

    return buildString {
        append("{")
        append("\"id\":${day.id},")
        append("\"weeklyPlanId\":${jsonNumber(day.weeklyPlanId)},")
        append("\"date\":${jsonString(day.date)},")
        append("\"summary\":${jsonStringOrNull(day.summary)},")
        append("\"energyLevel\":${jsonStringOrNull(day.energyLevel)},")
        append("\"review\":${jsonStringOrNull(day.review)},")
        append("\"status\":${jsonString(day.status)},")
        append("\"taskCount\":${dayTasks.size},")
        append("\"doneTaskCount\":$dayDoneCount,")
        append("\"tasks\":[")
        append(dayTasks.joinToString(",") { task -> buildTaskJson(task) })
        append("]")
        append("}")
    }
}

private fun buildTaskJson(task: ExecutionTaskEntity): String {
    return buildString {
        append("{")
        append("\"id\":${task.id},")
        append("\"title\":${jsonString(task.title)},")
        append("\"note\":${jsonStringOrNull(task.note)},")
        append("\"status\":${jsonString(task.status)},")
        append("\"priority\":${jsonString(task.priority)},")
        append("\"timeSegment\":${jsonStringOrNull(task.timeSegment)},")
        append("\"sourceType\":${jsonString(task.sourceType)}")
        append("}")
    }
}

private fun jsonString(value: String): String {
    return buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}

private fun jsonStringOrNull(value: String?): String {
    return value?.let { jsonString(it) } ?: ('n'.toString() + "ull")
}

private fun jsonNumber(value: Number?): String {
    return value?.toString() ?: ('n'.toString() + "ull")
}

private fun writePlanTreeSnapshot(
    context: Context,
    json: String,
    date: String?,
    weeklyPlanId: Long?,
    monthlyPlanId: Long?,
): File {
    val dir = File(context.filesDir, "exports/plan_tree").apply { mkdirs() }
    val suffix = buildList {
        date?.let { add("date_$it") }
        weeklyPlanId?.let { add("week_$it") }
        monthlyPlanId?.let { add("month_$it") }
        if (isEmpty()) add("all")
    }.joinToString("_")
    val file = File(dir, "latest_plan_tree_$suffix.json")
    file.writeText(json)
    return file
}
