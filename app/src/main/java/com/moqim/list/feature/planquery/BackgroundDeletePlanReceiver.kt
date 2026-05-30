package com.moqim.list.feature.planquery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomDailyPlanRepository
import com.moqim.list.data.repository.RoomMonthlyPlanRepository
import com.moqim.list.data.repository.RoomWeeklyPlanRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class BackgroundDeletePlanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        val planType = intent.getStringExtra(EXTRA_PLAN_TYPE)?.trim()?.lowercase().orEmpty()
        val planId = intent.getLongExtra(EXTRA_PLAN_ID, -1L)
        val cascade = intent.getBooleanExtra(EXTRA_CASCADE, true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                require(planId > 0) { "缺少有效 planId" }
                require(planType in setOf(TYPE_MONTHLY, TYPE_WEEKLY, TYPE_DAILY)) { "planType 仅支持 monthly / weekly / daily" }

                val db = DatabaseProvider.get(appContext)

                val beforeMonthlyCount = db.monthlyPlanDao().countAll()
                val beforeWeeklyCount = db.weeklyPlanDao().countAll()
                val beforeDailyCount = db.dailyPlanDao().getAll().size
                val beforeTaskCount = db.executionTaskDao().getAll().size

                val existsBefore = when (planType) {
                    TYPE_MONTHLY -> db.monthlyPlanDao().getById(planId) != null
                    TYPE_WEEKLY -> db.weeklyPlanDao().getById(planId) != null
                    TYPE_DAILY -> db.dailyPlanDao().getById(planId) != null
                    else -> false
                }
                require(existsBefore) { "PLAN_NOT_FOUND: 未找到目标计划" }

                val monthlyRepository = RoomMonthlyPlanRepository(
                    monthlyPlanDao = db.monthlyPlanDao(),
                    weeklyPlanDao = db.weeklyPlanDao(),
                    dailyPlanDao = db.dailyPlanDao(),
                    executionTaskDao = db.executionTaskDao(),
                )
                val weeklyRepository = RoomWeeklyPlanRepository(
                    weeklyPlanDao = db.weeklyPlanDao(),
                    monthlyPlanDao = db.monthlyPlanDao(),
                    dailyPlanDao = db.dailyPlanDao(),
                    executionTaskDao = db.executionTaskDao(),
                )
                val dailyRepository = RoomDailyPlanRepository(
                    dailyPlanDao = db.dailyPlanDao(),
                    weeklyPlanDao = db.weeklyPlanDao(),
                    executionTaskDao = db.executionTaskDao(),
                )

                when (planType) {
                    TYPE_MONTHLY -> if (cascade) monthlyRepository.deleteMonthlyPlanCascade(planId) else monthlyRepository.deleteMonthlyPlan(planId)
                    TYPE_WEEKLY -> if (cascade) weeklyRepository.deleteWeeklyPlanCascade(planId) else weeklyRepository.deleteWeeklyPlan(planId)
                    TYPE_DAILY -> if (cascade) dailyRepository.deleteDailyPlanCascade(planId) else dailyRepository.deleteDailyPlan(planId)
                }

                val existsAfter = when (planType) {
                    TYPE_MONTHLY -> db.monthlyPlanDao().getById(planId) != null
                    TYPE_WEEKLY -> db.weeklyPlanDao().getById(planId) != null
                    TYPE_DAILY -> db.dailyPlanDao().getById(planId) != null
                    else -> false
                }
                check(!existsAfter) { "DELETE_TRANSACTION_FAILED: 删除后目标计划仍存在" }

                val afterMonthlyCount = db.monthlyPlanDao().countAll()
                val afterWeeklyCount = db.weeklyPlanDao().countAll()
                val afterDailyCount = db.dailyPlanDao().getAll().size
                val afterTaskCount = db.executionTaskDao().getAll().size

                val deletedMonthlyCount = (beforeMonthlyCount - afterMonthlyCount).coerceAtLeast(0)
                val deletedWeeklyCount = (beforeWeeklyCount - afterWeeklyCount).coerceAtLeast(0)
                val deletedDailyCount = (beforeDailyCount - afterDailyCount).coerceAtLeast(0)
                val deletedTaskCount = (beforeTaskCount - afterTaskCount).coerceAtLeast(0)

                val latestPlanTreeFile = writeLatestPlanTreeAllSnapshot(appContext, db)
                val resultFile = writeDeleteResultSnapshot(
                    context = appContext,
                    resultOk = true,
                    planType = planType,
                    planId = planId,
                    cascade = cascade,
                    errorCode = null,
                    message = "Deleted $planType plan $planId",
                    deletedMonthlyCount = deletedMonthlyCount,
                    deletedWeeklyCount = deletedWeeklyCount,
                    deletedDailyCount = deletedDailyCount,
                    deletedTaskCount = deletedTaskCount,
                    latestPlanTreeFilePath = latestPlanTreeFile.absolutePath,
                )

                appContext.sendBroadcast(
                    Intent(ACTION_DELETE_PLAN_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, true)
                        putExtra(EXTRA_RESULT_PLAN_TYPE, planType)
                        putExtra(EXTRA_RESULT_PLAN_ID, planId)
                        putExtra(EXTRA_RESULT_CASCADE, cascade)
                        putExtra(EXTRA_RESULT_MESSAGE, "Deleted $planType plan $planId")
                        putExtra(EXTRA_RESULT_FILE_PATH, resultFile.absolutePath)
                        putExtra(EXTRA_RESULT_LATEST_PLAN_TREE_FILE_PATH, latestPlanTreeFile.absolutePath)
                        putExtra(EXTRA_RESULT_DELETED_MONTHLY_COUNT, deletedMonthlyCount)
                        putExtra(EXTRA_RESULT_DELETED_WEEKLY_COUNT, deletedWeeklyCount)
                        putExtra(EXTRA_RESULT_DELETED_DAILY_COUNT, deletedDailyCount)
                        putExtra(EXTRA_RESULT_DELETED_TASK_COUNT, deletedTaskCount)
                    },
                )
            } catch (error: Throwable) {
                val errorCode = when {
                    error.message?.startsWith("PLAN_NOT_FOUND:") == true -> ERROR_PLAN_NOT_FOUND
                    error.message?.startsWith("DELETE_TRANSACTION_FAILED:") == true -> ERROR_DELETE_TRANSACTION_FAILED
                    error.message?.contains("planType") == true -> ERROR_INVALID_PLAN_TYPE
                    error.message?.contains("planId") == true -> ERROR_INVALID_PLAN_ID
                    else -> ERROR_UNKNOWN
                }

                val resultFile = writeDeleteResultSnapshot(
                    context = appContext,
                    resultOk = false,
                    planType = planType,
                    planId = planId,
                    cascade = cascade,
                    errorCode = errorCode,
                    message = error.message ?: "未知错误",
                    deletedMonthlyCount = 0,
                    deletedWeeklyCount = 0,
                    deletedDailyCount = 0,
                    deletedTaskCount = 0,
                    latestPlanTreeFilePath = null,
                )

                appContext.sendBroadcast(
                    Intent(ACTION_DELETE_PLAN_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, false)
                        putExtra(EXTRA_RESULT_PLAN_TYPE, planType)
                        putExtra(EXTRA_RESULT_PLAN_ID, planId)
                        putExtra(EXTRA_RESULT_CASCADE, cascade)
                        putExtra(EXTRA_RESULT_ERROR_CODE, errorCode)
                        putExtra(EXTRA_RESULT_ERROR, error.message ?: "未知错误")
                        putExtra(EXTRA_RESULT_FILE_PATH, resultFile.absolutePath)
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BACKGROUND_DELETE_PLAN = "com.moqim.list.action.BACKGROUND_DELETE_PLAN"
        const val ACTION_DELETE_PLAN_RESULT = "com.moqim.list.action.DELETE_PLAN_RESULT"

        const val EXTRA_PLAN_TYPE = "planType"
        const val EXTRA_PLAN_ID = "planId"
        const val EXTRA_CASCADE = "cascade"

        const val EXTRA_RESULT_OK = "result_ok"
        const val EXTRA_RESULT_PLAN_TYPE = "result_plan_type"
        const val EXTRA_RESULT_PLAN_ID = "result_plan_id"
        const val EXTRA_RESULT_CASCADE = "result_cascade"
        const val EXTRA_RESULT_MESSAGE = "result_message"
        const val EXTRA_RESULT_ERROR = "result_error"
        const val EXTRA_RESULT_ERROR_CODE = "result_error_code"
        const val EXTRA_RESULT_FILE_PATH = "result_file_path"
        const val EXTRA_RESULT_LATEST_PLAN_TREE_FILE_PATH = "latest_plan_tree_file_path"
        const val EXTRA_RESULT_DELETED_MONTHLY_COUNT = "deleted_monthly_count"
        const val EXTRA_RESULT_DELETED_WEEKLY_COUNT = "deleted_weekly_count"
        const val EXTRA_RESULT_DELETED_DAILY_COUNT = "deleted_daily_count"
        const val EXTRA_RESULT_DELETED_TASK_COUNT = "deleted_task_count"

        const val TYPE_MONTHLY = "monthly"
        const val TYPE_WEEKLY = "weekly"
        const val TYPE_DAILY = "daily"

        const val ERROR_INVALID_PLAN_TYPE = "INVALID_PLAN_TYPE"
        const val ERROR_INVALID_PLAN_ID = "INVALID_PLAN_ID"
        const val ERROR_PLAN_NOT_FOUND = "PLAN_NOT_FOUND"
        const val ERROR_DELETE_TRANSACTION_FAILED = "DELETE_TRANSACTION_FAILED"
        const val ERROR_UNKNOWN = "UNKNOWN_ERROR"
    }
}

