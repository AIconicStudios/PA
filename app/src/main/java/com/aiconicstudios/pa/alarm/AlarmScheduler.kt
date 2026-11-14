package com.aiconicstudios.pa.alarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.aiconicstudios.pa.MainActivity
import com.aiconicstudios.pa.data.Activity
import com.aiconicstudios.pa.data.ScheduleMath
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every activity with a reminder has exactly one pending alarm: its next occurrence.
 * When it rings, the receiver books the following one, so the timetable repeats weekly forever.
 */
object AlarmScheduler {

    const val CHANNEL_ID = "activity_alarms"
    const val ACTION_RING = "com.aiconicstudios.pa.RING"
    const val ACTION_SNOOZE = "com.aiconicstudios.pa.SNOOZE"
    const val EXTRA_ACTIVITY_ID = "activity_id"
    const val EXTRA_SNOOZED = "snoozed"
    const val TEST_ID = "test-alarm"

    private const val PREFS = "alarms"
    private const val KEY_IDS = "scheduled_ids"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, "Activity alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings at the start of each timetable activity"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setBypassDnd(true)
        }
        nm.createNotificationChannel(channel)
    }

    /** Cancels everything previously booked and books the next occurrence of each activity. */
    @Synchronized
    fun scheduleAll(context: Context, activities: List<Activity>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().forEach { cancel(context, it) }
        val now = LocalDateTime.now()
        val booked = activities.filter { it.reminder }.onEach { schedule(context, it, now) }
        prefs.edit().putStringSet(KEY_IDS, booked.map { it.id }.toSet()).apply()
    }

    fun schedule(context: Context, activity: Activity, after: LocalDateTime) {
        if (!activity.reminder) return
        val trigger = ScheduleMath.nextTrigger(activity, after) ?: return
        setExact(context, trigger.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ringIntent(context, activity.id, snoozed = false))
    }

    fun snooze(context: Context, activityId: String, minutes: Int = 5) {
        setExact(context, System.currentTimeMillis() + minutes * 60_000L,
            ringIntent(context, activityId, snoozed = true))
    }

    fun cancel(context: Context, activityId: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(ringIntent(context, activityId, snoozed = false))
        am.cancel(ringIntent(context, activityId, snoozed = true))
    }

    fun canRingExactly(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun setExact(context: Context, atMillis: Long, operation: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (canRingExactly(context)) {
            // setAlarmClock is exact, survives Doze and shows the alarm icon in the status bar.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, openAppIntent(context)), operation)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    private fun ringIntent(context: Context, activityId: String, snoozed: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_RING)
            // A distinct data URI per activity keeps each PendingIntent unique.
            .setData(Uri.parse("pa://${if (snoozed) "snooze" else "ring"}/$activityId"))
            .putExtra(EXTRA_ACTIVITY_ID, activityId)
            .putExtra(EXTRA_SNOOZED, snoozed)
        return PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}
