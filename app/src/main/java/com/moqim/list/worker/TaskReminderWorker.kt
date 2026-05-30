package com.moqim.list.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.moqim.list.core.model.TimeSegment
import com.moqim.list.data.local.provider.DatabaseProvider
import com.moqim.list.data.preferences.SettingsPreferencesRepository
import com.moqim.list.reminder.TaskReminderNotifier
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first

class TaskReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val db = DatabaseProvider.get(applicationContext)
        val settingsRepository = SettingsPreferencesRepository(applicationContext)
        settingsRepository.clearExpiredTaskReminderSentState()
        val sentState = settingsRepository.getTaskReminderSentState()
        val settings = settingsRepository.settingsFlow.first()
        val now = LocalTime.now()
        val today = LocalDate.now().toString()
        val dailyPlan = db.dailyPlanDao().observeByDate(today).first() ?: return Result.success()
        val tasks = db.executionTaskDao().getByDailyPlanId(dailyPlan.id)

        val segmentTimes = mapOf(
            TimeSegment.MORNING_START.name to settings.morningStartTime,
            TimeSegment.MORNING.name to settings.morningTime,
            TimeSegment.NOON.name to settings.noonTime,
            TimeSegment.AFTERNOON.name to settings.afternoonTime,
            TimeSegment.EVENING.name to settings.eveningTime,
        )

        val nowKey = "%02d:%02d".format(now.hour, now.minute)

        segmentTimes.forEach { (segment, time) ->
            if (time == nowKey) {
                val reminderKey = "segment:$today:$segment:$time"
                val segmentTasks = tasks.filter { it.timeSegment == segment && it.status != "DONE" && it.specificTime.isNullOrBlank() }
                if (segmentTasks.isNotEmpty() && reminderKey !in sentState.sentKeys) {
                    TaskReminderNotifier.notifySegmentReminder(
                        context = applicationContext,
                        segmentName = segment,
                        taskTitles = segmentTasks.map { it.title },
                    )
                    settingsRepository.markTaskReminderSent(reminderKey)
                }
            }
        }

        tasks.filter { it.status != "DONE" && !it.specificTime.isNullOrBlank() && it.specificTime == nowKey }
            .forEach { task ->
                val reminderKey = "task:$today:${task.id}:${task.specificTime}"
                if (reminderKey !in sentState.sentKeys) {
                    TaskReminderNotifier.notifyTaskReminder(
                        context = applicationContext,
                        taskId = task.id,
                        title = task.title,
                        note = task.note,
                        specificTime = task.specificTime,
                    )
                    settingsRepository.markTaskReminderSent(reminderKey)
                }
            }

        return Result.success()
    }
}