private fun writeDeleteResultSnapshot(
    context: Context,
    resultOk: Boolean,
    planType: String,
    planId: Long,
    cascade: Boolean,
    errorCode: String?,
    message: String,
    deletedMonthlyCount: Int,
    deletedWeeklyCount: Int,
    deletedDailyCount: Int,
    deletedTaskCount: Int,
    latestPlanTreeFilePath: String?,
): File {
    val dir = File(context.filesDir, "exports/delete_result").apply { mkdirs() }
    val file = File(dir, "latest_delete_result.json")
    val json = buildString {
        append("{")
        append("\"resultOk\":$resultOk,")
        append("\"planType\":${deleteJsonString(planType)},")
        append("\"planId\":$planId,")
        append("\"cascade\":$cascade,")
        append("\"errorCode\":${deleteJsonStringOrNull(errorCode)},")
        append("\"message\":${deleteJsonString(message)},")
        append("\"deletedCounts\":{")
        append("\"monthlyPlans\":$deletedMonthlyCount,")
        append("\"weeklyPlans\":$deletedWeeklyCount,")
        append("\"dailyPlans\":$deletedDailyCount,")
        append("\"tasks\":$deletedTaskCount")
        append("},")
        append("\"latestPlanTreeFilePath\":${deleteJsonStringOrNull(latestPlanTreeFilePath)},")
        append("\"timestamp\":${deleteJsonString(System.currentTimeMillis().toString())}")
        append("}")
    }
    file.writeText(json)
    return file
}

