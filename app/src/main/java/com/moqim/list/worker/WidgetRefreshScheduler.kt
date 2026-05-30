package com.moqim.list.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moqim.list.data.local.provider.DatabaseProvider
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

object WidgetRefreshScheduler {

    fun hasAggressiveWidgets(context: Context): Boolean = runBlocking {
        val db = DatabaseProvider.get(context)
        val widgetDao = db.widgetInstanceConfigDao()
        val surfaceDao = db.surfaceConfigDao()
        widgetDao.getAll().any { widget ->
            surfaceDao.getById(widget.surfaceConfigId)?.refreshPolicy == "AGGRESSIVE"
        }
    }

    fun enqueueAggressiveRefreshIfNeeded(context: Context, reason: String) {
        if (!hasAggressiveWidgets(context)) return

        val request = OneTimeWorkRequestBuilder<AggressiveWidgetRefreshWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .addTag("aggressive_widget_refresh")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "aggressive_widget_refresh_$reason",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
