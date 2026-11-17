package com.aiconicstudios.pa.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aiconicstudios.pa.MainActivity
import com.aiconicstudios.pa.R
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.Category
import com.aiconicstudios.pa.data.Repository
import com.aiconicstudios.pa.data.ScheduleMath
import java.time.LocalDateTime

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Repository.init(context)
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ACTIVITY_ID) ?: return
        val notificationId = id.hashCode()

        when (intent.action) {
            AlarmScheduler.ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(notificationId)
                AlarmScheduler.snooze(context, id)
            }
            AlarmScheduler.ACTION_RING -> {
                val state = Repository.state.value
                val activity = if (id == AlarmScheduler.TEST_ID) {
                    Activity(id = id, title = "Test alarm", category = Category.CUSTOM, days = emptySet(),
                        startMinute = ScheduleMath.minuteOf(LocalDateTime.now().toLocalTime()), durationMinutes = 30)
                } else state.activities.firstOrNull { it.id == id } ?: return
                val snoozed = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false)
                if (!snoozed && id != AlarmScheduler.TEST_ID) {
                    // Book next week's (or next day's) occurrence straight away.
                    AlarmScheduler.schedule(context, activity, LocalDateTime.now().plusMinutes(1))
                }
                ring(context, activity, notificationId, state.settings.use24h, state.settings.ringUntilDismissed,
                    nextTask = state.workspace(id).tasks.let { t ->
                        t.firstOrNull { it.date == java.time.LocalDate.now().toString() } ?: t.firstOrNull { !it.done }
                    }?.text?.substringAfter(": "))
            }
        }
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    private fun ring(
        context: Context,
        activity: Activity,
        notificationId: Int,
        use24h: Boolean,
        insistent: Boolean,
        nextTask: String?,
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        AlarmScheduler.ensureChannel(context)

        val open = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("pa://workspace/${activity.id}"))
                .putExtra(MainActivity.EXTRA_OPEN_WORKSPACE, activity.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snooze = PendingIntent.getBroadcast(
            context, notificationId,
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmScheduler.ACTION_SNOOZE)
                .setData(Uri.parse("pa://snooze-tap/${activity.id}"))
                .putExtra(AlarmScheduler.EXTRA_ACTIVITY_ID, activity.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (activity.leadMinutes > 0)
            "${activity.category.emoji} ${activity.title} in ${activity.leadMinutes} min"
        else "${activity.category.emoji} Time for ${activity.title}!"
        val body = buildString {
            append(ScheduleMath.formatRange(activity, use24h))
            append(" · ").append(ScheduleMath.formatDuration(activity.durationMinutes))
            if (nextTask != null) append("\nToday: ").append(nextTask)
        }

        val notification = NotificationCompat.Builder(context, AlarmScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Snooze 5 min", snooze)
            .addAction(0, "Open coach", open)
            .build()
        // "Insistent" keeps the alarm sound looping until the user opens or dismisses it.
        if (insistent) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm.notify(notificationId, notification)
    }
}
