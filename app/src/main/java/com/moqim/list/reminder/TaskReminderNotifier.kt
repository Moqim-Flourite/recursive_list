package com.moqim.list.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moqim.list.MainActivity

object TaskReminderNotifier {
    private const val CHANNEL_ID = "task_reminder_channel"

    fun notifySegmentReminder(
        context: Context,
        segmentName: String,
        taskTitles: List<String>,
    ) {
        ensureChannel(context)
        val pendingIntent = buildOpenAppIntent(context)
        val content = taskTitles.take(3).joinToString("、")
        NotificationManagerCompat.from(context).notify(
            ("segment_$segmentName").hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("当前时段提醒")
                .setContentText("这个时段建议处理：$content")
                .setStyle(NotificationCompat.BigTextStyle().bigText("这个时段建议处理：$content"))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    fun notifyTaskReminder(
        context: Context,
        taskId: Long,
        title: String,
        note: String?,
        specificTime: String?,
    ) {
        ensureChannel(context)
        val pendingIntent = buildOpenAppIntent(context)
        val content = buildString {
            specificTime?.let { append("现在到 $it 了") }
            if (isNotBlank()) append(" · ")
            append(title)
            note?.takeIf { it.isNotBlank() }?.let {
                append(" · ")
                append(it)
            }
        }
        NotificationManagerCompat.from(context).notify(
            taskId.toInt(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("任务提醒")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "任务提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildOpenAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            9001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}