package com.moqim.list.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.repository.RoomHabitRepository
import com.moqim.list.wallpaper.WallpaperRefreshNotifier
import com.moqim.list.widget.CurrentSegmentWidgetProvider
import com.moqim.list.widget.MorningWidgetProvider
import java.time.LocalDate

class DailyRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = DatabaseProvider.get(applicationContext)
        val habitRepository = RoomHabitRepository(
            habitTemplateDao = db.habitTemplateDao(),
            habitRecordDao = db.habitRecordDao(),
        )
        habitRepository.seedDefaultsIfNeeded()
        habitRepository.ensureTodayRecords(LocalDate.now().toString())

        MorningWidgetProvider.refreshAll(applicationContext)
        CurrentSegmentWidgetProvider.refreshAll(applicationContext)
        WallpaperRefreshNotifier.notifyRefresh(applicationContext)
        return Result.success()
    }
}
