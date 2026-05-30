package com.moqim.list.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.widget.CurrentSegmentWidgetProvider
import com.moqim.list.widget.MorningWidgetProvider
import com.moqim.list.worker.DailyRefreshWorker
import com.moqim.list.worker.SegmentRefreshWorker
import com.moqim.list.worker.WidgetRefreshScheduler
import com.moqim.list.worker.WidgetRefreshWorker
import java.util.concurrent.TimeUnit

class MoqimListApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MorningWidgetProvider.refreshAll(this)
        CurrentSegmentWidgetProvider.refreshAll(this)
        WallpaperRefreshNotifier.notifyRefresh(this)
        WidgetRefreshScheduler.enqueueAggressiveRefreshIfNeeded(this, "app_launch")
        scheduleWorkerRefreshes()
    }

    private fun scheduleWorkerRefreshes() {
        val widgetRequest = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            15, TimeUnit.MINUTES,
        ).build()

        val dailyRequest = PeriodicWorkRequestBuilder<DailyRefreshWorker>(
            24, TimeUnit.HOURS,
            6, TimeUnit.HOURS,
        ).build()

        val segmentRequest = PeriodicWorkRequestBuilder<SegmentRefreshWorker>(
            15, TimeUnit.MINUTES,
        ).build()

        val workManager = WorkManager.getInstance(this)
        workManager.enqueueUniquePeriodicWork(
            "widget_refresh_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            widgetRequest,
        )
        workManager.enqueueUniquePeriodicWork(
            "daily_refresh_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            dailyRequest,
        )
        workManager.enqueueUniquePeriodicWork(
            "segment_refresh_work",
            ExistingPeriodicWorkPolicy.UPDATE,
            segmentRequest,
        )
    }
}
