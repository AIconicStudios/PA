package com.aiconicstudios.pa

import android.app.Application
import com.aiconicstudios.pa.alarm.AlarmScheduler
import com.aiconicstudios.pa.data.Repository

class PaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repository.init(this)
        AlarmScheduler.ensureChannel(this)
        AlarmScheduler.scheduleAll(this, Repository.state.value.activities)
    }
}