private suspend fun writeLatestPlanTreeAllSnapshot(
    context: Context,
    db: Any,
): File {
    val databaseProvider = DatabaseProvider.get(context)
    val monthlyPlans = databaseProvider.monthlyPlanDao().getAll()
    val weeklyPlans = databaseProvider.weeklyPlanDao().getAll()
    val dailyPlans = databaseProvider.dailyPlanDao().getAll()
    val allTasks = databaseProvider.executionTaskDao().getAll()

    val json = buildDeletePlanTreeJson(
        monthlyPlans = monthlyPlans,
        weeklyPlans = weeklyPlans,
        dailyPlans = dailyPlans,
        allTasks = allTasks,
    )

    val dir = File(context.filesDir, "exports/plan_tree").apply { mkdirs() }
    val file = File(dir, "latest_plan_tree_all.json")
    file.writeText(json)
    return file
}

private fun buildDeletePlanTreeJson(
    monthlyPlans: List<com.moqim.list.data.local.entity.MonthlyPlanEntity>,
    weeklyPlans: List<com.moqim.list.data.local.entity.WeeklyPlanEntity>,
    dailyPlans: List<com.moqim.list.data.local.entity.DailyPlanEntity>,
    allTasks: List<com.moqim.list.data.local.entity.ExecutionTaskEntity>,
): String {
    return buildString {
        append("{")
        append("\"schemaVersion\":\"1.0\",")
        append("\"sourceApp\":\"moqim_list\",")
        append("\"monthlyPlans\":[")
        append(
            monthlyPlans.joinToString(",") { month ->
                buildDeleteMonthlyJson(month, weeklyPlans, dailyPlans, allTasks)
            },
        )
        append("]")
        append("}")
    }
}

private fun buildDeleteMonthlyJson(
    month: com.moqim.list.data.local.entity.MonthlyPlanEntity,
    weeklyPlans: List<com.moqim.list.data.local.entity.WeeklyPlanEntity>,
    dailyPlans: List<com.moqim.list.data.local.entity.DailyPlanEntity>,
    allTasks: List<com.moqim.list.data.local.entity.ExecutionTaskEntity>,
): String {
    val monthWeeks = weeklyPlans.filter { it.monthlyPlanId == month.id }
    val monthTasks = allTasks.filter { it.monthlyPlanId == month.id }
    val monthDoneCount = monthTasks.count { it.status == "DONE" }

    return buildString {
        append("{")
        append("\"id\":${month.id},")
        append("\"title\":${deleteJsonString(month.title)},")
        append("\"theme\":${deleteJsonString(month.theme)},")
        append("\"goal\":${deleteJsonStringOrNull(month.goal)},")
        append("\"startDate\":${deleteJsonString(month.startDate)},")
        append("\"endDate\":${deleteJsonString(month.endDate)},")
        append("\"status\":${deleteJsonString(month.status)},")
        append("\"taskCount\":${monthTasks.size},")
        append("\"doneTaskCount\":$monthDoneCount,")
        append("\"weeks\":[")
        append(monthWeeks.joinToString(",") { week -> buildDeleteWeeklyJson(week, dailyPlans, allTasks) })
        append("]")
        append("}")
    }
}

