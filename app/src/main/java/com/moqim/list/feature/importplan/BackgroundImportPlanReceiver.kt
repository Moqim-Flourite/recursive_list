package com.moqim.list.feature.importplan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moqim.list.data.importplan.PlanImportResult
import com.moqim.list.data.importplan.PlanImportService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BackgroundImportPlanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val json = intent.getStringExtra(ImportPlanActivity.EXTRA_PLAN_JSON)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                require(!json.isNullOrBlank()) { "缺少 plan_json / EXTRA_TEXT" }
                val result = PlanImportService().import(json, appContext)
                val resultFile = writeLatestImportResult(appContext, result)
                appContext.sendBroadcast(
                    Intent(ACTION_IMPORT_PLAN_RESULT).apply {
                        setPackage(appContext.packageName)
                        putImportResult(result)
                        putExtra(EXTRA_RESULT_OK, true)
                        putExtra(EXTRA_RESULT_FILE_PATH, resultFile.absolutePath)
                    },
                )
            } catch (error: Throwable) {
                appContext.sendBroadcast(
                    Intent(ACTION_IMPORT_PLAN_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, false)
                        putExtra(EXTRA_RESULT_TEXT, error.message ?: "未知错误")
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BACKGROUND_IMPORT_PLAN = "com.moqim.list.action.BACKGROUND_IMPORT_PLAN"
        const val ACTION_IMPORT_PLAN_RESULT = "com.moqim.list.action.IMPORT_PLAN_RESULT"

        const val EXTRA_RESULT_OK = "result_ok"
        const val EXTRA_RESULT_TEXT = "result_text"
        const val EXTRA_RESULT_SOURCE = "result_source"
        const val EXTRA_RESULT_MODE = "result_mode"
        const val EXTRA_RESULT_MONTHLY_PLAN_CREATED = "monthly_plan_created"
        const val EXTRA_RESULT_WEEKLY_PLAN_CREATED = "weekly_plan_created"
        const val EXTRA_RESULT_DAILY_PLAN_CREATED = "daily_plan_created"
        const val EXTRA_RESULT_DAILY_PLAN_MERGED = "daily_plan_merged"
        const val EXTRA_RESULT_CREATED_TASK_COUNT = "created_task_count"
        const val EXTRA_RESULT_MERGED_TASK_COUNT = "merged_task_count"
        const val EXTRA_RESULT_CREATED_WEEK_POOL_TASK_COUNT = "created_week_pool_task_count"
        const val EXTRA_RESULT_MERGED_WEEK_POOL_TASK_COUNT = "merged_week_pool_task_count"
        const val EXTRA_RESULT_FILE_PATH = "result_file_path"
    }
}

private fun Intent.putImportResult(result: PlanImportResult): Intent = apply {
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_TEXT, buildImportResultText(result))
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_SOURCE, result.source)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_MODE, result.mode)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_MONTHLY_PLAN_CREATED, result.monthlyPlanCreated)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_WEEKLY_PLAN_CREATED, result.weeklyPlanCreated)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_DAILY_PLAN_CREATED, result.dailyPlanCreated)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_DAILY_PLAN_MERGED, result.dailyPlanMerged)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_CREATED_TASK_COUNT, result.createdTaskCount)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_MERGED_TASK_COUNT, result.mergedTaskCount)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_CREATED_WEEK_POOL_TASK_COUNT, result.createdWeekPoolTaskCount)
    putExtra(BackgroundImportPlanReceiver.EXTRA_RESULT_MERGED_WEEK_POOL_TASK_COUNT, result.mergedWeekPoolTaskCount)
}

private fun buildImportResultText(result: PlanImportResult): String {
    return buildString {
        append("导入完成")
        append("\n模式：${result.mode}")
        append("\n月计划：${if (result.monthlyPlanCreated) "新建/更新完成" else "未新建"}")
        append("\n周计划：${if (result.weeklyPlanCreated) "新建/更新完成" else "未新建"}")
        append("\n日计划：")
        append(
            when {
                result.dailyPlanCreated -> "已新建"
                result.dailyPlanMerged -> "已合并"
                else -> "未变更或未提供"
            },
        )
        append("\n日任务：新增 ${result.createdTaskCount} / 合并 ${result.mergedTaskCount}")
        append("\n周池任务：新增 ${result.createdWeekPoolTaskCount} / 合并 ${result.mergedWeekPoolTaskCount}")
    }
}

private fun writeLatestImportResult(context: Context, result: PlanImportResult): File {
    val dir = File(context.filesDir, "exports/import_result").apply { mkdirs() }
    val file = File(dir, "latest_import_result.json")
    file.writeText(
        buildString {
            append("{")
            append("\"resultOk\":true,")
            append("\"status\":\"success\",")
            append("\"message\":${jsonString(buildImportResultText(result))},")
            append("\"source\":${jsonString(result.source)},")
            append("\"mode\":${jsonString(result.mode)},")
            append("\"created\":{")
            append("\"monthlyPlan\":${result.monthlyPlanCreated},")
            append("\"weeklyPlan\":${result.weeklyPlanCreated},")
            append("\"dailyPlan\":${result.dailyPlanCreated},")
            append("\"tasks\":${result.createdTaskCount},")
            append("\"weekPoolTasks\":${result.createdWeekPoolTaskCount}")
            append("},")
            append("\"merged\":{")
            append("\"dailyPlan\":${result.dailyPlanMerged},")
            append("\"tasks\":${result.mergedTaskCount},")
            append("\"weekPoolTasks\":${result.mergedWeekPoolTaskCount}")
            append("},")
            append("\"timestamp\":${jsonString(System.currentTimeMillis().toString())}")
            append("}")
        },
    )
    return file
}

private fun jsonString(value: String): String =
    "\"" + value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n") + "\""