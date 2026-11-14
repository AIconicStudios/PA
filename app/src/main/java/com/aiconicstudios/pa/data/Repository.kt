package com.aiconicstudios.pa.data

import android.content.Context
import com.aiconicstudios.pa.alarm.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.Executors

/**
 * Single source of truth. The whole timetable fits comfortably in one small JSON file,
 * which keeps things simple and survives app updates.
 */
object Repository {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = Executors.newSingleThreadExecutor()
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private lateinit var appContext: Context
    private lateinit var file: File
    @Volatile private var loaded = false

    @Synchronized
    fun init(context: Context) {
        if (loaded) return
        appContext = context.applicationContext
        file = File(appContext.filesDir, "timetable.json")
        _state.value = runCatching { json.decodeFromString<AppState>(file.readText()) }
            .getOrDefault(AppState())
        loaded = true
    }

    fun update(transform: (AppState) -> AppState) {
        val before = _state.value
        val after = _state.updateAndGet(transform)
        if (after == before) return
        val text = json.encodeToString(AppState.serializer(), after)
        writer.execute {
            val tmp = File(file.parentFile, "timetable.json.tmp")
            tmp.writeText(text)
            tmp.renameTo(file)
        }
        if (after.activities != before.activities) {
            AlarmScheduler.scheduleAll(appContext, after.activities)
        }
    }

    // ---- Convenience operations -------------------------------------------------------------

    fun upsertActivity(activity: Activity) = update { s ->
        val exists = s.activities.any { it.id == activity.id }
        s.copy(
            activities = if (exists) s.activities.map { if (it.id == activity.id) activity else it }
            else s.activities + activity
        )
    }

    fun deleteActivity(id: String) = update { s ->
        s.copy(activities = s.activities.filterNot { it.id == id }, workspaces = s.workspaces - id)
    }

    fun updateWorkspace(id: String, transform: (Workspace) -> Workspace) = update { s ->
        s.copy(workspaces = s.workspaces + (id to transform(s.workspace(id))))
    }

    fun updateSettings(transform: (Settings) -> Settings) = update { s ->
        s.copy(settings = transform(s.settings))
    }

    /** A realistic sample week so first-time users can see how the board works. */
    fun loadSampleWeek() = update { s ->
        val weekdays = setOf(1, 2, 3, 4, 5)
        s.copy(
            activities = s.activities + listOf(
                Activity(title = "Studies", category = Category.STUDIES, days = weekdays,
                    startMinute = 5 * 60 + 30, durationMinutes = 120),
                Activity(title = "Work", category = Category.WORK, days = weekdays,
                    startMinute = 9 * 60, durationMinutes = 8 * 60, reminder = false),
                Activity(title = "Exercise", category = Category.EXERCISE, days = setOf(1, 3, 5),
                    startMinute = 18 * 60, durationMinutes = 60, leadMinutes = 10),
                Activity(title = "Reading", category = Category.READING, days = setOf(1, 2, 3, 4, 5, 6, 7),
                    startMinute = 21 * 60, durationMinutes = 30),
                Activity(title = "Long run", category = Category.EXERCISE, days = setOf(6),
                    startMinute = 7 * 60, durationMinutes = 90),
                Activity(title = "Meal prep", category = Category.MEALS, days = setOf(7),
                    startMinute = 16 * 60, durationMinutes = 90),
            )
        )
    }
}
