package com.moqim.list.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.widget.CurrentSegmentWidgetProvider
import com.moqim.list.widget.MorningWidgetProvider

class AggressiveWidgetRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        MorningWidgetProvider.refreshAll(applicationContext)
        CurrentSegmentWidgetProvider.refreshAll(applicationContext)
        WallpaperRefreshNotifier.notifyRefresh(applicationContext)
        return Result.success()
    }
}
