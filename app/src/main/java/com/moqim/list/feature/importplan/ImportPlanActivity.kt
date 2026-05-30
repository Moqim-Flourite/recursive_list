package com.moqim.list.feature.importplan

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.moqim.list.data.importplan.PlanImportParser
import com.moqim.list.data.importplan.PlanImportResult
import com.moqim.list.data.importplan.PlanImportService
import com.moqim.list.data.importplan.PlanImportService.Companion.MODE_REPLACE_DAY
import com.moqim.list.data.importplan.PlanImportService.Companion.MODE_REPLACE_WEEK
import com.moqim.list.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class ImportPlanActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val prefilledJson = remember {
                        extractJsonFromIntent(intent)?.takeIf { it.isNotBlank() } ?: sampleJson()
                    }
                    val returnResult = remember { intent.getBooleanExtra(EXTRA_RETURN_RESULT, false) }
                    val autoFinish = remember { intent.getBooleanExtra(EXTRA_AUTO_FINISH, false) }
                    var jsonText by remember(prefilledJson) { mutableStateOf(prefilledJson) }
                    var importResultText: String? by remember { mutableStateOf(null) }
                    val importPreview = remember(jsonText) {
                        runCatching { buildPreviewText(jsonText) }
                            .getOrElse { "预览不可用：${it.message ?: "JSON 无法解析"}" }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "导入计划",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "支持 Operit AI 通过 Intent 预填 JSON，也支持手动粘贴。导入不会移除手动编辑能力。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            tonalElevation = 0.dp,
                        ) {
                            Text(
                                text = importPreview,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedTextField(
                            value = jsonText,
                            onValueChange = {
                                jsonText = it
                                importResultText = null
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            label = { Text("计划 JSON") },
                            minLines = 18,
                        )
                        importResultText?.let { resultText ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                tonalElevation = 0.dp,
                            ) {
                                Text(
                                    text = resultText,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    runCatching {
                                        PlanImportService().import(jsonText, applicationContext)
                                    }.onSuccess { result ->
                                        val resultText = buildImportResultText(result)
                                        importResultText = resultText
                                        if (returnResult) {
                                            deliverImportResult(
                                                ok = true,
                                                result = result,
                                                resultText = resultText,
                                            )
                                        } else {
                                            Toast.makeText(
                                                this@ImportPlanActivity,
                                                "导入成功",
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            if (autoFinish) finish()
                                        }
                                    }.onFailure { error ->
                                        val errorText = "导入失败：${error.message ?: "未知错误"}"
                                        importResultText = errorText
                                        if (returnResult) {
                                            deliverImportFailure(errorText)
                                        } else {
                                            Toast.makeText(
                                                this@ImportPlanActivity,
                                                errorText,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("确认导入")
                        }
                    }
                }
            }
        }
    }

    private fun extractJsonFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return intent.getStringExtra(EXTRA_PLAN_JSON)
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
    }

    private fun buildPreviewText(json: String): String {
        val payload = PlanImportParser.parse(json)
        return buildString {
            append("来源：${payload.source} · 模式：${payload.mode}")
            payload.monthlyPlan?.let {
                append("\n月计划：${it.title}（${it.startDate} ~ ${it.endDate}）")
            }
            payload.weeklyPlan?.let {
                append("\n周计划：${it.title}（${it.weekStartDate} ~ ${it.weekEndDate}）")
            }
            payload.dailyPlan?.let {
                append("\n日计划：${it.date} · ${it.summary.ifBlank { "无摘要" }}")
                append("\n摘要策略：${describeSummaryMode(it.summaryMode)}")
            }
            when (payload.mode) {
                MODE_REPLACE_DAY -> append("\n导入策略：覆盖该日计划及其任务")
                MODE_REPLACE_WEEK -> append("\n导入策略：覆盖该周计划及其下属日计划/任务")
                else -> append("\n导入策略：智能合并")
            }
            append("\n任务：${payload.tasks.size} 条")
            append("\n周池：${payload.weekPoolTasks.size} 条")
        }
    }

    private fun sampleJson(): String = """
        {
          "source": "operit_ai",
          "mode": "replace_day",
          "monthlyPlan": {
            "title": "4月主线",
            "theme": "计划系统收口",
            "goal": "把计划讨论结果稳定导入执行系统",
            "startDate": "2026-04-01",
            "endDate": "2026-04-30"
          },
          "weeklyPlan": {
            "title": "第2周推进",
            "goal": "完成导入接口底座",
            "weekStartDate": "2026-04-06",
            "weekEndDate": "2026-04-12",
            "capacity": 480
          },
          "dailyPlan": {
            "date": "2026-04-09",
            "summary": "承接周计划，完成导入器与验证",
            "summaryMode": "append",
            "energyLevel": "HIGH",
            "review": ""
          },
          "tasks": [
            {
              "title": "设计导入 payload",
              "note": "确定 schema",
              "estimatedMinutes": 45,
              "timeSegment": "MORNING",
              "isTopFocus": true
            },
            {
              "title": "实现导入服务",
              "note": "先支持 merge",
              "estimatedMinutes": 60,
              "timeSegment": "AFTERNOON",
              "isTopFocus": true
            }
          ],
          "weekPoolTasks": [
            {
              "title": "补导入协议文档",
              "estimatedMinutes": 30
            }
          ]
        }
    """.trimIndent()

    private fun buildImportResultText(result: PlanImportResult): String {
        return buildString {
            append("导入完成")
            append("\n来源：${result.source}")
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

    private fun deliverImportResult(
        ok: Boolean,
        result: PlanImportResult,
        resultText: String,
    ) {
        setResult(
            if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Intent().apply {
                putExtra(EXTRA_RESULT_OK, ok)
                putExtra(EXTRA_RESULT_TEXT, resultText)
                putExtra(EXTRA_RESULT_SOURCE, result.source)
                putExtra(EXTRA_RESULT_MODE, result.mode)
                putExtra(EXTRA_RESULT_MONTHLY_PLAN_CREATED, result.monthlyPlanCreated)
                putExtra(EXTRA_RESULT_WEEKLY_PLAN_CREATED, result.weeklyPlanCreated)
                putExtra(EXTRA_RESULT_DAILY_PLAN_CREATED, result.dailyPlanCreated)
                putExtra(EXTRA_RESULT_DAILY_PLAN_MERGED, result.dailyPlanMerged)
                putExtra(EXTRA_RESULT_CREATED_TASK_COUNT, result.createdTaskCount)
                putExtra(EXTRA_RESULT_MERGED_TASK_COUNT, result.mergedTaskCount)
                putExtra(EXTRA_RESULT_CREATED_WEEK_POOL_TASK_COUNT, result.createdWeekPoolTaskCount)
                putExtra(EXTRA_RESULT_MERGED_WEEK_POOL_TASK_COUNT, result.mergedWeekPoolTaskCount)
            },
        )
        finish()
    }

    private fun deliverImportFailure(errorText: String) {
        setResult(
            Activity.RESULT_CANCELED,
            Intent().apply {
                putExtra(EXTRA_RESULT_OK, false)
                putExtra(EXTRA_RESULT_TEXT, errorText)
            },
        )
        finish()
    }

    private fun describeSummaryMode(summaryMode: String): String = when (summaryMode) {
        "append" -> "追加到已有摘要后"
        "keep_existing" -> "若已有摘要则保留原摘要"
        else -> "用导入摘要直接覆盖"
    }

    companion object {
        const val EXTRA_PLAN_JSON = "plan_json"
        const val EXTRA_RETURN_RESULT = "return_result"
        const val EXTRA_AUTO_FINISH = "auto_finish"

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
    }
}
