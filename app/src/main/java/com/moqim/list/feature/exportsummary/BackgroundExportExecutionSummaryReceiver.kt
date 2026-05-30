package com.moqim.list.feature.exportsummary

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moqim.list.data.export.ExecutionSummaryExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class BackgroundExportExecutionSummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val date = intent.getStringExtra(EXTRA_DATE)?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = ExecutionSummaryExporter(appContext).exportForDate(date)
                appContext.sendBroadcast(
                    Intent(ACTION_EXPORT_EXECUTION_SUMMARY_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, true)
                        putExtra(EXTRA_RESULT_DATE, date)
                        putExtra(EXTRA_RESULT_JSON, json)
                    },
                )
            } catch (error: Throwable) {
                appContext.sendBroadcast(
                    Intent(ACTION_EXPORT_EXECUTION_SUMMARY_RESULT).apply {
                        setPackage(appContext.packageName)
                        putExtra(EXTRA_RESULT_OK, false)
                        putExtra(EXTRA_RESULT_DATE, date)
                        putExtra(EXTRA_RESULT_ERROR, error.message ?: "未知错误")
                    },
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_BACKGROUND_EXPORT_EXECUTION_SUMMARY = "com.moqim.list.action.BACKGROUND_EXPORT_EXECUTION_SUMMARY"
        const val ACTION_EXPORT_EXECUTION_SUMMARY_RESULT = "com.moqim.list.action.EXPORT_EXECUTION_SUMMARY_RESULT"

        const val EXTRA_DATE = "date"
        const val EXTRA_RESULT_OK = "result_ok"
        const val EXTRA_RESULT_DATE = "result_date"
        const val EXTRA_RESULT_JSON = "result_json"
        const val EXTRA_RESULT_ERROR = "result_error"
    }
}