private fun buildDeleteWeeklyJson(
    week: com.moqim.list.data.local.entity.WeeklyPlanEntity,
    dailyPlans: List<com.moqim.list.data.local.entity.DailyPlanEntity>,
    allTasks: List<com.moqim.list.data.local.entity.ExecutionTaskEntity>,
): String {
    val weekDays = dailyPlans.filter { it.weeklyPlanId == week.id }
    val weekTasks = allTasks.filter { it.weeklyPlanId == week.id }
    val weekDoneCount = weekTasks.count { it.status == "DONE" }
    val weekPoolTasks = weekTasks.filter { it.dailyPlanId == null && it.sourceType == "WEEK_POOL" }

    return buildString {
        append("{")
        append("\"id\":${week.id},")
        append("\"monthlyPlanId\":${week.monthlyPlanId},")
        append("\"title\":${deleteJsonString(week.title)},")
        append("\"goal\":${deleteJsonStringOrNull(week.goal)},")
        append("\"weekStartDate\":${deleteJsonString(week.weekStartDate)},")
        append("\"weekEndDate\":${deleteJsonString(week.weekEndDate)},")
        append("\"capacity\":${deleteJsonNumber(week.capacity)},")
        append("\"review\":${deleteJsonStringOrNull(week.review)},")
        append("\"status\":${deleteJsonString(week.status)},")
        append("\"taskCount\":${weekTasks.size},")
        append("\"doneTaskCount\":$weekDoneCount,")
        append("\"days\":[")
        append(weekDays.joinToString(",") { day -> buildDeleteDailyJson(day, allTasks) })
        append("],")
        append("\"weekPoolTasks\":[")
        append(weekPoolTasks.joinToString(",") { task -> buildDeleteTaskJson(task) })
        append("]")
        append("}")
    }
}

private fun buildDeleteDailyJson(
    day: com.moqim.list.data.local.entity.DailyPlanEntity,
    allTasks: List<com.moqim.list.data.local.entity.ExecutionTaskEntity>,
): String {
    val dayTasks = allTasks.filter { it.dailyPlanId == day.id }
    val dayDoneCount = dayTasks.count { it.status == "DONE" }

    return buildString {
        append("{")
        append("\"id\":${day.id},")
        append("\"weeklyPlanId\":${deleteJsonNumber(day.weeklyPlanId)},")
        append("\"date\":${deleteJsonString(day.date)},")
        append("\"summary\":${deleteJsonStringOrNull(day.summary)},")
        append("\"energyLevel\":${deleteJsonStringOrNull(day.energyLevel)},")
        append("\"review\":${deleteJsonStringOrNull(day.review)},")
        append("\"status\":${deleteJsonString(day.status)},")
        append("\"taskCount\":${dayTasks.size},")
        append("\"doneTaskCount\":$dayDoneCount,")
        append("\"tasks\":[")
        append(dayTasks.joinToString(",") { task -> buildDeleteTaskJson(task) })
        append("]")
        append("}")
    }
}

private fun buildDeleteTaskJson(
    task: com.moqim.list.data.local.entity.ExecutionTaskEntity,
): String {
    return buildString {
        append("{")
        append("\"id\":${task.id},")
        append("\"title\":${deleteJsonString(task.title)},")
        append("\"note\":${deleteJsonStringOrNull(task.note)},")
        append("\"status\":${deleteJsonString(task.status)},")
        append("\"priority\":${deleteJsonString(task.priority)},")
        append("\"timeSegment\":${deleteJsonStringOrNull(task.timeSegment)},")
        append("\"sourceType\":${deleteJsonString(task.sourceType)}")
        append("}")
    }
}

private fun deleteJsonString(value: String): String {
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

private fun deleteJsonStringOrNull(value: String?): String {
    return value?.let { deleteJsonString(it) } ?: ('n'.toString() + "ull")
}

private fun deleteJsonNumber(value: Number?): String {
    return value?.toString() ?: ('n'.toString() + "ull")
}
