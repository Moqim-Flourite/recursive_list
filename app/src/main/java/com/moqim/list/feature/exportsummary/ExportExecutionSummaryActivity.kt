package com.moqim.list.feature.exportsummary

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moqim.list.data.export.ExecutionSummaryExporter
import com.moqim.list.ui.theme.MyApplicationTheme
import java.time.LocalDate

class ExportExecutionSummaryActivity : ComponentActivity() {
    private var autoShared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val initialDate = remember {
                        intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()
                    }
                    val autoShare = remember {
                        intent.getBooleanExtra(EXTRA_AUTO_SHARE, false)
                    }
                    val returnResult = remember {
                        intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)
                    }
                    var dateText by remember(initialDate) { mutableStateOf(initialDate) }
                    val exportJson by produceState(initialValue = "加载中...", dateText) {
                        value = runCatching {
                            ExecutionSummaryExporter(applicationContext).exportForDate(dateText.trim())
                        }.getOrElse {
                            "导出失败：${it.message ?: "未知错误"}"
                        }
                    }

                    if (returnResult && !exportJson.startsWith("加载中")) {
                        deliverResult(exportJson)
                    } else if (autoShare && !exportJson.startsWith("加载中") && !exportJson.startsWith("导出失败")) {
                        shareJson(exportJson)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "导出执行摘要",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = "用于把某天真实执行结果回传给 Operit，辅助递归生成下一天计划。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = { dateText = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("日期（YYYY-MM-DD）") },
                            singleLine = true,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {
                                    dateText = LocalDate.now().toString()
                                    autoShared = false
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("今天")
                            }
                            Button(
                                onClick = {
                                    val text = exportJson
                                    if (text.startsWith("导出失败")) {
                                        Toast.makeText(this@ExportExecutionSummaryActivity, text, Toast.LENGTH_LONG).show()
                                    } else {
                                        autoShared = false
                                        shareJson(text)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("分享 JSON")
                            }
                        }
                        OutlinedTextField(
                            value = exportJson,
                            onValueChange = {},
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("执行摘要 JSON") },
                            minLines = 20,
                        )
                    }
                }
            }
        }
    }

    private fun shareJson(text: String) {
        if (autoShared) return
        autoShared = true
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                },
                "分享执行摘要 JSON",
            )
        )
    }

    private fun deliverResult(text: String) {
        if (autoShared) return
        autoShared = true
        val resultIntent = Intent().apply {
            putExtra(EXTRA_RESULT_JSON, text)
            putExtra(EXTRA_RESULT_DATE, intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString())
            putExtra(EXTRA_RESULT_OK, !text.startsWith("导出失败"))
        }
        setResult(
            if (text.startsWith("导出失败")) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            resultIntent,
        )
        finish()
    }

    companion object {
        const val ACTION_EXPORT_EXECUTION_SUMMARY = "com.moqim.list.action.EXPORT_EXECUTION_SUMMARY"
        const val EXTRA_DATE = "date"
        const val EXTRA_AUTO_SHARE = "auto_share"
        const val EXTRA_RETURN_RESULT = "return_result"
        const val EXTRA_RESULT_JSON = "result_json"
        const val EXTRA_RESULT_DATE = "result_date"
        const val EXTRA_RESULT_OK = "result_ok"
    }
}