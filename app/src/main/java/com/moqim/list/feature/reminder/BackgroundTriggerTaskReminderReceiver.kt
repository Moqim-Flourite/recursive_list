package com.moqim.list.feature.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.moqim.list.worker.TaskReminderWorker

class BackgroundTriggerTaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }
}