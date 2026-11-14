package com.aiconicstudios.pa.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aiconicstudios.pa.data.Repository

/** Alarms are wiped on reboot and on clock/time-zone changes — book them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        Repository.init(context)
        AlarmScheduler.scheduleAll(context, Repository.state.value.activities)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
